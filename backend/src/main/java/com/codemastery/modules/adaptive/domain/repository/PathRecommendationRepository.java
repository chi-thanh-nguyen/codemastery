package com.codemastery.modules.adaptive.domain.repository;

import com.codemastery.modules.adaptive.domain.model.PathRecommendation;

/**
 * Adaptive-owned append-only recommendation history (table {@code path_recommendations}, V005;
 * AD06.history-writes).
 *
 * <p>ADR011 §9 requires every row to keep the type, target Lesson, reason and instant supplied by
 * the caller, and history is never collapsed, rewritten or deleted. Whether a new row is needed
 * at all, the reason text and the strictly-later instant are the caller's obligations under
 * ADR011 §§7 and 9. Reads for the current recommendation and the Instructor log belong to AD16.
 *
 * <p>The caller owns the transaction (ADR011 §§4–5). The append requires an active transaction,
 * joins it, and never starts, commits or retries one; any failure propagates. This interface
 * declares no clock and no de-duplication policy.
 */
public interface PathRecommendationRepository {

    /**
     * Inserts {@code recommendation} as a new history row, field for field. A {@code recommendedAt}
     * finer than one microsecond is rejected before any SQL rather than rounded. Without an active
     * transaction, or when the identifier already exists, a referenced Enrollment or Lesson does
     * not exist or the reason is blank, the append fails and no row is changed.
     *
     * @throws NullPointerException if the recommendation or any of its fields is {@code null}
     */
    void append(PathRecommendation recommendation);
}
