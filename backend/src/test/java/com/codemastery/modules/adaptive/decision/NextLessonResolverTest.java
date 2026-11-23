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
}