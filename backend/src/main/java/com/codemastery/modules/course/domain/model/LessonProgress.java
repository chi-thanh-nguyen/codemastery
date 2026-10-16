package com.codemastery.modules.course.domain.model;

import java.util.UUID;

public record LessonProgress(UUID id,
                             UUID enrollmentId,
                             UUID lessonId,
                             boolean completed,
                             boolean masterySkipped) {
}