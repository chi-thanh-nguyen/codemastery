package com.codemastery.modules.adaptive.application.skillgraph;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.adaptive.domain.model.LessonSkill;
import com.codemastery.modules.adaptive.domain.model.Skill;
import com.codemastery.modules.adaptive.domain.model.SkillGraph;
import com.codemastery.modules.adaptive.domain.model.SkillPrerequisite;
import com.codemastery.modules.adaptive.domain.repository.SkillRepository;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.CourseManagementService;
import com.codemastery.modules.course.application.service.CourseStructureService;
import com.codemastery.modules.course.application.service.CourseStructureService.LessonReference;
import com.codemastery.modules.course.application.service.CourseStructureService.StructureSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * CT05/CT40 skill graph use cases (ADR005 §§3.2, 4, 5, 8.1). Every mutation runs inside
 * Course's {@code withCourseConfigurationWrite} guard, which admits the owning Instructor,
 * locks the Course row and supplies the READ COMMITTED transaction; invariant inputs are read
 * only after that lock. The owner graph read uses one read-only REPEATABLE READ snapshot, so
 * its ownership check, skills, prerequisites and mappings come from the same committed state.
 *
 * <p>Every assembled graph also proves each persisted Lesson mapping against Course's CT40
 * structural projection ({@code CourseStructureService.readStructure}, ADR005 §§3.1, 4.1): the
 * mapped Lesson must be one of the Course's own Lessons through Lesson → Chapter → Course. The
 * projection joins the caller's transaction, so the owner read sees Course and Adaptive state from
 * one snapshot and a guarded command reads it under the held Course root. A mapping outside the
 * Course, or an inconsistent projection, is a persisted-integrity failure (safe 500): nothing is
 * repaired and no partial graph is returned. Mapping commands keep the cheaper CT03
 * {@code requireLessonOwnership} assertion, which ADR005 §3.1 selects for known identities.
 */
@Service
public class SkillGraphService {

    private final SkillRepository skills;
    private final CourseAccessService access;
    private final CourseManagementService courseConfiguration;
    private final CourseStructureService structure;
    private final TransactionTemplate snapshotRead;

    public SkillGraphService(
            SkillRepository skills,
            CourseAccessService access,
            CourseManagementService courseConfiguration,
            CourseStructureService structure,
            PlatformTransactionManager transactionManager) {
        this.skills = Objects.requireNonNull(skills);
        this.access = Objects.requireNonNull(access);
        this.courseConfiguration = Objects.requireNonNull(courseConfiguration);
        this.structure = Objects.requireNonNull(structure);
        this.snapshotRead = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        snapshotRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        snapshotRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshotRead.setReadOnly(true);
    }

    /** ADR005 §§4.1, 5: owner display read from one coherent snapshot; conveys no permission. */
    public GraphSnapshot readGraph(UUID courseId) {
        requireId(courseId);
        return snapshotRead.execute(status -> {
            access.requireCourseOwnership(courseId);
            return snapshot(load(courseId));
        });
    }

    public void requireSkillInCourse(UUID courseId, UUID skillId) {
        requireId(courseId);
        requireId(skillId);
        access.requireCourseOwnership(courseId);
        if (skills.findSkill(courseId, skillId).isEmpty()) {
            throw unavailable();
        }
    }

    public List<UUID> readSkillIds(UUID courseId) {
        requireId(courseId);
        access.requireCourseOwnership(courseId);
        return skills.findSkills(courseId).stream()
                .map(Skill::id)
                .sorted(Comparator.comparing(SkillGraphService::identity))
                .toList();
    }

    public SkillReference createSkill(UUID courseId, String name, String description) {
        requireId(courseId);
        String skillName = requireNonBlank(name, "Skill name");
        String skillDescription = optionalNonBlank(description, "Skill description");
        return courseConfiguration.withCourseConfigurationWrite(
                courseId,
                () -> SkillReference.from(
                        skills.insertSkill(new Skill(UUID.randomUUID(),
                                courseId,
                                skillName,
                                skillDescription))));
    }

    public SkillReference updateSkill(UUID courseId, UUID skillId, String name, String description) {
        requireId(courseId);
        requireId(skillId);
        String skillName = requireNonBlank(name, "Skill name");
        String skillDescription = optionalNonBlank(description, "Skill description");
        return courseConfiguration.withCourseConfigurationWrite(courseId, () -> {
            if (skills.findSkill(courseId, skillId).isEmpty()) {
                throw unavailable();
            }
            return SkillReference.from(skills.updateSkill(
                    new Skill(
                            skillId,
                            courseId,
                            skillName,
                            skillDescription)));
        });
    }

