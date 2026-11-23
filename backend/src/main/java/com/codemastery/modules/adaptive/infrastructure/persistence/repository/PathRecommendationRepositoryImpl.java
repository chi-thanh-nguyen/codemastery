package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.repository.PathRecommendationRepository;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.PathRecommendationEntity;
import com.codemastery.modules.adaptive.infrastructure.persistence.mapper.RecommendationMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Adapter for {@link PathRecommendationRepository} (AD06.history-writes;
 * {@code AD06-PERSISTENCE-2026-11-23-r1}).
 *
 * <p><b>Approved write predicate.</b> ADR011 §9 (CT07.reasons): each persisted row keeps the
 * type, target Lesson, reason and recommendation instant supplied by the caller, and history is
 * append-only: no row is rewritten, collapsed or deleted. Whether a row is needed at all is the
 * caller's decision under ADR011 §7 (CT07.routing); this adapter compares nothing and
 * de-duplicates nothing.
 *
 * <p><b>Mechanism.</b> One plain {@code INSERT}, executed at the call. A reused identifier fails
 * with the V005 primary-key violation and never changes the stored row. Every field is required
 * (V005 {@code NOT NULL}); the reason text, the type code and the foreign keys stay enforced by
 * V005. The instant must already be at most microsecond-precise (ADR011 §9 truncates it in the
 * caller), because PostgreSQL would otherwise round it; the adapter rejects rather than alters it
 * and samples no clock.
 *
 * <p><b>Consumed execution contract.</b> ADR011 §§4–5 (CT08.execution, CT08.failure): the append
 * joins the caller's transaction ({@link Propagation#MANDATORY} refuses to run without one and
 * never starts, suspends or commits one), runs last in the approved lock order, retries nothing
 * and propagates every failure so the whole unit rolls back. The strictly-later instant rule of
 * ADR011 §9 is checked by the caller.
 */
@Repository
public class PathRecommendationRepositoryImpl implements PathRecommendationRepository {

    private final PathRecommendationJpaRepository recommendations;
    private final RecommendationMapper mapper;

    public PathRecommendationRepositoryImpl(PathRecommendationJpaRepository recommendations, RecommendationMapper mapper) {
        this.recommendations = Objects.requireNonNull(recommendations);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(PathRecommendation recommendation) {
        Objects.requireNonNull(recommendation, "recommendation");
        Objects.requireNonNull(recommendation.id(), "recommendation id");
        Objects.requireNonNull(recommendation.enrollmentId(), "recommendation enrollmentId");
        Objects.requireNonNull(recommendation.lessonId(), "recommendation lessonId");
        Objects.requireNonNull(recommendation.recommendationType(), "recommendation type");
        Objects.requireNonNull(recommendation.reason(), "recommendation reason");
        Objects.requireNonNull(recommendation.recommendedAt(), "recommendedAt");
        if (recommendation.recommendedAt().getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("recommendedAt must be at most microsecond-precise");
        }

        PathRecommendationEntity row = mapper.toEntity(recommendation);
        if (recommendations.insert(row.getId(), row.getEnrollmentId(), row.getLessonId(),
                row.getRecommendationType(), row.getReason(), row.getRecommendedAt()) != 1) {
            throw new IllegalStateException("Recommendation append did not insert exactly one row");
        }
    }
}
