package com.codemastery.modules.assessment.domain.repository;

import com.codemastery.modules.assessment.domain.model.Attempt;
import com.codemastery.modules.assessment.domain.model.AttemptAnswer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Assessment-private persistence boundary over V004 {@code attempts} and {@code attempt_answers}
 * (CT04, ADR007 §§8–11, §13).
 */
public interface AttemptRepository {

    /** Inserts a new attempt or updates terminal submission fields on an existing attempt. */
    Attempt save(Attempt attempt);

    Optional<Attempt> findById(UUID attemptId);

    Optional<Attempt> findInQuiz(UUID attemptId, UUID quizId);

    Optional<Attempt> findInQuizForLearner(UUID attemptId, UUID quizId, UUID userId);

    /** Locks the attempt row {@code FOR UPDATE} after Course root and Quiz locks are held. */
    Optional<Attempt> findInQuizForUpdate(UUID attemptId, UUID quizId);

    Optional<Attempt> findUnsubmittedByQuizAndLearner(UUID quizId, UUID userId);

    int findMaxAttemptNumber(UUID quizId, UUID userId);

    long countByQuizAndLearner(UUID quizId, UUID userId);

    /** Learner attempts on a quiz ordered by {@code attempt_number DESC}. */
    List<Attempt> findPageByQuizAndLearner(UUID quizId, UUID userId, int page, int size);

    /** Inserts captured answer rows in position order. */
    void insertAnswers(List<AttemptAnswer> answers);

    /** Reads captured answers ordered by {@code position ASC}. */
    List<AttemptAnswer> findAnswersInPositionOrder(UUID attemptId);

    /** Locks captured answers {@code FOR UPDATE} ordered by {@code position ASC}. */
    List<AttemptAnswer> findAnswersInPositionOrderForUpdate(UUID attemptId);

    /** Updates mutable selection/grading fields on captured answers in position order. */
    void updateAnswers(List<AttemptAnswer> answers);

    /**
     * Discovers unsubmitted due attempts ({@code submitted_at IS NULL AND expires_at <= now})
     * ordered by {@code (expires_at ASC, attempt UUID text ASC)} using keyset pagination.
     */
    List<Attempt> findDueUnsubmitted(Instant now, Instant afterExpiresAt, UUID afterAttemptId, int limit);
}