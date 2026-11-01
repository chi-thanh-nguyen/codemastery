package com.codemastery.modules.assessment.domain.model;

import java.util.UUID;

public record AttemptAnswer(
    UUID id,
    UUID attempt_id,
    UUID question_id,
    Integer position,
    Boolean[] selected_options,
    Boolean is_correct
) {}