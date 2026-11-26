package com.codemastery.modules.assessment.api.dto;

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

/** Closed B3AnswersWrite: strict JSON/UTF-8, canonical UUID shape, no coercion or trailing content. */
public final class SubmitAttemptRequest {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private SubmitAttemptRequest() {}

    public record AnswerEntryRequest(UUID questionId, List<Boolean> selectedOptions) {
        public AnswerEntryRequest { selectedOptions = List.copyOf(selectedOptions); }
    }
    public record AnswersWriteRequest(List<AnswerEntryRequest> answers) {
        public AnswersWriteRequest { answers = List.copyOf(answers); }
        public static Optional<AnswersWriteRequest> parse(byte[] bytes) {
            try {
                JsonNode root = JSON.readTree(bytes);
                if (root == null || !root.isObject() || root.size() != 1 || !root.has("answers")
                        || !root.get("answers").isArray() || root.get("answers").isEmpty()) return Optional.empty();
                List<AnswerEntryRequest> answers = new ArrayList<>();
                Set<UUID> seen = new HashSet<>();
                for (JsonNode entry : root.get("answers")) {
                    if (!entry.isObject() || !Set.copyOf(entry.propertyNames()).equals(Set.of("questionId", "selectedOptions"))
                            || !entry.get("questionId").isString()
                            || !UUID_TEXT.matcher(entry.get("questionId").asString()).matches()
                            || !entry.get("selectedOptions").isArray()) return Optional.empty();
                    UUID id = UUID.fromString(entry.get("questionId").asString());
                    if (!seen.add(id)) return Optional.empty();
                    List<Boolean> mask = new ArrayList<>();
                    for (JsonNode value : entry.get("selectedOptions")) {
                        if (!value.isBoolean()) return Optional.empty();
                        mask.add(value.booleanValue());
                    }
                    answers.add(new AnswerEntryRequest(id, mask));
                }
                return Optional.of(new AnswersWriteRequest(answers));
            } catch (RuntimeException invalid) {
                return Optional.empty();
            }
        }
    }
}
