package com.codemastery.modules.course.application.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CT26.event ({@code CT26-EVENT-2026-11-15-r1}, ADR012 §9.1): a Lesson of a published Course made an
 * actual {@code hidden → published} transition. Published only by CT21.authoring's Lesson publication
 * operation inside its guarded transaction; Interaction's after-commit listener is the only subscriber
 * (ADR012 §8). {@code recipientLearnerIds} are the joined (active/completed) learners after completion
 * reconciliation, ascending and distinct; an empty set publishes no event.
 */
public record ContentPublishedEvent(UUID courseId, UUID lessonId, Instant publishedAt,
        List<UUID> recipientLearnerIds) {
    public ContentPublishedEvent {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(lessonId, "lessonId");
        Objects.requireNonNull(publishedAt, "publishedAt");
        recipientLearnerIds = List.copyOf(recipientLearnerIds);
    }
}
