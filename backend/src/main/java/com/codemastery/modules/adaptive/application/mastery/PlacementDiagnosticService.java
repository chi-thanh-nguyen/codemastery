package com.codemastery.modules.adaptive.application.mastery;

import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.model.Skill;
import com.codemastery.modules.adaptive.domain.repository.LearnerSkillMasteryRepository;
import com.codemastery.modules.adaptive.domain.repository.SkillRepository;
import com.codemastery.modules.assessment.application.service.AttemptService;
import com.codemastery.modules.course.application.service.LessonProgressService;
import com.codemastery.modules.course.application.service.LessonProgressService.LearnerCourseState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Placement diagnostic offer composition ({@code CT25-BINDING-2026-11-12-r1}, ADR011 §11).
 *
 * <p>Evaluates whether the current Learner should be offered a Course's placement diagnostic
 * and resolves the server-selected diagnostic Quiz ID in one read-only {@code REPEATABLE READ}
 * snapshot without writing mastery, progress or recommendation state. Diagnostic submission and
 * initial mastery updates flow through {@code QuizSubmittedEvent} and {@code MasteryUpdateService}
 * (ADR011 §§4, 11); skipping is client navigation with no server write ({@code CT25.skip}).
 */
@Service
public class PlacementDiagnosticService {

    private final LessonProgressService learnerStates;
    private final SkillRepository skills;
    private final LearnerSkillMasteryRepository masteries;
    private final AttemptService attempts;

    public PlacementDiagnosticService(
            LessonProgressService learnerStates,
            SkillRepository skills,
            LearnerSkillMasteryRepository masteries,
            AttemptService attempts) {
        this.learnerStates = Objects.requireNonNull(learnerStates, "learnerStates");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.masteries = Objects.requireNonNull(masteries, "masteries");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
    }

    /**
     * Evaluates the placement offer for the authenticated Learner in {@code courseId} inside a
     * read-only {@code REPEATABLE READ} snapshot.
     *
     * @throws IllegalArgumentException if {@code courseId} is {@code null}
     * @throws IllegalStateException    on an invalid provider response or stored graph/mastery state
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PlacementOffer readPlacementOffer(UUID courseId) {
        if (courseId == null) {
            throw new IllegalArgumentException("Course identifier must not be null");
        }
        requireReportingSnapshot();
        LearnerCourseState state = learnerStates.readLearnerState(courseId);
        if (state == null || !courseId.equals(state.courseId())) {
            throw new IllegalStateException("Invalid CT33 learner state");
        }
        return evaluateOffer(state);
    }

    /**
     * Evaluates the placement offer from an already-admitted {@link LearnerCourseState} inside the
     * caller's read-only {@code REPEATABLE READ} snapshot (ADR011 §§11, 15). The public
     * record is re-admitted and compared in that snapshot before any Adaptive repository read.
     *
     * @throws IllegalStateException    without a read-only {@code REPEATABLE READ} transaction or on
     *                                  invalid stored state
     * @throws IllegalArgumentException if {@code state} is {@code null}
     */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PlacementOffer readPlacementOffer(LearnerCourseState state) {
        requireReportingSnapshot();
        if (state == null || state.courseId() == null) {
            throw new IllegalArgumentException("Learner course state and Course identifier must not be null");
        }
        // A public record is data, not proof of admission. Re-admit in this same snapshot before
        // using its learner ID or configuration, including when the offer would otherwise be false.
        LearnerCourseState admitted = learnerStates.readLearnerState(state.courseId());
        if (!state.equals(admitted)) {
            throw new IllegalStateException("Placement state does not match the admitted Course snapshot");
        }
        return evaluateOffer(admitted);
    }

    private PlacementOffer evaluateOffer(LearnerCourseState state) {
        if (state == null || state.courseId() == null || state.learnerId() == null) {
            throw new IllegalStateException("Invalid CT33 learner state");
        }
        if (!isJoinedMembership(state.membershipStatus())) {
            return PlacementOffer.none();
        }
        if (state.enrollmentId() == null) {
            throw new IllegalStateException("Invalid CT33 learner state");
        }
        if (!state.adaptiveEnabled() || state.adaptiveSettings() == null) {
            return PlacementOffer.none();
        }

        Set<UUID> courseSkillIds = readValidatedSkillIds(state.courseId());
        if (courseSkillIds.isEmpty()) {
            return PlacementOffer.none();
        }

        if (hasAnyStoredMasteryRow(state.learnerId(), courseSkillIds)) {
            return PlacementOffer.none();
        }

        UUID placementQuizId = attempts.readMyPlacementQuizId(state.courseId());
        return placementQuizId == null
                ? PlacementOffer.none()
                : new PlacementOffer(true, placementQuizId);
    }

    private Set<UUID> readValidatedSkillIds(UUID courseId) {
        List<Skill> courseSkills = skills.findSkills(courseId);
        if (courseSkills == null) {
            throw new IllegalStateException("Invalid stored skill graph");
        }
        Set<UUID> skillIds = new HashSet<>(courseSkills.size());
        for (Skill skill : courseSkills) {
            if (skill == null || skill.id() == null || !courseId.equals(skill.courseId())
                    || !skillIds.add(skill.id())) {
                throw new IllegalStateException("Invalid stored skill graph");
            }
        }
        return Set.copyOf(skillIds);
    }

    private boolean hasAnyStoredMasteryRow(UUID learnerId, Set<UUID> courseSkillIds) {
        Map<UUID, MasteryState> stored = masteries.findStates(learnerId, courseSkillIds);
        if (stored == null || !courseSkillIds.containsAll(stored.keySet())
                || stored.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException("Invalid mastery repository result");
        }
        return !stored.isEmpty();
    }

    private static boolean isJoinedMembership(String membershipStatus) {
        return "active".equals(membershipStatus) || "completed".equals(membershipStatus);
    }

    private static void requireReportingSnapshot() {
        // Joining an existing transaction does not apply an annotation's isolation/readOnly settings.
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Objects.equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(),
                        Isolation.REPEATABLE_READ.value())) {
            throw new IllegalStateException("Placement reads require a read-only REPEATABLE READ transaction");
        }
    }

    /**
     * Placement offer fields of OpenAPI {@code B6AdaptiveGuidance}: {@code placementQuizId} is
     * non-null if and only if {@code placementOffered} is {@code true}.
     */
    public record PlacementOffer(boolean placementOffered, UUID placementQuizId) {

        public PlacementOffer {
            if (placementOffered && placementQuizId == null) {
                throw new IllegalArgumentException("An offered placement requires a placementQuizId");
            }
            if (!placementOffered && placementQuizId != null) {
                throw new IllegalArgumentException("An unoffered placement must have null placementQuizId");
            }
        }

        public static PlacementOffer none() {
            return new PlacementOffer(false, null);
        }
    }
}
