package com.codemastery.modules.interaction.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Local representation of a lesson discussion post (table {@code discussion_posts}).
 *
 * <p>{@code lessonId} and {@code authorId} are scalar identifiers of other modules.
 * {@code parentPostId} is {@code null} for an original post.
 */
public class DiscussionPost {

    private final UUID id;
    private final UUID lessonId;
    private final UUID authorId;
    private final UUID parentPostId;
    private final String content;
    private final Instant createdAt;
    private final boolean hidden;

    public DiscussionPost(
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

    /** Returns {@code null} for an original (non-reply) post. */
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
