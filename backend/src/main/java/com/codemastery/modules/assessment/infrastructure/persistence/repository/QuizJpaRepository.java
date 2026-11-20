package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuizEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuizQuestionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizJpaRepository extends JpaRepository<QuizEntity, UUID> {

    @Query("SELECT q FROM QuizEntity q WHERE q.id = :quizId AND q.course_id = :courseId")
    Optional<QuizEntity> findByIdAndCourseId(@Param("quizId") UUID quizId, @Param("courseId") UUID courseId);

    @Query("SELECT q FROM QuizEntity q WHERE q.course_id = :courseId")
    List<QuizEntity> findByCourseId(@Param("courseId") UUID courseId);

    @Query("SELECT q FROM QuizEntity q WHERE q.course_id = :courseId AND q.publication_status = 'published'")
    List<QuizEntity> findPublishedByCourseId(@Param("courseId") UUID courseId);

    @Query("SELECT qq FROM QuizQuestionEntity qq WHERE qq.quiz_id = :quizId ORDER BY qq.position ASC")
    List<QuizQuestionEntity> findMembershipsByQuizId(@Param("quizId") UUID quizId);

    @Modifying
    @Query("DELETE FROM QuizQuestionEntity qq WHERE qq.quiz_id = :quizId")
    void deleteMembershipsByQuizId(@Param("quizId") UUID quizId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AttemptEntity a WHERE a.quiz_id = :quizId")
    boolean existsAttemptByQuizId(@Param("quizId") UUID quizId);
}