package com.codemastery.modules.interaction.infrastructure.persistence.repository;

import com.codemastery.modules.interaction.infrastructure.persistence.entity.ContentReportEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ContentReportJpaRepository extends Repository<ContentReportEntity, UUID> {

    Optional<ContentReportEntity> findById(UUID id);

    /** Inserts a new row. Must not be used for an existing row (ADR012 §19.2). */
    ContentReportEntity save(ContentReportEntity entity);

    /** Targeted update of the only mutable columns, {@code status} and {@code resolved_at} (ADR012 §19.2). */
    @Modifying(flushAutomatically = true)
    @Query("update ContentReportEntity r set r.status = :status, r.resolvedAt = :resolvedAt where r.id = :id")
    int updateResolution(@Param("id") UUID id, @Param("status") String status, @Param("resolvedAt") Instant resolvedAt);
}
