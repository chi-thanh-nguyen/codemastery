package com.codemastery.modules.adaptive.mastery;

import com.codemastery.modules.adaptive.application.mastery.MasteryCalculator;
import com.codemastery.modules.adaptive.application.mastery.MasteryStateClassifier;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class MasteryStateClassifierTest {
        private final MasteryCalculator calculator = new MasteryCalculator();
        private final MasteryStateClassifier classifier = new MasteryStateClassifier();

        private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

        private static final UUID SKILL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

        private static final MasteryStateClassifier.Thresholds THRESHOLDS = new MasteryStateClassifier.Thresholds(
                        new MasteryStateClassifier.Rational(1, 2),
                        new MasteryStateClassifier.Rational(3, 4));

        @ParameterizedTest
        @MethodSource("approvedClassificationCases")
        void classifiesApprovedCases(int correctCount, int answeredCount, MasteryState expected) {
                MasteryCalculator.Calculation calculation = calculation(correctCount, answeredCount);

                assertEquals(expected,
                                classifier.classify(calculation, THRESHOLDS));
        }

        private static Stream<Arguments> approvedClassificationCases() {
                return Stream.of(
                                arguments(0, 0, MasteryState.UNKNOWN),
                                arguments(0, 1, MasteryState.WEAK),
                                arguments(1, 2, MasteryState.LEARNING),
                                arguments(2, 3, MasteryState.LEARNING),
                                arguments(3, 4, MasteryState.MASTERED),
                                arguments(1, 1, MasteryState.MASTERED));
        }

        @Test
        void classifiesValuesBelowWeakThresholdAsWeak() {
                MasteryCalculator.Calculation calculation = calculation(1, 3);

                assertEquals(
                                MasteryState.WEAK,
                                classifier.classify(calculation, THRESHOLDS));
        }

        @Test
        void classifiesWeakThresholdAsLearning() {
                MasteryCalculator.Calculation calculation = calculation(1, 2);

                assertEquals(
                                MasteryState.LEARNING,
                                classifier.classify(calculation, THRESHOLDS));
        }

        @Test
        void classifiesMasteredThresholdAsMastered() {
                MasteryCalculator.Calculation calculation = calculation(3, 4);

                assertEquals(
                                MasteryState.MASTERED,
                                classifier.classify(calculation, THRESHOLDS));
        }

        @Test
        void rejectsInvalidThresholdOrder() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new MasteryStateClassifier.Thresholds(
                                                new MasteryStateClassifier.Rational(3, 4),
                                                new MasteryStateClassifier.Rational(1, 2)));
        }

        @Test
        void rejectsZeroThresholdNumerator() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new MasteryStateClassifier.Rational(0, 1));
        }

        @Test
        void rejectsNonPositiveThresholdDenominator() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new MasteryStateClassifier.Rational(1, 0));
        }

        @Test
        void rejectsNullCalculation() {
                assertThrows(
                                NullPointerException.class,
                                () -> classifier.classify(null, THRESHOLDS));
        }

        @Test
        void rejectsNullThresholds() {
                MasteryCalculator.Calculation calculation = calculation(1, 1);

                assertThrows(
                                NullPointerException.class,
                                () -> classifier.classify(calculation, null));
        }

        private MasteryCalculator.Calculation calculation(
                        int correctCount,
                        int answeredCount) {
                return calculator.calculate(new MasteryCalculator.Snapshot(
                                COURSE_ID,
                                SKILL_ID,
                                correctCount,
                                answeredCount));
        }
}