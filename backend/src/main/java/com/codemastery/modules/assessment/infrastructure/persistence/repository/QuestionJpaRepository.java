package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuestionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionJpaRepository extends JpaRepository<QuestionEntity, UUID> {

    @Query("SELECT q FROM QuestionEntity q WHERE q.skill_id = :skillId")
    List<QuestionEntity> findAllBySkillId(@Param("skillId") UUID skillId);

    @Query("SELECT q FROM QuestionEntity q WHERE q.skill_id = :skillId AND q.difficulty = :difficulty")
    List<QuestionEntity> findAllBySkillIdAndDifficulty(
            @Param("skillId") UUID skillId,
            @Param("difficulty") String difficulty
    );

    @Query("SELECT COUNT(q) FROM QuestionEntity q WHERE q.skill_id = :skillId")
    long countAllBySkillId(@Param("skillId") UUID skillId);

    @Query("SELECT CASE WHEN COUNT(qq) > 0 THEN true ELSE false END FROM QuizQuestionEntity qq WHERE qq.question_id = :questionId")
    boolean existsInQuizByQuestionId(@Param("questionId") UUID questionId);
}