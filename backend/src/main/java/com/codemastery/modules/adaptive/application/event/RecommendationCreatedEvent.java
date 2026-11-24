package com.codemastery.modules.adaptive.application.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The recommendation history rows inserted by one Adaptive processing run
 * ({@code CT29-EVENT-2026-11-15-r1}, ADR012 §9.4).
 *
 * <p>Published by {@code AdaptiveDecisionService} inside the submission unit, after the inserts
 * were executed, and only for a run that inserted at least one row. {@code recommendations} lists
 * exactly those rows, in insertion order, with the V005 type literal and the reason verbatim;
 * {@code recommendedAt} is the run's single instant.
 */
public record RecommendationCreatedEvent(
        UUID courseId,
        UUID learnerId,
        UUID enrollmentId,
        Instant recommendedAt,
        List<CreatedRecommendation> recommendations) {

    public RecommendationCreatedEvent {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(enrollmentId, "enrollmentId");
        Objects.requireNonNull(recommendedAt, "recommendedAt");
        recommendations = List.copyOf(recommendations);
        if (recommendations.isEmpty()) {
            throw new IllegalArgumentException("A run that inserts nothing publishes no event");
        }
    }

    /** One inserted {@code path_recommendations} row. */
    public record CreatedRecommendation(
            UUID recommendationId,
            String type,
            UUID targetLessonId,
            String reason) {

        public CreatedRecommendation {
            Objects.requireNonNull(recommendationId, "recommendationId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(targetLessonId, "targetLessonId");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
