package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.infrastructure.persistence.entity.LearnerSkillMasteryEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Spring Data access to {@code learner_skill_masteries} for the AD06 write path
 * only.
 *
 * <p>
 * ADR012 §19.2 grants {@code SELECT}, {@code INSERT} and {@code UPDATE (state)}
 * only on
 * this relation, so an existing row is changed by one targeted statement and no
 * delete is
 * declared. A full-row update would touch a forbidden column and fail with
 * {@code 42501}.
 * Reads belong to AD16.
 */
public interface LearnerSkillMasteryJpaRepository extends Repository<LearnerSkillMasteryEntity, UUID> {

        LearnerSkillMasteryEntity save(LearnerSkillMasteryEntity entity);

        /**
         * Targeted update of the only mutable column, {@code state}. Returns the number
         * of
         * matched rows, so {@code 0} means the (user, skill) pair has no row yet.
         */
        @Modifying(flushAutomatically = true)
        @Query("update LearnerSkillMasteryEntity m set m.state = :state "
                        + "where m.userId = :userId and m.skillId = :skillId")
        int updateState(
                        @Param("userId") UUID userId,
                        @Param("skillId") UUID skillId,
                        @Param("state") String state);
}