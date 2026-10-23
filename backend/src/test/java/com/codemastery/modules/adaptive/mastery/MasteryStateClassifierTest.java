package com.codemastery.modules.adaptive.mastery;

import com.codemastery.modules.adaptive.application.mastery.MasteryCalculator;
import com.codemastery.modules.adaptive.application.mastery.MasteryStateClassifier;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class MasteryStateClassifierTest {

        private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

        private static final UUID SKILL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

        private static final MasteryStateClassifier.ClassificationThresholds DEFAULT_THRESHOLDS = new MasteryStateClassifier.ClassificationThresholds(
                        new MasteryStateClassifier.RationalThreshold(1, 2),
                        new MasteryStateClassifier.RationalThreshold(3, 4));

        private final MasteryCalculator calculator = new MasteryCalculator();
        private final MasteryStateClassifier classifier = new MasteryStateClassifier();

        @ParameterizedTest
        @MethodSource("approvedClassificationCases")
        void classifiesApprovedClassificationCases(
                        int correctCount,
                        int answeredCount,
                        MasteryState expected) {
                MasteryCalculator.MasteryEvidenceRate evidenceRate = evidenceRate(correctCount, answeredCount);

                assertEquals(
                                expected,
                                classifier.classify(evidenceRate, DEFAULT_THRESHOLDS));
        }

        private static Stream<Arguments> approvedClassificationCases() {
                return Stream.of(
                                arguments(0, 0, MasteryState.UNKNOWN),
                                arguments(0, 1, MasteryState.WEAK),
                                arguments(1, 2, MasteryState.LEARNING),
                                arguments(49, 100, MasteryState.WEAK),
                                arguments(50, 100, MasteryState.LEARNING),
                                arguments(74, 100, MasteryState.LEARNING),
                                arguments(75, 100, MasteryState.MASTERED),
                                arguments(100, 100, MasteryState.MASTERED),
                                arguments(
                                                Integer.MAX_VALUE,
                                                Integer.MAX_VALUE,
                                                MasteryState.MASTERED));
        }

        @Test
        void classifiesUsingAlternativeThresholds() {
                MasteryStateClassifier.ClassificationThresholds thresholds = new MasteryStateClassifier.ClassificationThresholds(
                                new MasteryStateClassifier.RationalThreshold(1, 3),
                                new MasteryStateClassifier.RationalThreshold(2, 3));

                assertEquals(
                                MasteryState.LEARNING,
                                classifier.classify(
                                                evidenceRate(1, 3),
                                                thresholds));

                assertEquals(
                                MasteryState.MASTERED,
                                classifier.classify(
                                                evidenceRate(2, 3),
                                                thresholds));
        }

        @Test
        void rejectsWeakThresholdAtOrAboveMasteredThreshold() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new MasteryStateClassifier.ClassificationThresholds(
                                                new MasteryStateClassifier.RationalThreshold(3, 4),
                                                new MasteryStateClassifier.RationalThreshold(1, 2)));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> new MasteryStateClassifier.ClassificationThresholds(
                                                new MasteryStateClassifier.RationalThreshold(1, 2),
                                                new MasteryStateClassifier.RationalThreshold(1, 2)));
        }

        @Test
        void rejectsZeroThresholdNumerator() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new MasteryStateClassifier.RationalThreshold(0, 1));
        }

        @Test
        void rejectsNegativeThresholdNumerator() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new MasteryStateClassifier.RationalThreshold(-1, 2));
        }

        @Test
        void rejectsZeroThresholdDenominator() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new MasteryStateClassifier.RationalThreshold(1, 0));
        }

        @Test
        void rejectsNegativeThresholdDenominator() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new MasteryStateClassifier.RationalThreshold(1, -1));
        }

        @Test
        void rejectsThresholdAboveOne() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new MasteryStateClassifier.RationalThreshold(2, 1));
        }

        @Test
        void rejectsNullWeakThreshold() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new MasteryStateClassifier.ClassificationThresholds(
                                                null,
                                                new MasteryStateClassifier.RationalThreshold(3, 4)));
        }

        @Test
        void rejectsNullMasteredThreshold() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new MasteryStateClassifier.ClassificationThresholds(
                                                new MasteryStateClassifier.RationalThreshold(1, 2),
                                                null));
        }

        @Test
        void rejectsNullEvidenceRate() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> classifier.classify(
                                                null,
                                                DEFAULT_THRESHOLDS));
        }

        @Test
        void rejectsNullThresholds() {
                MasteryCalculator.MasteryEvidenceRate evidenceRate = evidenceRate(1, 1);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> classifier.classify(
                                                evidenceRate,
                                                null));
        }

        private MasteryCalculator.MasteryEvidenceRate evidenceRate(
                        int correctCount,
                        int answeredCount) {
                return calculator.calculate(
                                new MasteryCalculator.SkillEvidence(
                                                COURSE_ID,
                                                SKILL_ID,
                                                correctCount,
                                                answeredCount));
        }
}
