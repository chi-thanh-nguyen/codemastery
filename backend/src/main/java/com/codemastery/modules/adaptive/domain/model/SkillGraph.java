package com.codemastery.modules.adaptive.domain.model;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable, Course-scoped skill graph (ADR005 §4.1). Dependency direction is
 * {@code skill -> prerequisite}. Construction performs no I/O and enforces only structural
 * invariants: same-Course references, unique identities and pairs, no self-edge, acyclicity.
 * It implies no pedagogical order and holds no lesson ancestry or mastery.
 */
public final class SkillGraph {

    /** The candidate graph closes a dependency cycle. */
    public static final class CycleException extends IllegalArgumentException {
        public CycleException(String message) {
            super(message);
        }
    }

    private final UUID courseId;
    private final List<Skill> skills;
    private final List<SkillPrerequisite> prerequisites;
    private final List<LessonSkill> mappings;
    private final Map<UUID, Set<UUID>> dependencies;

    public SkillGraph(
            UUID courseId,
            List<Skill> skills,
            List<SkillPrerequisite> prerequisites,
            List<LessonSkill> mappings) {
        this.courseId = Objects.requireNonNull(courseId, "courseId");

        Map<UUID, Skill> indexed = indexSkills(
                this.courseId,
                Objects.requireNonNull(skills, "skills"));

        this.skills = List.copyOf(indexed.values());

        this.prerequisites = indexPrerequisites(
                this.courseId,
                Objects.requireNonNull(prerequisites, "prerequisites"),
                indexed);
        this.mappings = indexMappings(
                Objects.requireNonNull(mappings, "mappings"),
                indexed);

        this.dependencies = dependencies(this.prerequisites);
        requireAcyclic(this.dependencies);
    }

    public UUID courseId() {
        return courseId;
    }

    public List<Skill> skills() {
        return skills;
    }

    public List<SkillPrerequisite> prerequisites() {
        return prerequisites;
    }

    public List<LessonSkill> mappings() {
        return mappings;
    }

    public boolean hasDependency(UUID skillId, UUID prerequisiteSkillId) {
        return dependencies.getOrDefault(skillId, Set.of()).contains(prerequisiteSkillId);
    }

    /** True when the skill exists here. */
    public boolean containsSkill(UUID skillId) {
        return skills.stream().anyMatch(skill -> skillId.equals(skill.id()));
    }

    public boolean referencesSkill(UUID skillId) {
        return prerequisites.stream()
                .anyMatch(edge -> skillId.equals(edge.skillId()) || skillId.equals(edge.prerequisiteSkillId()))
                || mappings.stream().anyMatch(mapping -> skillId.equals(mapping.skillId()));
    }

    private static Map<UUID, Skill> indexSkills(UUID courseId, List<Skill> skills) {
        Map<UUID, Skill> indexed = new LinkedHashMap<>();
        for (Skill skill : skills) {
            if (skill == null || skill.id() == null) {
                throw new IllegalArgumentException("Skill identity must not be null");
            }
            if (!courseId.equals(skill.courseId())) {
                throw new IllegalArgumentException("Skill belongs to another Course");
            }
            if (indexed.putIfAbsent(skill.id(), skill) != null) {
                throw new IllegalArgumentException("Duplicate skill identity");
            }
        }
        Map<UUID, Skill> ordered = new LinkedHashMap<>();
        indexed.values().stream()
                .sorted(Comparator.comparing((Skill skill) -> identity(skill.id())))
                .forEach(skill -> ordered.put(skill.id(), skill));
        return ordered;
    }

