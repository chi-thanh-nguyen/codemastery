package com.codemastery.modules.adaptive.domain.repository;

import com.codemastery.modules.adaptive.domain.model.MasteryState;

import java.util.UUID;

/**
 * Adaptive-owned write capability for categorical skill mastery
 * (table {@code learner_skill_masteries}, V005).
 *
 * <p>
 * AD06.mastery-writes checkpoint. The only approved write stores a state the
 * caller has
 * already classified under CT06/CT24: ADR011 §4 step 5 inserts or updates the
 * row of an
 * assessed skill and leaves unassessed skills untouched. An absent row means
 * {@code Unknown} (ADR005 §6.1), so no default state is ever written to encode
 * it.
 * Calculation, routing, Mastery Map reads and reporting belong to other Issues.
 *
 * <p>
 * Callers own the transaction. This interface declares no propagation, retry or
 * concurrency policy, because the approved unit in ADR011 §§4–5 must be neither
 * narrowed
 * nor widened here.
 */
public interface LearnerSkillMasteryRepository {

    void storeState(UUID learnerId, UUID skillId, MasteryState state);
}