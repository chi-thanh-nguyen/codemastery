package com.codemastery.modules.interaction.api.dto;

import com.codemastery.common.exception.BusinessRuleException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class InteractionRequests {

    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final tools.jackson.databind.json.JsonMapper JSON = tools.jackson.databind.json.JsonMapper.builder()
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public static Map<String, Object> readBody(jakarta.servlet.http.HttpServletRequest request) {
        try {
            byte[] bytes = request.getInputStream().readAllBytes();
            String text = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            var node = JSON.readTree(text);
            if (node == null || !node.isObject()) throw invalid();
            return JSON.convertValue(node, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (java.io.IOException | tools.jackson.core.JacksonException ex) {
            throw invalid();
        }
    }

    public static void requireJson(String contentType) throws org.springframework.web.HttpMediaTypeNotSupportedException {
        org.springframework.http.MediaType type = null;
        try { if (contentType != null) type = org.springframework.http.MediaType.parseMediaType(contentType); }
        catch (org.springframework.http.InvalidMediaTypeException ex) { throw new org.springframework.web.HttpMediaTypeNotSupportedException(ex.getMessage()); }
        if (type == null || !org.springframework.http.MediaType.APPLICATION_JSON.includes(type))
            throw new org.springframework.web.HttpMediaTypeNotSupportedException(type, java.util.List.of(org.springframework.http.MediaType.APPLICATION_JSON));
    }

    public static void noQuery(jakarta.servlet.http.HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) throw invalid();
    }

    public static int pageValue(org.springframework.util.MultiValueMap<String, String> params, String name, int fallback) {
        var values = params.get(name);
        if (values == null) return fallback;
        if (values.size() != 1 || !values.getFirst().matches("[0-9]+")) throw invalid();
        try { return Integer.parseInt(values.getFirst()); } catch (NumberFormatException ex) { throw invalid(); }
    }

    /** Parser result only: body shape validation is deferred until a usable parent is concealed/admitted. */
    public record DiscussionPostWrite(String content, UUID parentPostId, boolean validShape) {
        public DiscussionPostWrite(String content, UUID parentPostId) { this(content, parentPostId, true); }

        public static DiscussionPostWrite parse(Map<String, Object> body) {
            if (body == null) throw invalid();
            Object parent = body.get("parentPostId");
            if (parent != null && !(parent instanceof String)) throw BusinessRuleException.invalidField("parentPostId");
            UUID parentId;
            try { parentId = parent == null ? null : parseId((String) parent); }
            catch (BusinessRuleException ex) { throw BusinessRuleException.invalidField("parentPostId"); }
            boolean valid = body.get("content") instanceof String
                    && java.util.Set.of("content", "parentPostId").containsAll(body.keySet());
            return new DiscussionPostWrite(valid ? (String) body.get("content") : null, parentId, valid);
        }
    }

    private InteractionRequests() {
    }

    /** Strict path identifier: canonical UUID text only, otherwise 400 (ADR012 section 12, stage 2). */
    public static UUID parseId(String value) {
        if (value == null || !UUID_TEXT.matcher(value).matches()) {
            throw invalid();
        }
        return UUID.fromString(value);
    }

    /**
     * CT30.reports request (B7ContentReportWrite): a closed object with exactly one string property,
     * {@code reason}. The text is returned verbatim; whether it is non-whitespace is a field rule the
     * service applies after Lesson admission (stage 4).
     */
    public record ContentReportWrite(String reason) {

        public static ContentReportWrite parse(Map<String, Object> body) {
            if (body == null || body.size() != 1 || !(body.get("reason") instanceof String reason)) {
                throw invalid();
            }
            return new ContentReportWrite(reason);
        }
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid request");
    }
}
