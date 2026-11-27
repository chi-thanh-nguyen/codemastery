package com.codemastery.modules.interaction.domain.repository;

import com.codemastery.modules.interaction.domain.model.ContentReport;

import java.util.Optional;
import java.util.UUID;

public interface ContentReportRepository {
    Optional<ContentReport> findById(UUID id);
    ContentReport save(ContentReport report);
    org.springframework.data.domain.Page<ContentReport> findByStatus(ContentReport.Status status,
            org.springframework.data.domain.Pageable pageable);
    Optional<ContentReport> lockById(UUID id);
    /** Exactly one target is non-null. One deterministic ORDER BY id FOR UPDATE statement. */
    java.util.List<ContentReport> lockOpenForTarget(UUID postId, UUID lessonId);
}
