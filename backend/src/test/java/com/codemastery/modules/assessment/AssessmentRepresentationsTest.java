package com.codemastery.modules.assessment;

import com.codemastery.modules.assessment.domain.model.AttemptAnswer;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuizEntity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AssessmentRepresentationsTest {

    @Test
    void courseSetterAssignsAndReplacesTheSuppliedReference() {
        QuizEntity quiz = new QuizEntity();
        UUID firstCourse = UUID.randomUUID();
        UUID secondCourse = UUID.randomUUID();

        quiz.setCourseId(firstCourse);
        assertEquals(firstCourse, quiz.getCourseId());
        quiz.setCourseId(secondCourse);
        assertEquals(secondCourse, quiz.getCourseId());
    }

    @Test
    void difficultyCodesAreExactlyTheApprovedLowercaseValues() {
        assertAll(
                () -> assertEquals("easy", DifficultyLevel.EASY.getCode()),
                () -> assertEquals("medium", DifficultyLevel.MEDIUM.getCode()),
                () -> assertEquals("hard", DifficultyLevel.HARD.getCode()),
                () -> assertEquals(List.of("easy", "medium", "hard"),
                        Arrays.stream(DifficultyLevel.values()).map(DifficultyLevel::getCode).toList()));
    }

    @Test
    void questionAndSelectionShapesSupplyTheApprovedAs04InputsWithoutGrading() {
        UUID questionId = UUID.randomUUID();
        Question question = new Question(questionId, UUID.randomUUID(), "Select both values",
                "multiple_choice", DifficultyLevel.MEDIUM.getCode(),
                new String[]{"first", "second", "third"}, new Boolean[]{true, false, true});
        AttemptAnswer answer = new AttemptAnswer(UUID.randomUUID(), UUID.randomUUID(),
                questionId, 1, new Boolean[]{true, false, true}, null);

        // ADR007's future GradingItem uses List<Boolean>; this verifies only the typed handoff.
        List<Boolean> selectedOptions = List.copyOf(Arrays.asList(answer.selected_options()));
        List<Boolean> correctOptions = List.copyOf(Arrays.asList(question.correct_options()));
        assertAll(
                () -> assertEquals(question.id(), answer.question_id()),
                () -> assertEquals(question.options().length, selectedOptions.size()),
                () -> assertEquals(List.of(true, false, true), selectedOptions),
                () -> assertEquals(List.of(true, false, true), correctOptions),
                () -> assertArrayEquals(new String[]{"first", "second", "third"}, question.options()),
                () -> assertNull(answer.is_correct()));
    }
}
