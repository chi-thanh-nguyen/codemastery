package com.codemastery.modules.course.domain.model;

import java.util.UUID;

public record Lesson(UUID id,
                     UUID chapterId,
                     String title,
                     String contentMarkdown,
                     int position,
                     PublishStatus publicationStatus
                     ) {
}