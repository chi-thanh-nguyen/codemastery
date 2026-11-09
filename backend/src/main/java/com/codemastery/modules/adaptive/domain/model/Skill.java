package com.codemastery.modules.adaptive.domain.model;

import java.util.UUID;

public record Skill(
        UUID id,
        UUID courseId,
        String name,
        String description) {

}