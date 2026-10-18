package com.codemastery.modules.adaptive.application.mastery;

import com.codemastery.modules.adaptive.domain.model.MasteryState;
import java.util.Objects;

public final class MasteryStateClassifier {
    public MasteryState classify(MasteryCalculator.Calculation calculation, Thresholds thresholds) {
        Objects.requireNonNull(calculation, "calculation must not be null");
        Objects.requireNonNull(thresholds, "thresholds must not be null");

        if (calculation.answeredCount() == 0) {
            return MasteryState.UNKNOWN;
        }

        long correctCount = calculation.correctCount();
        long answeredCount = calculation.answeredCount();

        if (isBelow(correctCount, answeredCount, thresholds.weak())) {
            return MasteryState.WEAK;

        }

        if (isBelow(correctCount, answeredCount, thresholds.mastered())) {
            return MasteryState.LEARNING;
        }

        return MasteryState.MASTERED;
    }

    public record Thresholds(Rational weak, Rational mastered) {
        public Thresholds {
            Objects.requireNonNull(weak, "weak threshold must not be null");
            Objects.requireNonNull(mastered, "mastered threshold must not be null");

            if (weak.compareTo(mastered) >= 0) {
                throw new IllegalArgumentException("weak threshold must be lower than mastered threshold");
            }
        }
    }

    public record Rational(long numerator, long denominator) {
        public Rational {
            if (numerator <= 0) {
                throw new IllegalArgumentException("threshold numerator must be positive");
            }

            if (denominator <= 0) {
                throw new IllegalArgumentException("threshold denominator must be positive");
            }

            if (numerator > denominator) {
                throw new IllegalArgumentException("threshold must not exceed one");
            }
        }

        private int compareTo(Rational other) {
            try {
                long left = Math.multiplyExact(numerator, other.denominator);
                long right = Math.multiplyExact(other.numerator, denominator);
                return Long.compare(left, right);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("threshold comparison overflowed", exception);
            }
        }
    }

    private boolean isBelow(long correctCount, long answeredCount, Rational threshold) {
        try {
            long left = Math.multiplyExact(correctCount, threshold.denominator);
            long right = Math.multiplyExact(answeredCount, threshold.numerator);
            return left < right;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("threshold comparison overflowed", exception);
        }
    }
}
