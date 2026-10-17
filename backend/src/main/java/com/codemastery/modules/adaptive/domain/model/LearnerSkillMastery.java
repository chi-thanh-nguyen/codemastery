package com.codemastery.modules.adaptive.domain.model;

import java.util.UUID;

public record LearnerSkillMastery(
        UUID id,
        UUID userId,
        UUID skillId,
        MasteryState state) {
}