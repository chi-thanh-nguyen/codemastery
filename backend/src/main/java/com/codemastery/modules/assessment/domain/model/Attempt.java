package com.codemastery.modules.assessment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Attempt(
    UUID id,
    UUID user_id,
    UUID quiz_id,
    Integer attempt_number,
    Instant started_at,
    Instant expires_at,
    Instant submitted_at,
    BigDecimal score_percentage
) {}