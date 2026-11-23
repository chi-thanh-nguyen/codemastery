package com.codemastery.modules.admin.infrastructure.persistence.repository;

import com.codemastery.modules.admin.infrastructure.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

import java.util.UUID;

/** Read side only; inserts use the persistence context so they can never become updates. */
public interface AuditLogJpaRepository
        extends Repository<AuditLogEntity, UUID>, JpaSpecificationExecutor<AuditLogEntity> {
}
