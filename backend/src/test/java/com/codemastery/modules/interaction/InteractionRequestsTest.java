package com.codemastery.modules.interaction;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.interaction.api.dto.InteractionRequests;
import com.codemastery.modules.interaction.api.dto.InteractionRequests.ContentReportWrite;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InteractionRequestsTest {

    @Test
    void parseIdAcceptsCanonicalUuidTextInAnyCase() {
        UUID id = UUID.randomUUID();

        assertEquals(id, InteractionRequests.parseId(id.toString()));
        assertEquals(id, InteractionRequests.parseId(id.toString().toUpperCase()));
    }

    @Test
    void parseIdRejectsEverythingElseWithBadRequest() {
        String valid = UUID.randomUUID().toString();
        List<String> invalid = java.util.Arrays.asList(null, "", " ", "not-a-uuid", "12345",
                valid.substring(1), valid + "0", " " + valid, valid + " ", "{" + valid + "}", "urn:uuid:" + valid,
                valid.replace("-", ""));

        for (String value : invalid) {
            BusinessRuleException thrown = assertThrows(BusinessRuleException.class, () -> InteractionRequests.parseId(value));
            assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatus());
        }
    }

    @Test
    void contentReportWriteReturnsTheReasonVerbatim() {
        assertEquals("  keep\n  spaces  ", ContentReportWrite.parse(Map.of("reason", "  keep\n  spaces  ")).reason());
        assertEquals("   ", ContentReportWrite.parse(Map.of("reason", "   ")).reason());
    }

    @Test
    void contentReportWriteIsAClosedObjectWithOneStringProperty() {
        Map<String, Object> nullReason = new HashMap<>();
        nullReason.put("reason", null);
        List<Map<String, Object>> invalid = java.util.Arrays.asList(
                null,
                Map.of(),
                Map.of("reason", 1),
                Map.of("reason", true),
                Map.of("reason", List.of("a")),
                nullReason,
                Map.of("other", "x"),
                Map.of("reason", "x", "extra", "y"));

        for (Map<String, Object> body : invalid) {
            BusinessRuleException thrown = assertThrows(BusinessRuleException.class, () -> ContentReportWrite.parse(body));
            assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatus());
        }
    }
}
