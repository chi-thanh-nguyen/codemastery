package com.codemastery.modules.admin.api.dto;

import com.codemastery.common.exception.BusinessRuleException;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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
            try {
                return OffsetDateTime.parse(value).toInstant();
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
}
