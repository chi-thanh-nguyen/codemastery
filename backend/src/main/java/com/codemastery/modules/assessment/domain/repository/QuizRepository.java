package com.codemastery.modules.assessment.domain.repository;

import com.codemastery.modules.assessment.domain.model.Quiz;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizRepository {

    Quiz save(Quiz quiz);

    Optional<Quiz> findById(UUID id);

    List<Quiz> findByCourseId(UUID courseId);

    boolean existsById(UUID id);
}