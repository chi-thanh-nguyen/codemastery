package com.codemastery.modules.adaptive.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.HashSet;
import java.util.Set;

public final class SkillRequests {

    private static final Set<String> ALLOWED_FIELDS = Set.of("name", "description");

    private SkillRequests() {
    }

    /**
     * ADR005 §8.1 closed skill body: only {@code name} and {@code description}, each a JSON string
     * or null. Any other member, value type or a repeated member name is a malformed body (400).
     */
    @JsonDeserialize(using = ClosedSkillWrite.class)
    public record SkillWrite(
            @NotBlank String name,
            @Pattern(regexp = "(?s).*\\S.*") String description) {
    }

    /**
     * Reads the object from the token stream so a duplicate member is seen before any tree or
     * binding could silently keep the last value.
     */
    public static final class ClosedSkillWrite extends ValueDeserializer<SkillWrite> {

        @Override
        public SkillWrite deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.isExpectedStartObjectToken()) {
                return invalid(context);
            }
            Set<String> seen = new HashSet<>();
            String name = null;
            String description = null;
            for (String field = parser.nextName(); field != null; field = parser.nextName()) {
                if (!ALLOWED_FIELDS.contains(field) || !seen.add(field)) {
                    return invalid(context);
                }
                JsonToken value = parser.nextToken();
                String text;
                if (value == JsonToken.VALUE_NULL) {
                    text = null;
                } else if (value == JsonToken.VALUE_STRING) {
                    text = parser.getString();
                } else {
                    return invalid(context);
                }
                if ("name".equals(field)) {
                    name = text;
                } else {
                    description = text;
                }
            }
            if (parser.currentToken() != JsonToken.END_OBJECT) {
                return invalid(context);
            }
            return new SkillWrite(name, description);
        }

        private static SkillWrite invalid(DeserializationContext context) {
            return context.reportInputMismatch(SkillWrite.class, "Invalid skill body");
        }
    }
}
