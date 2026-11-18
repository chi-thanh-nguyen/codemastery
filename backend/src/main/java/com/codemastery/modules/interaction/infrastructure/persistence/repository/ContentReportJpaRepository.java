package com.codemastery.modules.interaction.infrastructure.persistence.repository;

import com.codemastery.modules.interaction.infrastructure.persistence.entity.ContentReportEntity;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface ContentReportJpaRepository extends Repository<ContentReportEntity, UUID> {

    Optional<ContentReportEntity> findById(UUID id);

    ContentReportEntity save(ContentReportEntity entity);
}
