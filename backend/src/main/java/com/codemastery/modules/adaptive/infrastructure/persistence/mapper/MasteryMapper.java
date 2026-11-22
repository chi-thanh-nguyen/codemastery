package com.codemastery.modules.adaptive.infrastructure.persistence.mapper;

import com.codemastery.modules.adaptive.domain.model.LearnerSkillMastery;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.LearnerSkillMasteryEntity;
import org.springframework.stereotype.Component;

/**
 * Scalar mapping between {@link LearnerSkillMastery} and its V005 row.
 *
 * <p>
 * AD06.mapping checkpoint. The categorical state is persisted by
 * {@link MasteryState#getCode()}, so exactly {@code unknown}, {@code learning},
 * {@code mastered} and {@code weak} are stored, matching the V005
 * {@code ck_learner_skill_masteries_state} check. ADR005 §6.1 keeps the state
 * categorical: no numeric mastery field, threshold, confidence or derived value
 * is
 * mapped. An unrecognised stored code is a failure, never a fallback to
 * {@code Unknown}.
 *
 * <p>
 * This mapper performs no calculation and no state transition; those belong to
 * the approved CT06/CT24 calculator and classifier. It reads no clock and
 * applies
 * no default (ADR008 §6 selects no shared {@code Clock} bean).
 */
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