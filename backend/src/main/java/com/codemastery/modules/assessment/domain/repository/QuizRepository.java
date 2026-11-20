package com.codemastery.modules.assessment.domain.repository;

import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.model.Quiz;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Assessment-private quiz persistence over V004 (CT23.composition/publication, ADR007 §§5–7).
 * Queries read Assessment tables only; Course scope is the quiz's own {@code course_id}, and
 * question scope is a caller-supplied CT40 skill set. Lists order by title {@code COLLATE "C"}
 * then UUID, with filtering applied before counting and paging.
 */
public interface QuizRepository {

    /** One captured membership row: its stored 1-based position and the referenced question. */
    record Member(int position, Question question) {}

    /** Inserts a new quiz row or replaces the row stored at its ID. */
    Quiz save(Quiz quiz);

    /**
     * Replaces the quiz's complete membership with {@code orderedQuestionIds} at positions 1..N in
     * the same transaction; an empty list leaves no rows.
     */
    void replaceMembership(UUID quizId, List<UUID> orderedQuestionIds);

    /** Deletes the quiz's own membership rows, then the quiz row. */
    void delete(UUID quizId);

    Optional<Quiz> findInCourse(UUID quizId, UUID courseId);

    /**
     * Locks the quiz row {@code FOR UPDATE} and returns its committed state. Called only after the
     * Course root is held, so the lock order is Course, then Quiz.
     */
    Optional<Quiz> findInCourseForUpdate(UUID quizId, UUID courseId);

    /** True when any Attempt, active or submitted, references the quiz. */
    boolean hasAttempts(UUID quizId);

    long countInCourse(UUID courseId, boolean publishedOnly);

    /** One page of the Course's quizzes, ordered by title {@code COLLATE "C"} then UUID. */
    List<Quiz> findPageInCourse(UUID courseId, boolean publishedOnly, int page, int size);

    /** Membership counts keyed by quiz ID; a quiz without rows is absent from the map. */
    Map<UUID, Integer> countMembers(Collection<UUID> quizIds);

    /** Captured membership rows in position order, keyed by quiz ID; a quiz without rows is absent. */
    Map<UUID, List<Member>> findMembers(Collection<UUID> quizIds);

    /** The questions among {@code questionIds} whose skill is in {@code skillIds}; empty input matches nothing. */
    List<Question> findQuestionsInSkills(Collection<UUID> questionIds, Collection<UUID> skillIds);
}
