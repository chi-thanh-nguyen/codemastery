package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.model.Quiz;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuestionEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuizEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuizQuestionEntity;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * V004 quiz persistence. Callers own the transaction: writes run inside the Course configuration
 * write unit, and list reads inside one read-only REPEATABLE READ snapshot.
 */
@Repository
public class QuizRepositoryImpl implements QuizRepository {

    private final QuizJpaRepository jpaRepository;
    private final EntityManager entityManager;

    public QuizRepositoryImpl(QuizJpaRepository jpaRepository, EntityManager entityManager) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Override
    public Quiz save(Quiz quiz) {
        Objects.requireNonNull(quiz, "quiz");
        if (!jpaRepository.existsById(quiz.id())) {
            QuizEntity created = toEntity(quiz);
            entityManager.persist(created);
            entityManager.flush();
            return toDomain(created);
        }
        // Never a full-row merge: the Course of a stored quiz is immutable (CT17-P6 grants no
        // course_id update), so a replacement names the same Course or changes nothing.
        if (jpaRepository.updateDefinition(quiz.id(), quiz.course_id(), quiz.title(), quiz.kind(),
                quiz.time_limit_seconds(), quiz.publication_status()) != 1) {
            throw new IllegalStateException("Quiz replacement lost its row or named another Course");
        }
        return quiz;
    }

    @Override
    public void replaceMembership(UUID quizId, List<UUID> orderedQuestionIds) {
        Objects.requireNonNull(quizId, "quizId");
        List<UUID> ids = List.copyOf(orderedQuestionIds);
        // Delete before insert: the pair key is not deferrable, and positions restart at 1.
        jpaRepository.deleteMembership(quizId);
        for (int index = 0; index < ids.size(); index++) {
            entityManager.persist(new QuizQuestionEntity(UUID.randomUUID(), quizId, ids.get(index), index + 1));
        }
        entityManager.flush();
    }

    @Override
    public void delete(UUID quizId) {
        Objects.requireNonNull(quizId, "quizId");
        jpaRepository.deleteMembership(quizId);
        jpaRepository.deleteById(quizId);
        entityManager.flush();
    }

    @Override
    public Optional<Quiz> findById(UUID id) {
        return jpaRepository.findById(id).map(QuizRepositoryImpl::toDomain);
    }

    @Override
    public Optional<Quiz> findInCourse(UUID quizId, UUID courseId) {
        return jpaRepository.findInCourse(quizId, courseId).map(QuizRepositoryImpl::toDomain);
    }

    @Override
    public Optional<Quiz> findInCourseForUpdate(UUID quizId, UUID courseId) {
        return jpaRepository.lockInCourse(quizId, courseId).map(entity -> {
            // Never trust state loaded before the lock was granted (ADR005 §13.3).
            entityManager.refresh(entity);
            return toDomain(entity);
        });
    }

    @Override
    public boolean hasAttempts(UUID quizId) {
        return jpaRepository.hasAttempts(quizId);
    }

    @Override
    public long countInCourse(UUID courseId, boolean publishedOnly) {
        return publishedOnly ? jpaRepository.countPublishedInCourse(courseId) : jpaRepository.countInCourse(courseId);
    }

    @Override
    public List<Quiz> findPageInCourse(UUID courseId, boolean publishedOnly, int page, int size) {
        long offset = (long) page * size;
        List<QuizEntity> rows = publishedOnly
                ? jpaRepository.findPublishedPageInCourse(courseId, size, offset)
                : jpaRepository.findPageInCourse(courseId, size, offset);
        return rows.stream().map(QuizRepositoryImpl::toDomain).toList();
    }

    @Override
    public Map<UUID, Integer> countMembers(Collection<UUID> quizIds) {
        if (quizIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> counts = new HashMap<>();
        for (Object[] row : jpaRepository.countMembers(Set.copyOf(quizIds))) {
            counts.put((UUID) row[0], Math.toIntExact((Long) row[1]));
        }
        return Map.copyOf(counts);
    }

    @Override
    public Map<UUID, List<Member>> findMembers(Collection<UUID> quizIds) {
        if (quizIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<Member>> members = new LinkedHashMap<>();
        for (Object[] row : jpaRepository.findMembers(Set.copyOf(quizIds))) {
            members.computeIfAbsent((UUID) row[0], ignored -> new ArrayList<>())
                    .add(new Member((Integer) row[1], toDomain((QuestionEntity) row[2])));
        }
        Map<UUID, List<Member>> result = new LinkedHashMap<>();
        members.forEach((quizId, rows) -> result.put(quizId, List.copyOf(rows)));
        return result;
    }

    @Override
    public List<Question> findQuestionsInSkills(Collection<UUID> questionIds, Collection<UUID> skillIds) {
        if (questionIds.isEmpty() || skillIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findQuestionsInSkills(Set.copyOf(questionIds), Set.copyOf(skillIds)).stream()
                .map(QuizRepositoryImpl::toDomain)
                .toList();
    }

    private static QuizEntity toEntity(Quiz quiz) {
        return new QuizEntity(quiz.id(), quiz.course_id(), quiz.title(), quiz.kind(), quiz.time_limit_seconds(),
                quiz.publication_status());
    }

    private static Quiz toDomain(QuizEntity entity) {
        return new Quiz(entity.getId(), entity.getCourseId(), entity.getTitle(), entity.getKind(),
                entity.getTimeLimitSeconds(), entity.getPublicationStatus());
    }

    private static Question toDomain(QuestionEntity entity) {
        return new Question(entity.getId(), entity.getSkillId(), entity.getPrompt(), entity.getKind(),
                entity.getDifficulty(),
                entity.getOptions() == null ? null : entity.getOptions().clone(),
                entity.getCorrectOptions() == null ? null : entity.getCorrectOptions().clone());
    }
}
