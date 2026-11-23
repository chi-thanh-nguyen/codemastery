package com.codemastery.modules.adaptive.domain.repository;

import com.codemastery.modules.adaptive.domain.model.MasteryState;

import java.util.UUID;

/**
 * Adaptive-owned write capability for categorical skill mastery (table
 * {@code learner_skill_masteries}, V005; AD06.mastery-writes).
 *
 * <p>The only approved write stores a state the caller has already classified under CT06/CT24:
 * ADR011 §4 step 5 inserts or updates the row of an assessed skill and leaves unassessed skills
 * untouched. An absent row means {@code Unknown} (ADR005 §6.1), so no default row is ever written
 * to encode it; an explicit {@code Unknown} supplied by the caller is stored. Calculation,
 * classification, routing, Mastery Map reads and reporting belong to other Issues (reads: AD16).
 *
 * <p>The caller owns the transaction (ADR011 §§4–5). The write requires an active transaction,
 * joins it, and never starts, commits or retries one; any failure propagates.
 */
public interface LearnerSkillMasteryRepository {

    /**
     * Stores {@code state} as the current mastery of {@code learnerId} for {@code skillId}: inserts
     * the row if absent, otherwise changes only its state. Concurrent writers of the same pair
     * converge on one row. Without an active transaction, or when the learner or skill does not
     * exist, the write fails and nothing is stored.
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    void storeState(UUID learnerId, UUID skillId, MasteryState state);
}
