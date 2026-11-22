package com.codemastery.modules.assessment.domain.repository;

import com.codemastery.modules.assessment.domain.model.Quiz;
import java.util.Optional;
import java.util.UUID;

/** Assessment-private quiz persistence over V004 (AS05.repo); scoped quiz queries belong to CT23 composition. */
public interface QuizRepository {

    Quiz save(Quiz quiz);

    Optional<Quiz> findById(UUID id);
}
