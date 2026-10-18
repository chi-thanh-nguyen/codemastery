package com.codemastery.modules.adaptive.mastery;

import com.codemastery.modules.adaptive.application.mastery.MasteryCalculator;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MasteryCalculatorTest {
    private final MasteryCalculator calculator = new MasteryCalculator();
    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SKILL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void preservesApprovedSnapshotValues() {
        MasteryCalculator.Snapshot snapshot = new MasteryCalculator.Snapshot(COURSE_ID, SKILL_ID, 3, 4);

        MasteryCalculator.Calculation result = calculator.calculate(snapshot);

        assertEquals(COURSE_ID, result.courseId());
        assertEquals(SKILL_ID, result.skillId());
        assertEquals(3, result.correctCount());
        assertEquals(4, result.answeredCount());
    }

    @Test
    void acceptsZeroAnsweredCountAsUnknownEvidenceInput() {
        MasteryCalculator.Snapshot snapshot = new MasteryCalculator.Snapshot(COURSE_ID, SKILL_ID, 0, 0);

        MasteryCalculator.Calculation result = calculator.calculate(snapshot);

        assertEquals(0, result.correctCount());
        assertEquals(0, result.answeredCount());
    }

    @Test
    void rejectsNegativeCorrectCount() {
        MasteryCalculator.Snapshot snapshot = new MasteryCalculator.Snapshot(COURSE_ID, SKILL_ID, -1, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(snapshot));
    }

    @Test
    void rejectsNegativeAnsweredCount() {
        MasteryCalculator.Snapshot snapshot = new MasteryCalculator.Snapshot(COURSE_ID, SKILL_ID, 0, -1);

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(snapshot));
    }

    @Test
    void rejectsMoreCorrectAnswersThanAnsweredQuestions() {
        MasteryCalculator.Snapshot snapshot = new MasteryCalculator.Snapshot(COURSE_ID, SKILL_ID, 2, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(snapshot));
    }

    @Test
    void rejectsMissingCourseId() {
        assertThrows(
                NullPointerException.class,
                () -> new MasteryCalculator.Snapshot(null, SKILL_ID, 1, 1));
    }

    @Test
    void rejectsMissingSkillId() {
        assertThrows(
                NullPointerException.class,
                () -> new MasteryCalculator.Snapshot(COURSE_ID, null, 1, 1));
    }

}
