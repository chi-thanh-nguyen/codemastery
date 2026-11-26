package com.codemastery.modules.assessment.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * CT27 graded-assignment notification event (ADR012 §9.2).
 * Published inside the locked grading transaction after the grade row is flushed.
 */
public record AssignmentGradedEvent(
        UUID submissionId,
        UUID assignmentId,
        UUID courseId,
        UUID lessonId,
        UUID learnerId,
        String assignmentTitle,
        boolean assignmentPublished,
        boolean regrade,
        Instant gradedAt) {
}