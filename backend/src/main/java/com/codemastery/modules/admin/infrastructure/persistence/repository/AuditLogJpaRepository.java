package com.codemastery.modules.admin.infrastructure.persistence.repository;

import com.codemastery.modules.admin.infrastructure.persistence.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;

import java.util.UUID;

/**
 * Read side only (CT31.read). Exactly the two Specification reads are exposed; Spring Data routes them
 * to its base implementation. {@code JpaSpecificationExecutor} is deliberately not extended, because it
 * also declares bulk {@code update} and {@code delete} operations, and CT31.actions requires that no
 * update or delete operation exists. Inserts use the persistence context in {@link AuditLogRepositoryImpl}.
 */
public interface AuditLogJpaRepository extends Repository<AuditLogEntity, UUID> {

    /** The filtered page; its count query runs only when the page does not reveal the total. */
    Page<AuditLogEntity> findAll(Specification<AuditLogEntity> specification, Pageable pageable);

    /** The filtered total. */
    long count(Specification<AuditLogEntity> specification);
}
