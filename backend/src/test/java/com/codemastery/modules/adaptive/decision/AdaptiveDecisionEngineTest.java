package com.codemastery.modules.adaptive.decision;

import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.DecisionContext;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.LessonInput;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision.Recommendation;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptiveDecisionEngineTest {

    @Test
    void skipsMasteredLessonThenContinuesToFirstIncompleteLesson() {
        UUID masteredLesson = new UUID(0, 1);
        UUID nextLesson = new UUID(0, 2);
        UUID loops = new UUID(0, 3);
        UUID arrays = new UUID(0, 4);

        var context = new DecisionContext(
                new UUID(0, 5), new UUID(0, 6),
                List.of(new LessonInput(masteredLesson, false, Set.of(loops, arrays)),
                        new LessonInput(nextLesson, false, Set.of())),
                Map.of(loops, MasteryState.MASTERED, arrays, MasteryState.MASTERED),
                List.of(),
                Map.of(loops, "Loops", arrays, "Arrays"));

        var result = new AdaptiveDecisionEngine().decide(context);

        assertEquals(List.of(masteredLesson), result.testOutLessonIds());
        assertEquals(List.of(
                new Recommendation(RecommendationType.SKIP, masteredLesson,
                        "Tested out: you have mastered every skill in this lesson (\"Arrays\", \"Loops\")."),
                new Recommendation(RecommendationType.CONTINUE, nextLesson,
                        "Continue with the next lesson in the course order.")),
                result.recommendations());
    }

    @Test
    void skipReasonPreservesCaseDuplicateNamesAndDistinctUnicodeForms() {
        UUID lesson = new UUID(0, 100);
        var names = Map.of(
                new UUID(0, 1), "beta",
                new UUID(0, 2), "Alpha",
                new UUID(0, 3), "Zeta",
                new UUID(0, 4), "\u00C4rger",
                new UUID(0, 5), "alpha",
                new UUID(0, 6), "alpha",
                new UUID(0, 7), "\u00E9",
                new UUID(0, 8), "e\u0301");

        Map<UUID, MasteryState> mastery = new HashMap<>();
        names.keySet().forEach(id -> mastery.put(id, MasteryState.MASTERED));

        var context = new DecisionContext(
                new UUID(0, 201), new UUID(0, 202),
                List.of(new LessonInput(lesson, false, names.keySet())),
                mastery, List.of(), names);

        var result = new AdaptiveDecisionEngine().decide(context);

        String expectedReason = "Tested out: you have mastered every skill in this lesson "
                + "(\"Alpha\", \"Zeta\", \"alpha\", \"alpha\", \"beta\", "
                + "\"e\u0301\", \"\u00C4rger\", \"\u00E9\").";

        assertEquals(List.of(lesson), result.testOutLessonIds());
        assertEquals(List.of(new Recommendation(
                RecommendationType.SKIP, lesson, expectedReason)), result.recommendations());
    }
}