package com.codemastery.modules.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codemastery.modules.assessment.application.service.QuizGradingService;
import com.codemastery.modules.assessment.application.service.QuizGradingService.Grade;
import com.codemastery.modules.assessment.application.service.QuizGradingService.GradedItem;
import com.codemastery.modules.assessment.application.service.QuizGradingService.GradingItem;
import com.codemastery.modules.assessment.domain.model.AttemptAnswer;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import com.codemastery.modules.assessment.domain.model.Question;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * AS04 valid-input grading through the ADR007 §9 binding. Fixtures are built from the AS01
 * {@link Question} and {@link AttemptAnswer} shapes and are checked against the data-model
 * §11 invariants before they are converted into {@link GradingItem}s. Empty, null, duplicate
 * and mismatched inputs are outside AS04 and are deliberately not exercised.
 */
class QuizGradingServiceTest {

    private static final UUID ATTEMPT_ID = new UUID(0L, 1_000L);
    private static final UUID SKILL_ID = new UUID(0L, 2_000L);

    private final QuizGradingService gradingService = new QuizGradingService();

    @Test
    void grade_allCorrectAnswers_returns100Point00() {
        Question q1 = question(1, "single_choice", true, false, false, false);
        Question q2 = question(2, "multiple_choice", true, false, true, false);

        Grade grade = gradingService.grade(List.of(
                item(q1, answer(q1, 1, true, false, false, false)),
                item(q2, answer(q2, 2, true, false, true, false))));

        assertGrade(grade, "100.00", graded(q1, true), graded(q2, true));
    }

    @Test
    void grade_allIncorrectAnswers_returns0Point00() {
        Question q1 = question(1, "single_choice", true, false, false, false);
        Question q2 = question(2, "single_choice", false, true, false, false);

        Grade grade = gradingService.grade(List.of(
                item(q1, answer(q1, 1, false, true, false, false)),
                item(q2, answer(q2, 2, false, false, true, false))));

        assertGrade(grade, "0.00", graded(q1, false), graded(q2, false));
    }

    @Test
    void grade_multiChoicePartialOrExtraOptions_receiveZeroCredit() {
        Question partial = question(1, "multiple_choice", true, false, true, false);
        Question extra = question(2, "multiple_choice", true, false, true, false);
        Question exact = question(3, "multiple_choice", false, true, false, true);

        Grade grade = gradingService.grade(List.of(
                item(partial, answer(partial, 1, true, false, false, false)),
                item(extra, answer(extra, 2, true, true, true, false)),
                item(exact, answer(exact, 3, false, true, false, true))));

        assertGrade(grade, "33.33", graded(partial, false), graded(extra, false), graded(exact, true));
    }

    @Test
    void grade_unansweredMask_receivesZeroCredit() {
        Question unanswered = question(1, "single_choice", false, true, false, false);
        Question answered = question(2, "single_choice", true, false, false, false);

        Grade grade = gradingService.grade(List.of(
                item(unanswered, answer(unanswered, 1, false, false, false, false)),
                item(answered, answer(answered, 2, true, false, false, false))));

        assertGrade(grade, "50.00", graded(unanswered, false), graded(answered, true));
    }

    @Test
    void grade_oneCorrectOutOfThree_roundsHalfUpTo33Point33() {
        Question q1 = question(1, "single_choice", true, false, false);
        Question q2 = question(2, "single_choice", false, true, false);
        Question q3 = question(3, "single_choice", false, false, true);

        Grade grade = gradingService.grade(List.of(
                item(q1, answer(q1, 1, true, false, false)),
                item(q2, answer(q2, 2, true, false, false)),
                item(q3, answer(q3, 3, true, false, false))));

        assertGrade(grade, "33.33", graded(q1, true), graded(q2, false), graded(q3, false));
    }

