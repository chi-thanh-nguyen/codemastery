package com.codemastery.modules.interaction.api.dto;

import com.codemastery.modules.interaction.domain.model.Notification;

import java.time.Instant;
import java.util.UUID;

/** Response DTOs of the Interaction REST boundary. Persistence entities are never exposed. */
public final class InteractionResponses {

    private InteractionResponses() {
    }

    /** B7Notification (CT11-READ-FIELDS r1): the message is plain text; there is no type or link. */
    public record NotificationResponse(UUID id, String message, boolean read, Instant createdAt) {

        public static NotificationResponse from(Notification notification) {
            return new NotificationResponse(notification.getId(), notification.getMessage(),
                    notification.isRead(), notification.getCreatedAt());
        }
    }

    /** B7UnreadCount. */
    public record UnreadCountResponse(long unreadCount) {
    }

    /** B7MarkedCount. */
    public record MarkedCountResponse(long markedCount) {
    }
}
