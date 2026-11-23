package com.codemastery.modules.adaptive.application.decision;

import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision.Recommendation;
import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.DecisionContext;
import com.codemastery.modules.adaptive.application.event.RecommendationCreatedEvent;
import com.codemastery.modules.adaptive.application.event.RecommendationCreatedEvent.CreatedRecommendation;
import com.codemastery.modules.adaptive.application.recommendation.RecommendationService;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Service
public class AdaptiveDecisionService {

    private final RecommendationService recommendationService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public AdaptiveDecisionService(
            RecommendationService recommendationService,
            ApplicationEventPublisher events) {
        this(recommendationService, events, Clock.systemUTC());
    }

    AdaptiveDecisionService(
            RecommendationService recommendationService,
            ApplicationEventPublisher events,
            Clock clock) {
        this.recommendationService = Objects.requireNonNull(recommendationService);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Determines whether a draft warrants a new history row under CT07.
     *
     * @param draft           a recommendation produced by the current decision run
     * @param currentGuidance the enrollment's latest continue or remedial
     *                        recommendation, if any
     * @return true for a new skip draft or when the guidance differs in type,
     *         target, or reason
     */
    static boolean shouldAppend(
            Recommendation draft, Optional<PathRecommendation> currentGuidance) {
        return draft.type() == RecommendationType.SKIP
                || currentGuidance.filter(current -> current.recommendationType() == draft.type()
                        && current.lessonId().equals(draft.targetLessonId())
                        && current.reason().equals(draft.reason()))
                        .isEmpty();
    }

    /**
     * Samples one microsecond-precision instant for all history rows in the current
     * run.
     * The caller samples once after acquiring the course lock and deciding to
     * append rows.
     *
     * @param latestHistoryInstant the latest instant in the enrollment's entire
     *                             history, if any
     * @return the instant to use for every row appended in this run
     * @throws IllegalStateException if the sampled instant is equal to or before
     *                               the latest instant
     */
    Instant sampleRecommendationTime(Optional<Instant> latestHistoryInstant) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (latestHistoryInstant.isPresent() && !now.isAfter(latestHistoryInstant.get())) {
            throw new IllegalStateException("Recommendation clock must advance");
        }
        return now;
    }

    /**
     * Records new recommendations and publishes one CT29 event for the inserted
     * rows.
     *
     * <p>
     * The submission caller holds the Course root and has already applied the
     * decision's test-outs. Both history inputs belong to the same enrollment
     * and were read under that lock.
     *
     * @param context              the course and learner snapshot used to produce
     *                             the decision
     * @param enrollmentId         the enrollment from the guarded Course execution
     *                             state
     * @param decision             the result produced by the engine for this run
     * @param currentGuidance      the latest continue or remedial row, if one
     *                             exists
     * @param latestHistoryInstant the latest instant across all rows, including
     *                             skips
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAndPublish(
            DecisionContext context,
            UUID enrollmentId,
            AdaptiveDecision decision,
            Optional<PathRecommendation> currentGuidance,
            Optional<Instant> latestHistoryInstant) {

        List<Recommendation> selectedDrafts = decision.recommendations().stream()
                .filter(draft -> shouldAppend(draft, currentGuidance))
                .toList();

        if (selectedDrafts.isEmpty()) {
            return;
        }

        Instant recommendedAt = sampleRecommendationTime(latestHistoryInstant);

        List<CreatedRecommendation> inserted = recommendationService.appendSelected(
                enrollmentId, recommendedAt, selectedDrafts);

        events.publishEvent(new RecommendationCreatedEvent(
                context.courseId(),
                context.learnerId(),
                enrollmentId,
                recommendedAt,
                inserted));
    }
}