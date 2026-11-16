package com.codemastery.modules.interaction.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Scalar JPA mapping of {@code public.discussion_posts} (V006). */
@Entity
@Table(name = "discussion_posts")
public class DiscussionPostEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "parent_post_id")
    private UUID parentPostId;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    protected DiscussionPostEntity() {
        // Required by JPA.
    }

    public DiscussionPostEntity(
            UUID id,
            UUID lessonId,
            UUID authorId,
            UUID parentPostId,
            String content,
            Instant createdAt,
            boolean hidden) {
        this.id = id;
        this.lessonId = lessonId;
        this.authorId = authorId;
        this.parentPostId = parentPostId;
        this.content = content;
        this.createdAt = createdAt;
        this.hidden = hidden;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public UUID getParentPostId() {
        return parentPostId;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isHidden() {
        return hidden;
    }
}