    public void deleteSkill(UUID courseId, UUID skillId) {
        requireId(courseId);
        requireId(skillId);
        courseConfiguration.withCourseConfigurationWrite(courseId, () -> {
            SkillGraph graph = load(courseId);
            if (!graph.containsSkill(skillId)) {
                throw unavailable();
            }
            if (graph.referencesSkill(skillId)) {
                throw conflict("A referenced skill cannot be deleted.");
            }

            skills.deleteSkill(courseId, skillId);
            return null;
        });
    }

    public void addPrerequisite(UUID courseId, UUID skillId, UUID prerequisiteSkillId) {
        requireIds(courseId, skillId, prerequisiteSkillId);
        if (skillId.equals(prerequisiteSkillId)) {
            throw new BusinessRuleException("A skill cannot be its own prerequisite.");
        }
        courseConfiguration.withCourseConfigurationWrite(courseId, () -> {
            SkillGraph graph = load(courseId);
            requirePresent(graph, skillId);
            requirePresent(graph, prerequisiteSkillId);
            if (graph.hasDependency(skillId, prerequisiteSkillId)) {
                return null;
            }

            List<SkillPrerequisite> candidate = new ArrayList<>(graph.prerequisites());
            candidate.add(new SkillPrerequisite(UUID.randomUUID(), courseId, skillId, prerequisiteSkillId));

            requireNoCycle(courseId, graph, candidate);

            skills.insertPrerequisite(new SkillPrerequisite(UUID.randomUUID(), courseId, skillId, prerequisiteSkillId));
            return null;
        });
    }

    public void removePrerequisite(UUID courseId, UUID skillId, UUID prerequisiteSkillId) {
        requireIds(courseId, skillId, prerequisiteSkillId);
        if (skillId.equals(prerequisiteSkillId)) {
            throw new BusinessRuleException("A skill cannot be its own prerequisite.");
        }
        courseConfiguration.withCourseConfigurationWrite(courseId, () -> {
            SkillGraph graph = load(courseId);
            requirePresent(graph, skillId);
            requirePresent(graph, prerequisiteSkillId);
            if (graph.hasDependency(skillId, prerequisiteSkillId)) {
                skills.deletePrerequisite(courseId, skillId, prerequisiteSkillId);
            }
            return null;
        });
    }

    public void mapLesson(UUID courseId, UUID lessonId, UUID skillId) {
        requireIds(courseId, lessonId, skillId);
        courseConfiguration.withCourseConfigurationWrite(courseId, () -> {

            // ADR005 §4.3: Course proves Lesson -> Chapter -> Course ancestry and ownership
            // first; Adaptive then validates the skill in its own Course scope.
            access.requireLessonOwnership(courseId, lessonId);
            if (skills.findSkill(courseId, skillId).isEmpty()) {
                throw unavailable();
            }
            if (skills.findMapping(lessonId, skillId).isEmpty()) {
                skills.insertMapping(new LessonSkill(UUID.randomUUID(), lessonId, skillId));
            }
            return null;
        });
    }

    public void unmapLesson(UUID courseId, UUID lessonId, UUID skillId) {
        requireIds(courseId, lessonId, skillId);
        courseConfiguration.withCourseConfigurationWrite(courseId, () -> {
            // ADR005 §4.3: same ancestry-then-skill order as mapLesson, including the no-op path.
            access.requireLessonOwnership(courseId, lessonId);
            if (skills.findSkill(courseId, skillId).isEmpty()) {
                throw unavailable();
            }
            if (skills.findMapping(lessonId, skillId).isPresent()) {
                skills.deleteMapping(lessonId, skillId);
            }
            return null;
        });
    }

    private SkillGraph load(UUID courseId) {
        List<Skill> courseSkills = skills.findSkills(courseId);
        List<SkillPrerequisite> edges = skills.findPrerequisites(courseId);
        List<LessonSkill> mappings = skills.findMappingsForSkills(
                courseSkills.stream().map(Skill::id).toList());
        SkillGraph graph;
        try {
            graph = new SkillGraph(courseId, courseSkills, edges, mappings);
        } catch (SkillGraph.CycleException corrupt) {
            throw new IllegalStateException("Persisted skill graph is cyclic", corrupt);
        } catch (IllegalArgumentException corrupt) {
            throw new IllegalStateException("Persisted skill graph is malformed", corrupt);
        }
        requireMappedLessonsInCourse(graph);
        return graph;
    }

