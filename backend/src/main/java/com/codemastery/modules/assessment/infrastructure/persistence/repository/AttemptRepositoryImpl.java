package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.domain.model.Attempt;
import com.codemastery.modules.assessment.domain.model.AttemptAnswer;
import com.codemastery.modules.assessment.domain.repository.AttemptRepository;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.AttemptAnswerEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.AttemptEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Set;
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
    public boolean hasSubmittedInCourse(UUID courseId) {
        return (Boolean) entityManager.createNativeQuery("""
                SELECT EXISTS (SELECT 1 FROM public.attempts a JOIN public.quizzes q ON q.id=a.quiz_id
                  WHERE q.course_id=:course AND a.submitted_at IS NOT NULL)
                """, Boolean.class).setParameter("course", courseId).getSingleResult();
    }

    @Override
    public boolean hasBlockingDiagnosticAttempt(UUID courseId, UUID learnerId, UUID quizId) {
        return (Boolean) entityManager.createNativeQuery("""
                SELECT EXISTS (SELECT 1 FROM public.attempts a JOIN public.quizzes q ON q.id=a.quiz_id
                  WHERE q.course_id=:course AND q.kind='diagnostic' AND a.user_id=:learner
                    AND (a.quiz_id<>:quiz OR a.submitted_at IS NOT NULL))
                """, Boolean.class).setParameter("course",courseId).setParameter("learner",learnerId)
                .setParameter("quiz",quizId).getSingleResult();
    }

    @Override
    public UUID findPlacementQuizId(UUID courseId, UUID learnerId, Instant now) {
        return (UUID) entityManager.createNativeQuery("""
                WITH history AS (
                  SELECT a.quiz_id,a.submitted_at,a.expires_at,q.publication_status
                  FROM public.attempts a JOIN public.quizzes q ON q.id=a.quiz_id
                  WHERE q.course_id=:course AND q.kind='diagnostic' AND a.user_id=:learner)
                SELECT CASE WHEN EXISTS (SELECT 1 FROM history) THEN
                  (SELECT CASE WHEN count(*)=1 AND bool_and(submitted_at IS NULL
                        AND expires_at>:now AND publication_status='published')
                    THEN CAST(min(CAST(quiz_id AS text)) AS uuid) ELSE NULL END FROM history)
                  ELSE (SELECT id FROM public.quizzes WHERE course_id=:course AND kind='diagnostic'
                    AND publication_status='published' ORDER BY title COLLATE "C",id LIMIT 1) END
                """, UUID.class).setParameter("course",courseId).setParameter("learner",learnerId)
                .setParameter("now",now).getSingleResult();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<EvidenceCandidate> findLatestEvidenceCandidates(UUID courseId, UUID learnerId, Set<UUID> skillIds) {
        if (skillIds.isEmpty()) return List.of();
        // Use both frozen membership and capture: corruption on either side must select an
        // integrity check, never silently become absent evidence. Validate the entire selected capture.
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT DISTINCT ON (e.skill_id) e.skill_id, a.id, a.quiz_id
                FROM public.attempts a JOIN public.quizzes z ON z.id=a.quiz_id
                CROSS JOIN LATERAL (
                  SELECT q.skill_id FROM public.quiz_questions qq JOIN public.questions q ON q.id=qq.question_id
                  WHERE qq.quiz_id=a.quiz_id
                  UNION
                  SELECT q.skill_id FROM public.attempt_answers aa JOIN public.questions q ON q.id=aa.question_id
                  WHERE aa.attempt_id=a.id) e
                WHERE z.course_id=:course AND a.user_id=:learner AND a.submitted_at IS NOT NULL
                  AND e.skill_id IN (:skills)
                ORDER BY e.skill_id, a.submitted_at DESC, a.started_at DESC,
                  CAST(a.quiz_id AS text) COLLATE "C" DESC, a.attempt_number DESC
                """, Object[].class).setParameter("course", courseId).setParameter("learner", learnerId)
                .setParameter("skills", skillIds).getResultList();
        return rows.stream().map(row -> new EvidenceCandidate((UUID) row[0], (UUID) row[1], (UUID) row[2])).toList();
    }

    @Override
    public Outcome readQuizOutcome(UUID courseId, Set<UUID> learnerIds) {
        if (learnerIds.isEmpty()) return new Outcome(0, null);
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                WITH ranked AS (
                  SELECT a.score_percentage, row_number() OVER (
                    PARTITION BY a.user_id,a.quiz_id ORDER BY a.submitted_at DESC,a.started_at DESC,
                      CAST(a.quiz_id AS text) COLLATE "C" DESC,a.attempt_number DESC) AS rank
                  FROM public.attempts a JOIN public.quizzes q ON q.id=a.quiz_id
                  WHERE q.course_id=:course AND a.user_id IN (:learners) AND a.submitted_at IS NOT NULL
                    AND q.kind IN ('practice','test'))
                SELECT count(*), round(avg(score_percentage),2) FROM ranked WHERE rank=1
                """, Object[].class).setParameter("course", courseId).setParameter("learners", learnerIds)
                .getSingleResult();
        return new Outcome(((Number) row[0]).longValue(), (BigDecimal) row[1]);
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
