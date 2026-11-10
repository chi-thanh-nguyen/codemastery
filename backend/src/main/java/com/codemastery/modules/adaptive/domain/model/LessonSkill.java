package com.codemastery.modules.adaptive.domain.model;

import java.util.UUID;

public record LessonSkill(
        UUID id,
        UUID lessonId,
        UUID skillId) {

}