    @Test
    void grade_twoCorrectOutOfThree_roundsHalfUpTo66Point67() {
        Question q1 = question(1, "single_choice", true, false, false);
        Question q2 = question(2, "single_choice", false, true, false);
        Question q3 = question(3, "single_choice", false, false, true);

        Grade grade = gradingService.grade(List.of(
                item(q1, answer(q1, 1, true, false, false)),
                item(q2, answer(q2, 2, false, true, false)),
                item(q3, answer(q3, 3, true, false, false))));

        assertGrade(grade, "66.67", graded(q1, true), graded(q2, true), graded(q3, false));
    }

    @Test
    void grade_exactHalfThousandth_roundsHalfUp() {
        // 1/32 = 3.125%: HALF_UP gives 3.13, whereas HALF_EVEN would give 3.12.
        List<GradingItem> items = new ArrayList<>();
        List<GradedItem> expected = new ArrayList<>();
        for (int number = 1; number <= 32; number++) {
            Question question = question(number, "single_choice", true, false);
            boolean correct = number == 1;
            AttemptAnswer answer = correct
                    ? answer(question, number, true, false)
                    : answer(question, number, false, true);
            items.add(item(question, answer));
            expected.add(graded(question, correct));
        }

        Grade grade = gradingService.grade(items);

        assertEquals(new BigDecimal("3.13"), grade.scorePercentage());
        assertEquals(expected, grade.items());
    }

    @Test
    void grade_itemsInNonCanonicalOrder_returnCorrectnessInInputOrderByQuestionId() {
        Question q1 = question(1, "single_choice", true, false);
        Question q2 = question(2, "single_choice", false, true);
        Question q3 = question(3, "single_choice", true, false);

        Grade grade = gradingService.grade(List.of(
                item(q3, answer(q3, 1, false, true)),
                item(q1, answer(q1, 2, true, false)),
                item(q2, answer(q2, 3, false, true))));

        assertGrade(grade, "66.67", graded(q3, false), graded(q1, true), graded(q2, true));
    }

    @Test
    void grade_everyApprovedQuestionKind_returnsPerItemCorrectness() {
        Question single = question(1, "single_choice", false, false, true);
        Question multiple = question(2, "multiple_choice", true, true, false, true);
        Question trueFalseCorrect = question(3, "true_false", true, false);
        Question trueFalseWrong = question(4, "true_false", false, true);

        Grade grade = gradingService.grade(List.of(
                item(single, answer(single, 1, false, false, true)),
                item(multiple, answer(multiple, 2, true, true, false, true)),
                item(trueFalseCorrect, answer(trueFalseCorrect, 3, true, false)),
                item(trueFalseWrong, answer(trueFalseWrong, 4, true, false))));

        assertGrade(grade, "75.00",
                graded(single, true), graded(multiple, true),
                graded(trueFalseCorrect, true), graded(trueFalseWrong, false));
    }

    @Test
    void grade_sameItems_isDeterministicAndLeavesInputUnchanged() {
        Question q1 = question(1, "multiple_choice", true, true, false);
        Question q2 = question(2, "single_choice", false, true, false);
        List<GradingItem> items = new ArrayList<>(List.of(
                item(q1, answer(q1, 1, true, true, false)),
                item(q2, answer(q2, 2, true, false, false))));
        List<GradingItem> snapshot = List.copyOf(items);

        Grade first = gradingService.grade(items);
        Grade second = gradingService.grade(items);

        assertEquals(first, second);
        assertEquals(snapshot, items);
        assertGrade(first, "50.00", graded(q1, true), graded(q2, false));
    }

    @Test
    void gradingItem_copiesMasks_soLaterSourceChangesDoNotAffectGrading() {
        Question question = question(1, "multiple_choice", true, false, true);
        AttemptAnswer answer = answer(question, 1, true, false, true);
        List<Boolean> selectedSource = new ArrayList<>(Arrays.asList(answer.selected_options()));
        GradingItem item = item(question, answer);
        GradingItem fromMutableList =
                new GradingItem(question.id(), selectedSource, Arrays.asList(question.correct_options()));

        // Arrays.asList views write through to the AS01 arrays; the item must not see it.
        answer.selected_options()[1] = true;
        question.correct_options()[0] = false;
        selectedSource.set(0, false);

        assertEquals(List.of(true, false, true), item.selectedOptions());
        assertEquals(List.of(true, false, true), item.correctOptions());
        assertEquals(List.of(true, false, true), fromMutableList.selectedOptions());
        assertThrows(UnsupportedOperationException.class, () -> item.selectedOptions().set(0, false));
        assertThrows(UnsupportedOperationException.class, () -> item.correctOptions().set(0, false));
        assertGrade(gradingService.grade(List.of(item)), "100.00", new GradedItem(question.id(), true));
    }

