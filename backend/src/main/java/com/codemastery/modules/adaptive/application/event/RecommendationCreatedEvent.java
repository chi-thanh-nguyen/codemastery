package com.codemastery.modules.adaptive.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The recommendation history rows inserted by one Adaptive processing run. */
public record RecommendationCreatedEvent(
        UUID courseId,
        UUID learnerId,
        UUID enrollmentId,
        Instant recommendedAt,
        List<CreatedRecommendation> recommendations) {

    public RecommendationCreatedEvent {
        recommendations = List.copyOf(recommendations);
    }

    public record CreatedRecommendation(
            UUID recommendationId,
            String type,
            UUID targetLessonId,
            String reason) {
    }
}