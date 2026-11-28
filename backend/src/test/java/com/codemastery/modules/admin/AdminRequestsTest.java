package com.codemastery.modules.admin;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.admin.api.dto.AdminRequests.AuditLogSearch;
import com.codemastery.modules.admin.api.dto.AdminRequests.AdminCourseSearch;
import com.codemastery.modules.admin.api.dto.AdminRequests.CoursePublicationWrite;
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
    void acceptsRfc3339LowerCaseSeparatorsAndFractionDigits() {
        AuditLogSearch search = AuditLogSearch.parse(params(
                "occurredFrom", "2026-11-18t01:00:00.5z",
                "occurredTo", "2026-11-18T01:00:00.123456789-01:30"));

        assertAll(
                () -> assertEquals(Instant.parse("2026-11-18T01:00:00.500Z"), search.occurredFrom()),
                // Precision beyond microseconds is parsed here and rejected by the audit service.
                () -> assertEquals(Instant.parse("2026-11-18T02:30:00.123456789Z"), search.occurredTo()));
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
                // Accepted by ISO-8601 parsing but outside RFC 3339 date-time.
                params("occurredFrom", "2026-11-18T01:00Z"),
                params("occurredFrom", "2026-11-18T08:00:00+07"),
                params("occurredFrom", "2026-11-18T08:00:00+0700"),
                params("occurredFrom", "2026-11-18T08:00:00+07:00:30"),
                params("occurredFrom", "+12026-11-18T01:00:00Z"),
                params("occurredFrom", "2026-11-18T01:00:00.Z"),
                params("occurredFrom", "2026-11-18T01:00:00Z[UTC]"),
                params("occurredFrom", "2026-02-30T01:00:00Z"),
                params("occurredTo", "2026-11-18T24:00:00Z"),
                params("page", "-1"),
                params("page", "1.5"),
                params("size", "ten"),
                params("size", "99999999999"));

        for (MultiValueMap<String, String> query : invalid) {
            BusinessRuleException thrown = assertThrows(BusinessRuleException.class, () -> AuditLogSearch.parse(query));
            assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatus());
        }
    }

    @Test
    void acceptsTheExactHiddenBodyWithJsonWhitespace() {
        assertEquals("hidden", CoursePublicationWrite.parse("{\"publicationStatus\":\"hidden\"}")
                .requireHidden().publicationStatus());
        assertEquals("hidden", CoursePublicationWrite.parse(" {\n \"publicationStatus\" : \"hidden\" }\n")
                .requireHidden().publicationStatus());
    }

    @Test
    void acceptsJsonEscapesAndRejectsTrailingTokensWithoutCoercion() {
        assertEquals("hidden", CoursePublicationWrite.parse("{\"publicationStatus\":\"hid\\u0064en\"}").requireHidden().publicationStatus());
        assertEquals("hidden", CoursePublicationWrite.parse("{\"publication\\u0053tatus\":\"hidden\"}").requireHidden().publicationStatus());
        for (String body : List.of("{\"publicationStatus\":\"hidden\"} {}", "{\"publicationStatus\":\"hidden\"} null",
                "{\"publicationStatus\":true}", "{\"publicationStatus\":[\"hidden\"]}",
                "{\"publicationStatus\":\"hidden\",\"publication\\u0053tatus\":\"hidden\"}")) {
            assertThrows(BusinessRuleException.class, () -> CoursePublicationWrite.parse(body).requireHidden());
        }
    }

    @Test
    void canonicalUuidGrammarRejectsShortenedGroupsButAcceptsUppercase() {
        var id = java.util.UUID.randomUUID();
        assertEquals(id, com.codemastery.modules.admin.api.dto.AdminRequests.courseId(id.toString().toUpperCase()));
        for (String value : List.of("1-1-1-1-1", "x", " " + id, id + " ")) {
            assertThrows(BusinessRuleException.class, () -> com.codemastery.modules.admin.api.dto.AdminRequests.courseId(value));
        }
    }

    @Test
    void publishedAndEveryOtherValueIsRejected() {
        assertThrows(BusinessRuleException.class,
                () -> CoursePublicationWrite.parse("{\"publicationStatus\":\"published\"}").requireHidden());
        assertThrows(BusinessRuleException.class,
                () -> CoursePublicationWrite.parse("{\"publicationStatus\":\"\"}").requireHidden());
        assertThrows(BusinessRuleException.class,
                () -> CoursePublicationWrite.parse("{\"publicationStatus\":\"Hidden\"}").requireHidden());
    }

    @Test
    void anyOtherBodyShapeIsRejected() {
        for (String body : new String[] {
                null, "", "{}", "[]", "{\"publicationStatus\":1}", "{\"publicationStatus\":null}",
                "{\"publicationStatus\":\"hidden\",\"extra\":true}",
                "{\"publicationStatus\":\"hidden\",\"publicationStatus\":\"hidden\"}",
                "{\"publicationStatus\":\"hidden\"", "not json",
                "{'publicationStatus':'hidden'}", "{\"publicationStatus\":\"hidden\"} trailing"}) {
            assertThrows(BusinessRuleException.class, () -> CoursePublicationWrite.parse(body), String.valueOf(body));
        }
    }

    @Test
    void searchAcceptsKnownSingleValuedParametersAndNoneAtAll() {
        AdminCourseSearch none = AdminCourseSearch.parse(new LinkedMultiValueMap<>());
        assertNull(none.query());
        assertNull(none.publicationStatus());
        assertNull(none.page());
        assertNull(none.size());

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("query", "Py");
        params.add("publicationStatus", "hidden");
        params.add("page", "2");
        params.add("size", "50");
        AdminCourseSearch search = AdminCourseSearch.parse(params);
        assertEquals("Py", search.query());
        assertEquals("hidden", search.publicationStatus());
        assertEquals(2, search.page());
        assertEquals(50, search.size());
    }

    @Test
    void searchRejectsUnknownRepeatedBlankOrNonNumericParameters() {
        assertThrows(BusinessRuleException.class, () -> AdminCourseSearch.parse(one("unknown", "x")));
        assertThrows(BusinessRuleException.class, () -> AdminCourseSearch.parse(one("page", "-1")));
        assertThrows(BusinessRuleException.class, () -> AdminCourseSearch.parse(one("page", "abc")));
        assertThrows(BusinessRuleException.class, () -> AdminCourseSearch.parse(one("size", "99999999999")));
        assertThrows(BusinessRuleException.class, () -> AdminCourseSearch.parse(one("publicationStatus", " ")));
        MultiValueMap<String, String> repeated = new LinkedMultiValueMap<>();
        repeated.put("page", List.of("1", "2"));
        assertThrows(BusinessRuleException.class, () -> AdminCourseSearch.parse(repeated));
    }

    private static MultiValueMap<String, String> one(String name, String value) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(name, value);
        return params;
    }
}
