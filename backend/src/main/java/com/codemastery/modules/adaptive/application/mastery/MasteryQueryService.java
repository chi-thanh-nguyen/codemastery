package com.codemastery.modules.adaptive.application.mastery;

import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.repository.LearnerSkillMasteryRepository;
import org.springframework.stereotype.Service;
import com.codemastery.modules.course.application.service.LessonProgressService;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.DecisionContext;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.LessonInput;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.PrerequisiteInput;
import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;
import com.codemastery.modules.adaptive.domain.repository.SkillRepository;
import com.codemastery.modules.adaptive.domain.repository.PathRecommendationRepository;
import java.util.List;
import java.util.HashSet;
import java.time.Instant;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * AD16 learner mastery reads (ADR011 §§13, 15; ADR013 §19.4).
 *
 * <p>This slice provides the learner's current state per skill, with an absent row read as
 * {@code Unknown} (ADR005 §6.1), for read-only compositions that have already admitted the learner
 * in the same snapshot (CT33 {@code readLearnerState}). It is the state input of the
 * practice recommendation and of the later Mastery Map and guidance reads. The guidance operation also composes CT25 placement, CT07 warnings and visible history.
 * The Mastery Map, insights and Home operations remain separate AD16 work.
 */
@Service
public class MasteryQueryService {

    private final LearnerSkillMasteryRepository masteries;
    private final LessonProgressService learnerStates;
    private final PlacementDiagnosticService placement;
    private final SkillRepository skills;
    private final PathRecommendationRepository history;
    private final AdaptiveDecisionEngine engine = new AdaptiveDecisionEngine();

    public MasteryQueryService(LearnerSkillMasteryRepository masteries, LessonProgressService learnerStates,
            PlacementDiagnosticService placement, SkillRepository skills, PathRecommendationRepository history) {
        this.masteries = Objects.requireNonNull(masteries);
        this.learnerStates = Objects.requireNonNull(learnerStates);
        this.placement = Objects.requireNonNull(placement);
        this.skills = Objects.requireNonNull(skills);
        this.history = Objects.requireNonNull(history);
    }

