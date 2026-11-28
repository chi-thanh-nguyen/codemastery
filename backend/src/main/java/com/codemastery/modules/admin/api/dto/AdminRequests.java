package com.codemastery.modules.admin.api.dto;

import com.codemastery.common.exception.BusinessRuleException;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class AdminRequests {

    private AdminRequests() {
    }

    /**
     * CT31.read query parameters. This only checks syntax (known single-valued parameters, UUID text,
     * RFC 3339 instants, digits); catalogue membership, precision, ordering and paging bounds are
     * decided by the audit service.
     */
    public record AuditLogSearch(UUID actorId, String action, String targetType, UUID targetReference,
                                 Instant occurredFrom, Instant occurredTo, Integer page, Integer size) {

        private static final Set<String> PARAMETERS = Set.of(
                "actorId", "action", "targetType", "targetReference", "occurredFrom", "occurredTo", "page", "size");
        private static final Pattern UUID_TEXT = Pattern.compile(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        /** RFC 3339 section 5.6 date-time; "T" and "Z" may be lower case (section 5.6, note). */
        private static final Pattern RFC_3339_DATE_TIME = Pattern.compile(
                "[0-9]{4}-[0-9]{2}-[0-9]{2}[Tt][0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]+)?([Zz]|[+-][0-9]{2}:[0-9]{2})");

        public static AuditLogSearch parse(MultiValueMap<String, String> params) {
            if (!PARAMETERS.containsAll(params.keySet())
                    || params.values().stream().anyMatch(values -> values.size() != 1)) {
                throw invalid();
            }
            return new AuditLogSearch(
                    uuid(params.getFirst("actorId")),
                    text(params.getFirst("action")),
                    text(params.getFirst("targetType")),
                    uuid(params.getFirst("targetReference")),
                    instant(params.getFirst("occurredFrom")),
                    instant(params.getFirst("occurredTo")),
                    number(params.getFirst("page")),
                    number(params.getFirst("size")));
        }

        private static String text(String value) {
            if (value == null) {
                return null;
            }
            if (value.isBlank()) {
                throw invalid();
            }
            return value;
        }

        private static UUID uuid(String value) {
            if (value == null) {
                return null;
            }
            if (!UUID_TEXT.matcher(value).matches()) {
                throw invalid();
            }
            return UUID.fromString(value);
        }

        private static Instant instant(String value) {
            if (value == null) {
                return null;
            }
            // ISO-8601 parsing alone also accepts forms RFC 3339 excludes (no seconds, "+07" or
            // second-precision offsets, signed years beyond four digits), so the grammar is checked first.
            if (!RFC_3339_DATE_TIME.matcher(value).matches()) {
                throw invalid();
            }
            try {
                return OffsetDateTime.parse(value.toUpperCase(Locale.ROOT)).toInstant();
            } catch (DateTimeParseException exception) {
                throw invalid();
            }
        }

        private static Integer number(String value) {
            if (value == null) {
                return null;
            }
            if (!value.matches("[0-9]+")) {
                throw invalid();
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw invalid();
            }
        }

        private static BusinessRuleException invalid() {
            return new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid query");
        }
    }

    /**
     * CT12.rest course list query parameters. Syntax only (known single-valued parameters, digits); the
     * status value, paging bounds and the role are decided by the service.
     */
    public record AdminCourseSearch(String query, String publicationStatus, Integer page, Integer size) {

        private static final Set<String> PARAMETERS = Set.of("query", "publicationStatus", "page", "size");

        public static AdminCourseSearch parse(MultiValueMap<String, String> params) {
            if (!PARAMETERS.containsAll(params.keySet())
                    || params.values().stream().anyMatch(values -> values.size() != 1)) {
                throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid query");
            }
            String status = params.getFirst("publicationStatus");
            if (status != null && status.isBlank()) {
                throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid query");
            }
            return new AdminCourseSearch(params.getFirst("query"), status,
                    number(params.getFirst("page")), number(params.getFirst("size")));
        }

        private static Integer number(String value) {
            if (value == null) {
                return null;
            }
            if (!value.matches("[0-9]+")) {
                throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid query");
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid query");
            }
        }
    }

    /**
     * CT12.rest body of {@code PUT /api/admin/courses/{courseId}/publication}: exactly one string property,
     * {@code publicationStatus}. Any other shape (missing, extra or repeated properties, a non-string value,
     * malformed JSON or an escape sequence) is 400. The body is read as text so the role decision (stage 3)
     * can precede the body rules (stage 4), as the existing password-reset requests also do.
     */
    public record CoursePublicationWrite(String publicationStatus) {

        private static final String WS = "[ \\t\\r\\n]*";
        private static final Pattern BODY = Pattern.compile(
                WS + "\\{" + WS + "\"publicationStatus\"" + WS + ":" + WS
                        + "\"([^\"\\\\\\x00-\\x1F]*)\"" + WS + "\\}" + WS);

        public static CoursePublicationWrite parse(String json) {
            if (json == null) {
                throw invalid();
            }
            var matcher = BODY.matcher(json);
            if (!matcher.matches()) {
                throw invalid();
            }
            return new CoursePublicationWrite(matcher.group(1));
        }

        /** Only {@code hidden} is accepted; {@code published} and any other value are 400 (ADR012 section 10.3). */
        public CoursePublicationWrite requireHidden() {
            if (!"hidden".equals(publicationStatus)) {
                throw invalid();
            }
            return this;
        }

        private static BusinessRuleException invalid() {
            return new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid request body");
        }
    }
}
