package com.codemastery.modules.assessment.application.port;

import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import java.util.UUID;
public interface PracticeDifficultyProvider {
    DifficultyLevel targetDifficulty(UUID courseId, UUID skillId);
}