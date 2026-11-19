package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuizEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizJpaRepository extends JpaRepository<QuizEntity, UUID> {

    @Query("SELECT q FROM QuizEntity q WHERE q.course_id = :courseId")
    List<QuizEntity> findAllByCourseId(@Param("courseId") UUID courseId);
}