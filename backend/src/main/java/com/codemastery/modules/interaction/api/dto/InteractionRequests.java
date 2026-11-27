package com.codemastery.modules.interaction.api.dto;

import com.codemastery.common.exception.BusinessRuleException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class InteractionRequests {

    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

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
