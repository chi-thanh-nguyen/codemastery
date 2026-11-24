package com.codemastery.modules.admin.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.admin.domain.model.AuditAction;
import com.codemastery.modules.admin.domain.model.AuditLog;
import com.codemastery.modules.admin.domain.repository.AuditLogRepository;
import com.codemastery.modules.admin.domain.repository.AuditLogRepository.AuditLogFilter;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.auth.application.service.UserAccountService.UserSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuditLogService {

    private static final Set<String> TARGET_TYPES = Arrays.stream(AuditAction.values())
            .map(AuditAction::getTargetType).collect(Collectors.toUnmodifiableSet());

    private final AuditLogRepository auditLogs;
    private final UserAccountService identities;
    private final Clock clock;

    /** Production seam: UTC. There is no shared Clock bean (ADR008 section 6). */
    @Autowired
    public AuditLogService(AuditLogRepository auditLogs, UserAccountService identities) {
        this(auditLogs, identities, Clock.systemUTC());
    }

    /** Test seam: a fixed or mutable Clock. */
    AuditLogService(AuditLogRepository auditLogs, UserAccountService identities, Clock clock) {
        this.auditLogs = Objects.requireNonNull(auditLogs);
        this.identities = Objects.requireNonNull(identities);
        this.clock = Objects.requireNonNull(clock);
    }

    /** One audit row as returned by {@link #search}; {@code actorDisplayName} is null for a missing account. */
    public record AuditLogView(
            UUID id, UUID actorId, String actorDisplayName, String action, String targetType,
            String targetReference, Instant occurredAt) {}

    /**
     * Records one actual change by the authenticated Administrator (CT31.actions, CT31.consistency).
     * Callers must not call this for no-ops, reads, denied or failed attempts, and call it after their
     * own change and locks. {@code MANDATORY} makes a call outside the caller's transaction fail instead
     * of committing an audit row on its own; the insert is executed before this method returns, so an
     * insert failure (including a read-only caller transaction) surfaces here and rolls back the
     * caller's whole unit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog record(AuditAction action, UUID targetId) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(targetId, "targetId");
        CurrentIdentity actor = requireAdministrator();
        AuditLog row = new AuditLog(
                UUID.randomUUID(),
                actor.userId(),
                action.getCode(),
                action.getTargetType(),
                targetId.toString(),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        return auditLogs.append(row);
    }

    /**
     * CT31.read: Administrator-only, AND-combined optional filters, newest first. {@code occurredFrom}
     * is inclusive and {@code occurredTo} exclusive, both at most microsecond precision and
     * {@code occurredFrom < occurredTo} when both are given. A null {@code page} or {@code size} uses
     * the approved paging defaults. ADR012 section 12 stage order: invalid query values are 400
     * (stage 2) before the role 403 (stage 3), and both are decided before any lookup.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<AuditLogView> search(
            UUID actorId, String action, String targetType, UUID targetReference,
            Instant occurredFrom, Instant occurredTo, Integer page, Integer size) {
        int pageNumber = page == null ? PageResponse.DEFAULT_PAGE : page;
        int pageSize = size == null ? PageResponse.DEFAULT_SIZE : size;
        PageResponse.validateRequest(pageNumber, pageSize);
        if (action != null && AuditAction.fromCode(action).isEmpty()) {
            throw invalid();
        }
        if (targetType != null && !TARGET_TYPES.contains(targetType)) {
            throw invalid();
        }
        if (isFinerThanMicroseconds(occurredFrom) || isFinerThanMicroseconds(occurredTo)) {
            throw invalid();
        }
        if (occurredFrom != null && occurredTo != null && !occurredFrom.isBefore(occurredTo)) {
            throw invalid();
        }
        requireAdministrator();
        AuditLogFilter filter = new AuditLogFilter(actorId, action, targetType,
                targetReference == null ? null : targetReference.toString(), occurredFrom, occurredTo);
        Page<AuditLog> result = auditLogs.search(filter, PageRequest.of(pageNumber, pageSize));

        Set<UUID> actorIds = result.getContent().stream().map(AuditLog::getActorId).collect(Collectors.toSet());
        Map<UUID, UserSummary> summaries = actorIds.isEmpty() ? Map.of() : identities.userSummaries(actorIds);
        List<AuditLogView> views = result.getContent().stream().map(row -> {
            UserSummary actor = summaries.get(row.getActorId());
            return new AuditLogView(row.getId(), row.getActorId(), actor == null ? null : actor.displayName(),
                    row.getAction(), row.getTargetType(), row.getTargetReference(), row.getOccurredAt());
        }).toList();
        return new PageResponse<>(views, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private CurrentIdentity requireAdministrator() {
        CurrentIdentity actor = identities.requireCurrentIdentity();
        if (actor.role() != IdentityRole.ADMINISTRATOR) {
            throw new AccessDeniedException("Access denied");
        }
        return actor;
    }

    private static boolean isFinerThanMicroseconds(Instant instant) {
        return instant != null && instant.getNano() % 1_000 != 0;
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException("Invalid audit log query");
    }
}
