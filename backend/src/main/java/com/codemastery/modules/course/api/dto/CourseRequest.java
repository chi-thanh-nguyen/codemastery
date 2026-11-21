package com.codemastery.modules.course.api.dto;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.course.domain.model.PublishStatus;
import org.springframework.http.HttpStatus;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.Set;

/**
 * CT21.authoring request bodies (OpenAPI OwnedCourseWrite, PublicationWrite). Parsing is strict:
 * exactly the declared fields with the declared JSON types; unknown, missing or duplicate fields
 * yield empty, so controllers can apply CT03 admission before the 400. Text rules are the service's.
 */
public final class CourseRequest {
    private static final JsonMapper STRICT_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private CourseRequest() {}

    public record OwnedCourseWrite(String title, String description, String prerequisiteDescription) {}

    public static Optional<OwnedCourseWrite> courseWrite(String json) {
        return object(json, Set.of("title", "description", "prerequisiteDescription"))
                .filter(input -> input.get("title").isString() && input.get("description").isString()
                        && nullableString(input.get("prerequisiteDescription")))
                .map(input -> new OwnedCourseWrite(input.get("title").asString(), input.get("description").asString(),
                        text(input.get("prerequisiteDescription"))));
    }

    public static Optional<PublishStatus> publication(String json) {
        return object(json, Set.of("publicationStatus")).map(input -> input.get("publicationStatus"))
                .filter(JsonNode::isString).flatMap(value -> switch (value.asString()) {
                    case "hidden" -> Optional.of(PublishStatus.HIDDEN);
                    case "published" -> Optional.of(PublishStatus.PUBLISHED);
                    default -> Optional.empty();
                });
    }

    public static BusinessRuleException invalidBody() {
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid authoring request body");
    }

    static Optional<JsonNode> object(String json, Set<String> fields) {
        if (json == null || json.isBlank()) return Optional.empty();
        try {
            JsonNode input = STRICT_JSON.readTree(json);
            if (input == null || !input.isObject() || input.size() != fields.size()) return Optional.empty();
            return fields.stream().allMatch(input::has) ? Optional.of(input) : Optional.empty();
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    static boolean nullableString(JsonNode value) {
        return value.isNull() || value.isString();
    }

    static String text(JsonNode value) {
        return value.isNull() ? null : value.asString();
    }
}
