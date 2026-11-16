package com.codemastery.modules.admin.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Local representation of an audit record (table {@code audit_logs}).
 *
 * <p>{@code action} and {@code targetType} are open nonblank text codes, and
 * {@code targetReference} is an opaque reference with no foreign key.
 * {@code actorId} is the scalar identifier of the acting user.
 */
public class AuditLog {

    private final UUID id;
    private final UUID actorId;
    private final String action;
    private final String targetType;
    private final String targetReference;
    private final Instant occurredAt;

    public AuditLog(
            UUID id,
            UUID actorId,
            String action,
            String targetType,
            String targetReference,
            Instant occurredAt) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetReference = targetReference;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetReference() {
        return targetReference;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
