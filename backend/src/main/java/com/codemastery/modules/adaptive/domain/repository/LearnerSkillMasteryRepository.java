package com.codemastery.modules.adaptive.domain.repository;

import com.codemastery.modules.adaptive.domain.model.MasteryState;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptive-owned access to categorical skill mastery (table {@code learner_skill_masteries}, V005):
 * the AD06.mastery-writes write and the AD16 learner state read.
 *
 * <p>The only approved write stores a state the caller has already classified under CT06/CT24:
 * ADR011 §4 step 5 inserts or updates the row of an assessed skill and leaves unassessed skills
 * untouched. An absent row means {@code Unknown} (ADR005 §6.1), so no default row is ever written
 * to encode it; an explicit {@code Unknown} supplied by the caller is stored. Calculation,
 * classification, routing, Mastery Map composition and reporting belong to other Issues.
 *
 * <p>The caller owns the transaction (ADR011 §§4–5). Both methods require an active transaction,
 * join it, and never start, commit or retry one; any failure propagates.
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

    /**
     * Reads the stored states of {@code learnerId} for the given skills, in the caller's transaction
     * and snapshot. Only existing rows are returned; the caller maps an absent skill to
     * {@code Unknown} (ADR005 §6.1). Rows of other learners or skills are never read. An empty
     * {@code skillIds} returns an empty map without SQL.
     *
     * <p>The statement reads only {@code user_id}, {@code skill_id} and {@code state}, the columns
     * that {@code CT17-P6-PRIVILEGES-2026-11-23-r8} grants for SELECT.
     *
     * <p>A {@code null} argument, a {@code null} skill ID or an invalid stored row is rejected. The
     * Spring adapter is a {@code @Repository}, so callers observe those rejections, like every other
     * failure, as Spring {@code DataAccessException}s (for example
     * {@code InvalidDataAccessApiUsageException}); nothing is defaulted.
     *
     * @return an immutable map from skill to stored state
     */
    Map<UUID, MasteryState> findStates(UUID learnerId, Collection<UUID> skillIds);
}
