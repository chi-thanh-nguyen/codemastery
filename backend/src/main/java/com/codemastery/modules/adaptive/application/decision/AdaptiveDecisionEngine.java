package com.codemastery.modules.adaptive.application.decision;

import com.codemastery.modules.adaptive.application.recommendation.RecommendationReasonBuilder;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision.Recommendation;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AdaptiveDecisionEngine {

    private final NextLessonResolver nextLessonResolver = new NextLessonResolver();

    /**
     * Computes test-out and next-lesson drafts from a single course snapshot.
     * The calculation is deterministic and performs no I/O.
     *
     * @param context published lessons in canonical order, mastery for every course
     *                skill
     *                (including UNKNOWN when no mastery row exists), direct
     *                prerequisites, and skill names
     * @return test-out lesson IDs and the ordered recommendation drafts
     * @throws IllegalArgumentException if the context is null
     */
    public AdaptiveDecision decide(DecisionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Decision context is required");
        }

        List<UUID> testOutLessonIds = new ArrayList<>();
        List<Recommendation> recommendations = new ArrayList<>();

        for (LessonInput lesson : context.lessons()) {
            boolean eligible = !lesson.completed()
                    && !lesson.skillIds().isEmpty()
                    && lesson.skillIds().stream()
                            .allMatch(skillId -> context.masteryBySkill().get(skillId) == MasteryState.MASTERED);

            if (eligible) {
                testOutLessonIds.add(lesson.lessonId());
                recommendations.add(new Recommendation(
                        RecommendationType.SKIP,
                        lesson.lessonId(),
                        RecommendationReasonBuilder.skip(lesson.skillIds(), context.skillNames())));
            }
        }

        nextLessonResolver.resolve(context, Set.copyOf(testOutLessonIds))
                .ifPresent(recommendations::add);
        return new AdaptiveDecision(testOutLessonIds, recommendations);
    }

    public record DecisionContext(
            UUID courseId,
            UUID learnerId,
            List<LessonInput> lessons,
            Map<UUID, MasteryState> masteryBySkill,
            List<PrerequisiteInput> prerequisites,
            Map<UUID, String> skillNames) {

        public DecisionContext {
            if (courseId == null || learnerId == null) {
                throw new IllegalArgumentException("Course and learner IDs are required");
            }

            try {
                lessons = List.copyOf(lessons);
                masteryBySkill = Map.copyOf(masteryBySkill);
                prerequisites = List.copyOf(prerequisites);
                skillNames = Map.copyOf(skillNames);
            } catch (NullPointerException cause) {
                throw new IllegalArgumentException(
                        "Decision inputs must not contain nulls", cause);
            }

            Set<UUID> lessonIds = new HashSet<>();
            for (LessonInput lesson : lessons) {
                if (!lessonIds.add(lesson.lessonId())) {
                    throw new IllegalArgumentException("Duplicate lesson ID");
                }
                for (UUID skillId : lesson.skillIds()) {
                    requireKnownSkill(skillId, masteryBySkill, skillNames);
                }
            }

            for (PrerequisiteInput edge : prerequisites) {
                requireKnownSkill(edge.skillId(), masteryBySkill, skillNames);
                requireKnownSkill(edge.prerequisiteSkillId(), masteryBySkill, skillNames);
            }
            validatePrerequisites(prerequisites, masteryBySkill.keySet());
        }
    }

    public record LessonInput(UUID lessonId, boolean completed, Set<UUID> skillIds) {
        public LessonInput {
            if (lessonId == null) {
                throw new IllegalArgumentException("Lesson ID is required");
            }
            try {
                skillIds = Set.copyOf(skillIds);
            } catch (NullPointerException cause) {
                throw new IllegalArgumentException("Lesson skill IDs are required", cause);
            }
        }
    }

    public record PrerequisiteInput(UUID skillId, UUID prerequisiteSkillId) {
        public PrerequisiteInput {
            if (skillId == null || prerequisiteSkillId == null) {
                throw new IllegalArgumentException("Both prerequisite skill IDs are required");
            }
        }
    }

    private static void requireKnownSkill(
            UUID skillId, Map<UUID, MasteryState> mastery, Map<UUID, String> names) {
        if (!mastery.containsKey(skillId) || !names.containsKey(skillId)) {
            throw new IllegalArgumentException("Referenced skill is missing mastery or name");
        }
    }

    private static void validatePrerequisites(
            List<PrerequisiteInput> prerequisites, Set<UUID> skillIds) {
        if (new HashSet<>(prerequisites).size() != prerequisites.size()) {
            throw new IllegalArgumentException("Duplicate prerequisite edge");
        }

        Map<UUID, Integer> remainingPrerequisites = new HashMap<>();
        Map<UUID, List<UUID>> dependents = new HashMap<>();
        for (UUID skillId : skillIds) {
            remainingPrerequisites.put(skillId, 0);
        }
        for (PrerequisiteInput edge : prerequisites) {
            remainingPrerequisites.merge(edge.skillId(), 1, Integer::sum);
            dependents.computeIfAbsent(edge.prerequisiteSkillId(), id -> new ArrayList<>())
                    .add(edge.skillId());
        }

        var ready = new ArrayDeque<UUID>();
        for (UUID skillId : skillIds) {
            if (remainingPrerequisites.get(skillId) == 0) {
                ready.addLast(skillId);
            }
        }

        int visited = 0;
        while (!ready.isEmpty()) {
            UUID skillId = ready.removeFirst();
            visited++;
            for (UUID dependent : dependents.getOrDefault(skillId, List.of())) {
                if (remainingPrerequisites.merge(dependent, -1, Integer::sum) == 0) {
                    ready.addLast(dependent);
                }
            }
        }

        if (visited != skillIds.size()) {
            throw new IllegalArgumentException("Prerequisites must not contain a cycle");
        }
    }
}