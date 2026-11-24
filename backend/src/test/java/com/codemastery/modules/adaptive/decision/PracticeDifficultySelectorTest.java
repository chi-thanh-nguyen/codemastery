package com.codemastery.modules.adaptive.decision;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.adaptive.application.decision.PracticeDifficultySelector;
import com.codemastery.modules.adaptive.application.decision.PracticeDifficultySelector.PracticeChoice;
import com.codemastery.modules.adaptive.application.decision.PracticeDifficultySelector.PracticeQuiz;
import com.codemastery.modules.adaptive.application.decision.PracticeDifficultySelector.PracticeSkill;
import com.codemastery.modules.adaptive.application.mastery.MasteryQueryService;
import com.codemastery.modules.adaptive.application.recommendation.RecommendationService;
import com.codemastery.modules.adaptive.application.recommendation.RecommendationService.PracticeRecommendation;
import com.codemastery.modules.adaptive.domain.model.LessonSkill;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.model.Skill;
import com.codemastery.modules.adaptive.domain.repository.PathRecommendationRepository;
import com.codemastery.modules.adaptive.domain.repository.SkillRepository;
import com.codemastery.modules.assessment.application.service.QuizService;
import com.codemastery.modules.assessment.application.service.QuizService.PracticeQuizProfile;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import com.codemastery.modules.course.application.service.LessonProgressService;
import com.codemastery.modules.course.application.service.LessonProgressService.LearnerCourseState;
import com.codemastery.modules.course.application.service.LessonProgressService.LessonState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.codemastery.modules.assessment.domain.model.DifficultyLevel.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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

    /**
     * {@code RecommendationService.readPracticeRecommendation}: the ADR013 §19.4 REST composition over
     * test doubles of its four providers (B8-P06–P09 at service level). Real PostgreSQL, the security
     * chain and snapshot evidence are in {@code AdaptiveLearningFlowIntegrationTest}.
     */
    @Nested
    class ReadPracticeRecommendation {

        private static final UUID COURSE = new UUID(1, 1);
        private static final UUID LEARNER = new UUID(1, 2);
        private static final UUID L1 = new UUID(2, 1);
        private static final UUID L2 = new UUID(2, 2);
        private static final UUID OTHER_SKILL = new UUID(3, 3);

        private final PathRecommendationRepository history = mock(PathRecommendationRepository.class);
        private final LessonProgressService learnerStates = mock(LessonProgressService.class);
        private final SkillRepository skills = mock(SkillRepository.class);
        private final MasteryQueryService masteries = mock(MasteryQueryService.class);
        private final QuizService quizzes = mock(QuizService.class);
        private final RecommendationService service =
                new RecommendationService(history, learnerStates, skills, masteries, quizzes);

        @Test
        void composesAdmissionGraphStatesAndProfilesInOrderForExactlyTheLessonsSkills() {
            admitted(true);
            graph();
            when(masteries.readStates(LEARNER, Set.of(LOOPS, ARRAYS)))
                    .thenReturn(Map.of(LOOPS, MasteryState.MASTERED, ARRAYS, MasteryState.UNKNOWN));
            when(quizzes.readPracticeQuizProfiles(COURSE, Set.of(LOOPS, ARRAYS))).thenReturn(List.of(
                    profile(1, "A mixed set", EASY, HARD), profile(2, "Hard set", HARD),
                    profile(3, "Easy set", EASY), profile(4, "Another easy set", EASY)));

            PracticeRecommendation result = service.readPracticeRecommendation(COURSE, L1);

            assertEquals(COURSE, result.courseId());
            assertEquals(L1, result.lessonId());
            assertTrue(result.adaptiveEnabled());
            PracticeChoice choice = result.recommendation();
            assertEquals(new UUID(0, 4), choice.quizId(), "title order among the uniform Easy candidates");
            assertEquals("Another easy set", choice.quizTitle());
            assertEquals(EASY, choice.difficulty());
            assertEquals(EASY, choice.targetDifficulty());
            assertFalse(choice.fallback());
            assertEquals(ARRAYS, choice.skillId(), "the Unknown skill has the easiest target and governs");
            assertEquals("Arrays", choice.skillName());
            assertEquals(MasteryState.UNKNOWN, choice.skillState());
            assertEquals("Recommended practice: Easy, because your skill \"Arrays\" is not yet assessed.", choice.reason());

            InOrder order = inOrder(learnerStates, skills, masteries, quizzes);
            order.verify(learnerStates).readLearnerState(COURSE);
            order.verify(skills).findSkills(COURSE);
            order.verify(skills).findMappingsForSkills(Set.of(LOOPS, ARRAYS, OTHER_SKILL));
            order.verify(masteries).readStates(LEARNER, Set.of(LOOPS, ARRAYS));
            order.verify(quizzes).readPracticeQuizProfiles(COURSE, Set.of(LOOPS, ARRAYS));
            verifyNoMoreInteractions(learnerStates, skills, masteries, quizzes);
            verifyNoInteractions(history);
        }

        @Test
        void aFallbackLevelIsReportedWithTheSecondTemplate() {
            admitted(true);
            graph();
            when(masteries.readStates(LEARNER, Set.of(OTHER_SKILL))).thenReturn(Map.of(OTHER_SKILL, MasteryState.MASTERED));
            when(quizzes.readPracticeQuizProfiles(COURSE, Set.of(OTHER_SKILL)))
                    .thenReturn(List.of(profile(5, "Medium set", MEDIUM), profile(6, "Easy set", EASY)));

            PracticeChoice choice = service.readPracticeRecommendation(COURSE, L2).recommendation();

            assertEquals(MEDIUM, choice.difficulty());
            assertEquals(HARD, choice.targetDifficulty());
            assertTrue(choice.fallback());
            assertEquals("Recommended practice: Medium, because your skill \"Strings\" is Mastered and no Hard practice "
                    + "quiz is available for this lesson.", choice.reason());
        }

        @Test
        void anAdmissionFailurePropagatesBeforeAnyOtherRead() {
            when(learnerStates.readLearnerState(COURSE)).thenThrow(new AccessDeniedException("Access denied"));

            assertThrows(AccessDeniedException.class, () -> service.readPracticeRecommendation(COURSE, L1));
            verifyNoInteractions(skills, masteries, quizzes, history);
        }

        @Test
        void aLessonThatIsNotAPublishedLessonOfTheCourseIsUnavailableBeforeAnyOtherRead() {
            admitted(true);

            assertThrows(ResourceNotFoundException.class,
                    () -> service.readPracticeRecommendation(COURSE, new UUID(2, 9)));
            verifyNoInteractions(skills, masteries, quizzes, history);
        }

        @Test
        void aDisabledCourseHasNoRecommendationAndReadsNothingElse() {
            admitted(false);

            PracticeRecommendation result = service.readPracticeRecommendation(COURSE, L1);

            assertFalse(result.adaptiveEnabled());
            assertNull(result.recommendation());
            verifyNoInteractions(skills, masteries, quizzes, history);
        }

        @Test
        void aLessonWithoutMappedSkillsHasNoRecommendationAndNeverReadsStatesOrAssessment() {
            admitted(true);
            when(skills.findSkills(COURSE)).thenReturn(List.of(new Skill(LOOPS, COURSE, "Loops", null)));
            when(skills.findMappingsForSkills(Set.of(LOOPS))).thenReturn(List.of(new LessonSkill(new UUID(4, 1), L2, LOOPS)));

            PracticeRecommendation result = service.readPracticeRecommendation(COURSE, L1);

            assertTrue(result.adaptiveEnabled());
            assertNull(result.recommendation());
            verifyNoInteractions(masteries, quizzes, history);

            when(skills.findSkills(COURSE)).thenReturn(List.of());
            when(skills.findMappingsForSkills(Set.of())).thenReturn(List.of());
            assertNull(service.readPracticeRecommendation(COURSE, L1).recommendation());
            verifyNoInteractions(masteries, quizzes, history);
        }

        @Test
        void noUniformCandidateAtAnyLevelIsANullRecommendation() {
            admitted(true);
            graph();
            when(masteries.readStates(LEARNER, Set.of(OTHER_SKILL))).thenReturn(Map.of(OTHER_SKILL, MasteryState.WEAK));
            when(quizzes.readPracticeQuizProfiles(COURSE, Set.of(OTHER_SKILL)))
                    .thenReturn(List.of(profile(7, "Mixed", EASY, MEDIUM)));

            PracticeRecommendation result = service.readPracticeRecommendation(COURSE, L2);

            assertTrue(result.adaptiveEnabled());
            assertNull(result.recommendation());
        }

        @Test
        void providerAndIntegrityFailuresFailClosed() {
            when(learnerStates.readLearnerState(COURSE)).thenReturn(null);
            assertThrows(IllegalStateException.class, () -> service.readPracticeRecommendation(COURSE, L1));

            when(learnerStates.readLearnerState(COURSE)).thenReturn(state(new UUID(9, 9), true));
            assertThrows(IllegalStateException.class, () -> service.readPracticeRecommendation(COURSE, L1));

            admitted(true);
            when(skills.findSkills(COURSE)).thenReturn(List.of(new Skill(LOOPS, new UUID(9, 9), "Loops", null)));
            assertThrows(IllegalStateException.class, () -> service.readPracticeRecommendation(COURSE, L1));

            when(skills.findSkills(COURSE)).thenReturn(List.of(new Skill(LOOPS, COURSE, "Loops", null)));
            when(skills.findMappingsForSkills(Set.of(LOOPS)))
                    .thenReturn(List.of(new LessonSkill(new UUID(4, 1), L1, OTHER_SKILL)));
            assertThrows(IllegalStateException.class, () -> service.readPracticeRecommendation(COURSE, L1));

            when(skills.findMappingsForSkills(Set.of(LOOPS))).thenReturn(List.of(new LessonSkill(new UUID(4, 1), L1, LOOPS)));
            when(masteries.readStates(LEARNER, Set.of(LOOPS))).thenReturn(Map.of(LOOPS, MasteryState.WEAK));
            when(quizzes.readPracticeQuizProfiles(COURSE, Set.of(LOOPS))).thenReturn(null);
            assertThrows(IllegalStateException.class, () -> service.readPracticeRecommendation(COURSE, L1));

            assertThrows(IllegalArgumentException.class, () -> service.readPracticeRecommendation(null, L1));
            assertThrows(IllegalArgumentException.class, () -> service.readPracticeRecommendation(COURSE, null));
            verifyNoInteractions(history);
        }

        @Test
        void theReadIsOneReadOnlyRepeatableReadSnapshotThatNeverUsesThePortOrWrites() throws Exception {
            Transactional tx = RecommendationService.class
                    .getMethod("readPracticeRecommendation", UUID.class, UUID.class).getAnnotation(Transactional.class);
            assertTrue(tx.readOnly());
            assertEquals(Isolation.REPEATABLE_READ, tx.isolation());
            assertEquals(Propagation.REQUIRED, tx.propagation(), "starts the snapshot that both upstream reads join");

            // The Assessment port keeps no production caller (ADR011 §8, ADR013 §19.2); the other
            // allowlists are asserted by PracticeQuizProfilesTest and LessonProgressServiceTest.
            String code = Files.readString(Path.of("src/main/java/com/codemastery/modules/adaptive/application/"
                    + "recommendation/RecommendationService.java"))
                    .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ").replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
            assertTrue(code.contains("readLearnerState") && code.contains("readPracticeQuizProfiles"), "comments stripped, code kept");
            assertFalse(code.contains("PracticeDifficultyProvider"));
            assertFalse(code.contains("ApplicationEventPublisher"));
        }

        /** AD16 {@code readStates} takes any learner ID, so only an admitting composition may call it. */
        @Test
        void masteryStatesHaveOnlyTheApprovedAdmittingCaller() throws Exception {
            Path root = Path.of("src", "main", "java", "com", "codemastery");
            Set<Path> allowed = Set.of(
                    root.resolve("modules/adaptive/application/recommendation/RecommendationService.java"));
            java.util.regex.Pattern call = java.util.regex.Pattern.compile("(\\.|::)\\s*readStates\\b");
            List<Path> callers;
            try (var files = Files.walk(root)) {
                callers = files.filter(file -> file.toString().endsWith(".java")).filter(file -> {
                    try {
                        return call.matcher(code(Files.readString(file))).find();
                    } catch (java.io.IOException failure) {
                        throw new java.io.UncheckedIOException(failure);
                    }
                }).toList();
            }
            assertEquals(List.copyOf(allowed), callers, "Unapproved AD16 mastery state callers");
            assertTrue(call.matcher(code("class C { void r() { masteries.readStates(a, b); } }")).find());
            assertTrue(call.matcher(code("class C { Object f = service::readStates; }")).find());
            assertFalse(call.matcher(code("class C { // masteries.readStates(a, b)\n String s = \"x.readStates(\"; }")).find());
        }

        private void admitted(boolean adaptiveEnabled) {
            when(learnerStates.readLearnerState(COURSE)).thenReturn(state(COURSE, adaptiveEnabled));
        }

        private static LearnerCourseState state(UUID course, boolean adaptiveEnabled) {
            return new LearnerCourseState(course, LEARNER, new UUID(5, 1), "active", adaptiveEnabled,
                    adaptiveEnabled ? Map.of() : null,
                    List.of(new LessonState(L1, new UUID(6, 1), 1, 1, false, false),
                            new LessonState(L2, new UUID(6, 1), 1, 2, true, false)));
        }

        /** L1 maps Loops and Arrays, L2 maps Strings; a foreign Lesson's mapping is ignored. */
        private void graph() {
            when(skills.findSkills(COURSE)).thenReturn(List.of(new Skill(LOOPS, COURSE, "Loops", null),
                    new Skill(ARRAYS, COURSE, "Arrays", "Lists"), new Skill(OTHER_SKILL, COURSE, "Strings", null)));
            when(skills.findMappingsForSkills(Set.of(LOOPS, ARRAYS, OTHER_SKILL))).thenReturn(List.of(
                    new LessonSkill(new UUID(4, 1), L1, LOOPS), new LessonSkill(new UUID(4, 2), L1, ARRAYS),
                    new LessonSkill(new UUID(4, 3), L2, OTHER_SKILL), new LessonSkill(new UUID(4, 4), new UUID(2, 7), LOOPS)));
        }

        /** Source without comments and string literals, so only code is scanned. */
        private static String code(String source) {
            return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ")
                    .replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
        }

        private static PracticeQuizProfile profile(long id, String title, DifficultyLevel... levels) {
            return new PracticeQuizProfile(new UUID(0, id), title, Set.of(levels));
        }
    }
}
