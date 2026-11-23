package com.codemastery.modules.adaptive.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Immutable result of evaluating one learner's course snapshot under
 * CT24.decision.
 *
 * @param testOutLessonIds lessons eligible for test-out, in canonical course
 *                         order
 * @param recommendations  skip drafts in the same order, followed by at most
 *                         one
 *                         remedial or continue draft
 */
public record AdaptiveDecision(
        List<UUID> testOutLessonIds,
        List<Recommendation> recommendations) {

    public AdaptiveDecision {
        testOutLessonIds = List.copyOf(testOutLessonIds);
        recommendations = List.copyOf(recommendations);
    }

    public record Recommendation(
            RecommendationType type,
            UUID targetLessonId,
            String reason) {
    }
}