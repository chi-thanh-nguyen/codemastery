package com.codemastery.modules.interaction.infrastructure.persistence.repository;

import com.codemastery.modules.interaction.domain.model.DiscussionPost;
import com.codemastery.modules.interaction.domain.repository.DiscussionPostRepository;
import com.codemastery.modules.interaction.infrastructure.persistence.entity.DiscussionPostEntity;
import com.codemastery.modules.interaction.infrastructure.persistence.mapper.InteractionMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DiscussionPostRepositoryImpl implements DiscussionPostRepository {

    private final DiscussionPostJpaRepository jpaRepository;
    private final InteractionMapper mapper;

    public DiscussionPostRepositoryImpl(DiscussionPostJpaRepository jpaRepository, InteractionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<DiscussionPost> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public DiscussionPost save(DiscussionPost post) {
        Objects.requireNonNull(post, "post");
        DiscussionPostEntity saved = jpaRepository.save(mapper.toEntity(post));
        return mapper.toDomain(saved);
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
