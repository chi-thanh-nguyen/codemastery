package com.codemastery.modules.auth.api.dto;

import com.codemastery.common.validation.StrongPassword;
import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.JsonNode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Locale;
import java.util.Set;

public final class AuthRequests {

    private AuthRequests() {}

    public record RegisterRequest(
            @NotNull @Email @Pattern(regexp = "[!-~]+") String email,
            @NotNull @StrongPassword String password,
            @NotNull @DisplayName String displayName) {

        public RegisterRequest {
            email = canonicalEmail(email);
            displayName = displayName == null ? null : displayName.strip();
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static RegisterRequest fromJson(JsonNode input) {
            rejectUnknownProperties(input, Set.of("email", "password", "displayName"));
            return new RegisterRequest(string(input, "email"), string(input, "password"),
                    string(input, "displayName"));
        }

        @Override
        public String toString() {
            return "RegisterRequest[redacted]";
        }
    }

    /** Approved login representation; registration complexity is not imposed on login. */
    public record LoginRequest(@NotNull @Email @Pattern(regexp = "[!-~]+") String email,
                               @NotNull String password) {
        public LoginRequest {
            email = canonicalEmail(email);
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static LoginRequest fromJson(JsonNode input) {
            rejectUnknownProperties(input, Set.of("email", "password"));
            return new LoginRequest(string(input, "email"), string(input, "password"));
        }

        @Override
        public String toString() {
            return "LoginRequest[redacted]";
        }
    }

    private static void rejectUnknownProperties(JsonNode input, Set<String> allowed) {
        if (!input.isObject() || !allowed.containsAll(input.propertyNames())) {
            throw new IllegalArgumentException("Invalid account request.");
        }
    }

    private static String string(JsonNode input, String field) {
        JsonNode value = input.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException("Invalid account request.");
        }
        return value.asString();
    }

    private static String canonicalEmail(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.chars().allMatch(c -> c < 128) ? trimmed.toLowerCase(Locale.ROOT) : trimmed;
    }

    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = DisplayNameValidator.class)
    public @interface DisplayName {
        String message() default "Display name must be nonblank, at most 100 Unicode code points and contain no control characters";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class DisplayNameValidator implements ConstraintValidator<DisplayName, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || (!value.isBlank()
                    && value.codePointCount(0, value.length()) <= 100
                    && value.codePoints().noneMatch(Character::isISOControl));
        }
    }
}
