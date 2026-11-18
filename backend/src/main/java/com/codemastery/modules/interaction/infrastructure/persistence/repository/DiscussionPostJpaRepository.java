package com.codemastery.modules.interaction.infrastructure.persistence.repository;

import com.codemastery.modules.interaction.infrastructure.persistence.entity.DiscussionPostEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface DiscussionPostJpaRepository extends Repository<DiscussionPostEntity, UUID> {

    Optional<DiscussionPostEntity> findById(UUID id);

    DiscussionPostEntity save(DiscussionPostEntity entity);

    Page<DiscussionPostEntity> findByLessonId(UUID lessonId, Pageable pageable);

    Page<DiscussionPostEntity> findByLessonIdAndHiddenFalse(UUID lessonId, Pageable pageable);
}
