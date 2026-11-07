package com.codemastery.modules.assessment.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Assignment(
    UUID id,
    UUID course_id,
    UUID lesson_id,
    String title,
    String instructions,
    Instant deadline_at,
    String publication_status
) {}
