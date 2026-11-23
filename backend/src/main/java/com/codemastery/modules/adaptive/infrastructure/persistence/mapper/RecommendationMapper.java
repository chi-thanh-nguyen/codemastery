package com.codemastery.modules.adaptive.infrastructure.persistence.mapper;

import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.PathRecommendationEntity;
import org.springframework.stereotype.Component;

/**
 * Scalar mapping between the AD03 {@link PathRecommendation} record and its V005 row
 * (AD06.mapping).
 *
 * <p>ADR011 §9 requires every persisted row to keep its type, target Lesson, reason and time, so
 * the identifier, Enrollment, Lesson, type, reason and instant are carried unchanged and nothing
 * is collapsed, trimmed or re-timed. The type is persisted by {@link RecommendationType#getCode()},
 * so exactly {@code continue}, {@code skip} and {@code remedial} are stored, matching the V005
 * {@code ck_path_recommendations_type} check. An unrecognised stored code is a failure, never a
 * fallback. A {@code null} input maps to {@code null}; V005's {@code NOT NULL} columns stay the
 * authority on missing values.
 *
 * <p>Reason templates, name ordering and the single per-run instant are the caller's obligations
 * under ADR011 §§7 and 9. This mapper never samples a clock.
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

    /** The exact V005 stored code of a recommendation type. */
    public String toCode(RecommendationType type) {
        return type == null ? null : type.getCode();
    }

    /** The type whose V005 code is exactly {@code code}; any other spelling is rejected. */
    public RecommendationType toType(String code) {
        if (code == null) {
            return null;
        }
        for (RecommendationType type : RecommendationType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown recommendation type code: '" + code + "'");
    }
}
