package com.codemastery.modules.adaptive.decision;

import com.codemastery.modules.adaptive.application.decision.PracticeDifficultySelector;
import com.codemastery.modules.adaptive.application.decision.PracticeDifficultySelector.PracticeChoice;
import com.codemastery.modules.adaptive.application.decision.PracticeDifficultySelector.PracticeQuiz;
import com.codemastery.modules.adaptive.application.decision.PracticeDifficultySelector.PracticeSkill;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.codemastery.modules.assessment.domain.model.DifficultyLevel.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticeDifficultySelectorTest {

    private static final UUID LOOPS = new UUID(0, 0x10);
    private static final UUID ARRAYS = new UUID(0, 0x11);
    // Lowercase UUID text and the signed UUID.compareTo disagree on these two: "1..." < "8..." as
    // text, while 0x8000... is a negative most-significant long and compares first when signed.
    private static final UUID TEXT_FIRST = UUID.fromString("10000000-0000-0000-0000-000000000000");
    private static final UUID SIGNED_FIRST = UUID.fromString("80000000-0000-0000-0000-000000000000");

    // B6-R11 (ADR011 §8): mapping and fallback orders.

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

    @Test
    void selectsTheFirstAvailableLevelOfTheFallbackOrder() {
        assertEquals(Optional.of(MEDIUM), PracticeDifficultySelector.selectLevel(EASY, Set.of(MEDIUM, HARD)));
        assertEquals(Optional.of(EASY), PracticeDifficultySelector.selectLevel(MEDIUM, Set.of(EASY, HARD)));
        assertEquals(Optional.of(MEDIUM), PracticeDifficultySelector.selectLevel(HARD, Set.of(EASY, MEDIUM)));
        assertEquals(Optional.of(HARD), PracticeDifficultySelector.selectLevel(MEDIUM, Set.of(HARD)));
        assertEquals(Optional.empty(), PracticeDifficultySelector.selectLevel(HARD, Set.of()));
    }

    // ADR013 §19.4 practice recommendation rule, oracles B8-P01-P06 at rule level. Profile
    // admission (hidden, test, diagnostic, foreign-skill Quizzes) belongs to Assessment's read.

    @Test
    void b8P01EachStateGetsItsTargetLevelAndReasonWithNotYetAssessedForUnknown() {
        List<PracticeQuiz> uniform = List.of(quiz(1, "Loops practice (Easy)", EASY),
                quiz(2, "Loops practice (Medium)", MEDIUM), quiz(3, "Loops practice (Hard)", HARD));

        assertChoice(MasteryState.UNKNOWN, uniform, new UUID(0, 1), EASY,
                "Recommended practice: Easy, because your skill \"Loops\" is not yet assessed.");
        assertChoice(MasteryState.WEAK, uniform, new UUID(0, 1), EASY,
                "Recommended practice: Easy, because your skill \"Loops\" is Weak.");
        assertChoice(MasteryState.LEARNING, uniform, new UUID(0, 2), MEDIUM,
                "Recommended practice: Medium, because your skill \"Loops\" is Learning.");
        assertChoice(MasteryState.MASTERED, uniform, new UUID(0, 3), HARD,
                "Recommended practice: Hard, because your skill \"Loops\" is Mastered.");
    }

    @Test
    void b8P02TheEasiestTargetGovernsAndTiesFollowTheNameOrder() {
        List<PracticeQuiz> uniform = List.of(quiz(1, "Easy set", EASY), quiz(2, "Medium set", MEDIUM));

        // The easier target wins although its skill comes last in name order.
        PracticeChoice weakest = recommend(List.of(new PracticeSkill(LOOPS, "Arrays", MasteryState.MASTERED),
                new PracticeSkill(ARRAYS, "Zulu", MasteryState.LEARNING)), uniform).orElseThrow();
        assertEquals(ARRAYS, weakest.skillId());
        assertEquals("Zulu", weakest.skillName());
        assertEquals(MEDIUM, weakest.targetDifficulty());

        UUID upper = new UUID(0, 0x20), lowerSmall = new UUID(0, 0x21), lowerLarge = new UUID(0, 0x22);
        PracticeChoice caseSensitive = recommend(List.of(new PracticeSkill(lowerSmall, "alpha", MasteryState.WEAK),
                new PracticeSkill(upper, "Zeta", MasteryState.UNKNOWN)), uniform).orElseThrow();
        assertEquals(upper, caseSensitive.skillId(), "\"Zeta\" sorts before \"alpha\" by UTF-16 code unit");
        assertEquals("Recommended practice: Easy, because your skill \"Zeta\" is not yet assessed.", caseSensitive.reason());

        PracticeChoice equalNames = recommend(List.of(new PracticeSkill(lowerLarge, "alpha", MasteryState.WEAK),
                new PracticeSkill(lowerSmall, "alpha", MasteryState.UNKNOWN)), uniform).orElseThrow();
        assertEquals(lowerSmall, equalNames.skillId());

        PracticeChoice textOrder = recommend(List.of(new PracticeSkill(SIGNED_FIRST, "alpha", MasteryState.WEAK),
                new PracticeSkill(TEXT_FIRST, "alpha", MasteryState.WEAK)), uniform).orElseThrow();
        assertEquals(TEXT_FIRST, textOrder.skillId(), "equal names fall back to lowercase UUID text, not signed order");
    }

    @Test
    void b8P03MixedDifficultyAndEmptyQuizzesAreNeverRecommended() {
        List<PracticeQuiz> profiles = List.of(quiz(1, "A mixed set", EASY, MEDIUM), quiz(2, "An empty set"),
                quiz(3, "Z hard set", HARD));

        PracticeChoice choice = recommend(List.of(new PracticeSkill(LOOPS, "Loops", MasteryState.WEAK)), profiles)
                .orElseThrow();

        assertEquals(new UUID(0, 3), choice.quizId());
        assertEquals(HARD, choice.difficulty());
        assertTrue(recommend(List.of(new PracticeSkill(LOOPS, "Loops", MasteryState.WEAK)),
                List.of(quiz(1, "A mixed set", EASY, MEDIUM, HARD))).isEmpty());
    }

    @Test
    void b8P04FallbackFollowsTheOrderAndUsesTheSecondTemplateExactlyWhenTheLevelDiffers() {
        PracticeSkill learning = new PracticeSkill(LOOPS, "Loops", MasteryState.LEARNING);

        PracticeChoice toEasy = recommend(List.of(learning), List.of(quiz(1, "Easy", EASY), quiz(2, "Hard", HARD)))
                .orElseThrow();
        assertEquals(EASY, toEasy.difficulty());
        assertEquals(MEDIUM, toEasy.targetDifficulty());
        assertTrue(toEasy.fallback());
        assertEquals("Recommended practice: Easy, because your skill \"Loops\" is Learning and no Medium practice "
                + "quiz is available for this lesson.", toEasy.reason());

        PracticeChoice toHard = recommend(List.of(learning), List.of(quiz(2, "Hard", HARD))).orElseThrow();
        assertEquals(HARD, toHard.difficulty());
        assertTrue(toHard.fallback());

        PracticeChoice weakToMedium = recommend(List.of(new PracticeSkill(LOOPS, "Loops", MasteryState.WEAK)),
                List.of(quiz(2, "Hard", HARD), quiz(3, "Medium", MEDIUM))).orElseThrow();
        assertEquals(MEDIUM, weakToMedium.difficulty());
        assertEquals("Recommended practice: Medium, because your skill \"Loops\" is Weak and no Easy practice quiz "
                + "is available for this lesson.", weakToMedium.reason());

        PracticeSkill mastered = new PracticeSkill(LOOPS, "Loops", MasteryState.MASTERED);
        PracticeChoice masteredToMedium = recommend(List.of(mastered),
                List.of(quiz(1, "Easy", EASY), quiz(3, "Medium", MEDIUM))).orElseThrow();
        assertEquals(MEDIUM, masteredToMedium.difficulty());
        assertEquals(HARD, masteredToMedium.targetDifficulty());
        assertTrue(masteredToMedium.fallback());
        assertEquals("Recommended practice: Medium, because your skill \"Loops\" is Mastered and no Hard practice "
                + "quiz is available for this lesson.", masteredToMedium.reason());

        PracticeChoice masteredToEasy = recommend(List.of(mastered), List.of(quiz(1, "Easy", EASY))).orElseThrow();
        assertEquals(EASY, masteredToEasy.difficulty());
        assertTrue(masteredToEasy.fallback());
        assertEquals("Recommended practice: Easy, because your skill \"Loops\" is Mastered and no Hard practice "
                + "quiz is available for this lesson.", masteredToEasy.reason());

        PracticeChoice exact = recommend(List.of(learning), List.of(quiz(4, "Medium", MEDIUM))).orElseThrow();
        assertFalse(exact.fallback());
        assertEquals("Recommended practice: Medium, because your skill \"Loops\" is Learning.", exact.reason());

        assertTrue(recommend(List.of(learning), List.of()).isEmpty());
    }

    @Test
    void b8P05TiesAtOneLevelAreBrokenByTitleThenUuid() {
        PracticeSkill weak = new PracticeSkill(LOOPS, "Loops", MasteryState.WEAK);

        assertEquals(new UUID(0, 9), recommend(List.of(weak),
                List.of(quiz(1, "b set", EASY), quiz(9, "B set", EASY), quiz(5, "a set", EASY))).orElseThrow().quizId());
        assertEquals(new UUID(0, 4), recommend(List.of(weak),
                List.of(quiz(7, "Same", EASY), quiz(4, "Same", EASY))).orElseThrow().quizId());
        assertEquals(TEXT_FIRST, recommend(List.of(weak), List.of(new PracticeQuiz(SIGNED_FIRST, "Same", Set.of(EASY)),
                new PracticeQuiz(TEXT_FIRST, "Same", Set.of(EASY)))).orElseThrow().quizId());
    }

    @Test
    void b8P06NoRecommendationWhenAdaptiveModeIsDisabledOrNoSkillIsMapped() {
        List<PracticeQuiz> uniform = List.of(quiz(1, "Easy", EASY));

        assertTrue(PracticeDifficultySelector.recommendPractice(false,
                List.of(new PracticeSkill(LOOPS, "Loops", MasteryState.WEAK)), uniform).isEmpty());
        assertTrue(PracticeDifficultySelector.recommendPractice(true, List.of(), uniform).isEmpty());
    }

    @Test
    void invalidInputsAreRejectedAndChoicesAreReproducible() {
        PracticeSkill weak = new PracticeSkill(LOOPS, "Loops", MasteryState.WEAK);
        List<PracticeQuiz> uniform = List.of(quiz(1, "Easy", EASY), quiz(2, "Medium", MEDIUM));

        assertEquals(recommend(List.of(weak), uniform), recommend(List.of(weak), uniform.reversed()));
        assertThrows(IllegalArgumentException.class, () -> recommend(List.of(weak, weak), uniform));
        assertThrows(IllegalArgumentException.class, () -> recommend(List.of(weak), List.of(quiz(1, "x", EASY), quiz(1, "y", HARD))));
        assertThrows(IllegalArgumentException.class, () -> recommend(Arrays.asList(weak, null), uniform));
        assertThrows(IllegalArgumentException.class, () -> new PracticeSkill(LOOPS, null, MasteryState.WEAK));
        assertThrows(IllegalArgumentException.class, () -> new PracticeQuiz(new UUID(0, 1), "x", null));
        assertThrows(IllegalArgumentException.class, () -> new PracticeSkill(LOOPS, "", MasteryState.WEAK));
        assertThrows(IllegalArgumentException.class, () -> new PracticeQuiz(new UUID(0, 1), "", Set.of(EASY)));
        assertThrows(IllegalArgumentException.class, () -> PracticeDifficultySelector.targetDifficulty(null));
        assertThrows(IllegalArgumentException.class, () -> PracticeDifficultySelector.fallbackOrder(null));
        assertThrows(IllegalArgumentException.class, () -> PracticeDifficultySelector.selectLevel(EASY, null));
        assertThrows(IllegalArgumentException.class, () -> PracticeDifficultySelector.selectLevel(null, Set.of(EASY)));
    }

    private static void assertChoice(MasteryState state, List<PracticeQuiz> profiles, UUID quiz,
                                     DifficultyLevel level, String reason) {
        PracticeChoice choice = recommend(List.of(new PracticeSkill(LOOPS, "Loops", state)), profiles).orElseThrow();
        assertEquals(quiz, choice.quizId(), state.toString());
        assertEquals(level, choice.difficulty());
        assertEquals(level, choice.targetDifficulty());
        assertFalse(choice.fallback());
        assertEquals(state, choice.skillState());
        assertEquals("Loops", choice.skillName());
        assertEquals(reason, choice.reason());
    }

    private static Optional<PracticeChoice> recommend(List<PracticeSkill> skills, List<PracticeQuiz> profiles) {
        return PracticeDifficultySelector.recommendPractice(true, skills, profiles);
    }

    private static PracticeQuiz quiz(long id, String title, DifficultyLevel... levels) {
        return new PracticeQuiz(new UUID(0, id), title, Set.of(levels));
    }
}
