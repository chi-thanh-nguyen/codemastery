package com.codemastery.modules.adaptive.infrastructure.persistence.mapper;

import com.codemastery.modules.adaptive.domain.model.LearnerSkillMastery;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.LearnerSkillMasteryEntity;
import org.springframework.stereotype.Component;

/**
 * Scalar mapping between the AD03 {@link LearnerSkillMastery} record and its V005 row
 * (AD06.mapping).
 *
 * <p>The categorical state is persisted by {@link MasteryState#getCode()}, so exactly
 * {@code unknown}, {@code learning}, {@code mastered} and {@code weak} are stored, matching the
 * V005 {@code ck_learner_skill_masteries_state} check. ADR005 §6.1 keeps the state categorical: no
 * numeric mastery field, threshold, confidence or derived value is mapped. An unrecognised stored
 * code is a failure, never a fallback to {@code Unknown}. A {@code null} input maps to
 * {@code null}; V005's {@code NOT NULL} columns stay the authority on missing values.
 *
 * <p>This mapper performs no calculation, classification or state transition and applies no
 * default: those belong to the CT06/CT24 calculator and classifier and to the caller (ADR011 §4).
 * It reads no clock.
 */
@Component
public class MasteryMapper {

    public LearnerSkillMasteryEntity toEntity(LearnerSkillMastery domain) {
        if (domain == null) {
            return null;
        }
        return new LearnerSkillMasteryEntity(domain.id(), domain.userId(), domain.skillId(), toCode(domain.state()));
    }

    public LearnerSkillMastery toDomain(LearnerSkillMasteryEntity entity) {
        if (entity == null) {
            return null;
        }
        return new LearnerSkillMastery(entity.getId(), entity.getUserId(), entity.getSkillId(), toState(entity.getState()));
    }

    /** The exact V005 stored code of a state. */
    public String toCode(MasteryState state) {
        return state == null ? null : state.getCode();
    }

    /** The state whose V005 code is exactly {@code code}; any other spelling is rejected. */
    public MasteryState toState(String code) {
        if (code == null) {
            return null;
        }
        for (MasteryState state : MasteryState.values()) {
            if (state.getCode().equals(code)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown mastery state code: '" + code + "'");
    }
}
