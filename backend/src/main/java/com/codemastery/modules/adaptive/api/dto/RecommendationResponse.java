package com.codemastery.modules.adaptive.api.dto;

import com.codemastery.modules.adaptive.application.decision.PracticeDifficultySelector.PracticeChoice;
import com.codemastery.modules.adaptive.application.recommendation.RecommendationService;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Recommendation REST representations. Implemented: OpenAPI {@code B8PracticeRecommendation} and
 * {@code B8RecommendedPractice} (ADR013 §19.4). Enumerations use their lowercase contract codes, and
 * no field beyond the schema is exposed.
 */
public final class RecommendationResponse {

    private RecommendationResponse() {
    }

    /** OpenAPI {@code B8PracticeRecommendation}; {@code recommendation} is always present, possibly null. */
    public record PracticeRecommendation(UUID courseId, UUID lessonId, boolean adaptiveEnabled,
            @JsonInclude(JsonInclude.Include.ALWAYS) RecommendedPractice recommendation) {

        public static PracticeRecommendation from(RecommendationService.PracticeRecommendation result) {
            return new PracticeRecommendation(result.courseId(), result.lessonId(), result.adaptiveEnabled(),
                    result.recommendation() == null ? null : RecommendedPractice.from(result.recommendation()));
        }
    }

    /** OpenAPI {@code B8RecommendedPractice}. */
    public record RecommendedPractice(UUID quizId, String quizTitle, String difficulty, String targetDifficulty,
            boolean fallback, UUID skillId, String skillName, String skillState, String reason) {

        static RecommendedPractice from(PracticeChoice choice) {
            return new RecommendedPractice(choice.quizId(), choice.quizTitle(), choice.difficulty().getCode(),
                    choice.targetDifficulty().getCode(), choice.fallback(), choice.skillId(), choice.skillName(),
                    choice.skillState().getCode(), choice.reason());
        }
    }
}
