package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.infrastructure.persistence.entity.PathRecommendationEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data access to {@code path_recommendations} for the AD06 append-only write path
 * ({@code AD06-PERSISTENCE-2026-11-23-r1}). Reads belong to AD16.
 *
 * <p>ADR011 §9 makes history append-only, so only a plain {@code INSERT} is declared: no
 * {@code save} (whose merge path would overwrite a row with the same identifier), no update and
 * no delete.
 */
public interface PathRecommendationJpaRepository extends Repository<PathRecommendationEntity, UUID> {

    /**
     * Inserts one history row. An identifier that already exists fails with the V005
     * {@code pk_path_recommendations} unique violation (SQLSTATE 23505); the stored row is never
     * changed. Under CT17-P6 r8 it needs only {@code INSERT}. Pending persistence-context changes
     * are flushed first, so the statement runs in the caller's order.
     *
     * @return the number of inserted rows, always 1 when the statement succeeds
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO public.path_recommendations "
            + "(id, enrollment_id, lesson_id, recommendation_type, reason, recommended_at) "
            + "VALUES (:id, :enrollmentId, :lessonId, :recommendationType, :reason, :recommendedAt)",
            nativeQuery = true)
    int insert(
            @Param("id") UUID id,
            @Param("enrollmentId") UUID enrollmentId,
            @Param("lessonId") UUID lessonId,
            @Param("recommendationType") String recommendationType,
            @Param("reason") String reason,
            @Param("recommendedAt") Instant recommendedAt);
}
