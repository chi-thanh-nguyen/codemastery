package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.infrastructure.persistence.entity.ChapterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChapterJpaRepository extends JpaRepository<ChapterEntity, UUID> {

    List<ChapterEntity> findByCourseIdOrderByPositionAsc(UUID courseId);
}
