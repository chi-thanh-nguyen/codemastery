package com.codemastery.modules.adaptive.api.dto;

import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService.SkillReference;

import java.util.UUID;

public record SkillResponse(UUID id, String name, String description) {

    public static SkillResponse from(SkillReference skill) {
        return new SkillResponse(skill.id(), skill.name(), skill.description());
    }
}