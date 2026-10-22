package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.infrastructure.persistence.entity.ChapterEntity;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

public interface ChapterJpaRepository extends Repository<ChapterEntity, UUID> {

    List<ChapterEntity> findByCourseIdOrderByPositionAsc(UUID courseId);
}
