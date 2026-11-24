package com.codemastery.modules.adaptive.decision;

import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.DecisionContext;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionService;
import com.codemastery.modules.adaptive.application.event.RecommendationCreatedEvent;
import com.codemastery.modules.adaptive.application.event.RecommendationCreatedEvent.CreatedRecommendation;
import com.codemastery.modules.adaptive.application.recommendation.RecommendationReasonBuilder;
import com.codemastery.modules.adaptive.application.recommendation.RecommendationService;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision.Recommendation;
import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;
import com.codemastery.modules.adaptive.domain.repository.PathRecommendationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveDecisionEngineTest {

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

    private final List<PathRecommendation> appended = new ArrayList<>();
    private final List<Object> published = new ArrayList<>();
    private final PathRecommendationRepository repository = appended::add;
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
        var failingService = service(row -> {
            if (row.lessonId().equals(L2)) throw failure;
            appended.add(row);
        }, Clock.fixed(LATEST, ZoneOffset.UTC));

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

    private AdaptiveDecisionService service(Clock clock) {
        return service(repository, clock);
    }

    private AdaptiveDecisionService service(PathRecommendationRepository history, Clock clock) {
        // Exercise the service-local clock seam (ADR011 §9, ADR008 §6) without a public test API.
        try {
            return BeanUtils.instantiateClass(AdaptiveDecisionService.class.getDeclaredConstructor(
                            RecommendationService.class, ApplicationEventPublisher.class, Clock.class),
                    new RecommendationService(history), publisher, clock);
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
