package com.codemastery.modules.interaction.domain.repository;

import com.codemastery.modules.interaction.domain.model.ContentReport;

import java.util.Optional;
import java.util.UUID;

public interface ContentReportRepository {
    Optional<ContentReport> findById(UUID id);
    ContentReport save(ContentReport report);
}
