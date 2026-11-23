package com.codemastery.modules.adaptive.decision;

import com.codemastery.modules.adaptive.application.decision.PracticeDifficultySelector;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.codemastery.modules.assessment.domain.model.DifficultyLevel.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PracticeDifficultySelectorTest {

    @Test
    void mapsAllFourMasteryStates() {
        assertEquals(EASY, PracticeDifficultySelector.targetDifficulty(MasteryState.UNKNOWN));
        assertEquals(EASY, PracticeDifficultySelector.targetDifficulty(MasteryState.WEAK));
        assertEquals(MEDIUM, PracticeDifficultySelector.targetDifficulty(MasteryState.LEARNING));
        assertEquals(HARD, PracticeDifficultySelector.targetDifficulty(MasteryState.MASTERED));
    }

    @Test
    void usesTheContractFallbackOrderForEachTarget() {
        assertEquals(List.of(EASY, MEDIUM, HARD), PracticeDifficultySelector.fallbackOrder(EASY));
        assertEquals(List.of(MEDIUM, EASY, HARD), PracticeDifficultySelector.fallbackOrder(MEDIUM));
        assertEquals(List.of(HARD, MEDIUM, EASY), PracticeDifficultySelector.fallbackOrder(HARD));
    }
}