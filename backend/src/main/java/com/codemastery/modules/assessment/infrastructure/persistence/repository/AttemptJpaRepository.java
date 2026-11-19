package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.infrastructure.persistence.entity.AttemptEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttemptJpaRepository extends JpaRepository<AttemptEntity, UUID> {

    @Query("SELECT a FROM AttemptEntity a WHERE a.quiz_id = :quizId")
    List<AttemptEntity> findAllByQuizId(@Param("quizId") UUID quizId);

    @Query("SELECT CASE WHEN COUNT(aa) > 0 THEN true ELSE false END FROM AttemptAnswerEntity aa WHERE aa.question_id = :questionId")
    boolean existsAnswerByQuestionId(@Param("questionId") UUID questionId);
}