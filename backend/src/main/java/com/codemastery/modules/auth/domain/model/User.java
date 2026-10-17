package com.codemastery.modules.auth.domain.model;

import java.util.UUID;

public record User(
        UUID id,
        String email,
        String passwordHash,
        String displayName,
        Role role,
        AccountStatus accountStatus) {}
