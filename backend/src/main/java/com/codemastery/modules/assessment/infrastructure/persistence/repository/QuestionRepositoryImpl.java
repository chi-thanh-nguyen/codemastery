package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.repository.QuestionRepository;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuestionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionRepositoryImpl implements QuestionRepository {

    private final QuestionJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public QuestionRepositoryImpl(QuestionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Question save(Question question) {
        return toDomain(jpaRepository.save(toEntity(question)));
    }

    @Override
    public Optional<Question> findInSkills(UUID id, Collection<UUID> skillIds) {
        return skillIds.isEmpty() ? Optional.empty()
                : jpaRepository.findInSkills(id, skillIds).map(QuestionRepositoryImpl::toDomain);
    }

    @Override
    public Optional<Question> findInSkillsForUpdate(UUID id, Collection<UUID> skillIds) {
        if (skillIds.isEmpty()) {
            return Optional.empty();
        }
        // Reload after acquiring the lock so no earlier managed state of this row is trusted.
        return jpaRepository.lockInSkills(id, skillIds).map(locked -> {
            entityManager.refresh(locked);
            return toDomain(locked);
        });
    }

    @Override
    public long countInSkills(Collection<UUID> skillIds) {
        return skillIds.isEmpty() ? 0 : jpaRepository.countInSkills(skillIds);
    }

    @Override
    public List<Question> findPageInSkills(Collection<UUID> skillIds, int page, int size) {
        if (skillIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findPageInSkills(skillIds, PageRequest.of(page, size)).stream()
                .map(QuestionRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public boolean isReferencedByQuizQuestion(UUID questionId) {
        return jpaRepository.isReferencedByQuizQuestion(questionId);
    }

    @Override
    public boolean isReferencedByAttemptAnswer(UUID questionId) {
        return jpaRepository.isReferencedByAttemptAnswer(questionId);
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }

    private static QuestionEntity toEntity(Question question) {
        return new QuestionEntity(
                question.id(),
                question.skill_id(),
                question.prompt(),
                question.kind(),
                question.difficulty(),
                question.options() == null ? null : question.options().clone(),
                question.correct_options() == null ? null : question.correct_options().clone()
        );
    }

    private static Question toDomain(QuestionEntity entity) {
        return new Question(
                entity.getId(),
                entity.getSkillId(),
                entity.getPrompt(),
                entity.getKind(),
                entity.getDifficulty(),
                entity.getOptions() == null ? null : entity.getOptions().clone(),
                entity.getCorrectOptions() == null ? null : entity.getCorrectOptions().clone()
        );
    }
}
