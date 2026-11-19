package com.codemastery.modules.assessment.domain.repository;

import com.codemastery.modules.assessment.domain.model.Question;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository {

    Question save(Question question);

    Optional<Question> findById(UUID id);

    List<Question> findBySkillId(UUID skillId);

    List<Question> findBySkillIdAndDifficulty(UUID skillId, String difficulty);

    List<Question> findAllById(Collection<UUID> ids);

    boolean existsById(UUID id);

    long countBySkillId(UUID skillId);

    boolean isUsedInQuiz(UUID questionId);

    void deleteById(UUID id);
}