    /**
     * ADR005 §4.1: "mappings must pass §4.3", and a pure container cannot establish Lesson ancestry.
     * Read after the mappings, inside the same transaction, so a mapping can only reference a Lesson
     * that existed in the snapshot (owner read) or under the held Course root (guarded command).
     * An empty mapping set needs no structural read.
     */
    private void requireMappedLessonsInCourse(SkillGraph graph) {
        if (graph.mappings().isEmpty()) {
            return;
        }
        StructureSnapshot snapshot = structure.readStructure(graph.courseId());
        if (snapshot == null || !graph.courseId().equals(snapshot.courseId())) {
            throw new IllegalStateException("Course structure provider returned another Course");
        }
        Set<UUID> lessons = new HashSet<>();
        for (LessonReference lesson : snapshot.lessons()) {
            if (lesson == null || !lessons.add(lesson.lessonId())) {
                throw new IllegalStateException("Course structure provider returned an inconsistent projection");
            }
        }
        for (LessonSkill mapping : graph.mappings()) {
            if (!lessons.contains(mapping.lessonId())) {
                throw new IllegalStateException("Persisted Lesson mapping is outside the Course");
            }
        }
    }

    private void requireNoCycle(UUID courseId, SkillGraph current, List<SkillPrerequisite> candidate) {
        try {
            new SkillGraph(courseId, current.skills(), candidate, current.mappings());
        } catch (SkillGraph.CycleException cycle) {
            throw conflict("This prerequisite would create a cycle.");
        }
    }

    private static void requirePresent(SkillGraph graph, UUID skillId) {
        if (!graph.containsSkill(skillId)) {
            throw unavailable();
        }
    }

    private static GraphSnapshot snapshot(SkillGraph graph) {
        return new GraphSnapshot(
                graph.courseId(),
                graph.skills().stream().map(SkillReference::from).toList(),
                graph.prerequisites().stream()
                        .map(edge -> new PrerequisiteReference(edge.skillId(), edge.prerequisiteSkillId()))
                        .toList(),
                graph.mappings().stream()
                        .map(mapping -> new MappingReference(mapping.lessonId(), mapping.skillId()))
                        .toList());
    }

    private static String requireNonBlank(String value, String label) {
        if (value == null || !storableText(value)) {
            throw invalidText(label + " must be storable nonblank text");
        }
        return value;
    }

    private static String optionalNonBlank(String value, String label) {
        if (value != null && !storableText(value)) {
            throw invalidText(label + " must be null or storable nonblank text");
        }
        return value;
    }

    /**
     * Client text is invalid input (400), never a 500 from the V003 {@code ck_skills_text}
     * CHECK or PostgreSQL's NUL restriction. A value needs one character that is whitespace
     * under none of ECMAScript {@code \s} (the OpenAPI {@code \S} pattern), Java and
     * PostgreSQL's {@code [:space:]}. It must not contain NUL, which {@code text} cannot store, nor
     * an unpaired UTF-16 surrogate, which the JDBC driver would silently store as {@code ?} so the
     * persisted value would differ from the accepted one (the same rule as CO06's
     * {@code CourseManagementService.isStorable}). A valid surrogate pair is one supplementary code
     * point and is accepted.
     */
    private static boolean storableText(String value) {
        return value.codePoints().noneMatch(point -> point == 0
                        || point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE)
                && value.codePoints().anyMatch(point -> !Character.isWhitespace(point)
                        && !Character.isSpaceChar(point) && point != 0x0085 && point != 0xFEFF);
    }

    private static BusinessRuleException invalidText(String diagnostic) {
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, diagnostic);
    }

    private static void requireId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Identifier must not be null");
        }
    }

    private static void requireIds(UUID courseId, UUID first, UUID second) {
        requireId(courseId);
        requireId(first);
        requireId(second);
    }

    private static String identity(UUID id) {
        return id.toString().toLowerCase(Locale.ROOT);
    }

    private static ResourceNotFoundException unavailable() {
        return new ResourceNotFoundException("Resource not available");
    }

    private static BusinessRuleException conflict(String diagnostic) {
        return new BusinessRuleException(HttpStatus.CONFLICT, diagnostic);
    }

    public record GraphSnapshot(UUID courseId, List<SkillReference> skills,
            List<PrerequisiteReference> prerequisites,
            List<MappingReference> mappings) {
        public GraphSnapshot {
            skills = List.copyOf(skills);
            prerequisites = List.copyOf(prerequisites);
            mappings = List.copyOf(mappings);
        }
    }

    public record SkillReference(UUID id, String name, String description) {
        static SkillReference from(Skill skill) {
            return new SkillReference(
                    skill.id(),
                    skill.name(),
                    skill.description());
        }
    }

    public record PrerequisiteReference(UUID skillId, UUID prerequisiteSkillId) {
    }

    public record MappingReference(UUID lessonId, UUID skillId) {
    }
}