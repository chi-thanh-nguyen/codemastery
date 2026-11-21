package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.infrastructure.persistence.entity.SkillPrerequisiteEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillPrerequisiteJpaRepository extends Repository<SkillPrerequisiteEntity, UUID> {

    List<SkillPrerequisiteEntity> findByCourseId(UUID courseId);

    Optional<SkillPrerequisiteEntity> findByCourseIdAndSkillIdAndPrerequisiteSkillId(
            UUID courseId, UUID skillId, UUID prerequisiteSkillId);

    SkillPrerequisiteEntity save(SkillPrerequisiteEntity entity);

    @Modifying(flushAutomatically = true)
    @Query("delete from SkillPrerequisiteEntity p where p.courseId = :courseId "
            + "and p.skillId = :skillId and p.prerequisiteSkillId = :prerequisiteSkillId")
    int deleteEdge(
            @Param("courseId") UUID courseId,
            @Param("skillId") UUID skillId,
            @Param("prerequisiteSkillId") UUID prerequisiteSkillId);
}