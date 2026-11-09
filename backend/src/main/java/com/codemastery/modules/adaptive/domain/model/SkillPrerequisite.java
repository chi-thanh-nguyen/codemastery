package com.codemastery.modules.adaptive.domain.model;

import java.util.UUID;

public record SkillPrerequisite(
        UUID id,
        UUID courseId,
        UUID skillId,
        UUID prerequisiteSkillId) {

}