    private static List<SkillPrerequisite> indexPrerequisites(UUID courseId,
            List<SkillPrerequisite> prerequisites,
            Map<UUID, Skill> skills) {
        Set<Edge> seen = new LinkedHashSet<>();
        for (SkillPrerequisite edge : prerequisites) {
            if (edge == null || edge.skillId() == null || edge.prerequisiteSkillId() == null) {
                throw new IllegalArgumentException("Prerequisite identity must not be null");
            }
            if (!courseId.equals(edge.courseId())) {
                throw new IllegalArgumentException("Prerequisite belongs to another Course");
            }
            if (!skills.containsKey(edge.skillId()) || !skills.containsKey(edge.prerequisiteSkillId())) {
                throw new IllegalArgumentException("Prerequisite references a skill outside this Course");
            }
            if (edge.skillId().equals(edge.prerequisiteSkillId())) {
                throw new IllegalArgumentException("A skill cannot be its own prerequisite");
            }
            if (!seen.add(new Edge(edge.skillId(), edge.prerequisiteSkillId()))) {
                throw new IllegalArgumentException("Duplicate prerequisite edge");
            }
        }
        return prerequisites.stream()
                .sorted(Comparator.comparing((SkillPrerequisite edge) -> identity(edge.skillId()))
                        .thenComparing(edge -> identity(edge.prerequisiteSkillId())))
                .toList();
    }

    private static List<LessonSkill> indexMappings(List<LessonSkill> mappings, Map<UUID, Skill> skills) {
        Set<Edge> seen = new LinkedHashSet<>();
        for (LessonSkill mapping : mappings) {
            if (mapping == null || mapping.lessonId() == null || mapping.skillId() == null) {
                throw new IllegalArgumentException("Mapping identity must not be null");
            }
            if (!skills.containsKey(mapping.skillId())) {
                throw new IllegalArgumentException("Mapping references a skill outside this Course");
            }
            if (!seen.add(new Edge(mapping.lessonId(), mapping.skillId()))) {
                throw new IllegalArgumentException("Duplicate lesson mapping");
            }
        }
        return mappings.stream()
                .sorted(Comparator.comparing((LessonSkill mapping) -> identity(mapping.lessonId()))
                        .thenComparing(mapping -> identity(mapping.skillId())))
                .toList();
    }

    private static Map<UUID, Set<UUID>> dependencies(List<SkillPrerequisite> prerequisites) {
        Map<UUID, Set<UUID>> targets = new LinkedHashMap<>();
        for (SkillPrerequisite edge : prerequisites) {
            targets.computeIfAbsent(edge.skillId(), ignored -> new LinkedHashSet<>())
                    .add(edge.prerequisiteSkillId());
        }
        return targets;
    }

    /**
     * Deterministic depth-first search over full reachability with a visited set; no fixed
     * traversal depth. The traversal keeps its own stack, so an arbitrarily long dependency
     * path cannot exhaust the thread stack (ADR005 §4.1, B1-O02).
     */
    private static void requireAcyclic(Map<UUID, Set<UUID>> dependencies) {
        Set<UUID> visiting = new HashSet<>();
        Set<UUID> finished = new HashSet<>();
        Deque<Map.Entry<UUID, Iterator<UUID>>> path = new ArrayDeque<>();
        for (UUID root : dependencies.keySet()) {
            if (finished.contains(root)) {
                continue;
            }
            visiting.add(root);
            path.push(Map.entry(root, dependencies.getOrDefault(root, Set.of()).iterator()));
            while (!path.isEmpty()) {
                Map.Entry<UUID, Iterator<UUID>> top = path.peek();
                if (!top.getValue().hasNext()) {
                    path.pop();
                    visiting.remove(top.getKey());
                    finished.add(top.getKey());
                    continue;
                }
                UUID prerequisite = top.getValue().next();
                if (finished.contains(prerequisite)) {
                    continue;
                }
                if (!visiting.add(prerequisite)) {
                    throw new CycleException("Prerequisites must not contain a cycle");
                }
                path.push(Map.entry(prerequisite, dependencies.getOrDefault(prerequisite, Set.of()).iterator()));
            }
        }
    }

    private static String identity(UUID id) {
        return id.toString().toLowerCase(Locale.ROOT);
    }

    private record Edge(UUID first, UUID second) {
    }
}