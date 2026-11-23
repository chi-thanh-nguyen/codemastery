package com.codemastery.modules.adaptive.application.decision;

import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;

import java.util.List;

public final class PracticeDifficultySelector {

    private PracticeDifficultySelector() {
    }

    /**
     * Maps a resolved mastery state to its target practice difficulty under CT07.
     *
     * @param state the skill's mastery state, with UNKNOWN used for an absent
     *              mastery row
     * @return the target difficulty before quiz availability is considered
     */
    public static DifficultyLevel targetDifficulty(MasteryState state) {
        return switch (state) {
            case UNKNOWN, WEAK -> DifficultyLevel.EASY;
            case LEARNING -> DifficultyLevel.MEDIUM;
            case MASTERED -> DifficultyLevel.HARD;
        };
    }

    /**
     * Returns the CT07 search order for the target difficulty, starting with the
     * target.
     *
     * @param target the difficulty derived from mastery
     * @return the immutable order in which to look for eligible practice quizzes
     */
    public static List<DifficultyLevel> fallbackOrder(DifficultyLevel target) {
        return switch (target) {
            case EASY -> List.of(DifficultyLevel.EASY, DifficultyLevel.MEDIUM, DifficultyLevel.HARD);
            case MEDIUM -> List.of(DifficultyLevel.MEDIUM, DifficultyLevel.EASY, DifficultyLevel.HARD);
            case HARD -> List.of(DifficultyLevel.HARD, DifficultyLevel.MEDIUM, DifficultyLevel.EASY);
        };
    }
}