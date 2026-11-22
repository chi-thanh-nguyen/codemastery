package com.codemastery.modules.adaptive.domain.repository;

import com.codemastery.modules.adaptive.domain.model.PathRecommendation;

/**
 * Adaptive-owned append-only recommendation history
 * (table {@code path_recommendations}, V005).
 *
 * <p>
 * AD06.history-writes checkpoint. ADR011 §9 requires every row to keep the
 * type, target
 * Lesson, reason and instant supplied by the caller, and history is never
 * collapsed,
 * rewritten or deleted. Whether a new row is needed at all is the caller's
 * decision under
 * ADR011 §7. Reads for the current recommendation and the Instructor log belong
 * to AD16.
 *
 * <p>
 * Callers own the transaction. This interface declares no clock and no
 * de-duplication
 * policy.
 */
public interface PathRecommendationRepository {

    void append(PathRecommendation recommendation);
}