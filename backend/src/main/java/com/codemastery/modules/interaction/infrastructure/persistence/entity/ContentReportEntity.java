package com.codemastery.modules.interaction.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Scalar JPA mapping of {@code public.content_reports} (V006).
 *
 * <p>{@code status} holds the stored code ({@code open}, {@code resolved} or
 * {@code dismissed}) as text. Converting it to the domain status is mapper work.
 */
@Entity
@Table(name = "content_reports")
public class ContentReportEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "discussion_post_id")
    private UUID discussionPostId;

    @Column(name = "lesson_id")
    private UUID lessonId;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected ContentReportEntity() {
        // Required by JPA.
    }

    public ContentReportEntity(
            UUID id,
            UUID reporterId,
            UUID discussionPostId,
            UUID lessonId,
            String reason,
            String status,
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

    public UUID getDiscussionPostId() {
        return discussionPostId;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public String getReason() {
        return reason;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
