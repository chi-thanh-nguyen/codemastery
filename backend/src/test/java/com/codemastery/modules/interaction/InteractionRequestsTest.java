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
    @Test
    void invalidPrefixIsRejectedBeforeTheRemainingBodyIsBuffered() {
        var consumed = new java.util.concurrent.atomic.AtomicInteger();
        var request = new org.springframework.mock.web.MockHttpServletRequest() {
            @Override public jakarta.servlet.ServletInputStream getInputStream() {
                return new jakarta.servlet.ServletInputStream() {
                    @Override public int read() { int n = consumed.getAndIncrement(); return n == 0 ? '!' : n < 1048576 ? ' ' : -1; }
                    @Override public boolean isFinished() { return consumed.get() >= 1048576; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(jakarta.servlet.ReadListener listener) { }
                };
            }
        };
        assertThrows(BusinessRuleException.class, () -> InteractionRequests.readBody(request));
        org.junit.jupiter.api.Assertions.assertTrue(consumed.get() < 65536, "bytes consumed=" + consumed.get());
    }

    @Test
    void streamingDecodeRetainsStrictUtf8AndDoesNotAddATextLengthLimit() {
        String text = "  e\u0301 \ud83d\ude80 " + "x".repeat(1_100_000);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setContent(("{\"reason\":\"" + text + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(text, InteractionRequests.readBody(request).get("reason"));
        byte[] malformedTail = ("{\"reason\":\"x\"}" + " ".repeat(9000)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        malformedTail[malformedTail.length - 1] = (byte) 0xc3;
        request.setContent(malformedTail);
        assertThrows(BusinessRuleException.class, () -> InteractionRequests.readBody(request));
    }

}
