package com.codemastery.modules.adaptive.application.mastery;

import java.util.Objects;
import java.util.UUID;

public final class MasteryCalculator {
    public Calculation calculate(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");

        if (snapshot.correctCount() < 0) {
            throw new IllegalArgumentException("correctCount must not be negative");
        }
        if (snapshot.answeredCount() < 0) {
            throw new IllegalArgumentException("answeredCount must not be negative");
        }

        if (snapshot.correctCount() > snapshot.answeredCount()) {
            throw new IllegalArgumentException("correctCount must not exceed answeredCount");
        }

        return new Calculation(snapshot.courseId(),
                snapshot.skillId(),
                snapshot.correctCount(),
                snapshot.answeredCount());
    }

    public record Snapshot(UUID courseId, UUID skillId, int correctCount, int answeredCount) {
        public Snapshot {
            Objects.requireNonNull(courseId, "courseId must not be null");
            Objects.requireNonNull(skillId, "skillId must not be null");
        }
    }

    public record Calculation(UUID courseId, UUID skillId, int correctCount, int answeredCount) {
    }
}