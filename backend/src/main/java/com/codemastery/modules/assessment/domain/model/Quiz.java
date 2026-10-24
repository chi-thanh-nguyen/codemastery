package com.codemastery.modules.assessment.domain.model;

import java.util.UUID;

public record Quiz(
    UUID id,
    UUID course_id,
    String title,
    String kind,
    Integer time_limit_seconds,
    String publication_status
) {}