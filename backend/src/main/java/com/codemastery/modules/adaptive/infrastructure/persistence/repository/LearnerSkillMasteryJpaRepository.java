package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.infrastructure.persistence.entity.LearnerSkillMasteryEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LearnerSkillMasteryJpaRepository extends Repository<LearnerSkillMasteryEntity, UUID> {

    LearnerSkillMasteryEntity save(LearnerSkillMasteryEntity entity);

    @Modifying(flushAutomatically = true)
    @Query("update LearnerSkillMasteryEntity m set m.state = :state "
            + "where m.userId = :userId and m.skillId = :skillId")
    int updateState(
            @Param("userId") UUID userId,
            @Param("skillId") UUID skillId,
            @Param("state") String state);
}