package com.codemastery.modules.interaction.infrastructure.persistence.repository;

import com.codemastery.modules.interaction.infrastructure.persistence.entity.DiscussionPostEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DiscussionPostJpaRepository extends Repository<DiscussionPostEntity, UUID> {

    Optional<DiscussionPostEntity> findById(UUID id);

    /** Inserts a new row. Must not be used for an existing row (ADR012 §19.2). */
    DiscussionPostEntity save(DiscussionPostEntity entity);

    Page<DiscussionPostEntity> findByLessonId(UUID lessonId, Pageable pageable);

    Page<DiscussionPostEntity> findByLessonIdAndHiddenFalse(UUID lessonId, Pageable pageable);

    @Query(value = "SELECT * FROM public.discussion_posts WHERE lesson_id=:lessonId AND parent_post_id IS NULL AND NOT is_hidden ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    java.util.List<DiscussionPostEntity> findVisibleQuestions(@Param("lessonId") UUID lessonId,
            @Param("limit") int limit, @Param("offset") long offset);

    @Query("select count(p) from DiscussionPostEntity p where p.lessonId=:lessonId and p.parentPostId is null and p.hidden=false")
    long countVisibleQuestions(@Param("lessonId") UUID lessonId);

    @Query("select p from DiscussionPostEntity p where p.parentPostId in :ids and p.hidden = false and exists (select q.id from DiscussionPostEntity q where q.id=p.parentPostId and q.hidden=false and q.parentPostId is null and q.lessonId=p.lessonId) order by p.createdAt, p.id")
    java.util.List<DiscussionPostEntity> findVisibleReplies(@Param("ids") java.util.Set<UUID> ids);

    /** Targeted update of the only mutable column, {@code is_hidden} (ADR012 §19.2). */
    @Modifying(flushAutomatically = true)
    @Query("update DiscussionPostEntity p set p.hidden = :hidden where p.id = :id")
    int updateHidden(@Param("id") UUID id, @Param("hidden") boolean hidden);
}
