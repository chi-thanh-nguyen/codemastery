package com.codemastery.modules.admin.infrastructure.persistence.repository;

import com.codemastery.modules.admin.domain.model.AuditLog;
import com.codemastery.modules.admin.domain.repository.AuditLogRepository;
import com.codemastery.modules.admin.infrastructure.persistence.entity.AuditLogEntity;
import com.codemastery.modules.admin.infrastructure.persistence.mapper.AuditLogMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogJpaRepository jpaRepository;
    private final AuditLogMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public AuditLogRepositoryImpl(AuditLogJpaRepository jpaRepository, AuditLogMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public AuditLog append(AuditLog auditLog) {
        Objects.requireNonNull(auditLog, "auditLog");
        // persist never issues an UPDATE (merge could); a duplicate id fails instead of overwriting.
        entityManager.persist(mapper.toEntity(auditLog));
        return auditLog;
    }

    @Override
    public Page<AuditLog> search(AuditLogFilter filter, Pageable pageable) {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(pageable, "pageable");
        // SQL owns the fixed order. Never let Spring Data apply caller sorting.
        Pageable pagination = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        Specification<AuditLogEntity> matching = matching(filter);
        if (pagination.getOffset() > Integer.MAX_VALUE) {
            // Beyond any addressable row: an empty page with the true total.
            return new PageImpl<>(List.of(), pagination, jpaRepository.count(matching));
        }
        return jpaRepository.findAll(matching, pagination).map(mapper::toDomain);
    }

    private static Specification<AuditLogEntity> matching(AuditLogFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.actorId() != null) {
                predicates.add(builder.equal(root.get("actorId"), filter.actorId()));
            }
            if (filter.action() != null) {
                predicates.add(builder.equal(root.get("action"), filter.action()));
            }
            if (filter.targetType() != null) {
                predicates.add(builder.equal(root.get("targetType"), filter.targetType()));
            }
            if (filter.targetReference() != null) {
                predicates.add(builder.equal(root.get("targetReference"), filter.targetReference()));
            }
            if (filter.occurredFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), filter.occurredFrom()));
            }
            if (filter.occurredTo() != null) {
                predicates.add(builder.lessThan(root.<Instant>get("occurredAt"), filter.occurredTo()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