    /** CT33.rest: complete guidance, with no readiness evaluation or write, in one admitted snapshot. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdaptiveGuidance readAdaptiveGuidance(UUID courseId) {
        if (courseId == null) throw new IllegalArgumentException("Course identifier is required");
        requireReportingSnapshot();
        var state = learnerStates.readLearnerState(courseId);
        if (state == null || !courseId.equals(state.courseId()) || state.learnerId() == null
                || state.enrollmentId() == null
                || !("active".equals(state.membershipStatus()) || "completed".equals(state.membershipStatus()))) {
            throw new IllegalStateException("Invalid admitted Course state");
        }
        var offer = placement.readPlacementOffer(state);
        if (!state.adaptiveEnabled()) {
            return new AdaptiveGuidance(courseId, false, offer.placementOffered(), offer.placementQuizId(), null,
                    state.lessons().stream().map(l -> new LessonGuidance(l.lessonId(), List.of())).toList());
        }
        Map<UUID, String> names = new HashMap<>();
        for (var skill : Objects.requireNonNull(skills.findSkills(courseId), "Course skills")) {
            if (skill == null || skill.id() == null || !courseId.equals(skill.courseId())
                    || skill.name() == null || skill.name().isBlank()
                    || names.putIfAbsent(skill.id(), skill.name()) != null) {
                throw new IllegalStateException("Invalid stored skill graph");
            }
        }
        Map<UUID, Set<UUID>> mappings = new HashMap<>();
        for (var lesson : state.lessons()) {
            if (mappings.putIfAbsent(lesson.lessonId(), new HashSet<>()) != null) {
                throw new IllegalStateException("Duplicate published Lesson");
            }
        }
        Set<String> pairs = new HashSet<>();
        for (var mapping : Objects.requireNonNull(skills.findMappingsForSkills(names.keySet()), "Lesson mappings")) {
            if (mapping == null || mapping.lessonId() == null || !names.containsKey(mapping.skillId())
                    || !pairs.add(mapping.lessonId() + "/" + mapping.skillId())) {
                throw new IllegalStateException("Invalid stored Lesson mapping");
            }
            if (mappings.containsKey(mapping.lessonId())) mappings.get(mapping.lessonId()).add(mapping.skillId());
        }
        var edges = Objects.requireNonNull(skills.findPrerequisites(courseId), "Prerequisites").stream().map(edge -> {
            if (edge == null || !courseId.equals(edge.courseId())) {
                throw new IllegalStateException("Invalid stored prerequisite");
            }
            return new PrerequisiteInput(edge.skillId(), edge.prerequisiteSkillId());
        }).toList();
        DecisionContext input;
        try {
            input = new DecisionContext(courseId, state.learnerId(), state.lessons().stream()
                    .map(l -> new LessonInput(l.lessonId(), l.completed(), mappings.get(l.lessonId()))).toList(),
                    readStates(state.learnerId(), names.keySet()), edges, names);
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("Invalid stored adaptive inputs", failure);
        }
        // Select the newest history row BEFORE applying visibility. Never fall back to older rows.
        var current = history.findCurrentGuidance(state.enrollmentId());
        var draft = engine.decide(input).recommendations().stream()
                .filter(r -> r.type() != RecommendationType.SKIP).findFirst();
        CurrentRecommendation visible = current.filter(row -> {
            if (!state.enrollmentId().equals(row.enrollmentId())) {
                throw new IllegalStateException("Recommendation belongs to another Enrollment");
            }
            return draft.filter(d -> d.type() == row.recommendationType()
                    && d.targetLessonId().equals(row.lessonId()) && d.reason().equals(row.reason())).isPresent();
        }).map(row -> currentRecommendation(row, input)).orElse(null);
        return new AdaptiveGuidance(courseId, true, offer.placementOffered(), offer.placementQuizId(), visible,
                input.lessons().stream().map(l -> new LessonGuidance(l.lessonId(),
                        engine.prerequisiteWarnings(input, l.lessonId()))).toList());
    }

    private static CurrentRecommendation currentRecommendation(PathRecommendation row, DecisionContext input) {
        boolean completed = input.lessons().stream().filter(l -> l.lessonId().equals(row.lessonId()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing recommendation target")).completed();
        return new CurrentRecommendation(row.recommendationType().getCode(), row.lessonId(), row.reason(),
                row.recommendedAt(), completed);
    }

    public record AdaptiveGuidance(UUID courseId, boolean adaptiveEnabled, boolean placementOffered,
            UUID placementQuizId, CurrentRecommendation currentRecommendation, List<LessonGuidance> lessons) {
        public AdaptiveGuidance { lessons = List.copyOf(lessons); }
    }
    public record CurrentRecommendation(String type, UUID targetLessonId, String reason, Instant recommendedAt,
            boolean targetCompleted) { }
    public record LessonGuidance(UUID lessonId, List<UUID> unmasteredPrerequisiteSkillIds) {
        public LessonGuidance { unmasteredPrerequisiteSkillIds = List.copyOf(unmasteredPrerequisiteSkillIds); }
    }

    private static void requireReportingSnapshot() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Objects.equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(),
                        Isolation.REPEATABLE_READ.value())) {
            throw new IllegalStateException("Mastery reads require a read-only REPEATABLE READ transaction");
        }
    }

    /**
     * Returns the stored state of {@code learnerId} for every requested skill, {@code Unknown} where
     * no row exists. The caller passes the learner it has admitted in this transaction; no other
     * learner's rows are read.
     *
     * <p>Joins the caller's read-only REPEATABLE READ transaction and never starts one, so the
     * states belong to the caller's snapshot.
     *
     * <p><b>Callers.</b> Only Adaptive read compositions that admitted {@code learnerId} in the same
     * snapshot: today {@code RecommendationService.readPracticeRecommendation} and this service's
     * guidance composition, enforced by an
     * allowlist test. The method itself cannot tell whose rows it reads.
     *
     * @return an immutable map with exactly the requested skills as keys
     * @throws IllegalStateException    without a read-only REPEATABLE READ caller transaction
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if {@code skillIds} contains {@code null}
     * @throws org.springframework.dao.DataAccessException on an invalid stored row or any repository
     *                                  failure (the {@code @Repository} adapter's translation)
     */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<UUID, MasteryState> readStates(UUID learnerId, Collection<UUID> skillIds) {
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(skillIds, "skillIds");
        if (skillIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("skillIds must not contain null");
        }
        // Joining an existing transaction does not apply an annotation's isolation/readOnly settings.
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Objects.equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(),
                        Isolation.REPEATABLE_READ.value())) {
            throw new IllegalStateException("Mastery reads require a read-only REPEATABLE READ transaction");
        }
        Set<UUID> requested = Set.copyOf(skillIds);
        if (requested.isEmpty()) {
            return Map.of();
        }

        Map<UUID, MasteryState> stored = masteries.findStates(learnerId, requested);
        if (stored == null || !requested.containsAll(stored.keySet())
                || stored.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException("Invalid mastery repository result");
        }
        Map<UUID, MasteryState> states = new HashMap<>();
        for (UUID skillId : requested) {
            states.put(skillId, stored.getOrDefault(skillId, MasteryState.UNKNOWN));
        }
        return Map.copyOf(states);
    }
}
