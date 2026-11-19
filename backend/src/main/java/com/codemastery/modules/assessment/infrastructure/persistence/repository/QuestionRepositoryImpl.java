package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.repository.QuestionRepository;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuestionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionRepositoryImpl implements QuestionRepository {

    private final QuestionJpaRepository jpaRepository;

    public QuestionRepositoryImpl(QuestionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Question save(Question question) {
        QuestionEntity saved = jpaRepository.save(toEntity(question));
        return toDomain(saved);
    }

    @Override
    public Optional<Question> findById(UUID id) {
        return jpaRepository.findById(id).map(QuestionRepositoryImpl::toDomain);
    }

    @Override
    public List<Question> findBySkillId(UUID skillId) {
        return jpaRepository.findAllBySkillId(skillId).stream()
                .map(QuestionRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public List<Question> findBySkillIdAndDifficulty(UUID skillId, String difficulty) {
        return jpaRepository.findAllBySkillIdAndDifficulty(skillId, difficulty).stream()
                .map(QuestionRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public List<Question> findAllById(Collection<UUID> ids) {
        return jpaRepository.findAllById(ids).stream()
                .map(QuestionRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public long countBySkillId(UUID skillId) {
        return jpaRepository.countAllBySkillId(skillId);
    }

    private static QuestionEntity toEntity(Question question) {
        return new QuestionEntity(
                question.id(),
                question.skill_id(),
                question.prompt(),
                question.kind(),
                question.difficulty(),
                question.options(),
                question.correct_options()
        );
    }

    private static Question toDomain(QuestionEntity entity) {
        return new Question(
                entity.getId(),
                entity.getSkillId(),
                entity.getPrompt(),
                entity.getKind(),
                entity.getDifficulty(),
                entity.getOptions(),
                entity.getCorrectOptions()
        );
    }

    @Override
    public boolean isUsedInQuiz(UUID questionId) {
        return jpaRepository.existsInQuizByQuestionId(questionId);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}