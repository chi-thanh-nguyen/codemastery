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
        requireDecisionShape(testOutLessonIds, recommendations);
    }

    /**
     * ADR011 §§6-7: one {@code skip} per test-out Lesson in the same order, followed by at most one
     * {@code continue} or {@code remedial} draft. A second guidance draft would give an Enrollment
     * two current rows with one instant, an integrity failure that append-only history cannot repair.
     */
    private static void requireDecisionShape(List<UUID> testOutLessonIds, List<Recommendation> recommendations) {
        int skips = 0;
        while (skips < recommendations.size() && recommendations.get(skips).type() == RecommendationType.SKIP) {
            skips++;
        }
        if (recommendations.size() - skips > 1) {
            throw new IllegalArgumentException("A decision has at most one continue or remedial draft, after its skips");
        }
        if (!recommendations.subList(0, skips).stream().map(Recommendation::targetLessonId).toList()
                .equals(testOutLessonIds)) {
            throw new IllegalArgumentException("Skip drafts must target exactly the test-out Lessons, in order");
        }
    }

    public record Recommendation(
            RecommendationType type,
            UUID targetLessonId,
            String reason) {
    }
}