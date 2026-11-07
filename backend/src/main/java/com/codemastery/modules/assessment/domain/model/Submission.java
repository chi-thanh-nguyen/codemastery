package com.codemastery.modules.assessment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Submission(
    UUID id,
    UUID assignment_id,
    UUID user_id,
    String object_key,
    String original_filename,
    Instant submitted_at,
    BigDecimal grade_percentage,
    String feedback,
    Instant graded_at
){}