package com.codemastery.modules.assessment.domain.repository;

import com.codemastery.modules.assessment.domain.model.Question;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Assessment-private question persistence (ADR007 §7). Question has no Course column, so every
 * read is restricted to a caller-supplied CT40 skill set; an empty set matches nothing.
 */
public interface QuestionRepository {

    /** Inserts a new question or fully replaces the definition stored at its ID. */
    Question save(Question question);

    Optional<Question> findInSkills(UUID id, Collection<UUID> skillIds);

    /**
     * Locks the question row {@code FOR UPDATE} and returns its committed state. The lock conflicts
     * with the key-share lock a new QuizQuestion or AttemptAnswer reference takes on the row, so a
     * reference check made after it cannot be invalidated before commit.
     */
    Optional<Question> findInSkillsForUpdate(UUID id, Collection<UUID> skillIds);

    long countInSkills(Collection<UUID> skillIds);

    /** Returns every stored question tagged to any of {@code skillIds} in UUID order. */
    List<Question> findAllInSkills(Collection<UUID> skillIds);

    /** One page of the skill set's questions in UUID ascending order. */
    List<Question> findPageInSkills(Collection<UUID> skillIds, int page, int size);

    boolean isReferencedByQuizQuestion(UUID questionId);

    boolean isReferencedByAttemptAnswer(UUID questionId);

    void delete(UUID id);
}
