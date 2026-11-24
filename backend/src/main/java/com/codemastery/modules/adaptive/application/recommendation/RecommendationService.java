package com.codemastery.modules.adaptive.application.recommendation;

import com.codemastery.modules.adaptive.application.event.RecommendationCreatedEvent.CreatedRecommendation;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision.Recommendation;
import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.repository.PathRecommendationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Appends recommendation history (AD14.history; ADR011 §§7, 9, V005).
 *
 * <p>Each row keeps the draft's type, target Lesson and reason verbatim and the run's instant.
 * History is append-only: rows are inserted through {@link PathRecommendationRepository#append},
 * never updated or deleted. Whether a draft needs a row at all and the strictly-later instant are
 * decided by {@code AdaptiveDecisionService} before this call.
 */
@Service
public class RecommendationService {

    private final PathRecommendationRepository history;

    public RecommendationService(PathRecommendationRepository history) {
        this.history = Objects.requireNonNull(history);
    }

    /**
     * Appends the drafts selected by AdaptiveDecisionService in the current
     * submission transaction. Each returned entry describes one successful insert.
     *
     * <p>Every draft is checked before the first insert, so a draft without a type, target or
     * non-blank reason fails without writing any row of the run. Any persistence failure
     * propagates and the caller's transaction rolls back every row of the run.
     *
     * @param enrollmentId   enrollment whose history receives the new rows
     * @param recommendedAt  the caller's validated, microsecond-precision run
     *                       instant
     * @param selectedDrafts drafts that passed the CT07 append rule, in decision
     *                       order
     * @return the inserted rows, in insertion order
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CreatedRecommendation> appendSelected(
            UUID enrollmentId,
            Instant recommendedAt,
            List<Recommendation> selectedDrafts) {

        Objects.requireNonNull(enrollmentId, "enrollmentId");
        Objects.requireNonNull(recommendedAt, "recommendedAt");
        List<Recommendation> drafts = List.copyOf(selectedDrafts);
        for (Recommendation draft : drafts) {
            requireComplete(draft);
        }

        List<CreatedRecommendation> inserted = new ArrayList<>(drafts.size());

        for (Recommendation draft : drafts) {
            PathRecommendation row = new PathRecommendation(
                    UUID.randomUUID(),
                    enrollmentId,
                    draft.targetLessonId(),
                    draft.type(),
                    draft.reason(),
                    recommendedAt);

            history.append(row);

            inserted.add(new CreatedRecommendation(
                    row.id(),
                    row.recommendationType().getCode(),
                    row.lessonId(),
                    row.reason()));
        }

        return List.copyOf(inserted);
    }

    private static void requireComplete(Recommendation draft) {
        if (draft.type() == null || draft.targetLessonId() == null
                || draft.reason() == null || draft.reason().isBlank()) {
            throw new IllegalArgumentException(
                    "A recommendation needs a type, a target Lesson and a reason");
        }
    }
}
