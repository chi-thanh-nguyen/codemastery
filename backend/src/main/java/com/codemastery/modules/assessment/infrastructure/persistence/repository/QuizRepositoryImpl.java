package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.domain.model.Quiz;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuizEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuizQuestionEntity;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class QuizRepositoryImpl implements QuizRepository {

    private static final Comparator<Quiz> TITLE_C_THEN_UUID =
            Comparator.comparing(Quiz::title).thenComparing(q -> q.id().toString());

    private final QuizJpaRepository jpaRepository;
    private final EntityManager entityManager;

    public QuizRepositoryImpl(QuizJpaRepository jpaRepository, EntityManager entityManager) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Override
    @Transactional
    public Quiz save(Quiz quiz, List<UUID> orderedQuestionIds) {
        Objects.requireNonNull(quiz, "quiz");
        Objects.requireNonNull(orderedQuestionIds, "orderedQuestionIds");

        QuizEntity entity = new QuizEntity(
                quiz.id(),
                quiz.course_id(),
                quiz.title(),
                quiz.kind(),
                quiz.time_limit_seconds(),
                quiz.publication_status()
        );
        QuizEntity saved = jpaRepository.saveAndFlush(entity);

        jpaRepository.deleteMembershipsByQuizId(saved.getId());
        entityManager.flush();

        List<QuizQuestionEntity> rows = new ArrayList<>(orderedQuestionIds.size());
        for (int i = 0; i < orderedQuestionIds.size(); i++) {
            rows.add(new QuizQuestionEntity(
                    UUID.randomUUID(),
                    saved.getId(),
                    orderedQuestionIds.get(i),
                    i + 1
            ));
        }
        for (QuizQuestionEntity row : rows) {
            entityManager.persist(row);
        }
        entityManager.flush();

        return toDomain(saved);
    }

    @Override
    @Transactional
    public Quiz updatePublicationStatus(UUID quizId, String publicationStatus) {
        QuizEntity entity = jpaRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found: " + quizId));
        entity.setPublicationStatus(publicationStatus);
        QuizEntity saved = jpaRepository.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Quiz> findByIdAndCourseId(UUID quizId, UUID courseId) {
        if (quizId == null || courseId == null) {
            return Optional.empty();
        }
        return jpaRepository.findByIdAndCourseId(quizId, courseId).map(QuizRepositoryImpl::toDomain);
    }

    @Override
    public List<Quiz> findByCourseId(UUID courseId) {
        if (courseId == null) {
            return List.of();
        }
        return jpaRepository.findByCourseId(courseId).stream()
                .map(QuizRepositoryImpl::toDomain)
                .sorted(TITLE_C_THEN_UUID)
                .toList();
    }

    @Override
    public List<Quiz> findPublishedByCourseId(UUID courseId) {
        if (courseId == null) {
            return List.of();
        }
        return jpaRepository.findPublishedByCourseId(courseId).stream()
                .map(QuizRepositoryImpl::toDomain)
                .sorted(TITLE_C_THEN_UUID)
                .toList();
    }

    @Override
    public List<QuizQuestionMembership> findMembershipsByQuizId(UUID quizId) {
        if (quizId == null) {
            return List.of();
        }
        return jpaRepository.findMembershipsByQuizId(quizId).stream()
                .map(row -> new QuizQuestionMembership(
                        row.getId(),
                        row.getQuizId(),
                        row.getQuestionId(),
                        row.getPosition()
                ))
                .toList();
    }

    @Override
    public boolean hasAttempts(UUID quizId) {
        return quizId != null && jpaRepository.existsAttemptByQuizId(quizId);
    }

    @Override
    @Transactional
    public void deleteById(UUID quizId) {
        jpaRepository.deleteMembershipsByQuizId(quizId);
        entityManager.flush();
        jpaRepository.deleteById(quizId);
        entityManager.flush();
    }

    private static Quiz toDomain(QuizEntity entity) {
        return new Quiz(
                entity.getId(),
                entity.getCourseId(),
                entity.getTitle(),
                entity.getKind(),
                entity.getTimeLimitSeconds(),
                entity.getPublicationStatus()
        );
    }
}