package com.codemastery.modules.adaptive.infrastructure.persistence.mapper;

import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.PathRecommendationEntity;
import org.springframework.stereotype.Component;

/**
 * Scalar mapping between {@link PathRecommendation} and its V005 row.
 *
 * <p>
 * AD06.mapping checkpoint. ADR011 §9 requires every persisted row to keep its
 * type,
 * target Lesson, reason and time, so all five ADR03 fields are carried
 * unchanged and
 * nothing is collapsed. The type is persisted by
 * {@link RecommendationType#getCode()},
 * so exactly {@code continue}, {@code skip} and {@code remedial} are stored,
 * matching
 * the V005 {@code ck_path_recommendations_type} check. An unrecognised stored
 * code is a
 * failure, never a fallback.
 *
 * <p>
 * Reason templates, name ordering and the single per-run instant are the
 * caller's
 * obligations under ADR011 §§7 and 9 (CT07.routing and CT07.reasons). This
 * mapper never
 * samples a clock.
 */

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