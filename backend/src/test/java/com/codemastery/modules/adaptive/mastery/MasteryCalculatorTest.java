package com.codemastery.modules.adaptive.mastery;

import com.codemastery.modules.adaptive.application.mastery.MasteryCalculator;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasteryCalculatorTest {
    private final MasteryCalculator calculator = new MasteryCalculator();
    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SKILL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void calculateReturnsValidatedMasteryEvidenceRate() {
        MasteryCalculator.SkillEvidence evidence = new MasteryCalculator.SkillEvidence(
                COURSE_ID,
                SKILL_ID,
                3,
                4);

        MasteryCalculator.MasteryEvidenceRate result = calculator.calculate(evidence);

        assertEquals(COURSE_ID, result.courseId());
        assertEquals(SKILL_ID, result.skillId());
        assertEquals(3, result.correctCount());
        assertEquals(4, result.answeredCount());
        assertFalse(result.isUnknown());
    }

    @Test
    void zeroAnsweredCountIsUnknown() {
        MasteryCalculator.MasteryEvidenceRate result = calculator.calculate(
                new MasteryCalculator.SkillEvidence(
                        COURSE_ID,
                        SKILL_ID,
                        0,
                        0));

        assertTrue(result.isUnknown());
    }

    @Test
    void calculateRejectsNullEvidence() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(null));
    }

    @Test
    void skillEvidenceRejectsNullIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MasteryCalculator.SkillEvidence(
                        null,
                        SKILL_ID,
                        1,
                        1));

        assertThrows(
                IllegalArgumentException.class,
                () -> new MasteryCalculator.SkillEvidence(
                        COURSE_ID,
                        null,
                        1,
                        1));
    }

    @Test
    void skillEvidenceRejectsInvalidCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MasteryCalculator.SkillEvidence(
                        COURSE_ID,
                        SKILL_ID,
                        -1,
                        1));

        assertThrows(
                IllegalArgumentException.class,
                () -> new MasteryCalculator.SkillEvidence(
                        COURSE_ID,
                        SKILL_ID,
                        0,
                        -1));

        assertThrows(
                IllegalArgumentException.class,
                () -> new MasteryCalculator.SkillEvidence(
                        COURSE_ID,
                        SKILL_ID,
                        2,
                        1));
    }

    @Test
    void masteryEvidenceRateRejectsInvalidCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MasteryCalculator.MasteryEvidenceRate(
                        COURSE_ID,
                        SKILL_ID,
                        -1,
                        1));

        assertThrows(
                IllegalArgumentException.class,
                () -> new MasteryCalculator.MasteryEvidenceRate(
                        COURSE_ID,
                        SKILL_ID,
                        0,
                        -1));

        assertThrows(
                IllegalArgumentException.class,
                () -> new MasteryCalculator.MasteryEvidenceRate(
                        COURSE_ID,
                        SKILL_ID,
                        2,
                        1));
    }

    @Test
    void masteryEvidenceRateRejectsNullIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MasteryCalculator.MasteryEvidenceRate(
                        null,
                        SKILL_ID,
                        1,
                        1));

        assertThrows(
                IllegalArgumentException.class,
                () -> new MasteryCalculator.MasteryEvidenceRate(
                        COURSE_ID,
                        null,
                        1,
                        1));
    }

}
