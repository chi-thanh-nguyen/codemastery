package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuestionEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuizEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizJpaRepository extends JpaRepository<QuizEntity, UUID> {

    @Query("SELECT q FROM QuizEntity q WHERE q.id = :id AND q.course_id = :courseId")
    Optional<QuizEntity> findInCourse(@Param("id") UUID id, @Param("courseId") UUID courseId);

    /**
     * Native {@code FOR UPDATE}: Hibernate renders PESSIMISTIC_WRITE on PostgreSQL as
     * {@code FOR NO KEY UPDATE}, which does not conflict with the {@code FOR KEY SHARE} lock that a
     * new Attempt or QuizQuestion foreign-key reference takes on the quiz row.
     */
    @Query(value = "SELECT * FROM public.quizzes WHERE id = :id AND course_id = :courseId FOR UPDATE",
            nativeQuery = true)
    Optional<QuizEntity> lockInCourse(@Param("id") UUID id, @Param("courseId") UUID courseId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AttemptEntity a WHERE a.quiz_id = :quizId")
    boolean hasAttempts(@Param("quizId") UUID quizId);

    @Query("SELECT COUNT(q) FROM QuizEntity q WHERE q.course_id = :courseId")
    long countInCourse(@Param("courseId") UUID courseId);

    @Query("SELECT COUNT(q) FROM QuizEntity q WHERE q.course_id = :courseId AND q.publication_status = 'published'")
    long countPublishedInCourse(@Param("courseId") UUID courseId);

    /** Byte-order title ({@code COLLATE "C"}) then uuid order, both evaluated by PostgreSQL. */
    @Query(value = "SELECT * FROM public.quizzes WHERE course_id = :courseId "
            + "ORDER BY title COLLATE \"C\", id LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<QuizEntity> findPageInCourse(@Param("courseId") UUID courseId, @Param("limit") int limit,
                                      @Param("offset") long offset);

    @Query(value = "SELECT * FROM public.quizzes WHERE course_id = :courseId AND publication_status = 'published' "
            + "ORDER BY title COLLATE \"C\", id LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<QuizEntity> findPublishedPageInCourse(@Param("courseId") UUID courseId, @Param("limit") int limit,
                                               @Param("offset") long offset);

    @Query("SELECT qq.quiz_id, COUNT(qq) FROM QuizQuestionEntity qq WHERE qq.quiz_id IN :quizIds GROUP BY qq.quiz_id")
    List<Object[]> countMembers(@Param("quizIds") Collection<UUID> quizIds);

    /** Membership rows with their question, ordered by quiz then stored position. */
    @Query("SELECT qq.quiz_id, qq.position, q FROM QuizQuestionEntity qq, QuestionEntity q "
            + "WHERE q.id = qq.question_id AND qq.quiz_id IN :quizIds ORDER BY qq.quiz_id, qq.position")
    List<Object[]> findMembers(@Param("quizIds") Collection<UUID> quizIds);

    @Query("SELECT q FROM QuestionEntity q WHERE q.id IN :questionIds AND q.skill_id IN :skillIds")
    List<QuestionEntity> findQuestionsInSkills(@Param("questionIds") Collection<UUID> questionIds,
                                               @Param("skillIds") Collection<UUID> skillIds);

    /**
     * Targeted definition write (ADR012 §19.2): the runtime role may update only title, kind, time
     * limit and publication of a quiz, never its {@code course_id}, so no full-row merge is issued.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE QuizEntity q SET q.title = :title, q.kind = :kind, q.time_limit_seconds = :timeLimitSeconds, "
            + "q.publication_status = :publicationStatus WHERE q.id = :id AND q.course_id = :courseId")
    int updateDefinition(@Param("id") UUID id, @Param("courseId") UUID courseId, @Param("title") String title,
                         @Param("kind") String kind, @Param("timeLimitSeconds") Integer timeLimitSeconds,
                         @Param("publicationStatus") String publicationStatus);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM QuizQuestionEntity qq WHERE qq.quiz_id = :quizId")
    void deleteMembership(@Param("quizId") UUID quizId);
    @Query("""
            SELECT DISTINCT q.id, q.title, quest.difficulty
            FROM QuizEntity q, QuizQuestionEntity qq, QuestionEntity quest
            WHERE q.course_id = :courseId
              AND q.publication_status = 'published'
              AND q.kind = 'practice'
              AND qq.quiz_id = q.id
              AND quest.id = qq.question_id
              AND NOT EXISTS (
                  SELECT 1
                  FROM QuizQuestionEntity qq2, QuestionEntity quest2
                  WHERE qq2.quiz_id = q.id
                    AND quest2.id = qq2.question_id
                    AND quest2.skill_id NOT IN :skillIds
              )
            ORDER BY q.title, q.id
            """)
    List<Object[]> findPublishedPracticeQuizDifficultyRows(@Param("courseId") UUID courseId,
                                                           @Param("skillIds") Collection<UUID> skillIds);
}
