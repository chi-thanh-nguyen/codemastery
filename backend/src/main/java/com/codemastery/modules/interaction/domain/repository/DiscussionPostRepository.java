package com.codemastery.modules.interaction.domain.repository;

import com.codemastery.modules.interaction.domain.model.DiscussionPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface DiscussionPostRepository {

    Optional<DiscussionPost> findById(UUID id);
    DiscussionPost save(DiscussionPost post);

    /**
     * Posts of one lesson, filtered before paging/counting. When {@code includeHidden}
     * is false, hidden posts are excluded. Ordering comes only from the supplied
     * {@code Pageable}; callers must pass a deterministic sort (for example one
     * ending in {@code id}) to get stable pages.
     */
    Page<DiscussionPost> findByLessonId(UUID lessonId, boolean includeHidden, Pageable pageable);
}
