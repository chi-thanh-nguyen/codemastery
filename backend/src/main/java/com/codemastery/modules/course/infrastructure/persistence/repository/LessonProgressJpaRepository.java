package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.infrastructure.persistence.entity.LessonProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LessonProgressJpaRepository extends JpaRepository<LessonProgressEntity, UUID> {

    Optional<LessonProgressEntity> findByEnrollmentIdAndLessonId(UUID enrollmentId, UUID lessonId);

    List<LessonProgressEntity> findByEnrollmentId(UUID enrollmentId);
}