    @Test
    void grade_copiesGradedItems_andReturnsAnUnmodifiableList() {
        Question q1 = question(1, "single_choice", true, false);
        Question q2 = question(2, "single_choice", false, true);
        List<GradedItem> source = new ArrayList<>(List.of(graded(q1, true)));
        Grade constructed = new Grade(new BigDecimal("100.00"), source);

        source.add(graded(q2, false));
        Grade result = gradingService.grade(List.of(item(q1, answer(q1, 1, true, false))));

        assertEquals(List.of(graded(q1, true)), constructed.items());
        assertThrows(UnsupportedOperationException.class, () -> constructed.items().add(graded(q2, true)));
        assertThrows(UnsupportedOperationException.class, () -> result.items().add(graded(q2, true)));
    }

    private static void assertGrade(Grade grade, String expectedScore, GradedItem... expectedItems) {
        assertEquals(new BigDecimal(expectedScore), grade.scorePercentage());
        assertEquals(List.of(expectedItems), grade.items());
    }

    private static GradedItem graded(Question question, boolean correct) {
        return new GradedItem(question.id(), correct);
    }

    /** Converts a captured AS01 answer and its Question into the ADR007 grading input. */
    private static GradingItem item(Question question, AttemptAnswer answer) {
        if (!question.id().equals(answer.question_id())) {
            throw new IllegalArgumentException("Answer does not belong to question " + question.id());
        }
        return new GradingItem(question.id(), Arrays.asList(answer.selected_options()),
                Arrays.asList(question.correct_options()));
    }

    /**
     * Builds a valid AS01 Question: one label per answer-key entry (True/False labels for
     * true_false), at least two entries, no null elements, exactly one correct option for
     * single_choice/true_false and at least one for multiple_choice.
     */
    private static Question question(int number, String kind, Boolean... correctOptions) {
        String[] options = "true_false".equals(kind)
                ? new String[] {"True", "False"}
                : IntStream.rangeClosed(1, correctOptions.length)
                        .mapToObj(index -> "Option " + index)
                        .toArray(String[]::new);
        Question question = new Question(new UUID(0L, number), SKILL_ID, "Question " + number,
                kind, DifficultyLevel.EASY.getCode(), options, correctOptions);
        long correctCount = countTrue(correctOptions);
        boolean valid = options.length >= 2
                && options.length == correctOptions.length
                && correctCount >= 0
                && ("multiple_choice".equals(kind) ? correctCount >= 1 : correctCount == 1);
        if (!valid) {
            throw new IllegalArgumentException("Invalid Question fixture " + number);
        }
        return question;
    }

    /**
     * Builds a captured, not yet graded AS01 answer whose mask matches its Question; an
     * all-false mask means unanswered, and single-answer kinds select at most one option.
     */
    private static AttemptAnswer answer(Question question, int position, Boolean... selectedOptions) {
        long selectedCount = countTrue(selectedOptions);
        boolean valid = selectedOptions.length == question.correct_options().length
                && selectedCount >= 0
                && ("multiple_choice".equals(question.kind()) || selectedCount <= 1);
        if (!valid) {
            throw new IllegalArgumentException("Invalid AttemptAnswer fixture at position " + position);
        }
        return new AttemptAnswer(new UUID(1L, position), ATTEMPT_ID, question.id(), position,
                selectedOptions, null);
    }

    /** Returns the number of true entries, or -1 when the mask contains a null element. */
    private static long countTrue(Boolean[] mask) {
        if (Arrays.stream(mask).anyMatch(Objects::isNull)) {
            return -1;
        }
        return Arrays.stream(mask).filter(Boolean.TRUE::equals).count();
    }
}
