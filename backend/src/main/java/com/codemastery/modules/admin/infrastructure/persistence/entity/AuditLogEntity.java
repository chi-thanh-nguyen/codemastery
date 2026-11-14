package com.codemastery.modules.admin.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Scalar JPA mapping of {@code public.audit_logs} (V006). */
@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "action", nullable = false, columnDefinition = "text")
    private String action;

    @Column(name = "target_type", nullable = false, columnDefinition = "text")
    private String targetType;

    @Column(name = "target_reference", nullable = false, columnDefinition = "text")
    private String targetReference;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditLogEntity() {
        // Required by JPA.
    }

    public AuditLogEntity(
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
