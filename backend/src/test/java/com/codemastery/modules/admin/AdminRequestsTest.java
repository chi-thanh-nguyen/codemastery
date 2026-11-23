package com.codemastery.modules.admin;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.admin.api.dto.AdminRequests.AuditLogSearch;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminRequestsTest {

    private static MultiValueMap<String, String> params(String... keysAndValues) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            params.add(keysAndValues[i], keysAndValues[i + 1]);
        }
        return params;
    }

    @Test
    void noParametersMeansNoFilterAndDefaultPaging() {
        AuditLogSearch search = AuditLogSearch.parse(params());

        assertAll(
                () -> assertNull(search.actorId()),
                () -> assertNull(search.action()),
                () -> assertNull(search.targetType()),
                () -> assertNull(search.targetReference()),
                () -> assertNull(search.occurredFrom()),
                () -> assertNull(search.occurredTo()),
                () -> assertNull(search.page()),
                () -> assertNull(search.size()));
    }

    @Test
    void parsesEveryParameterAndNormalisesInstantsToUtc() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        AuditLogSearch search = AuditLogSearch.parse(params(
                "actorId", actor.toString().toUpperCase(),
                "action", "user_locked",
                "targetType", "user",
                "targetReference", target.toString(),
                "occurredFrom", "2026-11-18T08:00:01.123456+07:00",
                "occurredTo", "2026-11-18T02:00:00Z",
                "page", "3",
                "size", "50"));

        assertAll(
                () -> assertEquals(actor, search.actorId()),
                () -> assertEquals("user_locked", search.action()),
                () -> assertEquals("user", search.targetType()),
                () -> assertEquals(target, search.targetReference()),
                () -> assertEquals(Instant.parse("2026-11-18T01:00:01.123456Z"), search.occurredFrom()),
                () -> assertEquals(Instant.parse("2026-11-18T02:00:00Z"), search.occurredTo()),
                () -> assertEquals(3, search.page()),
                () -> assertEquals(50, search.size()));
    }

    @Test
    void rejectsMalformedQueriesWithBadRequest() {
        MultiValueMap<String, String> duplicate = params("action", "user_locked");
        duplicate.add("action", "user_unlocked");
        List<MultiValueMap<String, String>> invalid = List.of(
                params("unknown", "1"),
                params("sort", "id"),
                duplicate,
                params("action", " "),
                params("actorId", "not-a-uuid"),
                params("targetReference", "12345"),
                params("occurredFrom", "yesterday"),
                params("occurredTo", "2026-11-18 01:00:00Z"),
                params("occurredFrom", "2026-11-18T01:00:00"),
                params("page", "-1"),
                params("page", "1.5"),
                params("size", "ten"),
                params("size", "99999999999"));

        for (MultiValueMap<String, String> query : invalid) {
            BusinessRuleException thrown = assertThrows(BusinessRuleException.class, () -> AuditLogSearch.parse(query));
            assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatus());
        }
    }
}
