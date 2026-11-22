package com.codemastery.modules.adaptive.api.dto;

import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService.GraphSnapshot;

import java.util.List;
import java.util.UUID;

public record SkillGraphResponse(UUID courseId, List<SkillResponse> skills,
        List<Prerequisite> prerequisites, List<Mapping> mappings) {

    public SkillGraphResponse {
        skills = List.copyOf(skills);
        prerequisites = List.copyOf(prerequisites);
        mappings = List.copyOf(mappings);
    }

    public static SkillGraphResponse from(GraphSnapshot snapshot) {
        return new SkillGraphResponse(
                snapshot.courseId(),
                snapshot.skills().stream().map(SkillResponse::from).toList(),
                snapshot.prerequisites().stream()
                        .map(edge -> new Prerequisite(edge.skillId(), edge.prerequisiteSkillId()))
                        .toList(),
                snapshot.mappings().stream()
                        .map(mapping -> new Mapping(mapping.lessonId(), mapping.skillId()))
                        .toList());
    }

    public record Prerequisite(UUID skillId, UUID prerequisiteSkillId) {
    }

    public record Mapping(UUID lessonId, UUID skillId) {
    }
}