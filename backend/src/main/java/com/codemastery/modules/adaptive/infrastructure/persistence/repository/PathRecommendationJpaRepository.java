package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.infrastructure.persistence.entity.PathRecommendationEntity;
import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface PathRecommendationJpaRepository extends Repository<PathRecommendationEntity, UUID> {

    /** Inserts one history row; an existing row is never rewritten. */
    PathRecommendationEntity save(PathRecommendationEntity entity);
}