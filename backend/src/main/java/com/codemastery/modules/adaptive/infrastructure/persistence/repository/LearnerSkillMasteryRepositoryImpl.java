package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.repository.LearnerSkillMasteryRepository;
import com.codemastery.modules.adaptive.infrastructure.persistence.mapper.MasteryMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter for {@link LearnerSkillMasteryRepository} (AD06.mastery-writes,
 * {@code AD06-PERSISTENCE-2026-11-23-r1}; AD16 state read).
 *
 * <p><b>Approved write predicate.</b> ADR011 §4 step 5 (CT08.execution): an assessed skill's
 * mastery row is <em>inserted or updated</em> with the state the CT06/CT24 calculator and
 * classifier produced; unassessed skills are untouched. ADR005 §6.1 keeps the value categorical
 * and unique per learner and skill; an absent row means {@code Unknown}, an explicit
 * {@code Unknown} is still allowed, and this adapter never writes a row the caller did not ask
 * for.
 *
 * <p><b>Mechanism.</b> One {@code INSERT ... ON CONFLICT (user_id, skill_id) DO UPDATE SET state}
 * statement, executed at the call. An existing row keeps its identifier, learner and skill, and
 * no full-row update is ever issued, so the statement stays within the ADR012 §19.2 column
 * ceiling. The statement bypasses the persistence context; this adapter never loads mastery
 * entities, so it leaves none stale.
 *
 * <p><b>Consumed execution contract.</b> ADR011 §§4–5 (CT08.execution, CT08.failure): the write
 * joins the caller's transaction ({@link Propagation#MANDATORY} refuses to run without one and
 * never starts, suspends or commits one), takes the {@code learner_skill_masteries} row lock in
 * the approved lock order after the Course root, retries nothing, and propagates every failure so
 * the whole unit rolls back.
 *
 * <p><b>State read (AD16).</b> One native {@code SELECT skill_id, state} for one learner and a
 * set of skills, in the caller's transaction and snapshot. It reads no other column, so it needs
 * only the r8 column grant. A stored code outside V005's four values, a null value or a repeated
 * skill fails closed; nothing is defaulted here.
 */
@Repository
public class LearnerSkillMasteryRepositoryImpl implements LearnerSkillMasteryRepository {

    private final LearnerSkillMasteryJpaRepository masteries;
    private final MasteryMapper mapper;

    public LearnerSkillMasteryRepositoryImpl(LearnerSkillMasteryJpaRepository masteries, MasteryMapper mapper) {
        this.masteries = Objects.requireNonNull(masteries);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void storeState(UUID learnerId, UUID skillId, MasteryState state) {
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(state, "state");

        if (masteries.upsertState(UUID.randomUUID(), learnerId, skillId, mapper.toCode(state)) != 1) {
            throw new IllegalStateException("Mastery write did not affect exactly one row");
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, MasteryState> findStates(UUID learnerId, Collection<UUID> skillIds) {
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(skillIds, "skillIds");
        if (skillIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("skillIds must not contain null");
        }
        Set<UUID> requested = Set.copyOf(skillIds);
        if (requested.isEmpty()) {
            return Map.of();
        }

        Map<UUID, MasteryState> states = new HashMap<>();
        for (Object[] row : masteries.findStates(learnerId, requested)) {
            if (row == null || row.length != 2 || !(row[0] instanceof UUID skillId) || !(row[1] instanceof String code)
                    || !requested.contains(skillId)) {
                throw new IllegalStateException("Stored mastery row violates V005");
            }
            MasteryState state;
            try {
                state = mapper.toState(code);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("Stored mastery row violates V005", invalid);
            }
            if (states.putIfAbsent(skillId, state) != null) {
                throw new IllegalStateException("Stored mastery rows repeat a skill");
            }
        }
        return Map.copyOf(states);
    }
}
