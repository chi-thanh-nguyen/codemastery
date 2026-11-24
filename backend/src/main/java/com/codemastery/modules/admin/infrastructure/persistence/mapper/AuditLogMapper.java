package com.codemastery.modules.admin.infrastructure.persistence.mapper;

import com.codemastery.modules.admin.domain.model.AuditLog;
import com.codemastery.modules.admin.infrastructure.persistence.entity.AuditLogEntity;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogEntity toEntity(AuditLog domain) {
        if (domain == null) {
            return null;
        }
        return new AuditLogEntity(
                domain.getId(),
                domain.getActorId(),
                domain.getAction(),
                domain.getTargetType(),
                domain.getTargetReference(),
                domain.getOccurredAt());
    }

    public AuditLog toDomain(AuditLogEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AuditLog(
                entity.getId(),
                entity.getActorId(),
                entity.getAction(),
                entity.getTargetType(),
                entity.getTargetReference(),
                entity.getOccurredAt());
    }
}
