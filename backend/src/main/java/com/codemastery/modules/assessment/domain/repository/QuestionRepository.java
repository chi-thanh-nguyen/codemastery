package com.codemastery.modules.assessment.domain.repository;

import com.codemastery.modules.assessment.domain.model.Question;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository {

    Optional<Question> findById(UUID questionId);

    List<Question> findAllByIds(Collection<UUID> questionIds);
}