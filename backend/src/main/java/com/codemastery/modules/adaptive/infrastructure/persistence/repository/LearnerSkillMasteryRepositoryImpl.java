package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.repository.LearnerSkillMasteryRepository;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.LearnerSkillMasteryEntity;
import com.codemastery.modules.adaptive.infrastructure.persistence.mapper.MasteryMapper;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.UUID;

/**
 * Adapter for {@link LearnerSkillMasteryRepository}. AD06.mastery-writes
 * checkpoint.
 *
 * <p>
 * <b>Approved write predicate.</b> ADR011 §4 step 5: an assessed skill's
 * mastery row is
 * <em>inserted or updated</em> under the policy the calculator produced, and
 * unassessed
 * skills are left untouched. ADR005 §6.1 keeps the stored value categorical and
 * unique per
 * learner and skill ({@code uq_learner_skill_masteries_user_skill}); an absent
 * row still
 * means {@code Unknown}, so this adapter never writes a default row to encode
 * it.
 *
 * <p>
 * <b>No consumed execution contract.</b> This adapter declares no transaction
 * of its
 * own: the atomic quiz-to-mastery unit, its rollback behaviour and the lock
 * order belong to
 * the orchestration approved in ADR011 §§4–5 (CT08.execution and CT08.failure).
 * AD06
 * forbids adapter-owned transaction propagation that could narrow or widen that
 * unit.
 *
 * <p>
 * <b>Column ceiling.</b> ADR012 §19.2 grants {@code SELECT}, {@code INSERT} and
 * {@code UPDATE (state)} only, so an existing row is changed by one targeted
 * statement and
 * a full-row update is never issued. Identifier, learner and skill of an
 * existing row are
 * immutable.
 *
 * <p>
 * Not verified here: actual persistence against PostgreSQL is the separate
 * AD06.database-verification checkpoint and remains open.
 */
@Repository
public class LearnerSkillMasteryRepositoryImpl implements LearnerSkillMasteryRepository {

    private final LearnerSkillMasteryJpaRepository masteries;

    public LearnerSkillMasteryRepositoryImpl(LearnerSkillMasteryJpaRepository masteries) {
        this.masteries = Objects.requireNonNull(masteries);
    }

    @Override
    public void storeState(UUID learnerId, UUID skillId, MasteryState state) {
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(state, "state");

        String storedCode = MasteryMapper.toCode(state);
        if (masteries.updateState(learnerId, skillId, storedCode) == 1) {
            return;
        }
        masteries.save(new LearnerSkillMasteryEntity(UUID.randomUUID(), learnerId, skillId, storedCode));
    }
}