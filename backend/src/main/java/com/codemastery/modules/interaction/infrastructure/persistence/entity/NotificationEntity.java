package com.codemastery.modules.interaction.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Scalar JPA mapping of {@code public.notifications} (V006). */
@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationEntity() {
        // Required by JPA.
    }

    public NotificationEntity(UUID id, UUID userId, String message, boolean read, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.message = message;
        this.read = read;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
