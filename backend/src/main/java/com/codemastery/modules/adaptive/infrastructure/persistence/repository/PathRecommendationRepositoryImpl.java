package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.repository.PathRecommendationRepository;
import com.codemastery.modules.adaptive.infrastructure.persistence.mapper.RecommendationMapper;
import org.springframework.stereotype.Repository;

import java.util.Objects;

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