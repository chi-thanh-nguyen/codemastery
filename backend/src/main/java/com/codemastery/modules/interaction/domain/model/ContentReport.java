package com.codemastery.modules.interaction.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Local representation of a content report (table {@code content_reports}).
 *
 * <p>A report targets exactly one discussion post or one lesson: exactly one of
 * {@code discussionPostId} and {@code lessonId} is non-null. This class only
 * carries the values; it does not enforce that rule or implement resolution.
 */
public class ContentReport {

    /** Approved report status codes, persisted by {@link #getCode() code}, never by ordinal. */
    public enum Status {
        OPEN("open"),
        RESOLVED("resolved"),
        DISMISSED("dismissed");

        private final String code;

        Status(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private final UUID id;
    private final UUID reporterId;
    private final UUID discussionPostId;
    private final UUID lessonId;
    private final String reason;
    private final Status status;
    private final Instant createdAt;
    private final Instant resolvedAt;

    public ContentReport(
            UUID id,
            UUID reporterId,
            UUID discussionPostId,
            UUID lessonId,
            String reason,
            Status status,
            Instant createdAt,
            Instant resolvedAt) {
        this.id = id;
        this.reporterId = reporterId;
        this.discussionPostId = discussionPostId;
        this.lessonId = lessonId;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    /** Returns {@code null} when the report targets a lesson. */
    public UUID getDiscussionPostId() {
        return discussionPostId;
    }

    /** Returns {@code null} when the report targets a discussion post. */
    public UUID getLessonId() {
        return lessonId;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Returns {@code null} while the report is open. */
    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
