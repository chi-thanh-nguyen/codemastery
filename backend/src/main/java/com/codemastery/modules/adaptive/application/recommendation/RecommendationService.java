package com.codemastery.modules.adaptive.application.recommendation;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.adaptive.application.decision.PracticeDifficultySelector;
import com.codemastery.modules.adaptive.application.decision.PracticeDifficultySelector.PracticeChoice;
import com.codemastery.modules.adaptive.application.decision.PracticeDifficultySelector.PracticeQuiz;
import com.codemastery.modules.adaptive.application.decision.PracticeDifficultySelector.PracticeSkill;
import com.codemastery.modules.adaptive.application.event.RecommendationCreatedEvent.CreatedRecommendation;
import com.codemastery.modules.adaptive.application.mastery.MasteryQueryService;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision.Recommendation;
import com.codemastery.modules.adaptive.domain.model.LessonSkill;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.model.Skill;
import com.codemastery.modules.adaptive.domain.repository.PathRecommendationRepository;
import com.codemastery.modules.adaptive.domain.repository.SkillRepository;
import com.codemastery.modules.assessment.application.service.QuizService;
import com.codemastery.modules.assessment.application.service.QuizService.PracticeQuizProfile;
import com.codemastery.modules.course.application.service.LessonProgressService;
import com.codemastery.modules.course.application.service.LessonProgressService.LearnerCourseState;
import com.codemastery.modules.course.application.service.LessonProgressService.LessonState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Recommendation history and the practice recommendation read.
 *
 * <p><b>History (AD14.history; ADR011 §§7, 9, V005).</b> Each row keeps the draft's type, target
 * Lesson and reason verbatim and the run's instant. History is append-only: rows are inserted
 * through {@link PathRecommendationRepository#append}, never updated or deleted. Whether a draft
 * needs a row at all and the strictly-later instant are decided by {@code AdaptiveDecisionService}
 * before this call.
 *
 * <p><b>Practice recommendation (AD11; {@code CT07-PRACTICE-RECOMMENDATION-2026-11-15-r1},
 * ADR013 §19.4, Leader decision E02 option a).</b> {@link #readPracticeRecommendation} composes,
 * in one read-only REPEATABLE READ snapshot, the CT33 learner admission and Course state
 * ({@code LessonProgressService.readLearnerState}), Adaptive's skills, Lesson mappings and the
 * learner's stored states (AD16 {@code MasteryQueryService}), and Assessment's
 * {@code CT23-PRACTICE-PROFILES-2026-11-15-r1} profiles ({@code QuizService.readPracticeQuizProfiles}),
 * then applies the pure {@link PracticeDifficultySelector#recommendPractice} rule. The learner is
 * admitted before any other read, and Assessment is read only for an admitted, published Lesson
 * of an adaptive Course with mapped skills. Nothing is written, published or persisted, and
 * Assessment's {@code PracticeDifficultyProvider} port is not used.
 */
@Service
public class RecommendationService {

    private final PathRecommendationRepository history;
    private final LessonProgressService learnerStates;
    private final SkillRepository skills;
    private final MasteryQueryService masteries;
    private final QuizService quizzes;

    public RecommendationService(PathRecommendationRepository history, LessonProgressService learnerStates,
            SkillRepository skills, MasteryQueryService masteries, QuizService quizzes) {
        this.history = Objects.requireNonNull(history);
        this.learnerStates = Objects.requireNonNull(learnerStates);
        this.skills = Objects.requireNonNull(skills);
        this.masteries = Objects.requireNonNull(masteries);
        this.quizzes = Objects.requireNonNull(quizzes);
    }

    /**
     * Reads the current Learner's practice recommendation for one Lesson (OpenAPI
     * {@code b8ReadPracticeRecommendation}).
     *
     * <ol>
     *   <li>CT33 {@code readLearnerState(courseId)}: CT03 Learner Course access and ACTIVE/COMPLETED
     *       membership; its 403/404 outcomes propagate before any other read.</li>
     *   <li>{@code lessonId} must be one of that state's currently published Lessons, otherwise
     *       {@link ResourceNotFoundException} (404).</li>
     *   <li>With the stored {@code adaptiveEnabled} false, or no Course skill mapped to the Lesson,
     *       the recommendation is {@code null} and nothing else is read.</li>
     *   <li>Otherwise the mapped skills' names and the learner's states (absent as {@code Unknown})
     *       and the Assessment profiles for exactly those skills feed the §19.4 rule.</li>
     * </ol>
     *
     * @return the response facts; {@code recommendation} is {@code null} for a null result
     * @throws IllegalStateException on a provider or integrity failure (500)
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PracticeRecommendation readPracticeRecommendation(UUID courseId, UUID lessonId) {
        if (courseId == null || lessonId == null) {
            throw new IllegalArgumentException("Identifier must not be null");
        }
        LearnerCourseState state = learnerStates.readLearnerState(courseId);
        if (state == null || !courseId.equals(state.courseId()) || state.learnerId() == null) {
            throw new IllegalStateException("Invalid CT33 learner state");
        }
        if (state.lessons().stream().map(LessonState::lessonId).noneMatch(lessonId::equals)) {
            throw new ResourceNotFoundException("Resource not available");
        }
        if (!state.adaptiveEnabled()) {
            return new PracticeRecommendation(courseId, lessonId, false, null);
        }

        Map<UUID, Skill> courseSkills = new HashMap<>();
        for (Skill skill : skills.findSkills(courseId)) {
            if (skill == null || skill.id() == null || !courseId.equals(skill.courseId())
                    || courseSkills.putIfAbsent(skill.id(), skill) != null) {
                throw new IllegalStateException("Invalid stored skill graph");
            }
        }
        Set<UUID> lessonSkills = new HashSet<>();
        for (LessonSkill mapping : skills.findMappingsForSkills(courseSkills.keySet())) {
            if (mapping == null || mapping.lessonId() == null || !courseSkills.containsKey(mapping.skillId())) {
                throw new IllegalStateException("Invalid stored Lesson mapping");
            }
            if (lessonId.equals(mapping.lessonId())) {
                lessonSkills.add(mapping.skillId());
            }
        }
        if (lessonSkills.isEmpty()) {
            return new PracticeRecommendation(courseId, lessonId, true, null);
        }

        Map<UUID, MasteryState> states = masteries.readStates(state.learnerId(), lessonSkills);
        List<PracticeSkill> mapped = new ArrayList<>(lessonSkills.size());
        for (UUID skillId : lessonSkills) {
            mapped.add(new PracticeSkill(skillId, courseSkills.get(skillId).name(), states.get(skillId)));
        }
        List<PracticeQuizProfile> profiles = quizzes.readPracticeQuizProfiles(courseId, Set.copyOf(lessonSkills));
        if (profiles == null) {
            throw new IllegalStateException("Invalid practice quiz profile result");
        }
        List<PracticeQuiz> candidates = profiles.stream()
                .map(profile -> new PracticeQuiz(profile.quizId(), profile.title(), profile.difficulties()))
                .toList();
        PracticeChoice choice = PracticeDifficultySelector.recommendPractice(true, mapped, candidates).orElse(null);
        return new PracticeRecommendation(courseId, lessonId, true, choice);
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

    /**
     * The facts of OpenAPI {@code B8PracticeRecommendation}; {@code recommendation} is {@code null}
     * when adaptive mode is off, no skill is mapped to the Lesson or no candidate Quiz exists.
     */
    public record PracticeRecommendation(UUID courseId, UUID lessonId, boolean adaptiveEnabled,
            PracticeChoice recommendation) {
        public PracticeRecommendation {
            Objects.requireNonNull(courseId, "courseId");
            Objects.requireNonNull(lessonId, "lessonId");
            if (!adaptiveEnabled && recommendation != null) {
                throw new IllegalArgumentException("A non-adaptive Course has no practice recommendation");
            }
        }
    }
}
