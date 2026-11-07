package com.codemastery.modules.assessment.domain.model;

import java.util.UUID;

public record Question(
    UUID id,
    UUID skill_id,
    String prompt,
    String kind,
    String difficulty,
    String[] options,
    Boolean[] correct_options
) {}