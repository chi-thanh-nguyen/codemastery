package com.codemastery.modules.auth.api.dto;

import com.codemastery.modules.auth.domain.model.User;

import java.util.UUID;

/** Exact CT01 PublicUser shape; no email, password hash or account status. */
public record UserResponse(UUID id, String displayName, String role) {

    public static UserResponse from(User user) {
        String role = switch (user.role()) {
            case LEARNER -> "Learner";
            case INSTRUCTOR -> "Instructor";
            case ADMIN -> "Administrator";
        };
        return new UserResponse(user.id(), user.displayName(), role);
    }
}
