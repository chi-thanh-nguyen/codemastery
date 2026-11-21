package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.infrastructure.persistence.entity.SkillEntity;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillJpaRepository extends Repository<SkillEntity, UUID> {
    List<SkillEntity> findByCourseId(UUID courseId);

    Optional<SkillEntity> findByCourseIdAndId(UUID courseId, UUID id);

    SkillEntity save(SkillEntity entity);

    @Modifying(flushAutomatically = true)
    @Query("update SkillEntity s set s.name = :name, s.description = :description "
            + "where s.courseId = :courseId and s.id = :id")
    int updateText(
            @Param("courseId") UUID courseId,
            @Param("id") UUID id,
            @Param("name") String name,
            @Param("description") String description);

    @Modifying(flushAutomatically = true)
    @Query("delete from SkillEntity s where s.courseId = :courseId and s.id = :id")
    int deleteScoped(
            @Param("courseId") UUID courseId,
            @Param("id") UUID id);
}
