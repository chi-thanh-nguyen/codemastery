package com.codemastery.modules.adaptive.domain.repository;

import com.codemastery.modules.adaptive.domain.model.MasteryState;

import java.util.UUID;

public interface LearnerSkillMasteryRepository {

    void storeState(UUID learnerId, UUID skillId, MasteryState state);
}