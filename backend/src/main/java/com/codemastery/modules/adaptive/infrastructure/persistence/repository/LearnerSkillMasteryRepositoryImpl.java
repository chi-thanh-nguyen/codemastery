package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.repository.LearnerSkillMasteryRepository;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.LearnerSkillMasteryEntity;
import com.codemastery.modules.adaptive.infrastructure.persistence.mapper.MasteryMapper;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.UUID;

@Repository
public class LearnerSkillMasteryRepositoryImpl implements LearnerSkillMasteryRepository {

    private final LearnerSkillMasteryJpaRepository masteries;

    public LearnerSkillMasteryRepositoryImpl(LearnerSkillMasteryJpaRepository masteries) {
        this.masteries = Objects.requireNonNull(masteries);
    }

    @Override
    public void storeState(UUID learnerId, UUID skillId, MasteryState state) {
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(state, "state");

        String storedCode = MasteryMapper.toCode(state);
        if (masteries.updateState(learnerId, skillId, storedCode) == 1) {
            return;
        }
        masteries.save(new LearnerSkillMasteryEntity(UUID.randomUUID(), learnerId, skillId, storedCode));
    }
}