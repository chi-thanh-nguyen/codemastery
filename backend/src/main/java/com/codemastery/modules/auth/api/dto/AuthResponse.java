package com.codemastery.modules.auth.api.dto;

/** Exact approved CT01 login representation; never log a bearer credential. */
public record AuthResponse(String accessToken, String tokenType, int expiresIn, UserResponse user) {
    @Override public String toString() { return "AuthResponse[redacted]"; }
}
