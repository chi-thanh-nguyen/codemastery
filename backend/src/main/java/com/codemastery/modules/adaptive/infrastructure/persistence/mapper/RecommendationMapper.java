package com.codemastery.modules.adaptive.infrastructure.persistence.mapper;

import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.PathRecommendationEntity;
import org.springframework.stereotype.Component;

@Component
public class RecommendationMapper {

    public PathRecommendationEntity toEntity(PathRecommendation domain) {
        if (domain == null) {
            return null;
        }
        return new PathRecommendationEntity(
                domain.id(),
                domain.enrollmentId(),
                domain.lessonId(),
                toCode(domain.recommendationType()),
                domain.reason(),
                domain.recommendedAt());
    }

    public PathRecommendation toDomain(PathRecommendationEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PathRecommendation(
                entity.getId(),
                entity.getEnrollmentId(),
                entity.getLessonId(),
                toType(entity.getRecommendationType()),
                entity.getReason(),
                entity.getRecommendedAt());
    }

    public static String toCode(RecommendationType type) {
        return type == null ? null : type.getCode();
    }

    public static RecommendationType toType(String code) {
        if (code == null) {
            return null;
        }
        for (RecommendationType type : RecommendationType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown recommendation type code");
    }
}