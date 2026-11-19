package com.codemastery.modules.assessment.api.dto;

import com.codemastery.modules.assessment.application.service.QuestionBankService.QuestionInput;
import java.util.ArrayList;
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
 * Closed B3QuestionWrite body (ADR007 §4): exactly the six members, no unknown, duplicate or null
 * member and no type coercion. Question validity itself (kinds, labels, masks) is checked by
 * {@code QuestionBankService} after Course admission.
 */
public record QuestionRequest(UUID skillId, String prompt, String kind, String difficulty,
                              List<String> options, List<Boolean> correctOptions) {

    private static final JsonMapper STRICT_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final Set<String> MEMBERS =
            Set.of("skillId", "prompt", "kind", "difficulty", "options", "correctOptions");
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public QuestionRequest {
        options = List.copyOf(options);
        correctOptions = List.copyOf(correctOptions);
    }

    /** The parsed body, or empty when it is not exactly the closed JSON shape. */
    public static Optional<QuestionRequest> parse(String json) {
        if (json == null) {
            return Optional.empty();
        }
        JsonNode input;
        try {
            input = STRICT_JSON.readTree(json);
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
        if (input == null || !input.isObject() || !MEMBERS.equals(Set.copyOf(input.propertyNames()))) {
            return Optional.empty();
        }
        JsonNode skillId = input.get("skillId");
        if (!skillId.isString() || !UUID_TEXT.matcher(skillId.asString()).matches()) {
            return Optional.empty();
        }
        Optional<String> prompt = string(input.get("prompt"));
        Optional<String> kind = string(input.get("kind"));
        Optional<String> difficulty = string(input.get("difficulty"));
        Optional<List<String>> options = array(input.get("options"), JsonNode::isString, JsonNode::asString);
        Optional<List<Boolean>> mask = array(input.get("correctOptions"), JsonNode::isBoolean, JsonNode::booleanValue);
        if (prompt.isEmpty() || kind.isEmpty() || difficulty.isEmpty() || options.isEmpty() || mask.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new QuestionRequest(UUID.fromString(skillId.asString()), prompt.get(), kind.get(),
                difficulty.get(), options.get(), mask.get()));
    }

    public QuestionInput toInput() {
        return new QuestionInput(skillId, prompt, kind, difficulty, options, correctOptions);
    }

    private static Optional<String> string(JsonNode value) {
        return value.isString() ? Optional.of(value.asString()) : Optional.empty();
    }

    private static <T> Optional<List<T>> array(JsonNode value, java.util.function.Predicate<JsonNode> type,
                                               java.util.function.Function<JsonNode, T> read) {
        if (!value.isArray()) {
            return Optional.empty();
        }
        List<T> items = new ArrayList<>();
        for (JsonNode item : value) {
            if (!type.test(item)) {
                return Optional.empty();
            }
            items.add(read.apply(item));
        }
        return Optional.of(items);
    }
}
