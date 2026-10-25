package com.codemastery.modules.adaptive.application.mastery;

import com.codemastery.modules.adaptive.domain.model.MasteryState;

public final class MasteryStateClassifier {

    public MasteryState classify(
            MasteryCalculator.MasteryEvidenceRate evidenceRate,
            ClassificationThresholds thresholds) {
        requireNonNull(evidenceRate, "evidenceRate must not be null");
        requireNonNull(thresholds, "thresholds must not be null");

        if (evidenceRate.isUnknown()) {
            return MasteryState.UNKNOWN;
        }

        long correctCount = evidenceRate.correctCount();
        long answeredCount = evidenceRate.answeredCount();

        if (isBelow(
                correctCount,
                answeredCount,
                thresholds.weak())) {
            return MasteryState.WEAK;
        }

        if (isBelow(
                correctCount,
                answeredCount,
                thresholds.mastered())) {
            return MasteryState.LEARNING;
        }

        return MasteryState.MASTERED;
    }

    public record ClassificationThresholds(
            RationalThreshold weak,
            RationalThreshold mastered) {

        public ClassificationThresholds {
            requireNonNull(weak, "weak threshold must not be null");
            requireNonNull(mastered, "mastered threshold must not be null");

            if (weak.compareTo(mastered) >= 0) {
                throw new IllegalArgumentException(
                        "weak threshold must be lower than mastered threshold");
            }
        }
    }

    public record RationalThreshold(
            int numerator,
            int denominator) {

        public RationalThreshold {
            if (numerator <= 0) {
                throw new IllegalArgumentException(
                        "threshold numerator must be positive");
            }

            if (denominator <= 0) {
                throw new IllegalArgumentException(
                        "threshold denominator must be positive");
            }

            if (numerator > denominator) {
                throw new IllegalArgumentException(
                        "threshold must not exceed one");
            }
        }

        private int compareTo(RationalThreshold other) {
            requireNonNull(other, "threshold must not be null");

            long left = (long) numerator * other.denominator();
            long right = (long) other.numerator() * denominator;

            return Long.compare(left, right);
        }
    }

    private static boolean isBelow(
            long correctCount,
            long answeredCount,
            RationalThreshold threshold) {

        long left = correctCount * threshold.denominator();
        long right = answeredCount * threshold.numerator();

        return left < right;
    }

    private static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }
}
