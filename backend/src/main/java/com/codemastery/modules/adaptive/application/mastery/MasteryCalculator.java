package com.codemastery.modules.adaptive.application.mastery;

import java.util.UUID;

public final class MasteryCalculator {

    public MasteryEvidenceRate calculate(SkillEvidence evidence) {
        requireNonNull(evidence, "evidence must not be null");
        return new MasteryEvidenceRate(
                evidence.courseId(),
                evidence.skillId(),
                evidence.correctCount(),
                evidence.answeredCount());
    }

    public record SkillEvidence(
            UUID courseId,
            UUID skillId,
            int correctCount,
            int answeredCount) {
        public SkillEvidence {
            requireNonNull(courseId, "courseId must not be null");
            requireNonNull(skillId, "skillId must not be null");
            validateCounts(correctCount, answeredCount);
        }
    }

    public record MasteryEvidenceRate(
            UUID courseId,
            UUID skillId,
            int correctCount,
            int answeredCount) {
        public MasteryEvidenceRate {
            requireNonNull(courseId, "courseId must not be null");
            requireNonNull(skillId, "skillId must not be null");
            validateCounts(correctCount, answeredCount);
        }

        public boolean isUnknown() {
            return answeredCount == 0;
        }
    }

    private static void validateCounts(
            int correctCount,
            int answeredCount) {
        if (correctCount < 0) {
            throw new IllegalArgumentException(
                    "correctCount must not be negative");
        }

        if (answeredCount < 0) {
            throw new IllegalArgumentException(
                    "answeredCount must not be negative");
        }

        if (correctCount > answeredCount) {
            throw new IllegalArgumentException(
                    "correctCount must not exceed answeredCount");
        }
    }

    private static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }
}
