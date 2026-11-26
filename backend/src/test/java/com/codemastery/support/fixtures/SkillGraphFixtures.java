package com.codemastery.support.fixtures;

import com.codemastery.modules.adaptive.domain.model.LessonSkill;
import com.codemastery.modules.adaptive.domain.model.Skill;
import com.codemastery.modules.adaptive.domain.model.SkillGraph;
import com.codemastery.modules.adaptive.domain.model.SkillPrerequisite;
import java.util.List;
import java.util.UUID;

/**
 * Structural fixtures over the accepted AD01 representations ({@link Skill},
 * {@link SkillPrerequisite}, {@link LessonSkill}) and the isolated AD07
 * {@link SkillGraph} container binding (ADR005 §4.1).
 *
 * <p>
 * Reference-only helpers ({@code FT02.references}) construct unchecked domain
 * records
 * directly from caller-supplied identifiers, names, descriptions and mappings
 * without
 * invoking {@link SkillGraph}, graph algorithms, repositories or Spring
 * contexts.
 *
 * <p>
 * The {@code FT02.graph-binding} helpers isolate construction of
 * {@link SkillGraph},
 * which enforces its own structural invariants (same-course references, unique
 * identities and pairs, no self-edge, acyclicity) without inventing graph
 * records,
 * cycle scenarios, concurrency behavior, thresholds or routing decisions.
 */
public final class SkillGraphFixtures {

    // Prevent instantiation.
    private SkillGraphFixtures() {
    }

    // -------------------------------------------------------------------------
    // FT02.references: AD01 Skill, SkillPrerequisite and LessonSkill fixtures
    // -------------------------------------------------------------------------

    // Creates a skill with all caller-supplied attributes.
    public static Skill skill(
            UUID id,
            UUID courseId,
            String name,
            String description) {
        return new Skill(id, courseId, name, description);
    }

    // Creates a skill with a null optional description.
    public static Skill skill(
            UUID id,
            UUID courseId,
            String name) {
        return skill(id, courseId, name, null);
    }

    // Returns a builder for Skill representations.
    public static SkillBuilder skillBuilder() {
        return new SkillBuilder();
    }

    // Builder for Skill representations.
    public static final class SkillBuilder {
        private UUID id;
        private UUID courseId;
        private String name;
        private String description;

        public SkillBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public SkillBuilder courseId(UUID courseId) {
            this.courseId = courseId;
            return this;
        }

        public SkillBuilder name(String name) {
            this.name = name;
            return this;
        }

        public SkillBuilder description(String description) {
            this.description = description;
            return this;
        }

        public Skill build() {
            return new Skill(id, courseId, name, description);
        }
    }

    // Creates a directed skill prerequisite reference (skillId ->
    // prerequisiteSkillId).
    public static SkillPrerequisite skillPrerequisite(
            UUID id,
            UUID courseId,
            UUID skillId,
            UUID prerequisiteSkillId) {
        return new SkillPrerequisite(id, courseId, skillId, prerequisiteSkillId);
    }

    // Returns a builder for SkillPrerequisite representations.
    public static SkillPrerequisiteBuilder skillPrerequisiteBuilder() {
        return new SkillPrerequisiteBuilder();
    }

    // Builder for SkillPrerequisite representations.
    public static final class SkillPrerequisiteBuilder {
        private UUID id;
        private UUID courseId;
        private UUID skillId;
        private UUID prerequisiteSkillId;

        public SkillPrerequisiteBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public SkillPrerequisiteBuilder courseId(UUID courseId) {
            this.courseId = courseId;
            return this;
        }

        public SkillPrerequisiteBuilder skillId(UUID skillId) {
            this.skillId = skillId;
            return this;
        }

        public SkillPrerequisiteBuilder prerequisiteSkillId(UUID prerequisiteSkillId) {
            this.prerequisiteSkillId = prerequisiteSkillId;
            return this;
        }

        public SkillPrerequisite build() {
            return new SkillPrerequisite(id, courseId, skillId, prerequisiteSkillId);
        }
    }

    // Creates a lesson-to-skill mapping reference.
    public static LessonSkill lessonSkill(
            UUID id,
            UUID lessonId,
            UUID skillId) {
        return new LessonSkill(id, lessonId, skillId);
    }

    // Returns a builder for LessonSkill representations.
    public static LessonSkillBuilder lessonSkillBuilder() {
        return new LessonSkillBuilder();
    }

    // Builder for LessonSkill representations.
    public static final class LessonSkillBuilder {
        private UUID id;
        private UUID lessonId;
        private UUID skillId;

        public LessonSkillBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public LessonSkillBuilder lessonId(UUID lessonId) {
            this.lessonId = lessonId;
            return this;
        }

        public LessonSkillBuilder skillId(UUID skillId) {
            this.skillId = skillId;
            return this;
        }

        public LessonSkill build() {
            return new LessonSkill(id, lessonId, skillId);
        }
    }

    // -------------------------------------------------------------------------
    // FT02.graph-binding: isolated AD07 SkillGraph container binding
    // -------------------------------------------------------------------------

    // Binds caller-supplied references into the immutable AD07 SkillGraph
    // container.
    public static SkillGraph skillGraph(
            UUID courseId,
            List<Skill> skills,
            List<SkillPrerequisite> prerequisites,
            List<LessonSkill> mappings) {
        return new SkillGraph(courseId, skills, prerequisites, mappings);
    }

    // Binds an empty draft SkillGraph container for a caller-supplied courseId.
    public static SkillGraph emptySkillGraph(UUID courseId) {
        return skillGraph(courseId, List.of(), List.of(), List.of());
    }

    // Returns a builder for the AD07 SkillGraph container.
    public static SkillGraphBuilder skillGraphBuilder() {
        return new SkillGraphBuilder();
    }

    // Builder for SkillGraph containers; empty lists preserve valid empty-draft
    // defaults
    // while explicit null inputs pass through to SkillGraph validation unchanged.
    public static final class SkillGraphBuilder {
        private UUID courseId;
        private List<Skill> skills = List.of();
        private List<SkillPrerequisite> prerequisites = List.of();
        private List<LessonSkill> mappings = List.of();

        public SkillGraphBuilder courseId(UUID courseId) {
            this.courseId = courseId;
            return this;
        }

        public SkillGraphBuilder skills(List<Skill> skills) {
            this.skills = skills;
            return this;
        }

        public SkillGraphBuilder prerequisites(List<SkillPrerequisite> prerequisites) {
            this.prerequisites = prerequisites;
            return this;
        }

        public SkillGraphBuilder mappings(List<LessonSkill> mappings) {
            this.mappings = mappings;
            return this;
        }

        public SkillGraph build() {
            return new SkillGraph(courseId, skills, prerequisites, mappings);
        }
    }
}