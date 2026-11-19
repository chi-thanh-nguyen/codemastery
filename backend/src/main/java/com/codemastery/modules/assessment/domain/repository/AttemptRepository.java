package com.codemastery.modules.assessment.domain.repository;

import com.codemastery.modules.assessment.domain.model.Attempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttemptRepository {

    Attempt save(Attempt attempt);

    Optional<Attempt> findById(UUID id);

    List<Attempt> findByQuizId(UUID quizId);

    boolean hasAnswersForQuestion(UUID questionId);
}