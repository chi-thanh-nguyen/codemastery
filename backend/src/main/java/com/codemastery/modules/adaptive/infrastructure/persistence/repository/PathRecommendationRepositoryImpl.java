package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.repository.PathRecommendationRepository;
import com.codemastery.modules.adaptive.infrastructure.persistence.mapper.RecommendationMapper;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * Adapter for {@link PathRecommendationRepository}. AD06.history-writes
 * checkpoint.
 *
 * <p>
 * <b>Approved write predicate.</b> ADR011 §9: each persisted row keeps the
 * type, target
 * Lesson, reason and one recommendation instant supplied by the caller, and
 * history is
 * append-only so no row is rewritten, collapsed or deleted. Whether a new row
 * is needed at
 * all is the caller's decision under ADR011 §7 (an unchanged {@code continue}
 * or
 * {@code remedial} recommendation is not reinserted); this adapter does not
 * compare state.
 *
 * <p>
 * <b>No consumed execution contract.</b> The recommendation instant must be one
 * server value per processing run, strictly later than the Enrollment's latest
 * {@code recommended_at} (ADR011 §9). This adapter samples no clock; the
 * {@code Clock} belongs to the decision service. It declares no transaction of
 * its own,
 * because the append commits inside the approved unit of ADR011 §4.
 *
 * <p>
 * <b>Column ceiling.</b> ADR012 §19.2 grants {@code SELECT} and {@code INSERT}
 * only on
 * {@code path_recommendations}; no update and no delete statement exists.
 *
 * <p>
 * Not verified here: actual persistence against PostgreSQL is the separate
 * AD06.database-verification checkpoint and remains open.
 */
@Repository
public class PathRecommendationRepositoryImpl implements PathRecommendationRepository {

    private final PathRecommendationJpaRepository recommendations;
    private final RecommendationMapper mapper;

    public PathRecommendationRepositoryImpl(
            PathRecommendationJpaRepository recommendations, RecommendationMapper mapper) {
        this.recommendations = Objects.requireNonNull(recommendations);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void append(PathRecommendation recommendation) {
        Objects.requireNonNull(recommendation, "recommendation");
        Objects.requireNonNull(recommendation.id(), "recommendation id");
        recommendations.save(mapper.toEntity(recommendation));
    }
}