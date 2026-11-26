package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.infrastructure.persistence.entity.LearnerSkillMasteryEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data access to {@code learner_skill_masteries}: the AD06 write path
 * ({@code AD06-PERSISTENCE-2026-11-23-r1}) and the AD16 state read.
 *
 * <p>No {@code save}, update or delete method is declared: Spring Data's {@code save} of an
 * assigned identifier takes the JPA merge path, which issues an extra {@code SELECT}, defers the
 * {@code INSERT} to a later flush and can rewrite a row in full.
 */
public interface LearnerSkillMasteryJpaRepository extends Repository<LearnerSkillMasteryEntity, UUID> {

    /**
     * One statement that inserts the (user, skill) row with {@code candidateId}, or, when the V005
     * {@code uq_learner_skill_masteries_user_skill} key already exists, updates only its
     * {@code state}. An existing row keeps its identifier, learner and skill; {@code candidateId}
     * is then discarded. A concurrent first write of the same pair waits for the other writer and
     * then updates, instead of failing with a unique violation.
     *
     * <p>Under CT17-P6 r8 it needs exactly {@code INSERT}, {@code UPDATE (state)} and
     * {@code SELECT (user_id, skill_id, state)}: PostgreSQL requires SELECT on the conflict target
     * and on the {@code EXCLUDED} column read by the update. Pending persistence-context changes
     * are flushed first, so the statement runs in the caller's order.
     *
     * @return the number of affected rows, always 1 when the statement succeeds
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO public.learner_skill_masteries (id, user_id, skill_id, state) "
            + "VALUES (:candidateId, :userId, :skillId, :state) "
            + "ON CONFLICT (user_id, skill_id) DO UPDATE SET state = EXCLUDED.state",
            nativeQuery = true)
    int upsertState(
            @Param("candidateId") UUID candidateId,
            @Param("userId") UUID userId,
            @Param("skillId") UUID skillId,
            @Param("state") String state);

    /**
     * The stored {@code (skill_id, state)} pairs of one learner for the given skills. A native
     * column projection, so no entity is loaded and no column outside the CT17-P6 r8
     * {@code SELECT (user_id, skill_id, state)} grant is read (an entity read would also select
     * {@code id} and fail with 42501 under the runtime role).
     */
    @Query(value = "SELECT skill_id, state FROM public.learner_skill_masteries "
            + "WHERE user_id = :userId AND skill_id IN (:skillIds)",
            nativeQuery = true)
    List<Object[]> findStates(@Param("userId") UUID userId, @Param("skillIds") Collection<UUID> skillIds);
}
