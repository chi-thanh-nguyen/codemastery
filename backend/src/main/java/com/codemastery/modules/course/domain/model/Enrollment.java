package com.codemastery.modules.course.domain.model;

import java.util.UUID;

public record Enrollment(UUID id,
                         UUID userId,
                         UUID courseId,
                         EnrollmentStatus status,
                         UUID resumeLessonId) {
}