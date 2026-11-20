package com.codemastery.modules.assessment.api.dto;

import com.codemastery.modules.assessment.application.service.QuizService.QuizInput;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Closed request bodies of the CT23 quiz bindings (ADR007 §§5–7, OpenAPI B3QuizWrite and
 * PublicationWrite): exactly the declared members, no unknown, duplicate or null member and no
 * type coercion. Quiz validity itself (title, kind, range) is checked by {@code QuizService}
 * after Course admission.
 */
public final class QuizRequest {

    private static final JsonMapper STRICT_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final Set<String> WRITE_MEMBERS = Set.of("title", "kind", "timeLimitSeconds", "questionIds");
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private QuizRequest() {}

    /** B3QuizWrite: full explicit ordered membership; an empty list is a valid hidden draft. */
    public record WriteQuizRequest(String title, String kind, int timeLimitSeconds, List<UUID> questionIds) {
        public WriteQuizRequest {
            questionIds = List.copyOf(questionIds);
        }

        public QuizInput toInput() {
            return new QuizInput(title, kind, timeLimitSeconds, questionIds);
        }

        /** The parsed body, or empty when it is not exactly the closed JSON shape. */
        public static Optional<WriteQuizRequest> parse(String json) {
            Optional<JsonNode> parsed = object(json);
            if (parsed.isEmpty() || !WRITE_MEMBERS.equals(Set.copyOf(parsed.get().propertyNames()))) {
                return Optional.empty();
            }
            JsonNode input = parsed.get();
            JsonNode title = input.get("title");
            JsonNode kind = input.get("kind");
            JsonNode timeLimit = input.get("timeLimitSeconds");
            JsonNode questionIds = input.get("questionIds");
            if (!title.isString() || !kind.isString() || !timeLimit.isIntegralNumber() || !timeLimit.canConvertToInt()
                    || !questionIds.isArray()) {
                return Optional.empty();
            }
            List<UUID> ids = new ArrayList<>();
            for (JsonNode item : questionIds) {
                if (!item.isString() || !UUID_TEXT.matcher(item.asString()).matches()) {
                    return Optional.empty();
                }
                ids.add(UUID.fromString(item.asString()));
            }
            // OpenAPI uniqueItems: a repeated question is malformed input.
            if (new HashSet<>(ids).size() != ids.size()) {
                return Optional.empty();
            }
            return Optional.of(new WriteQuizRequest(title.asString(), kind.asString(), timeLimit.intValue(), ids));
        }
    }

    /**
     * PublicationWrite: exactly {@code {"publicationStatus":"published"}} or
     * {@code {"publicationStatus":"hidden"}}, mapped to the {@code published} boolean of
     * {@code QuizService.publish} (ADR007 §6).
     */
    public record PublicationRequest(boolean published) {

        /** The parsed body, or empty when it is not exactly the closed JSON shape. */
        public static Optional<PublicationRequest> parse(String json) {
            return object(json)
                    .filter(input -> input.size() == 1 && input.has("publicationStatus"))
                    .map(input -> input.get("publicationStatus"))
                    .filter(JsonNode::isString)
                    .map(JsonNode::asString)
                    .flatMap(status -> switch (status) {
                        case "published" -> Optional.of(new PublicationRequest(true));
                        case "hidden" -> Optional.of(new PublicationRequest(false));
                        default -> Optional.empty();
                    });
        }
    }

    private static Optional<JsonNode> object(String json) {
        if (json == null) {
            return Optional.empty();
        }
        try {
            JsonNode input = STRICT_JSON.readTree(json);
            return input != null && input.isObject() ? Optional.of(input) : Optional.empty();
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }
}
