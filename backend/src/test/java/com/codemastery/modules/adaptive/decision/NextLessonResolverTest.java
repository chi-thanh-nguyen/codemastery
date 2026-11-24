package com.codemastery.modules.adaptive.decision;

import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.DecisionContext;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.LessonInput;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.PrerequisiteInput;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision.Recommendation;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NextLessonResolverTest {

    @Test
    void completedCourseStillRecommendsReviewWhileTheDependentSkillIsWeak() {
        UUID basics = new UUID(0, 1);
        UUID loops = new UUID(0, 2);
        UUID basicsLesson = new UUID(0, 3);
        UUID loopsLesson = new UUID(0, 4);
        UUID course = new UUID(0, 5);
        UUID learner = new UUID(0, 6);

        var lessons = List.of(new LessonInput(basicsLesson, true, Set.of(basics)),
                new LessonInput(loopsLesson, true, Set.of(loops)));
        var edges = List.of(new PrerequisiteInput(loops, basics));
        var names = Map.of(basics, "Basics", loops, "Loops");
        var engine = new AdaptiveDecisionEngine();

        var weak = new DecisionContext(course, learner, lessons,
                Map.of(basics, MasteryState.UNKNOWN, loops, MasteryState.WEAK), edges, names);
        var learning = new DecisionContext(course, learner, lessons,
                Map.of(basics, MasteryState.UNKNOWN, loops, MasteryState.LEARNING), edges, names);

        assertEquals(List.of(new Recommendation(RecommendationType.REMEDIAL, basicsLesson,
                "Review this lesson first: your skill \"Loops\" is Weak "
                        + "and its prerequisite \"Basics\" is not yet Mastered.")),
                engine.decide(weak).recommendations());
        assertTrue(engine.decide(learning).recommendations().isEmpty());
    }

    @Test
    void earlierWeakSkillWinsEvenWhenTheOtherSkillHasAnEarlierRemedialLesson() {
        UUID variables = new UUID(0, 1);
        UUID indexing = new UUID(0, 2);
        UUID loops = new UUID(0, 20);
        UUID arrays = new UUID(0, 10);

        UUID indexingLesson = new UUID(0, 101);
        UUID variablesLesson = new UUID(0, 102);
        UUID loopsLesson = new UUID(0, 103);
        UUID arraysLesson = new UUID(0, 104);

        var lessons = List.of(
                new LessonInput(indexingLesson, false, Set.of(indexing)),
                new LessonInput(variablesLesson, false, Set.of(variables)),
                new LessonInput(loopsLesson, false, Set.of(loops)),
                new LessonInput(arraysLesson, false, Set.of(arrays)));

        var edges = List.of(
                new PrerequisiteInput(arrays, indexing),
                new PrerequisiteInput(loops, variables));

        var mastery = Map.of(
                variables, MasteryState.UNKNOWN, indexing, MasteryState.UNKNOWN,
                loops, MasteryState.WEAK, arrays, MasteryState.WEAK);
        var names = Map.of(
                variables, "Variables", indexing, "Indexing",
                loops, "Loops", arrays, "Arrays");

        var expected = List.of(new Recommendation(
                RecommendationType.REMEDIAL, variablesLesson,
                "Review this lesson first: your skill \"Loops\" is Weak "
                        + "and its prerequisite \"Variables\" is not yet Mastered."));

        var engine = new AdaptiveDecisionEngine();
        for (var inputEdges : List.of(edges, edges.reversed())) {
            var context = new DecisionContext(
                    new UUID(0, 201), new UUID(0, 202),
                    lessons, mastery, inputEdges, names);

            assertEquals(expected, engine.decide(context).recommendations());
        }
    }

    @Test
    void choosesEarliestEligibleTargetAcrossDirectPrerequisites() {
        UUID basics = new UUID(0, 1);
        UUID conditions = new UUID(0, 2);
        UUID unmapped = new UUID(0, 3);
        UUID loops = new UUID(0, 4);

        UUID completedBasics = new UUID(0, 101);
        UUID conditionsLesson = new UUID(0, 102);
        UUID nextBasicsLesson = new UUID(0, 103);
        UUID loopsLesson = new UUID(0, 104);

        var context = new DecisionContext(
                new UUID(0, 201), new UUID(0, 202),
                List.of(
                        new LessonInput(completedBasics, true, Set.of(basics)),
                        new LessonInput(conditionsLesson, false, Set.of(conditions)),
                        new LessonInput(nextBasicsLesson, false, Set.of(basics)),
                        new LessonInput(loopsLesson, false, Set.of(loops))),
                Map.of(basics, MasteryState.UNKNOWN, conditions, MasteryState.LEARNING,
                        unmapped, MasteryState.UNKNOWN, loops, MasteryState.WEAK),
                List.of(
                        new PrerequisiteInput(loops, unmapped),
                        new PrerequisiteInput(loops, basics),
                        new PrerequisiteInput(loops, conditions)),
                Map.of(basics, "Basics", conditions, "Conditions",
                        unmapped, "Unmapped", loops, "Loops"));

        var result = new AdaptiveDecisionEngine().decide(context);

        assertEquals(List.of(new Recommendation(
                RecommendationType.REMEDIAL, conditionsLesson,
                "Review this lesson first: your skill \"Loops\" is Weak "
                        + "and its prerequisite \"Conditions\" is not yet Mastered.")),
                result.recommendations());
    }

    // ADR011 §10 rule oracles on the standard fixture: L1-L3 map S1-S3; S2 -> S1 and S3 -> S2.

    private static final UUID COURSE = new UUID(0, 0x900), LEARNER = new UUID(0, 0x901);
    // UUIDs deliberately invert the canonical order so that only the specified orders can pass.
    private static final UUID S1 = new UUID(0, 0x73), S2 = new UUID(0, 0x72), S3 = new UUID(0, 0x71);
    private static final UUID L1 = new UUID(0, 0x803), L2 = new UUID(0, 0x802), L3 = new UUID(0, 0x801);
    private static final Map<UUID, String> NAMES = Map.of(S1, "Variables", S2, "Loops", S3, "Arrays");

    @Test
    void b6R04TheWeakSkillWithTheSmallerLessonIndexIsExaminedFirst() {
        var context = new DecisionContext(COURSE, LEARNER, standardLessons(false, false, false),
                Map.of(S1, MasteryState.UNKNOWN, S2, MasteryState.WEAK, S3, MasteryState.WEAK),
                List.of(new PrerequisiteInput(S2, S1), new PrerequisiteInput(S3, S2)), NAMES);

        assertEquals(List.of(new Recommendation(RecommendationType.REMEDIAL, L1,
                        "Review this lesson first: your skill \"Loops\" is Weak and its prerequisite \"Variables\" is not yet Mastered.")),
                new AdaptiveDecisionEngine().decide(context).recommendations());
    }

    @Test
    void b6R05AmongUnmasteredPrerequisitesTheEarliestTargetWinsWhateverTheUuidOrder() {
        // S3 requires both S1 and S2; S1's Lesson comes first although S2 has the smaller UUID.
        var context = new DecisionContext(COURSE, LEARNER, standardLessons(false, false, false),
                Map.of(S1, MasteryState.LEARNING, S2, MasteryState.UNKNOWN, S3, MasteryState.WEAK),
                List.of(new PrerequisiteInput(S3, S2), new PrerequisiteInput(S3, S1)), NAMES);

        assertEquals(new Recommendation(RecommendationType.REMEDIAL, L1,
                        "Review this lesson first: your skill \"Arrays\" is Weak and its prerequisite \"Variables\" is not yet Mastered."),
                new AdaptiveDecisionEngine().decide(context).recommendations().getFirst());
    }

    @Test
    void b6R06APrerequisiteWithoutAPublishedLessonIsNeverATarget() {
        // L1 is hidden and therefore absent from the snapshot; S1 has no published Lesson.
        var context = new DecisionContext(COURSE, LEARNER,
                List.of(new LessonInput(L2, false, Set.of(S2)), new LessonInput(L3, false, Set.of(S3))),
                Map.of(S1, MasteryState.UNKNOWN, S2, MasteryState.WEAK, S3, MasteryState.UNKNOWN),
                List.of(new PrerequisiteInput(S2, S1), new PrerequisiteInput(S3, S2)), NAMES);

        var decision = new AdaptiveDecisionEngine().decide(context);

        assertEquals(List.of(new Recommendation(RecommendationType.CONTINUE, L2,
                "Continue with the next lesson in the course order.")), decision.recommendations());
        assertTrue(decision.recommendations().stream().noneMatch(draft -> draft.targetLessonId().equals(L1)));
    }

    @Test
    void anIncompleteLessonOfThePrerequisiteIsPreferredOverAnEarlierCompletedOne() {
        UUID secondVariablesLesson = new UUID(0, 0x804);
        var context = new DecisionContext(COURSE, LEARNER,
                List.of(new LessonInput(L1, true, Set.of(S1)), new LessonInput(L2, false, Set.of(S2)),
                        new LessonInput(secondVariablesLesson, false, Set.of(S1))),
                Map.of(S1, MasteryState.WEAK, S2, MasteryState.WEAK, S3, MasteryState.UNKNOWN),
                List.of(new PrerequisiteInput(S2, S1)), NAMES);

        assertEquals(secondVariablesLesson,
                new AdaptiveDecisionEngine().decide(context).recommendations().getFirst().targetLessonId());
    }

    @Test
    void unknownNeverTriggersRemedialAndOnlyDirectPrerequisitesAreCandidates() {
        // S3 is Weak; its direct prerequisite S2 is Mastered, the indirect S1 is Unknown -> no remedial.
        var context = new DecisionContext(COURSE, LEARNER, standardLessons(false, false, false),
                Map.of(S1, MasteryState.UNKNOWN, S2, MasteryState.MASTERED, S3, MasteryState.WEAK),
                List.of(new PrerequisiteInput(S2, S1), new PrerequisiteInput(S3, S2)), NAMES);

        var decision = new AdaptiveDecisionEngine().decide(context);

        assertEquals(List.of(L2), decision.testOutLessonIds());
        assertEquals(new Recommendation(RecommendationType.CONTINUE, L1, "Continue with the next lesson in the course order."),
                decision.recommendations().getLast());
    }

    @Test
    void b6R14ACompletedReviewStaysSelectedUntilTheWeakSkillChanges() {
        var edges = List.of(new PrerequisiteInput(S2, S1));
        var weak = Map.of(S1, MasteryState.UNKNOWN, S2, MasteryState.WEAK);
        var remedial = new Recommendation(RecommendationType.REMEDIAL, L1,
                "Review this lesson first: your skill \"Loops\" is Weak and its prerequisite \"Variables\" is not yet Mastered.");
        var engine = new AdaptiveDecisionEngine();

        assertEquals(List.of(remedial), engine.decide(new DecisionContext(COURSE, LEARNER,
                twoLessons(true, false), weak, edges, NAMES)).recommendations());
        assertEquals(List.of(remedial), engine.decide(new DecisionContext(COURSE, LEARNER,
                twoLessons(true, true), weak, edges, NAMES)).recommendations());
        assertTrue(engine.decide(new DecisionContext(COURSE, LEARNER, twoLessons(true, true),
                Map.of(S1, MasteryState.UNKNOWN, S2, MasteryState.LEARNING), edges, NAMES)).recommendations().isEmpty());
    }

    private static List<LessonInput> standardLessons(boolean c1, boolean c2, boolean c3) {
        return List.of(new LessonInput(L1, c1, Set.of(S1)), new LessonInput(L2, c2, Set.of(S2)),
                new LessonInput(L3, c3, Set.of(S3)));
    }

    private static List<LessonInput> twoLessons(boolean c1, boolean c2) {
        return List.of(new LessonInput(L1, c1, Set.of(S1)), new LessonInput(L2, c2, Set.of(S2)));
    }
}
