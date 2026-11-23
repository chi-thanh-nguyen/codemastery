package com.codemastery.modules.admin.domain.repository;

import com.codemastery.modules.admin.domain.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

/** Admin-internal persistence port for the append-only audit log (IN10). */
public interface AuditLogRepository {

    /**
     * Inserts one new audit row. Append-only: this never updates an existing row, and
     * no update or delete operation exists. It joins the caller's transaction.
     */
    AuditLog append(AuditLog auditLog);

    /**
     * Rows matching every non-null filter value (AND), filtered before paging/counting and
     * ordered by {@code occurred_at} descending, then {@code id} descending. Any sort carried
     * by {@code pageable} is ignored.
     */
    Page<AuditLog> search(AuditLogFilter filter, Pageable pageable);

    /**
     * Optional AND conditions; a null value adds no condition. {@code occurredFrom} is
     * inclusive and {@code occurredTo} is exclusive. Validation of the values belongs to
     * the caller.
     */
    record AuditLogFilter(
            UUID actorId, String action, String targetType, String targetReference,
            Instant occurredFrom, Instant occurredTo) {}
}
