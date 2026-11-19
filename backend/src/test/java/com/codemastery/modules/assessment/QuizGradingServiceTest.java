package com.codemastery.modules.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.codemastery.modules.assessment.application.service.QuizGradingService;
import com.codemastery.modules.assessment.domain.model.AttemptAnswer;
import com.codemastery.modules.assessment.domain.model.Question;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuizGradingServiceTest {
    private QuizGradingService gradingService;

    @BeforeEach
    void setUp() {
        gradingService = new QuizGradingService();
    }

    @Test
    void gradeQuiz_allCorrectAnswers_returns100Point00() {
        UUID attemptId = UUID.randomUUID();
        UUID q1Id = UUID.randomUUID();
        UUID q2Id = UUID.randomUUID();

        List<Question> questions = List.of(
            question(q1Id, "single_choice", true, false, false, false),
            question(q2Id, "multiple_choice", true, false, true, false)
        );
        List<AttemptAnswer> answers = List.of(
            answer(attemptId, q1Id, 1, true, false, false, false),
            answer(attemptId, q2Id, 2, true, false, true, false)
        );

        BigDecimal score = gradingService.gradeQuiz(questions, answers);

        assertEquals(new BigDecimal("100.00"), score);
    }

    @Test
    void gradeQuiz_allIncorrectAnswers_returns0Point00() {
        UUID attemptId = UUID.randomUUID();
        UUID q1Id = UUID.randomUUID();
        UUID q2Id = UUID.randomUUID();

        List<Question> questions = List.of(
            question(q1Id, "single_choice", true, false, false, false),
            question(q2Id, "single_choice", false, true, false, false)
        );
        List<AttemptAnswer> answers = List.of(
            answer(attemptId, q1Id, 1, false, true, false, false),
            answer(attemptId, q2Id, 2, false, false, true, false)
        );

        BigDecimal score = gradingService.gradeQuiz(questions, answers);

        assertEquals(new BigDecimal("0.00"), score);
    }

    @Test
    void gradeQuiz_multiChoicePartialOrExtraOptions_receiveZeroCredit() {
        UUID attemptId = UUID.randomUUID();
        UUID q1Id = UUID.randomUUID();
        UUID q2Id = UUID.randomUUID();
        UUID q3Id = UUID.randomUUID();

        List<Question> questions = List.of(
            question(q1Id, "multiple_choice", true, false, true, false),
            question(q2Id, "multiple_choice", true, false, true, false),
            question(q3Id, "multiple_choice", false, true, false, true)
        );
        List<AttemptAnswer> answers = List.of(
            answer(attemptId, q1Id, 1, true, false, false, false),
            answer(attemptId, q2Id, 2, true, true, true, false),
            answer(attemptId, q3Id, 3, false, true, false, true)
        );

        BigDecimal score = gradingService.gradeQuiz(questions, answers);

        assertEquals(new BigDecimal("33.33"), score);
    }

    @Test
    void gradeQuiz_unansweredMask_receivesZeroCredit() {
        UUID attemptId = UUID.randomUUID();
        UUID q1Id = UUID.randomUUID();
        UUID q2Id = UUID.randomUUID();

        List<Question> questions = List.of(
            question(q1Id, "single_choice", false, true, false, false),
            question(q2Id, "single_choice", true, false, false, false)
        );
        List<AttemptAnswer> answers = List.of(
            answer(attemptId, q1Id, 1, false, false, false, false),
            answer(attemptId, q2Id, 2, true, false, false, false)
        );

        BigDecimal score = gradingService.gradeQuiz(questions, answers);

        assertEquals(new BigDecimal("50.00"), score);
    }

    @Test
    void gradeQuiz_oneCorrectOutOfThree_roundsHalfUpTo33Point33() {
        UUID attemptId = UUID.randomUUID();
        UUID q1Id = UUID.randomUUID();
        UUID q2Id = UUID.randomUUID();
        UUID q3Id = UUID.randomUUID();

        List<Question> questions = List.of(
            question(q1Id, "single_choice", true, false, false),
            question(q2Id, "single_choice", false, true, false),
            question(q3Id, "single_choice", false, false, true)
        );
        List<AttemptAnswer> answers = List.of(
            answer(attemptId, q1Id, 1, true, false, false),
            answer(attemptId, q2Id, 2, true, false, false),
            answer(attemptId, q3Id, 3, true, false, false)
        );

        BigDecimal score = gradingService.gradeQuiz(questions, answers);

        assertEquals(new BigDecimal("33.33"), score);
    }

    @Test
    void gradeQuiz_twoCorrectOutOfThree_roundsHalfUpTo66Point67() {
        UUID attemptId = UUID.randomUUID();
        UUID q1Id = UUID.randomUUID();
        UUID q2Id = UUID.randomUUID();
        UUID q3Id = UUID.randomUUID();

        List<Question> questions = List.of(
            question(q1Id, "single_choice", true, false, false),
            question(q2Id, "single_choice", false, true, false),
            question(q3Id, "single_choice", false, false, true)
        );
        List<AttemptAnswer> answers = List.of(
            answer(attemptId, q1Id, 1, true, false, false),
            answer(attemptId, q2Id, 2, false, true, false),
            answer(attemptId, q3Id, 3, true, false, false)
        );

        BigDecimal score = gradingService.gradeQuiz(questions, answers);

        assertEquals(new BigDecimal("66.67"), score);
    }

    @Test
    void gradeQuiz_exactHalfThousandth_roundsHalfUp() {
        UUID attemptId = UUID.randomUUID();
        List<Question> questions = new ArrayList<>();
        List<AttemptAnswer> answers = new ArrayList<>();

        for (int i = 0; i < 32; i++) {
            UUID qId = UUID.randomUUID();
            questions.add(question(qId, "single_choice", true, false));
            if (i == 0) {
                answers.add(answer(attemptId, qId, i + 1, true, false));
            } else {
                answers.add(answer(attemptId, qId, i + 1, false, true));
            }
        }

        BigDecimal score = gradingService.gradeQuiz(questions, answers);

        assertEquals(new BigDecimal("3.13"), score);
    }

    @Test
    void gradeQuiz_outOfOrderAnswers_matchesByQuestionId() {
        UUID attemptId = UUID.randomUUID();
        UUID q1Id = UUID.randomUUID();
        UUID q2Id = UUID.randomUUID();

        List<Question> questions = List.of(
            question(q1Id, "single_choice", true, false),
            question(q2Id, "single_choice", false, true)
        );
        List<AttemptAnswer> answers = List.of(
            answer(attemptId, q2Id, 2, false, true),
            answer(attemptId, q1Id, 1, true, false)
        );

        BigDecimal score = gradingService.gradeQuiz(questions, answers);

        assertEquals(new BigDecimal("100.00"), score);
    }

    private Question question(UUID id, String kind, Boolean... correctOptions) {
        return new Question(
            id,
            UUID.randomUUID(),
            "Question prompt",
            kind,
            "easy",
            new String[]{"Option 1", "Option 2", "Option 3", "Option 4"},
            correctOptions
        );
    }

    private AttemptAnswer answer(UUID attemptId, UUID questionId, int position, Boolean... selectedOptions) {
        return new AttemptAnswer(
            UUID.randomUUID(),
            attemptId,
            questionId,
            position,
            selectedOptions,
            null
        );
    }

}