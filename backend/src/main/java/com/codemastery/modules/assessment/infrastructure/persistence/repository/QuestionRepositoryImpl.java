package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.repository.QuestionRepository;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuestionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class QuestionRepositoryImpl implements QuestionRepository {

    private final QuestionJpaRepository jpaRepository;

    public QuestionRepositoryImpl(QuestionJpaRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
    }

    @Override
    public Optional<Question> findById(UUID questionId) {
        if (questionId == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(questionId).map(QuestionRepositoryImpl::toDomain);
    }

    @Override
    public List<Question> findAllByIds(Collection<UUID> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllByIds(questionIds).stream()
                .map(QuestionRepositoryImpl::toDomain)
                .toList();
    }

    private static Question toDomain(QuestionEntity entity) {
        return new Question(
                entity.getId(),
                entity.getSkillId(),
                entity.getPrompt(),
                entity.getKind(),
                entity.getDifficulty(),
                entity.getOptions() == null ? new String[0] : entity.getOptions().clone(),
                entity.getCorrectOptions() == null ? new Boolean[0] : entity.getCorrectOptions().clone()
        );
    }
}