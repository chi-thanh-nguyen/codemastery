package com.codemastery.modules.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

public final class ReportingResponses {

    private ReportingResponses() {
    }

    /** CT31.read row (B7AuditLogEntry); {@code actorDisplayName} is always present, null for a missing account. */
    public record AuditLogEntry(
            UUID id,
            UUID actorId,
            @JsonInclude(JsonInclude.Include.ALWAYS) String actorDisplayName,
            String action,
            String targetType,
            String targetReference,
            Instant occurredAt) {}
}
