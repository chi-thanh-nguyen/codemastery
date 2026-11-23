package com.codemastery.modules.admin;

import com.codemastery.modules.admin.domain.model.AuditLog;
import com.codemastery.modules.admin.infrastructure.persistence.entity.AuditLogEntity;
import com.codemastery.modules.admin.infrastructure.persistence.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuditLogMapperTest {

    private final AuditLogMapper mapper = new AuditLogMapper();

    @Test
    void mapsEveryFieldBetweenDomainAndEntity() {
        AuditLog domain = new AuditLog(UUID.randomUUID(), UUID.randomUUID(), "course_hidden", "course",
                UUID.randomUUID().toString(), Instant.parse("2026-11-18T01:00:00.123456Z"));

        AuditLogEntity entity = mapper.toEntity(domain);
        AuditLog roundTrip = mapper.toDomain(entity);

        assertAll(
                () -> assertEquals(domain.getId(), entity.getId()),
                () -> assertEquals(domain.getActorId(), entity.getActorId()),
                () -> assertEquals("course_hidden", entity.getAction()),
                () -> assertEquals("course", entity.getTargetType()),
                () -> assertEquals(domain.getTargetReference(), entity.getTargetReference()),
                () -> assertEquals(domain.getOccurredAt(), entity.getOccurredAt()),
                () -> assertEquals(domain.getId(), roundTrip.getId()),
                () -> assertEquals(domain.getActorId(), roundTrip.getActorId()),
                () -> assertEquals(domain.getAction(), roundTrip.getAction()),
                () -> assertEquals(domain.getTargetType(), roundTrip.getTargetType()),
                () -> assertEquals(domain.getTargetReference(), roundTrip.getTargetReference()),
                () -> assertEquals(domain.getOccurredAt(), roundTrip.getOccurredAt()));
    }

    @Test
    void nullInputsMapToNull() {
        assertNull(mapper.toEntity(null));
        assertNull(mapper.toDomain(null));
    }
}
