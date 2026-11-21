package com.codemastery.modules.adaptive.api.dto;

import com.codemastery.common.exception.BusinessRuleException;
import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

public final class SkillRequests {

    private static final JsonMapper STRICT_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private static final Set<String> ALLOWED_FIELDS = Set.of("name", "description");

    private SkillRequests() {
    }

    public record SkillWrite(
            @NotBlank String name,
            @Pattern(regexp = "(?s).*\\S.*") String description) {

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static SkillWrite fromJson(JsonNode input) {
            if (input == null || !input.isObject() || !ALLOWED_FIELDS.containsAll(input.propertyNames())) {
                throw invalid();
            }
            return new SkillWrite(text(input, "name"), text(input, "description"));
        }
    }

    private static String text(JsonNode input, String field) {
        JsonNode value = input.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw invalid();
        }
        return value.asString();
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid skill body");
    }
}