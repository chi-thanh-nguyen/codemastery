package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.domain.model.Attempt;
import com.codemastery.modules.assessment.domain.model.AttemptAnswer;
import com.codemastery.modules.assessment.domain.repository.AttemptRepository;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.AttemptAnswerEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.AttemptEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class AttemptRepositoryImpl implements AttemptRepository {

    private final AttemptJpaRepository jpaRepository;
    private final EntityManager entityManager;

    public AttemptRepositoryImpl(AttemptJpaRepository jpaRepository, EntityManager entityManager) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "jpaRepository");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public Attempt save(Attempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        AttemptEntity existing = entityManager.find(AttemptEntity.class, attempt.id());
        if (existing == null) {
            AttemptEntity created = new AttemptEntity(
                    attempt.id(),
                    attempt.quiz_id(),
                    attempt.user_id(),
                    attempt.attempt_number(),
                    attempt.started_at(),
                    attempt.expires_at(),
                    attempt.submitted_at(),
                    attempt.score_percentage()
            );
            entityManager.persist(created);
            entityManager.flush();
            return toDomain(created);
        }
        existing.setSubmittedAt(attempt.submitted_at());
        existing.setScorePercentage(attempt.score_percentage());
        entityManager.flush();
        return toDomain(existing);
    }

    @Override
    public Optional<Attempt> findById(UUID attemptId) {
        return jpaRepository.findById(attemptId).map(AttemptRepositoryImpl::toDomain);
    }

    @Override
    public Optional<Attempt> findInQuiz(UUID attemptId, UUID quizId) {
        return jpaRepository.findInQuiz(attemptId, quizId).map(AttemptRepositoryImpl::toDomain);
    }

    @Override
    public Optional<Attempt> findInQuizForLearner(UUID attemptId, UUID quizId, UUID userId) {
        return jpaRepository.findInQuizForLearner(attemptId, quizId, userId).map(AttemptRepositoryImpl::toDomain);
    }

    @Override
    public Optional<Attempt> findInQuizForUpdate(UUID attemptId, UUID quizId) {
        return jpaRepository.lockInQuiz(attemptId, quizId).map(entity -> {
            entityManager.refresh(entity);
            return toDomain(entity);
        });
    }

    @Override
    public Optional<Attempt> findUnsubmittedByQuizAndLearner(UUID quizId, UUID userId) {
        return jpaRepository.findUnsubmittedByQuizAndLearner(quizId, userId).map(AttemptRepositoryImpl::toDomain);
    }

    @Override
    public int findMaxAttemptNumber(UUID quizId, UUID userId) {
        return jpaRepository.findMaxAttemptNumber(quizId, userId);
    }

    @Override
    public long countByQuizAndLearner(UUID quizId, UUID userId) {
        return jpaRepository.countByQuizAndLearner(quizId, userId);
    }

    @Override
    public List<Attempt> findPageByQuizAndLearner(UUID quizId, UUID userId, int page, int size) {
        long offset = (long) page * size;
        return jpaRepository.findPageByQuizAndLearner(quizId, userId, size, offset).stream()
                .map(AttemptRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public void insertAnswers(List<AttemptAnswer> answers) {
        Objects.requireNonNull(answers, "answers");
        for (AttemptAnswer answer : answers) {
            entityManager.persist(new AttemptAnswerEntity(
                    answer.id(),
                    answer.attempt_id(),
                    answer.question_id(),
                    answer.position(),
                    answer.selected_options() == null ? null : answer.selected_options().clone(),
                    answer.is_correct()
            ));
        }
        entityManager.flush();
    }

    @Override
    public List<AttemptAnswer> findAnswersInPositionOrder(UUID attemptId) {
        return jpaRepository.findAnswersInPositionOrder(attemptId).stream()
                .map(AttemptRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public List<AttemptAnswer> findAnswersInPositionOrderForUpdate(UUID attemptId) {
        return jpaRepository.lockAnswersInPositionOrder(attemptId).stream()
                .map(entity -> {
                    entityManager.refresh(entity);
                    return toDomain(entity);
                })
                .toList();
    }

    @Override
    public void updateAnswers(List<AttemptAnswer> answers) {
        Objects.requireNonNull(answers, "answers");
        for (AttemptAnswer answer : answers) {
            AttemptAnswerEntity entity = entityManager.find(AttemptAnswerEntity.class, answer.id());
            if (entity == null || !entity.getAttemptId().equals(answer.attempt_id())) {
                throw new IllegalStateException("Stored attempt answer missing during update");
            }
            entity.setSelectedOptions(answer.selected_options() == null ? null : answer.selected_options().clone());
            entity.setIsCorrect(answer.is_correct());
        }
        entityManager.flush();
    }

    @Override
    public List<Attempt> findDueUnsubmitted(Instant now, Instant afterExpiresAt, UUID afterAttemptId, int limit) {
        Objects.requireNonNull(now, "now");
        List<AttemptEntity> rows = (afterExpiresAt == null || afterAttemptId == null)
                ? jpaRepository.findDueUnsubmittedFirstPage(now, limit)
                : jpaRepository.findDueUnsubmittedAfter(now, afterExpiresAt, afterAttemptId.toString(), limit);
        return rows.stream().map(AttemptRepositoryImpl::toDomain).toList();
    }

    private static Attempt toDomain(AttemptEntity entity) {
        return new Attempt(
                entity.getId(),
                entity.getUserId(),
                entity.getQuizId(),
                entity.getAttemptNumber(),
                entity.getStartedAt(),
                entity.getExpiresAt(),
                entity.getSubmittedAt(),
                entity.getScorePercentage()
        );
    }

    private static AttemptAnswer toDomain(AttemptAnswerEntity entity) {
        return new AttemptAnswer(
                entity.getId(),
                entity.getAttemptId(),
                entity.getQuestionId(),
                entity.getPosition(),
                entity.getSelectedOptions() == null ? null : entity.getSelectedOptions().clone(),
                entity.getIsCorrect()
        );
    }
}