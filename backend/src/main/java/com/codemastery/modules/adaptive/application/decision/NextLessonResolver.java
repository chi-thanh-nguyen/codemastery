package com.codemastery.modules.adaptive.application.decision;

import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.DecisionContext;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.LessonInput;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.PrerequisiteInput;
import com.codemastery.modules.adaptive.application.recommendation.RecommendationReasonBuilder;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision.Recommendation;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class NextLessonResolver {

    /**
     * Resolves remedial guidance before considering the next incomplete lesson.
     * A completed lesson can still be selected for remedial review.
     *
     * @param context          the validated course snapshot
     * @param testOutLessonIds lessons treated as completed for this decision
     * @return the next recommendation, or empty if no remedial or continue target
     *         exists
     */
    Optional<Recommendation> resolve(DecisionContext context, Set<UUID> testOutLessonIds) {
        Map<UUID, Integer> firstLessonBySkill = new HashMap<>();
        Map<UUID, Integer> firstIncompleteLessonBySkill = new HashMap<>();
        UUID firstIncompleteLessonId = null;

        List<LessonInput> lessons = context.lessons();
        for (int index = 0; index < lessons.size(); index++) {
            LessonInput lesson = lessons.get(index);
            boolean incomplete = !lesson.completed() && !testOutLessonIds.contains(lesson.lessonId());

            if (incomplete && firstIncompleteLessonId == null) {
                firstIncompleteLessonId = lesson.lessonId();
            }

            for (UUID skillId : lesson.skillIds()) {
                firstLessonBySkill.putIfAbsent(skillId, index);
                if (incomplete) {
                    firstIncompleteLessonBySkill.putIfAbsent(skillId, index);
                }
            }
        }

        Map<UUID, List<UUID>> prerequisitesBySkill = new HashMap<>();
        for (PrerequisiteInput edge : context.prerequisites()) {
            prerequisitesBySkill.computeIfAbsent(edge.skillId(), id -> new ArrayList<>())
                    .add(edge.prerequisiteSkillId());
        }

        Comparator<UUID> weakSkillOrder = Comparator
                .comparingInt((UUID id) -> firstLessonBySkill.getOrDefault(id, Integer.MAX_VALUE))
                .thenComparing(UUID::toString);
        List<UUID> weakSkills = context.masteryBySkill().entrySet().stream()
                .filter(entry -> entry.getValue() == MasteryState.WEAK)
                .map(Map.Entry::getKey)
                .sorted(weakSkillOrder)
                .toList();

        Comparator<RemedialTarget> targetOrder = Comparator
                .comparingInt(RemedialTarget::lessonIndex)
                .thenComparing(target -> target.prerequisiteId().toString());

        for (UUID weakSkillId : weakSkills) {
            Optional<RemedialTarget> bestTarget = prerequisitesBySkill
                    .getOrDefault(weakSkillId, List.of()).stream()
                    .filter(id -> context.masteryBySkill().get(id) != MasteryState.MASTERED)
                    .filter(firstLessonBySkill::containsKey)
                    .map(id -> new RemedialTarget(id, firstIncompleteLessonBySkill
                            .getOrDefault(id, firstLessonBySkill.get(id))))
                    .min(targetOrder);

            if (bestTarget.isPresent()) {
                RemedialTarget target = bestTarget.get();
                return Optional.of(new Recommendation(
                        RecommendationType.REMEDIAL,
                        lessons.get(target.lessonIndex()).lessonId(),
                        RecommendationReasonBuilder.remedial(
                                context.skillNames().get(weakSkillId),
                                context.skillNames().get(target.prerequisiteId()))));
            }
        }

        return Optional.ofNullable(firstIncompleteLessonId)
                .map(lessonId -> new Recommendation(
                        RecommendationType.CONTINUE,
                        lessonId,
                        RecommendationReasonBuilder.continueInCourseOrder()));
    }

    List<UUID> prerequisiteWarnings(DecisionContext context, UUID lessonId) {
        LessonInput lesson = context.lessons().stream()
                .filter(candidate -> candidate.lessonId().equals(lessonId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Lesson is not in the snapshot"));

        return context.prerequisites().stream()
                .filter(edge -> lesson.skillIds().contains(edge.skillId()))
                .map(PrerequisiteInput::prerequisiteSkillId)
                .filter(prerequisite -> !lesson.skillIds().contains(prerequisite))
                .filter(prerequisite -> context.masteryBySkill().get(prerequisite) != MasteryState.MASTERED)
                .distinct()
                .sorted(RecommendationReasonBuilder.nameOrder(context.skillNames()))
                .toList();
    }

    private record RemedialTarget(UUID prerequisiteId, int lessonIndex) {
    }
}
