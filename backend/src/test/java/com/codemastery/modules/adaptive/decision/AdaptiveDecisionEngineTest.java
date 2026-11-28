package com.codemastery.modules.adaptive.decision;

import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.DecisionContext;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.LessonInput;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.PrerequisiteInput;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionService;
import com.codemastery.modules.adaptive.application.event.RecommendationCreatedEvent;
import com.codemastery.modules.adaptive.application.event.RecommendationCreatedEvent.CreatedRecommendation;
import com.codemastery.modules.adaptive.application.recommendation.RecommendationReasonBuilder;
import com.codemastery.modules.adaptive.application.recommendation.RecommendationService;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision.Recommendation;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;
import com.codemastery.modules.adaptive.application.mastery.MasteryQueryService;
import com.codemastery.modules.adaptive.domain.repository.PathRecommendationRepository;
import com.codemastery.modules.adaptive.domain.repository.SkillRepository;
import com.codemastery.modules.assessment.application.service.QuizService;
import com.codemastery.modules.course.application.service.LessonProgressService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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

    // AD11: ADR011 §§6-7, 9-10 decision oracles (L1-L3 map S1-S3; S2 -> S1 and S3 -> S2).

    private static final UUID S1 = new UUID(0, 0x51), S2 = new UUID(0, 0x52), S3 = new UUID(0, 0x53);
    private static final UUID OL1 = new UUID(0, 0x201), OL2 = new UUID(0, 0x202), OL3 = new UUID(0, 0x203);
    private static final Map<UUID, String> ORACLE_NAMES = Map.of(S1, "Variables", S2, "Loops", S3, "Arrays");
    private static final List<PrerequisiteInput> ORACLE_EDGES =
            List.of(new PrerequisiteInput(S2, S1), new PrerequisiteInput(S3, S2));

    private static DecisionContext oracle(Map<UUID, MasteryState> mastery, boolean... completed) {
        boolean[] done = completed.length == 0 ? new boolean[3] : completed;
        return new DecisionContext(COURSE, LEARNER,
                List.of(new LessonInput(OL1, done[0], Set.of(S1)), new LessonInput(OL2, done[1], Set.of(S2)),
                        new LessonInput(OL3, done[2], Set.of(S3))),
                mastery, ORACLE_EDGES, ORACLE_NAMES);
    }

    private static Map<UUID, MasteryState> states(MasteryState s1, MasteryState s2, MasteryState s3) {
        return Map.of(S1, s1, S2, s2, S3, s3);
    }

    @Test
    void b6R01TwoOrderedTestOutsAreFollowedByContinue() {
        var decision = new AdaptiveDecisionEngine().decide(oracle(states(
                MasteryState.MASTERED, MasteryState.MASTERED, MasteryState.UNKNOWN)));

        assertEquals(List.of(OL1, OL2), decision.testOutLessonIds());
        assertEquals(List.of(
                new Recommendation(RecommendationType.SKIP, OL1,
                        "Tested out: you have mastered every skill in this lesson (\"Variables\")."),
                new Recommendation(RecommendationType.SKIP, OL2,
                        "Tested out: you have mastered every skill in this lesson (\"Loops\")."),
                new Recommendation(RecommendationType.CONTINUE, OL3, "Continue with the next lesson in the course order.")),
                decision.recommendations());
    }

    @Test
    void b6R02AWeakSkillWithAnUnknownPrerequisiteGetsRemedialFirst() {
        var decision = new AdaptiveDecisionEngine().decide(oracle(states(
                MasteryState.UNKNOWN, MasteryState.WEAK, MasteryState.UNKNOWN)));

        assertTrue(decision.testOutLessonIds().isEmpty());
        assertEquals(List.of(new Recommendation(RecommendationType.REMEDIAL, OL1,
                        "Review this lesson first: your skill \"Loops\" is Weak and its prerequisite \"Variables\" is not yet Mastered.")),
                decision.recommendations());
    }

    @Test
    void b6R03AMasteredPrerequisiteLeavesNoCandidateSoTheDecisionFallsBackToContinue() {
        var decision = new AdaptiveDecisionEngine().decide(oracle(states(
                MasteryState.MASTERED, MasteryState.WEAK, MasteryState.UNKNOWN)));

        assertEquals(List.of(OL1), decision.testOutLessonIds());
        assertEquals(RecommendationType.SKIP, decision.recommendations().getFirst().type());
        assertEquals(new Recommendation(RecommendationType.CONTINUE, OL2, "Continue with the next lesson in the course order."),
                decision.recommendations().getLast());
        assertEquals(2, decision.recommendations().size());
    }

    @Test
    void b6R07AnUnmappedLessonIsNeverTestedOutButCanBeTheContinueTarget() {
        UUID unmapped = new UUID(0, 0x2FF);
        var context = new DecisionContext(COURSE, LEARNER,
                List.of(new LessonInput(OL1, false, Set.of(S1)), new LessonInput(unmapped, false, Set.of()),
                        new LessonInput(OL2, false, Set.of(S2))),
                states(MasteryState.MASTERED, MasteryState.MASTERED, MasteryState.MASTERED), ORACLE_EDGES, ORACLE_NAMES);

        var decision = new AdaptiveDecisionEngine().decide(context);

        assertEquals(List.of(OL1, OL2), decision.testOutLessonIds());
        assertEquals(new Recommendation(RecommendationType.CONTINUE, unmapped, "Continue with the next lesson in the course order."),
                decision.recommendations().getLast());
    }

    @Test
    void b6R08AllLessonsCompletedWithoutAWeakCandidateYieldNoGuidanceDraft() {
        var engine = new AdaptiveDecisionEngine();

        var noWeak = engine.decide(oracle(states(
                MasteryState.LEARNING, MasteryState.UNKNOWN, MasteryState.LEARNING), true, true, true));
        assertTrue(noWeak.testOutLessonIds().isEmpty());
        assertTrue(noWeak.recommendations().isEmpty());

        // Completion alone never suppresses remedial: a Weak S3 with unmastered S2 still selects review of L2.
        assertEquals(List.of(new Recommendation(RecommendationType.REMEDIAL, OL2,
                        "Review this lesson first: your skill \"Arrays\" is Weak and its prerequisite \"Loops\" is not yet Mastered.")),
                engine.decide(oracle(states(MasteryState.LEARNING, MasteryState.UNKNOWN, MasteryState.WEAK),
                        true, true, true)).recommendations());
    }

    @Test
    void contrastingProfilesOnTheSameCourseReceiveDifferentNextRecommendations() {
        var engine = new AdaptiveDecisionEngine();
        // Strong foundation: Variables and Loops mastered. Loops gap: Variables learning, Loops weak.
        var strong = engine.decide(oracle(states(MasteryState.MASTERED, MasteryState.MASTERED, MasteryState.UNKNOWN)));
        var loopsGap = engine.decide(oracle(states(MasteryState.LEARNING, MasteryState.WEAK, MasteryState.UNKNOWN)));

        assertEquals(new Recommendation(RecommendationType.CONTINUE, OL3, "Continue with the next lesson in the course order."),
                strong.recommendations().getLast());
        assertEquals(new Recommendation(RecommendationType.REMEDIAL, OL1,
                        "Review this lesson first: your skill \"Loops\" is Weak and its prerequisite \"Variables\" is not yet Mastered."),
                loopsGap.recommendations().getLast());
        assertNotEquals(strong.recommendations().getLast(), loopsGap.recommendations().getLast());
    }

    @Test
    void decisionsAreReproducibleImmutableAndIndependentOfTheCallersCollections() {
        Map<UUID, MasteryState> mastery = new HashMap<>(states(MasteryState.MASTERED, MasteryState.WEAK, MasteryState.UNKNOWN));
        Set<UUID> skills = new java.util.HashSet<>(Set.of(S1));
        List<LessonInput> lessons = new ArrayList<>(List.of(new LessonInput(OL1, false, skills),
                new LessonInput(OL2, false, Set.of(S2)), new LessonInput(OL3, false, Set.of(S3))));
        var context = new DecisionContext(COURSE, LEARNER, lessons, mastery, ORACLE_EDGES, ORACLE_NAMES);
        var engine = new AdaptiveDecisionEngine();
        var first = engine.decide(context);

        mastery.put(S1, MasteryState.WEAK);
        skills.add(S2);
        lessons.clear();

        assertEquals(first, engine.decide(context));
        assertEquals(first, new AdaptiveDecisionEngine().decide(oracle(states(
                MasteryState.MASTERED, MasteryState.WEAK, MasteryState.UNKNOWN))));
        assertThrows(UnsupportedOperationException.class, () -> first.testOutLessonIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.recommendations().clear());
    }

    @Test
    void invalidSnapshotsAreRejected() {
        var engine = new AdaptiveDecisionEngine();
        var names = Map.of(S1, "Variables", S2, "Loops");
        var mastery = Map.of(S1, MasteryState.UNKNOWN, S2, MasteryState.UNKNOWN);

        assertThrows(IllegalArgumentException.class, () -> engine.decide(null));
        assertThrows(IllegalArgumentException.class, () -> new DecisionContext(COURSE, LEARNER,
                List.of(new LessonInput(OL1, false, Set.of(S1)), new LessonInput(OL1, false, Set.of(S2))),
                mastery, List.of(), names));
        assertThrows(IllegalArgumentException.class, () -> new DecisionContext(COURSE, LEARNER,
                List.of(new LessonInput(OL1, false, Set.of(S3))), mastery, List.of(), names));
        assertThrows(IllegalArgumentException.class, () -> new DecisionContext(COURSE, LEARNER,
                List.of(), mastery, List.of(new PrerequisiteInput(S1, S3)), names));
        assertThrows(IllegalArgumentException.class, () -> new DecisionContext(COURSE, LEARNER,
                List.of(), mastery, List.of(new PrerequisiteInput(S1, S2), new PrerequisiteInput(S2, S1)), names));
        assertThrows(IllegalArgumentException.class, () -> new DecisionContext(COURSE, LEARNER,
                List.of(), mastery, List.of(new PrerequisiteInput(S1, S1)), names));
        assertThrows(IllegalArgumentException.class, () -> new DecisionContext(COURSE, LEARNER,
                List.of(), mastery, List.of(new PrerequisiteInput(S2, S1), new PrerequisiteInput(S2, S1)), names));
        assertThrows(IllegalArgumentException.class, () -> new DecisionContext(COURSE, LEARNER,
                java.util.Arrays.asList(new LessonInput(OL1, false, Set.of()), null), mastery, List.of(), names));
        assertThrows(IllegalArgumentException.class, () -> new DecisionContext(null, LEARNER,
                List.of(), mastery, List.of(), names));
        assertThrows(IllegalArgumentException.class, () -> new LessonInput(OL1, false, null));
    }

    @Test
    void prerequisiteWarningsNameUnmasteredDirectPrerequisitesOutsideTheLessonInNameOrderWithoutBlocking() {
        UUID alphaLow = new UUID(0, 0x61), alphaHigh = new UUID(0, 0x62), zeta = new UUID(0, 0x63);
        UUID upper = new UUID(0, 0x64), target = new UUID(0, 0x65), mappedPrerequisite = new UUID(0, 0x66);
        UUID mastered = new UUID(0, 0x67), lesson = new UUID(0, 0x300), other = new UUID(0, 0x301);
        var names = Map.of(alphaLow, "alpha", alphaHigh, "alpha", zeta, "Zeta", upper, "Alpha", target, "Target",
                mappedPrerequisite, "Mapped", mastered, "Done");
        var mastery = new HashMap<UUID, MasteryState>();
        names.keySet().forEach(id -> mastery.put(id, MasteryState.UNKNOWN));
        mastery.put(zeta, MasteryState.WEAK);
        mastery.put(upper, MasteryState.LEARNING);
        mastery.put(mastered, MasteryState.MASTERED);
        var edges = List.of(new PrerequisiteInput(target, alphaHigh), new PrerequisiteInput(target, zeta),
                new PrerequisiteInput(target, alphaLow), new PrerequisiteInput(target, upper),
                new PrerequisiteInput(target, mappedPrerequisite), new PrerequisiteInput(target, mastered),
                new PrerequisiteInput(mappedPrerequisite, alphaLow));
        var context = new DecisionContext(COURSE, LEARNER,
                List.of(new LessonInput(lesson, false, Set.of(target, mappedPrerequisite)), new LessonInput(other, false, Set.of(zeta))),
                mastery, edges, names);
        var engine = new AdaptiveDecisionEngine();

        java.util.Locale original = java.util.Locale.getDefault();
        try {
            for (java.util.Locale locale : List.of(java.util.Locale.ROOT, java.util.Locale.forLanguageTag("tr-TR"),
                    java.util.Locale.GERMANY)) {
                java.util.Locale.setDefault(locale);
                assertEquals(List.of(upper, zeta, alphaLow, alphaHigh), engine.prerequisiteWarnings(context, lesson), locale.toString());
            }
        } finally {
            java.util.Locale.setDefault(original);
        }
        assertEquals(List.of(), engine.prerequisiteWarnings(context, other));
        assertThrows(IllegalArgumentException.class, () -> engine.prerequisiteWarnings(context, new UUID(0, 0x3FF)));
        assertThrows(UnsupportedOperationException.class, () -> engine.prerequisiteWarnings(context, lesson).clear());
        // Guidance only: the warned Lesson stays the continue target although its prerequisites are unmastered.
        assertEquals(List.of(new Recommendation(RecommendationType.CONTINUE, lesson,
                        "Continue with the next lesson in the course order.")), engine.decide(context).recommendations());
    }

    @Test
    void b6R12NameOrderIsIdenticalUnderAnyDefaultLocaleAndBreaksEqualNamesByUuid() {
        UUID smallAlpha = new UUID(0, 5), largeAlpha = new UUID(0, 6);
        var names = Map.of(new UUID(0, 1), "beta", new UUID(0, 2), "Alpha", new UUID(0, 3), "Zeta",
                new UUID(0, 4), "\u00C4rger", largeAlpha, "alpha", smallAlpha, "alpha");
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            for (java.util.Locale locale : List.of(java.util.Locale.ROOT, java.util.Locale.forLanguageTag("tr-TR"),
                    java.util.Locale.forLanguageTag("sv-SE"))) {
                java.util.Locale.setDefault(locale);
                assertEquals(List.of(new UUID(0, 2), new UUID(0, 3), smallAlpha, largeAlpha, new UUID(0, 1), new UUID(0, 4)),
                        names.keySet().stream().sorted(RecommendationReasonBuilder.nameOrder(names)).toList(), locale.toString());
            }
        } finally {
            java.util.Locale.setDefault(original);
        }
        String reason = RecommendationReasonBuilder.skip(names.keySet(), names);
        assertEquals("Tested out: you have mastered every skill in this lesson "
                + "(\"Alpha\", \"Zeta\", \"alpha\", \"alpha\", \"beta\", \"\u00C4rger\").", reason);
        assertFalse(reason.contains(smallAlpha.toString()));
    }

    // AD14 history and CT29 producer rules of AdaptiveDecisionService (ADR011 §§7, 9, B6-R09;
    // ADR012 §9.4). Real PostgreSQL commit, rollback and after-commit delivery are covered in
    // AdaptiveLearningFlowIntegrationTest.

    private static final UUID COURSE = new UUID(0, 0xC0);
    private static final UUID LEARNER = new UUID(0, 0xA1);
    private static final UUID ENROLLMENT = new UUID(0, 0xE1);
    private static final UUID OTHER_ENROLLMENT = new UUID(0, 0xE2);
    private static final UUID L1 = new UUID(0, 0x101);
    private static final UUID L2 = new UUID(0, 0x102);
    private static final UUID L3 = new UUID(0, 0x103);
    private static final Instant LATEST = Instant.parse("2026-11-24T10:00:00.000001Z");
    private static final DecisionContext CONTEXT =
            new DecisionContext(COURSE, LEARNER, List.of(), Map.of(), List.of(), Map.of());

    private static final String CONTINUE_REASON = RecommendationReasonBuilder.continueInCourseOrder();
    private static final String SKIP_REASON =
            "Tested out: you have mastered every skill in this lesson (\"Loops\").";
    private static final String REMEDIAL_REASON = RecommendationReasonBuilder.remedial("Loops", "Basics");

    private static PathRecommendationRepository appendOnly(java.util.function.Consumer<PathRecommendation> append) {
        return new PathRecommendationRepository() {
            public void append(PathRecommendation row) { append.accept(row); }
            public Optional<PathRecommendation> findCurrentGuidance(UUID enrollmentId) {
                throw new AssertionError("Write orchestration must not use the reporting read");
            }
        };
    }

    private final List<PathRecommendation> appended = new ArrayList<>();
    private final List<Object> published = new ArrayList<>();
    private final PathRecommendationRepository repository = appendOnly(appended::add);
    private final ApplicationEventPublisher publisher = published::add;

    @Test
    void aFirstRunAppendsEveryDraftWithOneTruncatedInstantAndPublishesExactlyThoseRows() {
        Instant sampled = Instant.parse("2026-11-24T10:00:00.123456789Z");
        var decision = decision(skip(L1), skip(L2), new Recommendation(RecommendationType.CONTINUE, L3, CONTINUE_REASON));

        service(Clock.fixed(sampled, ZoneOffset.UTC))
                .recordAndPublish(CONTEXT, ENROLLMENT, decision, Optional.empty(), Optional.empty());

        Instant runInstant = Instant.parse("2026-11-24T10:00:00.123456Z");
        assertEquals(3, appended.size());
        assertEquals(List.of(L1, L2, L3), appended.stream().map(PathRecommendation::lessonId).toList());
        assertEquals(List.of(RecommendationType.SKIP, RecommendationType.SKIP, RecommendationType.CONTINUE),
                appended.stream().map(PathRecommendation::recommendationType).toList());
        assertEquals(List.of(SKIP_REASON, SKIP_REASON, CONTINUE_REASON),
                appended.stream().map(PathRecommendation::reason).toList());
        assertTrue(appended.stream().allMatch(row -> row.enrollmentId().equals(ENROLLMENT)
                && row.recommendedAt().equals(runInstant)));
        assertEquals(3, appended.stream().map(PathRecommendation::id).distinct().count());

        var event = onlyEvent();
        assertEquals(COURSE, event.courseId());
        assertEquals(LEARNER, event.learnerId());
        assertEquals(ENROLLMENT, event.enrollmentId());
        assertEquals(runInstant, event.recommendedAt());
        assertEquals(appended.stream().map(row -> new CreatedRecommendation(
                        row.id(), row.recommendationType().getCode(), row.lessonId(), row.reason())).toList(),
                event.recommendations());
        assertEquals(List.of("skip", "skip", "continue"),
                event.recommendations().stream().map(CreatedRecommendation::type).toList());
    }

    @Test
    void anUnchangedCurrentGuidanceInsertsNothingPublishesNothingAndNeverSamplesTheClock() {
        var current = row(RecommendationType.CONTINUE, L3, CONTINUE_REASON, LATEST);

        service(new FailingClock()).recordAndPublish(CONTEXT, ENROLLMENT,
                decision(new Recommendation(RecommendationType.CONTINUE, L3, CONTINUE_REASON)),
                Optional.of(current), Optional.of(LATEST));

        assertTrue(appended.isEmpty());
        assertTrue(published.isEmpty());
    }

    @Test
    void aDecisionWithoutDraftsInsertsNothingAndPublishesNothing() {
        service(new FailingClock()).recordAndPublish(CONTEXT, ENROLLMENT, decision(),
                Optional.of(row(RecommendationType.REMEDIAL, L1, REMEDIAL_REASON, LATEST)), Optional.of(LATEST));

        assertTrue(appended.isEmpty());
        assertTrue(published.isEmpty());
    }

    @Test
    void aChangeOfTypeTargetOrReasonAppendsOneNewGuidanceRow() {
        var current = row(RecommendationType.CONTINUE, L2, CONTINUE_REASON, LATEST);
        List<Recommendation> changed = List.of(
                new Recommendation(RecommendationType.REMEDIAL, L2, CONTINUE_REASON),
                new Recommendation(RecommendationType.CONTINUE, L3, CONTINUE_REASON),
                new Recommendation(RecommendationType.CONTINUE, L2, CONTINUE_REASON + " "));

        for (Recommendation draft : changed) {
            appended.clear();
            published.clear();
            service(Clock.fixed(LATEST.plusSeconds(1), ZoneOffset.UTC)).recordAndPublish(
                    CONTEXT, ENROLLMENT, decision(draft), Optional.of(current), Optional.of(LATEST));

            assertEquals(1, appended.size(), draft.toString());
            assertEquals(draft.type(), appended.getFirst().recommendationType());
            assertEquals(draft.targetLessonId(), appended.getFirst().lessonId());
            assertEquals(draft.reason(), appended.getFirst().reason());
            assertEquals(1, onlyEvent().recommendations().size());
        }
    }

    @Test
    void newSkipRowsAreAlwaysAppendedWhileAnUnchangedGuidanceDraftIsNot() {
        var current = row(RecommendationType.CONTINUE, L3, CONTINUE_REASON, LATEST);

        service(Clock.fixed(LATEST.plusMillis(1), ZoneOffset.UTC)).recordAndPublish(CONTEXT, ENROLLMENT,
                decision(skip(L1), new Recommendation(RecommendationType.CONTINUE, L3, CONTINUE_REASON)),
                Optional.of(current), Optional.of(LATEST));

        assertEquals(List.of(L1), appended.stream().map(PathRecommendation::lessonId).toList());
        assertEquals(List.of("skip"), onlyEvent().recommendations().stream().map(CreatedRecommendation::type).toList());
    }

    @Test
    void anEqualEarlierOrSubMicrosecondLaterInstantFailsBeforeAnyInsertOrEvent() {
        List<Instant> nonAdvancing = List.of(LATEST, LATEST.minusSeconds(5), LATEST.plusNanos(999));
        for (Instant sampled : nonAdvancing) {
            var failure = assertThrows(IllegalStateException.class, () ->
                    service(Clock.fixed(sampled, ZoneOffset.UTC)).recordAndPublish(CONTEXT, ENROLLMENT,
                            decision(skip(L1)), Optional.empty(), Optional.of(LATEST)));

            assertEquals("Recommendation clock must advance", failure.getMessage());
            assertTrue(appended.isEmpty(), sampled.toString());
            assertTrue(published.isEmpty(), sampled.toString());
        }
    }

    @Test
    void oneMicrosecondAfterTheLatestRowIsStrictlyLaterAndIsUsedForEveryRowOfTheRun() {
        Instant next = LATEST.plusNanos(1_000);

        service(Clock.fixed(next, ZoneOffset.UTC)).recordAndPublish(CONTEXT, ENROLLMENT,
                decision(skip(L1), new Recommendation(RecommendationType.REMEDIAL, L2, REMEDIAL_REASON)),
                Optional.of(row(RecommendationType.CONTINUE, L2, CONTINUE_REASON, LATEST)), Optional.of(LATEST));

        assertTrue(appended.stream().allMatch(row -> row.recommendedAt().equals(next)));
        assertEquals(next, onlyEvent().recommendedAt());
    }

    @Test
    void inconsistentHistoryInputsFailBeforeAnyEffect() {
        var foreign = row(RecommendationType.CONTINUE, L3, CONTINUE_REASON, LATEST, OTHER_ENROLLMENT);
        var skipAsGuidance = row(RecommendationType.SKIP, L3, SKIP_REASON, LATEST);
        var current = row(RecommendationType.CONTINUE, L3, CONTINUE_REASON, LATEST);
        var service = service(new FailingClock());
        var decision = decision(skip(L1));

        assertThrows(IllegalArgumentException.class, () -> service.recordAndPublish(
                CONTEXT, ENROLLMENT, decision, Optional.of(foreign), Optional.of(LATEST)));
        assertThrows(IllegalArgumentException.class, () -> service.recordAndPublish(
                CONTEXT, ENROLLMENT, decision, Optional.of(skipAsGuidance), Optional.of(LATEST)));
        assertThrows(IllegalArgumentException.class, () -> service.recordAndPublish(
                CONTEXT, ENROLLMENT, decision, Optional.of(current), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> service.recordAndPublish(
                CONTEXT, ENROLLMENT, decision, Optional.of(current), Optional.of(LATEST.minusNanos(1_000))));
        assertThrows(NullPointerException.class, () -> service.recordAndPublish(
                CONTEXT, null, decision, Optional.empty(), Optional.empty()));

        assertTrue(appended.isEmpty());
        assertTrue(published.isEmpty());
    }

    @Test
    void aPersistenceFailurePropagatesAndPublishesNothing() {
        var failure = new IllegalStateException("insert failed");
        var failingService = service(appendOnly(row -> {
            if (row.lessonId().equals(L2)) throw failure;
            appended.add(row);
        }), Clock.fixed(LATEST, ZoneOffset.UTC));

        var thrown = assertThrows(IllegalStateException.class, () -> failingService.recordAndPublish(
                CONTEXT, ENROLLMENT, decision(skip(L1), skip(L2)), Optional.empty(), Optional.empty()));

        assertEquals(failure, thrown);
        assertTrue(published.isEmpty());
    }

    @Test
    void aDraftWithoutAReasonIsRejectedBeforeAnyRowOfTheRunIsInserted() {
        assertThrows(IllegalArgumentException.class, () -> service(Clock.fixed(LATEST, ZoneOffset.UTC))
                .recordAndPublish(CONTEXT, ENROLLMENT,
                        decision(skip(L1), new Recommendation(RecommendationType.CONTINUE, L2, " ")),
                        Optional.empty(), Optional.empty()));

        assertTrue(appended.isEmpty());
        assertTrue(published.isEmpty());
    }

    @Test
    void theEventIsImmutableAndNeverDescribesARunWithoutInsertedRows() {
        var rows = new ArrayList<>(List.of(new CreatedRecommendation(new UUID(0, 1), "skip", L1, SKIP_REASON)));
        var event = new RecommendationCreatedEvent(COURSE, LEARNER, ENROLLMENT, LATEST, rows);
        rows.clear();

        assertEquals(1, event.recommendations().size());
        assertThrows(UnsupportedOperationException.class, () -> event.recommendations().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new RecommendationCreatedEvent(COURSE, LEARNER, ENROLLMENT, LATEST, List.of()));
        assertThrows(NullPointerException.class,
                () -> new RecommendationCreatedEvent(COURSE, LEARNER, null, LATEST, List.copyOf(event.recommendations())));
    }

    @Test
    void aDecisionCannotCarryTwoGuidanceDraftsOrSkipsThatDisagreeWithItsTestOuts() {
        var cont = new Recommendation(RecommendationType.CONTINUE, L2, CONTINUE_REASON);
        var remedial = new Recommendation(RecommendationType.REMEDIAL, L1, REMEDIAL_REASON);

        assertThrows(IllegalArgumentException.class, () -> new AdaptiveDecision(List.of(), List.of(cont, remedial)));
        assertThrows(IllegalArgumentException.class, () -> new AdaptiveDecision(List.of(L1), List.of(cont, skip(L1))));
        assertThrows(IllegalArgumentException.class, () -> new AdaptiveDecision(List.of(L2), List.of(skip(L1), cont)));
        assertThrows(IllegalArgumentException.class, () -> new AdaptiveDecision(List.of(L1, L2), List.of(skip(L2), skip(L1))));
        assertThrows(IllegalArgumentException.class, () -> new AdaptiveDecision(List.of(L1), List.of(cont)));
        assertEquals(2, new AdaptiveDecision(List.of(L1), List.of(skip(L1), remedial)).recommendations().size());
        assertTrue(new AdaptiveDecision(List.of(), List.of()).recommendations().isEmpty());
    }

    private AdaptiveDecisionService service(Clock clock) {
        return service(repository, clock);
    }

    private AdaptiveDecisionService service(PathRecommendationRepository history, Clock clock) {
        // Exercise the service-local clock seam (ADR011 §9, ADR008 §6) without a public test API.
        try {
            return BeanUtils.instantiateClass(AdaptiveDecisionService.class.getDeclaredConstructor(
                            RecommendationService.class, ApplicationEventPublisher.class, Clock.class),
                    new RecommendationService(history, mock(LessonProgressService.class), mock(SkillRepository.class),
                            mock(MasteryQueryService.class), mock(QuizService.class)), publisher, clock);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError(missing);
        }
    }

    private RecommendationCreatedEvent onlyEvent() {
        assertEquals(1, published.size());
        return (RecommendationCreatedEvent) published.getFirst();
    }

    private static AdaptiveDecision decision(Recommendation... drafts) {
        List<UUID> testOuts = new ArrayList<>();
        for (Recommendation draft : drafts) {
            if (draft.type() == RecommendationType.SKIP) testOuts.add(draft.targetLessonId());
        }
        return new AdaptiveDecision(testOuts, List.of(drafts));
    }

    private static Recommendation skip(UUID lesson) {
        return new Recommendation(RecommendationType.SKIP, lesson, SKIP_REASON);
    }

    private static PathRecommendation row(RecommendationType type, UUID lesson, String reason, Instant at) {
        return row(type, lesson, reason, at, ENROLLMENT);
    }

    private static PathRecommendation row(RecommendationType type, UUID lesson, String reason, Instant at,
                                          UUID enrollment) {
        return new PathRecommendation(UUID.randomUUID(), enrollment, lesson, type, reason, at);
    }

    /** Proves that a run inserting nothing never reads the time. */
    private static final class FailingClock extends Clock {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { throw new AssertionError("The clock must not be sampled"); }
    }
}
