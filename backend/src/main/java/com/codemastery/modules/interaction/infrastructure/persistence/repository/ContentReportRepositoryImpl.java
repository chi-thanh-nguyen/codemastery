package com.codemastery.modules.interaction.infrastructure.persistence.repository;

import com.codemastery.modules.interaction.domain.model.ContentReport;
import com.codemastery.modules.interaction.domain.repository.ContentReportRepository;
import com.codemastery.modules.interaction.infrastructure.persistence.entity.ContentReportEntity;
import com.codemastery.modules.interaction.infrastructure.persistence.mapper.InteractionMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ContentReportRepositoryImpl implements ContentReportRepository {

    private final ContentReportJpaRepository jpaRepository;
    private final InteractionMapper mapper;
    private final EntityManager entityManager;

    public ContentReportRepositoryImpl(
            ContentReportJpaRepository jpaRepository, InteractionMapper mapper, EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<ContentReport> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    /**
     * Inserts a new report, or updates only {@code status} and {@code resolved_at} of an existing
     * one. ADR012 §19.2 grants no UPDATE on any other column, so a full-row update is never issued
     * and a change of any other field of an existing report is rejected. Lifecycle rules (CT32)
     * belong to the caller; V006 checks still apply.
     */
    @Override
    @Transactional
    public ContentReport save(ContentReport report) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(report.getId(), "report id");
        Optional<ContentReportEntity> existing = jpaRepository.findById(report.getId());
        ContentReportEntity requested = mapper.toEntity(report);
        if (existing.isEmpty()) {
            return mapper.toDomain(jpaRepository.save(requested));
        }
        ContentReportEntity current = existing.get();
        if (!Objects.equals(current.getReporterId(), requested.getReporterId())
                || !Objects.equals(current.getDiscussionPostId(), requested.getDiscussionPostId())
                || !Objects.equals(current.getLessonId(), requested.getLessonId())
                || !Objects.equals(current.getReason(), requested.getReason())
                || !Objects.equals(current.getCreatedAt(), requested.getCreatedAt())) {
            throw new IllegalArgumentException("Only the status and resolution time of an existing content report can change");
        }
        if (!Objects.equals(current.getStatus(), requested.getStatus())
                || !Objects.equals(current.getResolvedAt(), requested.getResolvedAt())) {
            if (jpaRepository.updateResolution(report.getId(), requested.getStatus(), requested.getResolvedAt()) != 1) {
                throw new IllegalStateException("Content report update lost its row");
            }
            entityManager.refresh(current);
        }
        return mapper.toDomain(current);
    }
}
