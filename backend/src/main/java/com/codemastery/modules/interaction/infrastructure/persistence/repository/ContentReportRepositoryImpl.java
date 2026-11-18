package com.codemastery.modules.interaction.infrastructure.persistence.repository;

import com.codemastery.modules.interaction.domain.model.ContentReport;
import com.codemastery.modules.interaction.domain.repository.ContentReportRepository;
import com.codemastery.modules.interaction.infrastructure.persistence.entity.ContentReportEntity;
import com.codemastery.modules.interaction.infrastructure.persistence.mapper.InteractionMapper;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ContentReportRepositoryImpl implements ContentReportRepository {

    private final ContentReportJpaRepository jpaRepository;
    private final InteractionMapper mapper;

    public ContentReportRepositoryImpl(ContentReportJpaRepository jpaRepository, InteractionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<ContentReport> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public ContentReport save(ContentReport report) {
        Objects.requireNonNull(report, "report");
        ContentReportEntity saved = jpaRepository.save(mapper.toEntity(report));
        return mapper.toDomain(saved);
    }
}
