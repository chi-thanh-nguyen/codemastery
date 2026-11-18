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

    /** Targeted update of the only mutable column, {@code is_hidden} (ADR012 §19.2). */
    @Modifying(flushAutomatically = true)
    @Query("update DiscussionPostEntity p set p.hidden = :hidden where p.id = :id")
    int updateHidden(@Param("id") UUID id, @Param("hidden") boolean hidden);
}
