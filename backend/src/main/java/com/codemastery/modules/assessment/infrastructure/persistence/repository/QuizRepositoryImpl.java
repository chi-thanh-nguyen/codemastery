package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.domain.model.Quiz;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuizEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class QuizRepositoryImpl implements QuizRepository {

    private final QuizJpaRepository jpaRepository;

    public QuizRepositoryImpl(QuizJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Quiz save(Quiz quiz) {
        QuizEntity saved = jpaRepository.save(toEntity(quiz));
        return toDomain(saved);
    }

    @Override
    public Optional<Quiz> findById(UUID id) {
        return jpaRepository.findById(id).map(QuizRepositoryImpl::toDomain);
    }

    private static QuizEntity toEntity(Quiz quiz) {
        return new QuizEntity(
                quiz.id(),
                quiz.course_id(),
                quiz.title(),
                quiz.kind(),
                quiz.time_limit_seconds(),
                quiz.publication_status()
        );
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
