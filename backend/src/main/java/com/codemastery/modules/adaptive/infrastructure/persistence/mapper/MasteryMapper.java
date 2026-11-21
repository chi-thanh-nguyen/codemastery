package com.codemastery.modules.adaptive.infrastructure.persistence.mapper;

import com.codemastery.modules.adaptive.domain.model.LearnerSkillMastery;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.LearnerSkillMasteryEntity;
import org.springframework.stereotype.Component;

@Component
public class MasteryMapper {

    public LearnerSkillMasteryEntity toEntity(LearnerSkillMastery domain) {
        if (domain == null) {
            return null;
        }

        return new LearnerSkillMasteryEntity(
                domain.id(),
                domain.userId(),
                domain.skillId(),
                toCode(domain.state()));
    }

    public LearnerSkillMastery toDomain(LearnerSkillMasteryEntity entity) {
        if (entity == null) {
            return null;
        }

        return new LearnerSkillMastery(
                entity.getId(),
                entity.getUserId(),
                entity.getSkillId(),
                toState(entity.getState()));
    }

    public static String toCode(MasteryState state) {
        return state == null ? null : state.getCode();
    }

    public static MasteryState toState(String code) {
        if (code == null) {
            return null;
        }

        for (MasteryState state : MasteryState.values()) {
            if (state.getCode().equals(code)) {
                return state;
            }
        }

        throw new IllegalArgumentException("Unknown mastery state code");
    }
}