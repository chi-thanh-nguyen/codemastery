package com.codemastery.modules.admin.infrastructure.persistence.repository;

import com.codemastery.modules.admin.infrastructure.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface AuditLogJpaRepository
        extends Repository<AuditLogEntity, UUID>, JpaSpecificationExecutor<AuditLogEntity> {
}
