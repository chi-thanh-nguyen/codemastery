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
import java.util.UUID;

@Service
public class RecommendationService {

    private final PathRecommendationRepository history;

    public RecommendationService(PathRecommendationRepository history) {
        this.history = history;
    }

    /**
     * Appends the drafts selected by AdaptiveDecisionService in the current
     * submission transaction. Each returned entry describes one successful insert.
     *
     * @param enrollmentId   enrollment whose history receives the new rows
     * @param recommendedAt  the caller's validated, microsecond-precision run
     *                       instant
     * @param selectedDrafts drafts that passed the CT07 append rule, in decision
     *                       order
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CreatedRecommendation> appendSelected(
            UUID enrollmentId,
            Instant recommendedAt,
            List<Recommendation> selectedDrafts) {

        List<CreatedRecommendation> inserted = new ArrayList<>(selectedDrafts.size());

        for (Recommendation draft : selectedDrafts) {
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
}