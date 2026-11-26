package com.codemastery.modules.assessment.api.dto;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.assessment.application.service.AssignmentService;
import com.codemastery.modules.assessment.application.service.AssignmentService.AssignmentInput;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.codemastery.modules.assessment.application.service.SubmissionService.GradeInput;
import java.math.BigDecimal;
/**
 * Closed CT10.rest JSON request bodies (ADR010 §§8, 12, 14; OpenAPI B5AssignmentWrite,
 * PublicationWrite). Unknown, missing, duplicate or coerced members yield empty so controllers
 * enforce stage-3 admission before reporting stage-4 body 400s.
 */
public final class AssignmentRequests {

    private static final JsonMapper STRICT_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final Set<String> WRITE_MEMBERS =
            Set.of("title", "instructions", "deadlineAt", "lessonId");
    private static final Set<String> PUBLICATION_MEMBERS = Set.of("publicationStatus");
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern RFC_3339_DATE_TIME = Pattern.compile(
            "([0-9]{4}-[0-9]{2}-[0-9]{2}[Tt][0-9]{2}:[0-9]{2}:[0-9]{2})(?:\\.([0-9]+))?([Zz]|[+-][0-9]{2}:[0-9]{2})");

    private static final Set<String> GRADE_MEMBERS = Set.of("gradePercentage", "feedback", "submittedAt");

    private AssignmentRequests() {
    }

    public record WriteAssignmentRequest(String title, String instructions, Instant deadlineAt, UUID lessonId) {
        public AssignmentInput toInput() {
            return new AssignmentInput(title, instructions, deadlineAt, lessonId);
        }
    }

    public record PublicationRequest(String publicationStatus) {
    }

    public static void requireJson(String contentType) throws HttpMediaTypeNotSupportedException {
        MediaType type;
        try {
            type = contentType == null ? null : MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException invalid) {
            throw new HttpMediaTypeNotSupportedException("Invalid Content-Type");
        }
        if (type == null || !MediaType.APPLICATION_JSON.includes(type)) {
            throw new HttpMediaTypeNotSupportedException(type, List.of(MediaType.APPLICATION_JSON));
        }
    }

    public static Optional<WriteAssignmentRequest> parseWrite(byte[] utf8Bytes) {
        return decodeUtf8(utf8Bytes).flatMap(AssignmentRequests::parseWrite);
    }

    public static Optional<WriteAssignmentRequest> parseWrite(String json) {
        Optional<JsonNode> node = object(json, WRITE_MEMBERS);
        if (node.isEmpty()) {
            return Optional.empty();
        }
        JsonNode input = node.get();
        JsonNode title = input.get("title");
        JsonNode instructions = input.get("instructions");
        JsonNode deadlineAt = input.get("deadlineAt");
        JsonNode lessonId = input.get("lessonId");
        if (!title.isString() || !instructions.isString() || !deadlineAt.isString()) {
            return Optional.empty();
        }
        Optional<Instant> parsedDeadline = parseMicrosecondInstant(deadlineAt.asString());
        if (parsedDeadline.isEmpty()) {
            return Optional.empty();
        }
        UUID parsedLesson = null;
        if (!lessonId.isNull()) {
            if (!lessonId.isString() || !UUID_TEXT.matcher(lessonId.asString()).matches()) {
                return Optional.empty();
            }
            parsedLesson = UUID.fromString(lessonId.asString());
        }
        return Optional.of(new WriteAssignmentRequest(
                title.asString(), instructions.asString(), parsedDeadline.get(), parsedLesson));
    }

    public static Optional<PublicationRequest> parsePublication(byte[] utf8Bytes) {
        return decodeUtf8(utf8Bytes).flatMap(AssignmentRequests::parsePublication);
    }

    public static Optional<PublicationRequest> parsePublication(String json) {
        return object(json, PUBLICATION_MEMBERS)
                .map(input -> input.get("publicationStatus"))
                .filter(JsonNode::isString)
                .map(JsonNode::asString)
                .filter(status -> "hidden".equals(status) || "published".equals(status))
                .map(PublicationRequest::new);
    }

    /**
     * Parses an RFC 3339 date-time with at most microsecond precision; nonzero digits below
     * the microsecond are rejected rather than rounded (ADR010 §§8–9). An instant outside years
     * 0000-9999 in UTC is rejected, because its response rendering would not be RFC 3339.
     */
    public static Optional<Instant> parseMicrosecondInstant(String value) {
        if (value == null) {
            return Optional.empty();
        }
        Matcher matcher = RFC_3339_DATE_TIME.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String fraction = matcher.group(2);
        String normalizedFraction = "";
        if (fraction != null) {
            if (fraction.length() > 6 && !fraction.substring(6).chars().allMatch(ch -> ch == '0')) {
                return Optional.empty();
            }
            normalizedFraction = "." + (fraction.length() > 6 ? fraction.substring(0, 6) : fraction);
        }
        String normalized = (matcher.group(1) + normalizedFraction + matcher.group(3)).toUpperCase(Locale.ROOT);
        try {
            Instant instant = OffsetDateTime.parse(normalized).toInstant();
            // The offset is applied first: the range is checked on the UTC instant that responses
            // render, not on the year written in the input (ADR010 §8 "representable deadline").
            return instant.getNano() % 1_000 == 0 && AssignmentService.isRepresentableDeadline(instant)
                    ? Optional.of(instant)
                    : Optional.empty();
        } catch (DateTimeParseException invalid) {
            return Optional.empty();
        }
    }

    public static BusinessRuleException invalid() {
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid assignment request");
    }

    private static Optional<String> decodeUtf8(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString());
        } catch (CharacterCodingException malformed) {
            return Optional.empty();
        }
    }

    private static Optional<JsonNode> object(String json, Set<String> expectedMembers) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        JsonNode parsed;
        try {
            parsed = STRICT_JSON.readTree(json);
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
        if (parsed == null || !parsed.isObject() || !expectedMembers.equals(Set.copyOf(parsed.propertyNames()))) {
            return Optional.empty();
        }
        return Optional.of(parsed);
    }

    public record GradeSubmissionRequest(BigDecimal gradePercentage, String feedback, Instant submittedAt) {
        public GradeInput toInput() {
            return new GradeInput(gradePercentage, feedback, submittedAt);
        }
    }

    public static Optional<GradeSubmissionRequest> parseGrade(byte[] utf8Bytes) {
        return decodeUtf8(utf8Bytes).flatMap(AssignmentRequests::parseGrade);
    }

    public static Optional<GradeSubmissionRequest> parseGrade(String json) {
        Optional<JsonNode> node = object(json, GRADE_MEMBERS);
        if (node.isEmpty()) {
            return Optional.empty();
        }
        JsonNode input = node.get();
        JsonNode grade = input.get("gradePercentage");
        JsonNode feedback = input.get("feedback");
        JsonNode submittedAt = input.get("submittedAt");
        if (!grade.isNumber() || !feedback.isString() || !submittedAt.isString()) {
            return Optional.empty();
        }
        BigDecimal parsedGrade;
        try {
            parsedGrade = grade.decimalValue();
        } catch (RuntimeException invalidNumber) {
            return Optional.empty();
        }
        Optional<Instant> parsedSubmittedAt = parseMicrosecondInstant(submittedAt.asString());
        if (parsedSubmittedAt.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new GradeSubmissionRequest(
                parsedGrade, feedback.asString(), parsedSubmittedAt.get()));
    }
}
