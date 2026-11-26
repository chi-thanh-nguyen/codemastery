package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.infrastructure.persistence.entity.AttemptAnswerEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.AttemptEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttemptJpaRepository extends JpaRepository<AttemptEntity, UUID> {

    @Query("SELECT a FROM AttemptEntity a WHERE a.id = :attemptId AND a.quiz_id = :quizId")
    Optional<AttemptEntity> findInQuiz(@Param("attemptId") UUID attemptId, @Param("quizId") UUID quizId);

    @Query("SELECT a FROM AttemptEntity a WHERE a.id = :attemptId AND a.quiz_id = :quizId AND a.user_id = :userId")
    Optional<AttemptEntity> findInQuizForLearner(@Param("attemptId") UUID attemptId,
                                                 @Param("quizId") UUID quizId,
                                                 @Param("userId") UUID userId);

    @Query(value = "SELECT * FROM public.attempts WHERE id = :attemptId AND quiz_id = :quizId FOR UPDATE",
            nativeQuery = true)
    Optional<AttemptEntity> lockInQuiz(@Param("attemptId") UUID attemptId, @Param("quizId") UUID quizId);

    @Query(value = "SELECT * FROM public.attempts WHERE quiz_id = :quizId AND user_id = :userId "
            + "AND submitted_at IS NULL ORDER BY attempt_number DESC", nativeQuery = true)
    Optional<AttemptEntity> findUnsubmittedByQuizAndLearner(@Param("quizId") UUID quizId,
                                                            @Param("userId") UUID userId);

    @Query(value = "SELECT COALESCE(MAX(attempt_number), 0) FROM public.attempts "
            + "WHERE quiz_id = :quizId AND user_id = :userId", nativeQuery = true)
    int findMaxAttemptNumber(@Param("quizId") UUID quizId, @Param("userId") UUID userId);

    @Query("SELECT COUNT(a) FROM AttemptEntity a WHERE a.quiz_id = :quizId AND a.user_id = :userId")
    long countByQuizAndLearner(@Param("quizId") UUID quizId, @Param("userId") UUID userId);

    @Query(value = "SELECT * FROM public.attempts WHERE quiz_id = :quizId AND user_id = :userId "
            + "ORDER BY attempt_number DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<AttemptEntity> findPageByQuizAndLearner(@Param("quizId") UUID quizId,
                                                 @Param("userId") UUID userId,
                                                 @Param("limit") int limit,
                                                 @Param("offset") long offset);

    @Query("SELECT aa FROM AttemptAnswerEntity aa WHERE aa.attempt_id = :attemptId ORDER BY aa.position ASC")
    List<AttemptAnswerEntity> findAnswersInPositionOrder(@Param("attemptId") UUID attemptId);

    @Query(value = "SELECT * FROM public.attempt_answers WHERE attempt_id = :attemptId "
            + "ORDER BY position ASC FOR UPDATE", nativeQuery = true)
    List<AttemptAnswerEntity> lockAnswersInPositionOrder(@Param("attemptId") UUID attemptId);

    @Query(value = "SELECT * FROM public.attempts WHERE submitted_at IS NULL AND expires_at <= :now "
            + "ORDER BY expires_at ASC, CAST(id AS text) ASC LIMIT :limit", nativeQuery = true)
    List<AttemptEntity> findDueUnsubmittedFirstPage(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = "SELECT * FROM public.attempts WHERE submitted_at IS NULL AND expires_at <= :now "
            + "AND (expires_at > :afterExpiresAt OR (expires_at = :afterExpiresAt AND CAST(id AS text) > :afterIdText)) "
            + "ORDER BY expires_at ASC, CAST(id AS text) ASC LIMIT :limit", nativeQuery = true)
    List<AttemptEntity> findDueUnsubmittedAfter(@Param("now") Instant now,
                                                @Param("afterExpiresAt") Instant afterExpiresAt,
                                                @Param("afterIdText") String afterIdText,
                                                @Param("limit") int limit);
}
