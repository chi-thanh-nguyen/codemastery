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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class SkillGraphService {

    private final SkillRepository skills;
    private final CourseAccessService access;
    private final CourseManagementService courseConfiguration;

    public SkillGraphService(
            SkillRepository skills,
            CourseAccessService access,
            CourseManagementService courseConfiguration) {
        this.skills = Objects.requireNonNull(skills);
        this.access = Objects.requireNonNull(access);
        this.courseConfiguration = Objects.requireNonNull(courseConfiguration);
    }

    public GraphSnapshot readGraph(UUID courseId) {
        requireId(courseId);
        access.requireCourseOwnership(courseId);
        return snapshot(load(courseId));
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
        try {
            return new SkillGraph(courseId, courseSkills, edges, mappings);
        } catch (SkillGraph.CycleException corrupt) {
            throw new IllegalStateException("Persisted skill graph is cyclic", corrupt);
        } catch (IllegalArgumentException corrupt) {
            throw new IllegalStateException("Persisted skill graph is malformed", corrupt);
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
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

    private static String optionalNonBlank(String value, String label) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(label + " must be null or nonblank");
        }
        return value;
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