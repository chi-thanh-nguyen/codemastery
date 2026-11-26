package com.codemastery.modules.adaptive.application.mastery;

import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.repository.LearnerSkillMasteryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * AD16 learner mastery reads (ADR011 §§13, 15; ADR013 §19.4).
 *
 * <p>This slice provides the learner's current state per skill, with an absent row read as
 * {@code Unknown} (ADR005 §6.1), for read-only compositions that have already admitted the learner
 * in the same snapshot (CT33 {@code readLearnerState}). It is the state input of the
 * practice recommendation and of the later Mastery Map and guidance reads. The Mastery Map
 * composition, insights and their REST operations are not part of this slice.
 */
@Service
public class MasteryQueryService {

    private final LearnerSkillMasteryRepository masteries;

    public MasteryQueryService(LearnerSkillMasteryRepository masteries) {
        this.masteries = Objects.requireNonNull(masteries);
    }

    /**
     * Returns the stored state of {@code learnerId} for every requested skill, {@code Unknown} where
     * no row exists. The caller passes the learner it has admitted in this transaction; no other
     * learner's rows are read.
     *
     * <p>Joins the caller's read-only REPEATABLE READ transaction and never starts one, so the
     * states belong to the caller's snapshot.
     *
     * <p><b>Callers.</b> Only Adaptive read compositions that admitted {@code learnerId} in the same
     * snapshot: today {@code RecommendationService.readPracticeRecommendation}, enforced by an
     * allowlist test. The method itself cannot tell whose rows it reads.
     *
     * @return an immutable map with exactly the requested skills as keys
     * @throws IllegalStateException    without a read-only REPEATABLE READ caller transaction
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if {@code skillIds} contains {@code null}
     * @throws org.springframework.dao.DataAccessException on an invalid stored row or any repository
     *                                  failure (the {@code @Repository} adapter's translation)
     */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<UUID, MasteryState> readStates(UUID learnerId, Collection<UUID> skillIds) {
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(skillIds, "skillIds");
        if (skillIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("skillIds must not contain null");
        }
        // Joining an existing transaction does not apply an annotation's isolation/readOnly settings.
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Objects.equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(),
                        Isolation.REPEATABLE_READ.value())) {
            throw new IllegalStateException("Mastery reads require a read-only REPEATABLE READ transaction");
        }
        Set<UUID> requested = Set.copyOf(skillIds);
        if (requested.isEmpty()) {
            return Map.of();
        }

        Map<UUID, MasteryState> stored = masteries.findStates(learnerId, requested);
        if (stored == null || !requested.containsAll(stored.keySet())) {
            throw new IllegalStateException("Invalid mastery repository result");
        }
        Map<UUID, MasteryState> states = new HashMap<>();
        for (UUID skillId : requested) {
            states.put(skillId, stored.getOrDefault(skillId, MasteryState.UNKNOWN));
        }
        return Map.copyOf(states);
    }
}
