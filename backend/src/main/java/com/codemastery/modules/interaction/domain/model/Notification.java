package com.codemastery.modules.interaction.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Local representation of an in-app notification (table {@code notifications}).
 *
 * <p>{@code userId} is the scalar identifier of the recipient user.
 */
public class Notification {

    private final UUID id;
    private final UUID userId;
    private final String message;
    private final boolean read;
    private final Instant createdAt;

    public Notification(UUID id, UUID userId, String message, boolean read, Instant createdAt) {
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
