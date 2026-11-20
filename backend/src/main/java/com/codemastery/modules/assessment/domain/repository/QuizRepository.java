package com.codemastery.modules.assessment.domain.repository;

import com.codemastery.modules.assessment.domain.model.Quiz;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizRepository {

    record QuizQuestionMembership(UUID id, UUID quizId, UUID questionId, int position) {}

    Quiz save(Quiz quiz, List<UUID> orderedQuestionIds);

    Quiz updatePublicationStatus(UUID quizId, String publicationStatus);

    Optional<Quiz> findByIdAndCourseId(UUID quizId, UUID courseId);

    List<Quiz> findByCourseId(UUID courseId);

    List<Quiz> findPublishedByCourseId(UUID courseId);

    List<QuizQuestionMembership> findMembershipsByQuizId(UUID quizId);

    boolean hasAttempts(UUID quizId);

    void deleteById(UUID quizId);
}