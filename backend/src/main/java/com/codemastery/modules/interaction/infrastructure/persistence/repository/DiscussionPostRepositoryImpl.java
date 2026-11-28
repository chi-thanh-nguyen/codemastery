package com.codemastery.modules.interaction.infrastructure.persistence.repository;

import com.codemastery.modules.interaction.domain.model.DiscussionPost;
import com.codemastery.modules.interaction.domain.repository.DiscussionPostRepository;
import com.codemastery.modules.interaction.infrastructure.persistence.entity.DiscussionPostEntity;
import com.codemastery.modules.interaction.infrastructure.persistence.mapper.InteractionMapper;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DiscussionPostRepositoryImpl implements DiscussionPostRepository {

    private final DiscussionPostJpaRepository jpaRepository;
    private final InteractionMapper mapper;
    private final EntityManager entityManager;

    public DiscussionPostRepositoryImpl(
            DiscussionPostJpaRepository jpaRepository, InteractionMapper mapper, EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<DiscussionPost> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<DiscussionPost> lockById(UUID id, boolean exclusive) {
        var rows = entityManager.createNativeQuery("SELECT * FROM public.discussion_posts WHERE id=:id FOR "
                        + (exclusive ? "UPDATE" : "SHARE"), DiscussionPostEntity.class)
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) return Optional.empty();
        var row = (DiscussionPostEntity) rows.getFirst();
        entityManager.refresh(row); // A pre-lock identity-map entry must not hide committed moderation.
        return Optional.of(mapper.toDomain(row));
    }

    @Override
    public Page<DiscussionPost> findVisibleQuestions(UUID lessonId, Pageable pageable) {
        long total = jpaRepository.countVisibleQuestions(lessonId);
        java.util.List<DiscussionPost> rows = pageable.getOffset() >= total ? java.util.List.of()
                : jpaRepository.findVisibleQuestions(lessonId, pageable.getPageSize(), pageable.getOffset()).stream().map(mapper::toDomain).toList();
        return new org.springframework.data.domain.PageImpl<>(rows, pageable, total);
    }

    @Override
    public java.util.List<DiscussionPost> findVisibleReplies(java.util.Set<UUID> ids) {
        return ids.isEmpty() ? java.util.List.of() : jpaRepository.findVisibleReplies(ids).stream().map(mapper::toDomain).toList();
    }

    /**
     * Inserts a new post, or updates only {@code is_hidden} of an existing one. ADR012 §19.2
     * grants no UPDATE on any other column, so a full-row update is never issued and a change
     * of any other field of an existing post is rejected.
     */
    @Override
    @Transactional
    public DiscussionPost save(DiscussionPost post) {
        Objects.requireNonNull(post, "post");
        Objects.requireNonNull(post.getId(), "post id");
        Optional<DiscussionPostEntity> existing = jpaRepository.findById(post.getId());
        if (existing.isEmpty()) {
            return mapper.toDomain(jpaRepository.save(mapper.toEntity(post)));
        }
        DiscussionPostEntity current = existing.get();
        if (!Objects.equals(current.getLessonId(), post.getLessonId())
                || !Objects.equals(current.getAuthorId(), post.getAuthorId())
                || !Objects.equals(current.getParentPostId(), post.getParentPostId())
                || !Objects.equals(current.getContent(), post.getContent())
                || !Objects.equals(current.getCreatedAt(), post.getCreatedAt())) {
            throw new IllegalArgumentException("Only the hidden flag of an existing discussion post can change");
        }
        if (current.isHidden() != post.isHidden()) {
            if (jpaRepository.updateHidden(post.getId(), post.isHidden()) != 1) {
                throw new IllegalStateException("Discussion post update lost its row");
            }
            entityManager.refresh(current);
        }
        return mapper.toDomain(current);
    }

    @Override
    public Page<DiscussionPost> findByLessonId(UUID lessonId, boolean includeHidden, Pageable pageable) {
        Objects.requireNonNull(lessonId, "lessonId");
        Objects.requireNonNull(pageable, "pageable");
        Page<DiscussionPostEntity> page = includeHidden
                ? jpaRepository.findByLessonId(lessonId, pageable)
                : jpaRepository.findByLessonIdAndHiddenFalse(lessonId, pageable);
        return page.map(mapper::toDomain);
    }
}
