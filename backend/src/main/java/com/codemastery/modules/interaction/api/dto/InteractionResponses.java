package com.codemastery.modules.interaction.api.dto;
import com.codemastery.modules.interaction.domain.model.Notification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class InteractionResponses {

    private InteractionResponses() {
    }

    public record ContentReportReceipt(UUID id, String status, Instant createdAt) {}
    public record UnreadCountResponse(long unreadCount) {
    }

    public record MarkedCountResponse(long markedCount) {
    }

    public record NotificationResponse(
            UUID id,
            String message,
            boolean read,
            Instant createdAt
    ) {
        public static NotificationResponse from(Notification notification) {
            Objects.requireNonNull(notification, "notification");
            return new NotificationResponse(
                    notification.getId(),
                    notification.getMessage(),
                    notification.isRead(),
                    notification.getCreatedAt()
            );
        }
    }
}
