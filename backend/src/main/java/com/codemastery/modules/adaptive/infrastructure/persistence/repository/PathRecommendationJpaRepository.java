package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.infrastructure.persistence.entity.PathRecommendationEntity;
import org.springframework.data.repository.Repository;

import java.util.UUID;

/**
 * Spring Data access to {@code path_recommendations} for the AD06 write path
 * only.
 *
 * <p>
 * ADR011 §9 makes history append-only and ADR012 §19.2 grants only
 * {@code SELECT} and
 * {@code INSERT} on this relation, so no update and no delete is declared.
 * Reads belong to
 * AD16.
 */
public interface PathRecommendationJpaRepository extends Repository<PathRecommendationEntity, UUID> {

    /** Inserts one history row; an existing row is never rewritten. */
    PathRecommendationEntity save(PathRecommendationEntity entity);
}