package com.codemastery.modules.adaptive.domain.model;

import java.time.Instant;
import java.util.UUID;

public record PathRecommendation(
        UUID id,
        UUID enrollmentId,
        UUID lessonId,
        RecommendationType recommendationType,
        String reason,
        Instant recommendedAt) {
}