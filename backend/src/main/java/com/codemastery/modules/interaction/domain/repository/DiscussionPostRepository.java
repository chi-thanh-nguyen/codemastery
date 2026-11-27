package com.codemastery.modules.interaction.domain.repository;

import com.codemastery.modules.interaction.domain.model.DiscussionPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface DiscussionPostRepository {

    Optional<DiscussionPost> findById(UUID id);
    /** Caller holds a write transaction; shared parent lock serializes with moderation hiding. */
    Optional<DiscussionPost> lockById(UUID id, boolean exclusive);
    Page<DiscussionPost> findVisibleQuestions(UUID lessonId, Pageable pageable);
    java.util.List<DiscussionPost> findVisibleReplies(java.util.Set<UUID> questionIds);
    DiscussionPost save(DiscussionPost post);

    /**
     * Posts of one lesson, filtered before paging/counting. When {@code includeHidden}
     * is false, hidden posts are excluded. Ordering comes only from the supplied
     * {@code Pageable}; callers must pass a deterministic sort (for example one
     * ending in {@code id}) to get stable pages.
     */
    Page<DiscussionPost> findByLessonId(UUID lessonId, boolean includeHidden, Pageable pageable);
}
