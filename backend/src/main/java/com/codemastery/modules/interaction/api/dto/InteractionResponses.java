package com.codemastery.modules.interaction.api.dto;
import com.codemastery.modules.interaction.domain.model.Notification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class InteractionResponses {

    private InteractionResponses() {
    }

    public record DiscussionPostResponse(UUID id, UUID lessonId, UUID parentPostId, UUID authorId,
            String authorDisplayName, String authorRole, boolean mine, String content, Instant createdAt) {}
    public record DiscussionReply(UUID id, UUID parentPostId, UUID authorId,
            String authorDisplayName, String authorRole, boolean mine, String content, Instant createdAt) {}
    public record DiscussionThread(UUID id, UUID authorId, String authorDisplayName, String authorRole,
            boolean mine, String content, Instant createdAt, java.util.List<DiscussionReply> replies) {}

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
