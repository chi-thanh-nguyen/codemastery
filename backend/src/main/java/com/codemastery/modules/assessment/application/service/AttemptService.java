package com.codemastery.modules.assessment.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService;
import com.codemastery.modules.assessment.application.event.QuizSubmittedEvent;
import com.codemastery.modules.assessment.application.service.QuizGradingService.Grade;
import com.codemastery.modules.assessment.application.service.QuizGradingService.GradedItem;
import com.codemastery.modules.assessment.application.service.QuizGradingService.GradingItem;
import com.codemastery.modules.assessment.domain.model.Attempt;
import com.codemastery.modules.assessment.domain.model.AttemptAnswer;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.model.Quiz;
import com.codemastery.modules.assessment.domain.repository.AttemptRepository;
import com.codemastery.modules.assessment.domain.repository.AttemptRepository.OwnedLearnerAttemptRow;
import com.codemastery.modules.assessment.domain.repository.QuizRepository.Member;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.CourseManagementService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * CT04 timed quiz attempts: start, answer save, manual submit, expiry finalization and result/history
 * reads (ADR007 §§8–13).
 *
 * <p>Uses server {@link Clock} injected locally (production UTC, tests fixed/mutable clock).
 * Answer keys ({@code correctOptions}) are never included in any boundary projection.
 */
@Service
public class AttemptService {

    static final String PUBLISHED = "published";
    static final String DIAGNOSTIC = "diagnostic";
    static final String STATUS_ACTIVE = "active";
    static final String STATUS_OVERDUE = "overdue";
    static final String STATUS_SUBMITTED = "submitted";

    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal HUNDRED_SCORE = BigDecimal.valueOf(100).setScale(2);
    private static final Set<String> VALID_QUIZ_KINDS = Set.of("diagnostic", "practice", "test");

    /** B3AttemptRead union: active/overdue captured view or immutable submitted result. */
    public sealed interface AttemptRead permits AttemptView, AttemptResult {}

    public record AttemptQuestionView(UUID questionId, int position, String prompt, String kind,
                                      List<String> options, List<Boolean> selectedOptions) {
        public AttemptQuestionView {
            options = List.copyOf(options);
            selectedOptions = List.copyOf(selectedOptions);
        }
    }

    /** B3Attempt: unsubmitted captured view with derived DTO status ({@code active|overdue}). */
    public record AttemptView(UUID attemptId, UUID quizId, UUID courseId, int attemptNumber,
                              Instant startedAt, Instant expiresAt, String status,
                              List<AttemptQuestionView> questions) implements AttemptRead {
        public AttemptView {
            questions = List.copyOf(questions);
        }
    }

    public record GradedAnswerView(UUID questionId, int position, String prompt, String kind,
                                   List<String> options, List<Boolean> selectedOptions, boolean correct) {
        public GradedAnswerView {
            options = List.copyOf(options);
            selectedOptions = List.copyOf(selectedOptions);
        }
    }

    /** B3AttemptResult: immutable submitted result with per-question correctness and no keys. */
    public record AttemptResult(UUID attemptId, UUID quizId, UUID courseId, int attemptNumber,
                                Instant startedAt, Instant expiresAt, String status,
                                Instant submittedAt, BigDecimal scorePercentage,
                                List<GradedAnswerView> answers) implements AttemptRead {
        public AttemptResult {
            answers = List.copyOf(answers);
        }
    }

    public record StartOutcome(AttemptView attempt, boolean created) {
        public StartOutcome {
            Objects.requireNonNull(attempt, "attempt");
        }
    }

    public record AnswerInput(UUID questionId, List<Boolean> selectedOptions) {
        public AnswerInput {
            selectedOptions = selectedOptions == null
                    ? null
                    : Collections.unmodifiableList(new ArrayList<>(selectedOptions));
        }
    }

    public record AnswersInput(List<AnswerInput> answers) {
        public AnswersInput {
            answers = answers == null ? null : Collections.unmodifiableList(new ArrayList<>(answers));
        }
    }

    /** B3AttemptSummary: learner history entry with derived DTO status. */
    public record AttemptSummary(UUID attemptId, UUID quizId, UUID courseId, int attemptNumber,
                                 Instant startedAt, Instant expiresAt, String status,
                                 Instant submittedAt, BigDecimal scorePercentage) {}

    private final AttemptRepository attempts;
    private final QuizRepository quizzes;
    private final QuizGradingService gradingService;
    private final CourseAccessService courseAccess;
    private final UserAccountService identities;
    private final CourseManagementService courseGuard;
    private final SkillGraphService skillGraph;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public AttemptService(AttemptRepository attempts,
                          QuizRepository quizzes,
                          QuizGradingService gradingService,
                          CourseAccessService courseAccess,
                          UserAccountService identities,
                          CourseManagementService courseGuard,
                          SkillGraphService skillGraph,
                          ApplicationEventPublisher eventPublisher) {
        this(attempts, quizzes, gradingService, courseAccess, identities, courseGuard, skillGraph,
                Clock.systemUTC(), eventPublisher);
    }

    public AttemptService(AttemptRepository attempts,
                          QuizRepository quizzes,
                          QuizGradingService gradingService,
                          CourseAccessService courseAccess,
                          UserAccountService identities,
                          CourseManagementService courseGuard, SkillGraphService skillGraph) {
        this(attempts, quizzes, gradingService, courseAccess, identities, courseGuard, skillGraph,
                Clock.systemUTC(), event -> {});
    }

    public AttemptService(AttemptRepository attempts,
                          QuizRepository quizzes,
                          QuizGradingService gradingService,
                          CourseAccessService courseAccess,
                          UserAccountService identities,
                          CourseManagementService courseGuard, SkillGraphService skillGraph,
                          Clock clock) {
        this(attempts, quizzes, gradingService, courseAccess, identities, courseGuard, skillGraph,
                clock, event -> {});
    }

    public AttemptService(AttemptRepository attempts,
                          QuizRepository quizzes,
                          QuizGradingService gradingService,
                          CourseAccessService courseAccess,
                          UserAccountService identities,
                          CourseManagementService courseGuard,
                          SkillGraphService skillGraph,
                          Clock clock,
                          ApplicationEventPublisher eventPublisher) {
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.quizzes = Objects.requireNonNull(quizzes, "quizzes");
        this.gradingService = Objects.requireNonNull(gradingService, "gradingService");
        this.courseAccess = Objects.requireNonNull(courseAccess, "courseAccess");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.courseGuard = Objects.requireNonNull(courseGuard, "courseGuard");
        this.skillGraph = Objects.requireNonNull(skillGraph, "skillGraph");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public record SkillEvidence(UUID skillId, UUID attemptId, UUID quizId, int attemptNumber,
            Instant startedAt, Instant submittedAt, int correctCount, int totalCount, BigDecimal scorePercentage) {}
    public record LatestSkillEvidence(UUID courseId, UUID learnerId, Map<UUID, SkillEvidence> bySkill) {
        public LatestSkillEvidence { bySkill = Map.copyOf(bySkill); }
    }
    /** CT33.results (ADR011 §12): submitted attempt summary for owning Instructor insights. */
    public record LearnerAttemptSummary(UUID attemptId, UUID quizId, String quizTitle, String quizKind,
            int attemptNumber, Instant submittedAt, BigDecimal scorePercentage) {}
    public record QuizOutcome(UUID courseId, long scoredLearnerQuizPairs, BigDecimal averageQuizScore) {}

    /** ADR005 §13.2: conservative owner-only settings barrier, including malformed submitted history. */
    @Transactional(readOnly = true)
    public boolean hasSubmittedAttempts(UUID courseId) {
        requireId(courseId);
        courseAccess.requireCourseOwnership(courseId);
        return attempts.hasSubmittedInCourse(courseId);
    }

    /** ADR011 §12: only MasteryUpdateService may consume this inside submission's guarded callback. */
    public LatestSkillEvidence readLatestSkillEvidence(UUID courseId, UUID learnerId, Set<UUID> skillIds) {
        requireExecutionScope(courseId); // before even a scope or history query
        return latestSkillEvidence(courseId, learnerId, skillIds);
    }

    /** ADR011 §12: self-only learner map projection; joins the caller's coherent snapshot. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY,
            readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LatestSkillEvidence readMyLatestSkillEvidence(UUID courseId, Set<UUID> skillIds) {
        requireReportingSnapshot();
        requireId(courseId);
        courseAccess.requireLearnerCourseAccess(courseId);
        return latestSkillEvidence(courseId, requireSelfLearnerId(), skillIds);
    }

    /**
     * ADR011 §12 (CT33.results): owning Instructor skill-evidence projection; callers must verify
     * joined learner membership via {@code readOwnedLearnerState} in the same snapshot.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY,
            readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LatestSkillEvidence readOwnedLatestSkillEvidence(UUID courseId, UUID learnerId, Set<UUID> skillIds) {
        requireReportingSnapshot();
        requireId(courseId);
        requireId(learnerId);
        Set<UUID> requested = Set.copyOf(Objects.requireNonNull(skillIds, "skillIds"));
        courseAccess.requireCourseOwnership(courseId);
        return latestSkillEvidence(courseId, learnerId, requested);
    }

    /**
     * ADR011 §12 (CT33.results): submitted attempts of {@code learnerId} on Quizzes in an owning
     * Instructor's Course (hidden Quizzes included), ordered by {@code submittedAt DESC} then
     * canonical lowercase attempt UUID ascending; no answer keys. Callers verify membership first.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY,
            readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<LearnerAttemptSummary> listLearnerAttemptsForOwner(
            UUID courseId, UUID learnerId, int page, int size) {
        requireReportingSnapshot();
        requireId(courseId);
        requireId(learnerId);
        PageResponse.validateRequest(page, size);
        courseAccess.requireCourseOwnership(courseId);

        long total = attempts.countSubmittedForLearnerInCourse(courseId, learnerId);
        if (total < 0) {
            throw new IllegalStateException("Submitted attempt count cannot be negative");
        }
        if ((long) page * size >= total) {
            return new PageResponse<>(List.of(), page, size, total);
        }

        List<OwnedLearnerAttemptRow> rows = attempts.findSubmittedForLearnerInCourse(courseId, learnerId, page, size);
        if (rows == null || rows.size() > size || rows.size() > total) {
            throw new IllegalStateException("Invalid learner attempt page result");
        }

        List<LearnerAttemptSummary> content = new ArrayList<>(rows.size());
        for (OwnedLearnerAttemptRow row : rows) {
            if (row == null || row.attemptId() == null || row.quizId() == null
                    || !hasNonWhitespaceText(row.quizTitle())
                    || !VALID_QUIZ_KINDS.contains(row.quizKind())
                    || row.attemptNumber() < 1 || row.submittedAt() == null
                    || !isValidStoredScore(row.scorePercentage())) {
                throw new IllegalStateException("Stored submitted attempt summary violates V004");
            }
            content.add(new LearnerAttemptSummary(
                    row.attemptId(),
                    row.quizId(),
                    row.quizTitle(),
                    row.quizKind(),
                    row.attemptNumber(),
                    row.submittedAt(),
                    row.scorePercentage().setScale(2, RoundingMode.UNNECESSARY)));
        }
        return new PageResponse<>(content, page, size, total);
    }

    /** ADR011 §11: only PlacementDiagnosticService; no mastery, policy or UI offer is invented. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY,
            readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public UUID readMyPlacementQuizId(UUID courseId) {
        requireReportingSnapshot();
        requireId(courseId);
        courseAccess.requireLearnerCourseAccess(courseId);
        return attempts.findPlacementQuizId(courseId, requireSelfLearnerId(), currentMicros());
    }

    private LatestSkillEvidence latestSkillEvidence(UUID courseId, UUID learnerId, Set<UUID> skillIds) {
        requireId(learnerId);
        Set<UUID> requested = Set.copyOf(skillIds);
        Map<UUID, SkillEvidence> result = new LinkedHashMap<>();
        Map<UUID, List<AttemptAnswer>> answerCache = new HashMap<>();
        Map<UUID, Map<UUID, Member>> definitionCache = new HashMap<>();
        for (var candidate : attempts.findLatestEvidenceCandidates(courseId, learnerId, requested)) {
            if (!requested.contains(candidate.skillId()) || result.containsKey(candidate.skillId())) {
                throw new IllegalStateException("Invalid latest-evidence projection");
            }
            Quiz quiz = quizzes.findInCourse(candidate.quizId(), courseId).orElseThrow(AttemptService::unavailable);
            requireStoredQuizStructure(courseId, quiz);
            Attempt attempt = attempts.findInQuizForLearner(candidate.attemptId(), quiz.id(), learnerId)
                    .orElseThrow(() -> new IllegalStateException("Missing authoritative evidence"));
            requireStoredAttempt(quiz.id(), learnerId, attempt);
            Map<UUID, Member> definitions = definitionCache.computeIfAbsent(quiz.id(),
                    id -> loadQuestionsForAttempt(courseId, id));
            List<AttemptAnswer> answers = answerCache.computeIfAbsent(attempt.id(), attempts::findAnswersInPositionOrder);
            if (attempt.submitted_at() == null) throw new IllegalStateException("Evidence is not submitted");
            requireCapturedAnswers(attempt, answers, definitions);
            int total = 0;
            int correct = 0;
            for (AttemptAnswer answer : answers) {
                if (candidate.skillId().equals(question(definitions, answer.question_id()).skill_id())) {
                    total++;
                    if (answer.is_correct()) correct++;
                }
            }
            if (total == 0) throw new IllegalStateException("Evidence omitted requested skill");
            result.put(candidate.skillId(), new SkillEvidence(candidate.skillId(), attempt.id(), quiz.id(),
                    attempt.attempt_number(), attempt.started_at(), attempt.submitted_at(), correct, total,
                    BigDecimal.valueOf(correct).multiply(HUNDRED_SCORE)
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)));
        }
        return new LatestSkillEvidence(courseId, learnerId, result);
    }

    /** ADR012 §13.2: only PlatformMetricsService, after trusted joined IDs, in its RR snapshot. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY,
            readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public QuizOutcome readQuizOutcome(UUID courseId, Set<UUID> learnerIds) {
        requireReportingSnapshot();
        requireId(courseId);
        var outcome = attempts.readQuizOutcome(courseId, Set.copyOf(learnerIds));
        if (outcome == null || outcome.scoredLearnerQuizPairs() < 0
                || (outcome.scoredLearnerQuizPairs() == 0) != (outcome.averageQuizScore() == null)) {
            throw new IllegalStateException("Invalid Quiz outcome projection");
        }
        return new QuizOutcome(courseId, outcome.scoredLearnerQuizPairs(), outcome.averageQuizScore());
    }

    private static void requireReportingSnapshot() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ).equals(
                        TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            throw new IllegalStateException("Read requires the caller's read-only REPEATABLE READ snapshot");
        }
    }

    /** Starts a new attempt (201) or returns an existing active unsubmitted attempt (200). */
    public StartOutcome start(UUID courseId, UUID quizId) {
        requireId(courseId);
        requireId(quizId);
        courseAccess.requireLearnerCourseAccess(courseId);
        UUID learnerId = requireSelfLearnerId();

        requirePublishedQuiz(courseId, quizId);

        return withLearnerAssessmentWrite(courseId, () -> {
            courseAccess.requireLearnerCourseAccess(courseId);
            Quiz quiz = quizzes.findInCourseForUpdate(quizId, courseId).orElseThrow(AttemptService::unavailable);
            requirePublishedQuizState(courseId, quiz);
            requireDiagnosticAdmission(courseId, quiz);

            List<Member> members = requireValidQuizMembers(courseId, quizId);
            Map<UUID, Member> questionsById = mapQuestions(members);

            Instant now = currentMicros();
            Attempt existing = attempts.findUnsubmittedByQuizAndLearner(quizId, learnerId).orElse(null);
            if (existing != null) {
                requireStoredAttempt(quizId, learnerId, existing);
                if (!now.isBefore(existing.expires_at())) {
                    throw new BusinessRuleException(HttpStatus.CONFLICT, "An overdue attempt is pending finalization");
                }
                List<AttemptAnswer> existingAnswers = attempts.findAnswersInPositionOrder(existing.id());
                return new StartOutcome(toAttemptView(courseId, existing, existingAnswers, questionsById, now), false);
            }

            int maxAttemptNumber = attempts.findMaxAttemptNumber(quizId, learnerId);
            if (maxAttemptNumber < 0) {
                throw new IllegalStateException("Stored attempt_number violates V004");
            }
            if (maxAttemptNumber == Integer.MAX_VALUE) {
                throw new BusinessRuleException(HttpStatus.CONFLICT, "Attempt number limit reached");
            }
            int nextAttemptNumber = maxAttemptNumber + 1;

            Instant expiresAt;
            try {
                expiresAt = now.plusSeconds(quiz.time_limit_seconds());
            } catch (DateTimeException | ArithmeticException ex) {
                throw new BusinessRuleException(HttpStatus.CONFLICT, "Attempt expiry out of range");
            }
            if (!expiresAt.isAfter(now)) {
                throw new IllegalStateException("Computed expiresAt must follow startedAt");
            }

            UUID attemptId = UUID.randomUUID();
            Attempt created = attempts.save(new Attempt(
                    attemptId, learnerId, quizId, nextAttemptNumber, now, expiresAt, null, null));

            List<AttemptAnswer> capturedAnswers = new ArrayList<>(members.size());
            for (Member member : members) {
                Question q = member.question();
                Boolean[] initialMask = new Boolean[q.options().length];
                Arrays.fill(initialMask, Boolean.FALSE);
                capturedAnswers.add(new AttemptAnswer(
                        UUID.randomUUID(), attemptId, q.id(), member.position(), initialMask, null));
            }
            attempts.insertAnswers(capturedAnswers);

            return new StartOutcome(toAttemptView(courseId, created, capturedAnswers, questionsById, now), true);
        });
    }

    /** Reads an active/overdue captured view or immutable submitted result; never mutates. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AttemptRead read(UUID courseId, UUID quizId, UUID attemptId) {
        requireReportingSnapshot();
        requireId(courseId);
        requireId(quizId);
        requireId(attemptId);
        courseAccess.requireLearnerCourseAccess(courseId);
        UUID learnerId = requireSelfLearnerId();

        requirePublishedQuiz(courseId, quizId);
        Attempt attempt = attempts.findInQuizForLearner(attemptId, quizId, learnerId)
                .orElseThrow(AttemptService::unavailable);
        requireStoredAttempt(quizId, learnerId, attempt);

        Map<UUID, Member> questionsById = loadQuestionsForAttempt(courseId, quizId);
        List<AttemptAnswer> answers = attempts.findAnswersInPositionOrder(attempt.id());
        if (attempt.submitted_at() != null) {
            return toAttemptResult(courseId, attempt, answers, questionsById);
        }
        return toAttemptView(courseId, attempt, answers, questionsById, currentMicros());
    }

    /** Atomically replaces all captured answer masks before expiry; produces no grading or event. */
    public AttemptView saveAnswers(UUID courseId, UUID quizId, UUID attemptId, AnswersInput input) {
        requireId(courseId);
        requireId(quizId);
        requireId(attemptId);
        courseAccess.requireLearnerCourseAccess(courseId);
        UUID learnerId = requireSelfLearnerId();
        requireNonEmptyInputShape(input);

        return withLearnerAssessmentWrite(courseId, () -> {
            courseAccess.requireLearnerCourseAccess(courseId);
            Quiz quiz = quizzes.findInCourseForUpdate(quizId, courseId).orElseThrow(AttemptService::unavailable);
            requirePublishedQuizState(courseId, quiz);

            Attempt attempt = attempts.findInQuizForUpdate(attemptId, quizId)
                    .orElseThrow(AttemptService::unavailable);
            if (!learnerId.equals(attempt.user_id())) {
                throw unavailable();
            }
            requireStoredAttempt(quizId, learnerId, attempt);

            List<AttemptAnswer> capturedAnswers = attempts.findAnswersInPositionOrderForUpdate(attempt.id());
            Map<UUID, Member> questionsById = loadQuestionsForAttempt(courseId, quizId);
            requireCapturedAnswers(attempt, capturedAnswers, questionsById);
            Map<UUID, List<Boolean>> validatedMasks =
                    validateAndCanonicalizeAnswers(input, capturedAnswers, questionsById);

            Instant now = currentMicros();
            if (now.isBefore(attempt.started_at())) {
                throw new IllegalStateException("Server clock regressed before attempt start");
            }
            if (attempt.submitted_at() != null || !now.isBefore(attempt.expires_at())) {
                throw new BusinessRuleException(HttpStatus.CONFLICT, "Attempt is no longer active");
            }

            List<AttemptAnswer> updatedAnswers = new ArrayList<>(capturedAnswers.size());
            for (AttemptAnswer existing : capturedAnswers) {
                List<Boolean> mask = validatedMasks.get(existing.question_id());
                updatedAnswers.add(new AttemptAnswer(
                        existing.id(),
                        existing.attempt_id(),
                        existing.question_id(),
                        existing.position(),
                        mask.toArray(new Boolean[0]),
                        null
                ));
            }
            attempts.updateAnswers(updatedAnswers);
            return toAttemptView(courseId, attempt, updatedAnswers, questionsById, now);
        });
    }

    /** Manual submission before expiry; idempotent on already-submitted attempts. */
    public AttemptResult submit(UUID courseId, UUID quizId, UUID attemptId, AnswersInput input) {
        requireId(courseId);
        requireId(quizId);
        requireId(attemptId);
        courseAccess.requireLearnerCourseAccess(courseId);
        UUID learnerId = requireSelfLearnerId();
        requireNonEmptyInputShape(input);

        return withLearnerAssessmentWrite(courseId, () -> inExecutionScope(courseId, () -> {
            courseAccess.requireLearnerCourseAccess(courseId);
            Quiz quiz = quizzes.findInCourseForUpdate(quizId, courseId).orElseThrow(AttemptService::unavailable);
            requirePublishedQuizState(courseId, quiz);

            Attempt attempt = attempts.findInQuizForUpdate(attemptId, quizId)
                    .orElseThrow(AttemptService::unavailable);
            if (!learnerId.equals(attempt.user_id())) {
                throw unavailable();
            }
            requireStoredAttempt(quizId, learnerId, attempt);

            List<AttemptAnswer> capturedAnswers = attempts.findAnswersInPositionOrderForUpdate(attempt.id());
            Map<UUID, Member> questionsById = loadQuestionsForAttempt(courseId, quizId);
            requireCapturedAnswers(attempt, capturedAnswers, questionsById);
            Map<UUID, List<Boolean>> validatedMasks =
                    validateAndCanonicalizeAnswers(input, capturedAnswers, questionsById);

            if (attempt.submitted_at() != null) {
                return toAttemptResult(courseId, attempt, capturedAnswers, questionsById);
            }

            Instant now = currentMicros();
            if (now.isBefore(attempt.started_at())) {
                throw new IllegalStateException("Server clock regressed before attempt start");
            }
            if (!now.isBefore(attempt.expires_at())) {
                throw new BusinessRuleException(HttpStatus.CONFLICT, "Attempt has expired");
            }

            return gradeAndFinalize(courseId, quiz, attempt, capturedAnswers, questionsById, validatedMasks, now);
        }));
    }

    /** Learner read of own submitted result; 409 before submission (including overdue). */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AttemptResult readResult(UUID courseId, UUID quizId, UUID attemptId) {
        requireReportingSnapshot();
        requireId(courseId);
        requireId(quizId);
        requireId(attemptId);
        courseAccess.requireLearnerCourseAccess(courseId);
        UUID learnerId = requireSelfLearnerId();

        requirePublishedQuiz(courseId, quizId);
        Attempt attempt = attempts.findInQuizForLearner(attemptId, quizId, learnerId)
                .orElseThrow(AttemptService::unavailable);
        requireStoredAttempt(quizId, learnerId, attempt);
        if (attempt.submitted_at() == null) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Attempt result is not available before submission");
        }

        Map<UUID, Member> questionsById = loadQuestionsForAttempt(courseId, quizId);
        List<AttemptAnswer> answers = attempts.findAnswersInPositionOrder(attempt.id());
        return toAttemptResult(courseId, attempt, answers, questionsById);
    }

    /** Owning Instructor read of a submitted result in their Course (hidden quiz allowed); no keys. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AttemptResult readOwnedResult(UUID courseId, UUID quizId, UUID attemptId) {
        requireReportingSnapshot();
        requireId(courseId);
        requireId(quizId);
        requireId(attemptId);
        courseAccess.requireCourseOwnership(courseId);

        Quiz quiz = quizzes.findInCourse(quizId, courseId).orElseThrow(AttemptService::unavailable);
        requireStoredQuizStructure(courseId, quiz);

        Attempt attempt = attempts.findInQuiz(attemptId, quizId).orElseThrow(AttemptService::unavailable);
        requireStoredAttempt(quizId, attempt.user_id(), attempt);
        if (attempt.submitted_at() == null) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Attempt result is not available before submission");
        }

        Map<UUID, Member> questionsById = loadQuestionsForAttempt(courseId, quizId);
        List<AttemptAnswer> answers = attempts.findAnswersInPositionOrder(attempt.id());
        return toAttemptResult(courseId, attempt, answers, questionsById);
    }

    /** Learner's own attempts on a published quiz ordered by {@code attempt_number DESC}. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<AttemptSummary> history(UUID courseId, UUID quizId, int page, int size) {
        requireReportingSnapshot();
        requireId(courseId);
        requireId(quizId);
        PageResponse.validateRequest(page, size);
        courseAccess.requireLearnerCourseAccess(courseId);
        UUID learnerId = requireSelfLearnerId();

        requirePublishedQuiz(courseId, quizId);
        long total = attempts.countByQuizAndLearner(quizId, learnerId);
        List<Attempt> rows = (long) page * size >= total
                ? List.of()
                : attempts.findPageByQuizAndLearner(quizId, learnerId, page, size);

        Instant now = currentMicros();
        List<AttemptSummary> content = new ArrayList<>(rows.size());
        for (Attempt attempt : rows) {
            requireStoredAttempt(quizId, learnerId, attempt);
            String status = deriveStatus(attempt, now);
            content.add(new AttemptSummary(
                    attempt.id(),
                    attempt.quiz_id(),
                    courseId,
                    attempt.attempt_number(),
                    attempt.started_at(),
                    attempt.expires_at(),
                    status,
                    attempt.submitted_at(),
                    attempt.score_percentage()
            ));
        }
        return new PageResponse<>(content, page, size, total);
    }

    /**
     * Internal scheduler callable (CT04.expiry, ADR007 §10). Grades the last committed saved masks
     * and sets {@code submitted_at = expires_at} (logical expiry boundary).
     */
    public void finalizeExpired(UUID attemptId) {
        requireId(attemptId);
        Attempt lookupAttempt = attempts.findById(attemptId).orElse(null);
        if (lookupAttempt == null || lookupAttempt.submitted_at() != null
                || currentMicros().isBefore(lookupAttempt.expires_at())) {
            return;
        }
        Quiz lookupQuiz = quizzes.findById(lookupAttempt.quiz_id()).orElse(null);
        if (lookupQuiz == null || lookupQuiz.course_id() == null) {
            throw new IllegalStateException("Stored attempt references missing quiz or course");
        }
        UUID courseId = lookupQuiz.course_id();
        UUID quizId = lookupQuiz.id();

        withAssessmentExpiryWrite(courseId, () -> inExecutionScope(courseId, () -> {
            Quiz lockedQuiz = quizzes.findInCourseForUpdate(quizId, courseId)
                    .orElseThrow(() -> new IllegalStateException("Quiz vanished during expiry finalization"));
            requireStoredQuizStructure(courseId, lockedQuiz);

            Attempt lockedAttempt = attempts.findInQuizForUpdate(attemptId, quizId)
                    .orElseThrow(() -> new IllegalStateException("Attempt vanished during expiry finalization"));
            requireStoredAttempt(quizId, lockedAttempt.user_id(), lockedAttempt);

            Instant now = currentMicros();
            if (lockedAttempt.submitted_at() != null || now.isBefore(lockedAttempt.expires_at())) {
                return null;
            }

            List<AttemptAnswer> capturedAnswers = attempts.findAnswersInPositionOrderForUpdate(attemptId);
            Map<UUID, Member> questionsById = loadQuestionsForAttempt(courseId, quizId);

            Map<UUID, List<Boolean>> savedMasks = new HashMap<>(capturedAnswers.size());
            for (AttemptAnswer answer : capturedAnswers) {
                Question q = question(questionsById, answer.question_id());
                if (q == null || answer.selected_options() == null
                        || answer.selected_options().length != q.options().length) {
                    throw new IllegalStateException("Stored attempt answer violates V004");
                }
                List<Boolean> mask = new ArrayList<>(answer.selected_options().length);
                for (Boolean choice : answer.selected_options()) {
                    if (choice == null) {
                        throw new IllegalStateException("Stored selected_options contains null");
                    }
                    mask.add(choice);
                }
                savedMasks.put(answer.question_id(), List.copyOf(mask));
            }

            gradeAndFinalize(courseId, lockedQuiz, lockedAttempt, capturedAnswers, questionsById, savedMasks,
                    lockedAttempt.expires_at());
            return null;
        }));
    }

    private <T> T withLearnerAssessmentWrite(UUID courseId, Supplier<T> work) {
        return courseGuard.withLearnerAssessmentWrite(courseId, work);
    }

    private <T> T withAssessmentExpiryWrite(UUID courseId, Supplier<T> work) {
        return courseGuard.withAssessmentExpiryWrite(courseId, work);
    }

    /** ADR011 §12: package-private key, bound only by submit/expiry inside the held Course guard. */
    static final class ExecutionScope implements TransactionSynchronization {
        final UUID courseId;
        ExecutionScope(UUID courseId) { this.courseId = courseId; }
        @Override public void afterCompletion(int status) {
            if (TransactionSynchronizationManager.getResource(ExecutionScope.class) == this) {
                TransactionSynchronizationManager.unbindResource(ExecutionScope.class);
            }
        }
    }

    private static <T> T inExecutionScope(UUID courseId, Supplier<T> work) {
        if (TransactionSynchronizationManager.hasResource(ExecutionScope.class)) {
            throw new IllegalStateException("Nested Assessment execution scope");
        }
        ExecutionScope scope = new ExecutionScope(courseId);
        TransactionSynchronizationManager.bindResource(ExecutionScope.class, scope);
        try {
            TransactionSynchronizationManager.registerSynchronization(scope);
            requireExecutionScope(courseId);
            return work.get();
        } finally {
            if (TransactionSynchronizationManager.getResource(ExecutionScope.class) == scope) {
                TransactionSynchronizationManager.unbindResource(ExecutionScope.class);
            }
        }
    }

    static void requireExecutionScope(UUID courseId) {
        Object resource = TransactionSynchronizationManager.getResource(ExecutionScope.class);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_READ_COMMITTED).equals(
                        TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                || !(resource instanceof ExecutionScope scope) || !scope.courseId.equals(courseId)
                // Custom resources survive suspension; the synchronization identifies this physical unit.
                || !TransactionSynchronizationManager.getSynchronizations().contains(scope)) {
            throw new IllegalStateException("Assessment read requires this Course's submission execution scope");
        }
    }

    private AttemptResult gradeAndFinalize(UUID courseId,
                                           Quiz quiz,
                                           Attempt attempt,
                                           List<AttemptAnswer> capturedAnswers,
                                           Map<UUID, Member> questionsById,
                                           Map<UUID, List<Boolean>> masksByQuestionId,
                                           Instant submittedAt) {
        requireStoredQuizStructure(courseId, quiz);
        requireCapturedAnswers(attempt, capturedAnswers, questionsById);
        List<GradingItem> gradingItems = new ArrayList<>(capturedAnswers.size());
        int prevPosition = 0;
        for (AttemptAnswer answer : capturedAnswers){
            if (answer.position() == null || answer.position() <= prevPosition) {
                throw new IllegalStateException("Captured attempt positions must be positive and strictly increasing");
            }
            prevPosition = answer.position();
            Question question = question(questionsById, answer.question_id());
            if (question == null) {
                throw new IllegalStateException("Captured question missing from quiz");
            }
            List<Boolean> selected = masksByQuestionId.get(answer.question_id());
            List<Boolean> correctKey = List.of(question.correct_options());
            if (selected == null || selected.size() != correctKey.size()) {
                throw new IllegalStateException("Selected mask length mismatches question key");
            }
            gradingItems.add(new GradingItem(question.id(), selected, correctKey));
        }

        Grade grade = gradingService.grade(gradingItems);
        if (grade == null || grade.items() == null || grade.items().size() != capturedAnswers.size()
                || grade.scorePercentage() == null
                || grade.scorePercentage().compareTo(ZERO_SCORE) < 0
                || grade.scorePercentage().compareTo(HUNDRED_SCORE) > 0) {
            throw new IllegalStateException("Grading provider returned invalid output");
        }

        long correctCount = gradingItems.stream().filter(item -> item.selectedOptions().equals(item.correctOptions())).count();
        BigDecimal expectedScore = BigDecimal.valueOf(correctCount).multiply(HUNDRED_SCORE)
                .divide(BigDecimal.valueOf(gradingItems.size()), 2, RoundingMode.HALF_UP);
        if (grade.scorePercentage().scale() != 2 || !expectedScore.equals(grade.scorePercentage())) {
            throw new IllegalStateException("Grading percentage differs from authoritative exact masks");
        }
        List<AttemptAnswer> gradedAnswers = new ArrayList<>(capturedAnswers.size());
        for (int index = 0; index < capturedAnswers.size(); index++) {
            AttemptAnswer existing = capturedAnswers.get(index);
            GradedItem gradedItem = grade.items().get(index);
            if (gradedItem == null || !existing.question_id().equals(gradedItem.questionId())
                    || gradedItem.correct() != gradingItems.get(index).selectedOptions().equals(
                            gradingItems.get(index).correctOptions())) {
                throw new IllegalStateException("Grading output order/IDs mismatch captured answers");
            }
            List<Boolean> selected = masksByQuestionId.get(existing.question_id());
            gradedAnswers.add(new AttemptAnswer(
                    existing.id(),
                    existing.attempt_id(),
                    existing.question_id(),
                    existing.position(),
                    selected.toArray(new Boolean[0]),
                    gradedItem.correct()
            ));
        }

        attempts.updateAnswers(gradedAnswers);
        Attempt submittedAttempt = attempts.save(new Attempt(
                attempt.id(),
                attempt.user_id(),
                attempt.quiz_id(),
                attempt.attempt_number(),
                attempt.started_at(),
                attempt.expires_at(),
                submittedAt,
                grade.scorePercentage()
        ));
        requireStoredAttempt(quiz.id(), attempt.user_id(), submittedAttempt);
        requireCapturedAnswers(submittedAttempt, gradedAnswers, questionsById);
        List<QuizSubmittedEvent.AnswerEvidence> answerEvidence = new ArrayList<>(gradedAnswers.size());
        for (AttemptAnswer gradedAnswer : gradedAnswers) {
            Question question = question(questionsById, gradedAnswer.question_id());
            if (question == null || question.skill_id() == null || gradedAnswer.is_correct() == null) {
                throw new IllegalStateException("Submitted answer evidence is incomplete");
            }
            answerEvidence.add(new QuizSubmittedEvent.AnswerEvidence(
                    gradedAnswer.question_id(),
                    gradedAnswer.position(),
                    question.skill_id(),
                    gradedAnswer.is_correct()));
        }
        eventPublisher.publishEvent(new QuizSubmittedEvent(
                submittedAttempt.id(),
                submittedAttempt.user_id(),
                courseId,
                quiz.id(),
                quiz.kind(),
                submittedAttempt.attempt_number(),
                submittedAttempt.started_at(),
                submittedAttempt.expires_at(),
                submittedAttempt.submitted_at(),
                answerEvidence));
        return toAttemptResult(courseId, submittedAttempt, gradedAnswers, questionsById);
    }

    private Map<UUID, List<Boolean>> validateAndCanonicalizeAnswers(AnswersInput input,
                                                                    List<AttemptAnswer> capturedAnswers,
                                                                    Map<UUID, Member> questionsById) {
        requireNonEmptyInputShape(input);
        if (capturedAnswers.isEmpty()) throw new IllegalStateException("Missing captured answers");
        if (input.answers().size() != capturedAnswers.size()) {
            throw new BusinessRuleException("Answers must match captured quiz questions exactly");
        }

        Map<UUID, Question> capturedQuestions = new HashMap<>(capturedAnswers.size());
        for (AttemptAnswer captured : capturedAnswers) {
            Question question = question(questionsById, captured.question_id());
            if (question == null) {
                throw new IllegalStateException("Captured question definition is missing");
            }
            capturedQuestions.put(captured.question_id(), question);
        }

        Map<UUID, List<Boolean>> result = new HashMap<>(input.answers().size());
        Set<UUID> seen = new HashSet<>(input.answers().size());
        for (AnswerInput item : input.answers()) {
            if (item == null || item.questionId() == null || item.selectedOptions() == null) {
                throw new BusinessRuleException("Each answer requires questionId and selectedOptions");
            }
            if (!seen.add(item.questionId())) {
                throw new BusinessRuleException("Duplicate questionId in answers");
            }
            Question question = capturedQuestions.get(item.questionId());
            if (question == null) {
                throw new BusinessRuleException("Unknown questionId for this attempt");
            }
            List<Boolean> options = item.selectedOptions();
            if (options.size() != question.options().length || options.stream().anyMatch(Objects::isNull)) {
                throw new BusinessRuleException("selectedOptions must match question option count without nulls");
            }
            if ("single_choice".equals(question.kind()) || "true_false".equals(question.kind())) {
                long trueCount = options.stream().filter(Boolean::booleanValue).count();
                if (trueCount > 1) {
                    throw new BusinessRuleException("Single-choice/true-false allows at most one true option");
                }
            }
            result.put(item.questionId(), List.copyOf(options));
        }

        if (result.size() != capturedAnswers.size()) {
            throw new BusinessRuleException("Answers must include every captured question");
        }
        return result;
    }

    private static void requireNonEmptyInputShape(AnswersInput input) {
        if (input == null || input.answers() == null || input.answers().isEmpty()) {
            throw new BusinessRuleException("Answers payload must be nonempty");
        }
    }

    private Quiz requirePublishedQuiz(UUID courseId, UUID quizId) {
        Quiz quiz = quizzes.findInCourse(quizId, courseId).orElseThrow(AttemptService::unavailable);
        requirePublishedQuizState(courseId, quiz);
        return quiz;
    }

    private static void requirePublishedQuizState(UUID courseId, Quiz quiz) {
        requireStoredQuizStructure(courseId, quiz);
        if (!PUBLISHED.equals(quiz.publication_status())) {
            throw unavailable();
        }
    }

    private static void requireStoredQuizStructure(UUID courseId, Quiz quiz) {
        if (quiz == null || quiz.id() == null || !courseId.equals(quiz.course_id())
                || quiz.time_limit_seconds() == null || quiz.time_limit_seconds() < 1) {
            throw new IllegalStateException("Stored quiz violates V004");
        }
    }

    private void requireDiagnosticAdmission(UUID courseId, Quiz quiz) {
        if (DIAGNOSTIC.equals(quiz.kind())
                && attempts.hasBlockingDiagnosticAttempt(courseId, requireSelfLearnerId(), quiz.id())) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Diagnostic attempt is no longer available");
        }
    }

    private List<Member> requireValidQuizMembers(UUID courseId, UUID quizId) {
        List<Member> members = quizzes.findMembers(List.of(quizId)).getOrDefault(quizId, List.of());
        if (members.isEmpty()) throw new IllegalStateException("Published quiz has no membership questions");
        int previous = 0;
        Set<UUID> ids = new HashSet<>();
        for (Member member : members) {
            if (member == null || member.position() <= previous
                    || !ids.add(QuestionSelectionService.requireValidDefinition(member.question()).id())) {
                throw new IllegalStateException("Invalid frozen Quiz membership");
            }
            previous = member.position();
        }
        skillGraph.requireAssessmentSkillsInCourse(courseId, members.stream().map(member -> member.question().skill_id())
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        return members;
    }

    private Map<UUID, Member> loadQuestionsForAttempt(UUID courseId, UUID quizId) {
        return mapQuestions(requireValidQuizMembers(courseId, quizId));
    }

    private static Map<UUID, Member> mapQuestions(List<Member> members) {
        Map<UUID, Member> byId = new LinkedHashMap<>();
        for (Member member : members) byId.put(member.question().id(), member);
        return byId;
    }

    private static Question question(Map<UUID, Member> definitions, UUID id) {
        Member member = definitions.get(id);
        return member == null ? null : member.question();
    }

    private static void requireCapturedAnswers(Attempt attempt, List<AttemptAnswer> answers,
                                               Map<UUID, Member> definitions) {
        if (answers.isEmpty() || answers.size() != definitions.size()) {
            throw new IllegalStateException("Attempt capture differs from frozen Quiz membership");
        }
        int previous = 0;
        Set<UUID> seen = new HashSet<>();
        int correct = 0;
        for (AttemptAnswer answer : answers) {
            Member member = definitions.get(answer.question_id());
            Question q = member == null ? null : member.question();
            if (answer.id() == null || !attempt.id().equals(answer.attempt_id()) || q == null
                    || !seen.add(q.id()) || answer.position() == null || answer.position() <= previous
                    || answer.position() != member.position() || answer.selected_options() == null
                    || answer.selected_options().length != q.options().length
                    || Arrays.stream(answer.selected_options()).anyMatch(Objects::isNull)
                    || !"multiple_choice".equals(q.kind())
                        && Arrays.stream(answer.selected_options()).filter(Boolean.TRUE::equals).count() > 1
                    || (attempt.submitted_at() == null) != (answer.is_correct() == null)) {
                throw new IllegalStateException("Invalid persisted Attempt capture");
            }
            previous = answer.position();
            if (attempt.submitted_at() != null) {
                boolean exact = Arrays.equals(answer.selected_options(), q.correct_options());
                if (exact != answer.is_correct()) throw new IllegalStateException("Stored grading differs from exact masks");
                if (exact) correct++;
            }
        }
        if (attempt.submitted_at() != null && attempt.score_percentage().compareTo(
                BigDecimal.valueOf(correct).multiply(HUNDRED_SCORE)
                    .divide(BigDecimal.valueOf(answers.size()), 2, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalStateException("Stored score differs from captured grading");
        }
    }

    private static AttemptView toAttemptView(UUID courseId,
                                             Attempt attempt,
                                             List<AttemptAnswer> answers,
                                             Map<UUID, Member> questionsById,
                                             Instant now) {
        requireCapturedAnswers(attempt, answers, questionsById);
        if (now.isBefore(attempt.started_at())) throw new IllegalStateException("Server clock regressed before attempt start");
        List<AttemptQuestionView> questionViews = new ArrayList<>(answers.size());
        int prevPosition = 0;
        for (AttemptAnswer answer : answers) {
            if (answer.position() == null || answer.position() <= prevPosition) {
                throw new IllegalStateException("Captured attempt positions must be positive and strictly increasing");
            }
            prevPosition = answer.position();
            Question q = question(questionsById, answer.question_id());
            if (q == null || answer.selected_options() == null
                    || answer.selected_options().length != q.options().length) {
                throw new IllegalStateException("Stored attempt answer violates V004");
            }
            questionViews.add(new AttemptQuestionView(
                    q.id(),
                    answer.position(),
                    q.prompt(),
                    q.kind(),
                    List.of(q.options()),
                    List.of(answer.selected_options())
            ));
        }
        String status = now.isBefore(attempt.expires_at()) ? STATUS_ACTIVE : STATUS_OVERDUE;
        return new AttemptView(
                attempt.id(),
                attempt.quiz_id(),
                courseId,
                attempt.attempt_number(),
                attempt.started_at(),
                attempt.expires_at(),
                status,
                questionViews
        );
    }

    private static AttemptResult toAttemptResult(UUID courseId,
                                                 Attempt attempt,
                                                 List<AttemptAnswer> answers,
                                                 Map<UUID, Member> questionsById) {
        if (attempt.submitted_at() == null || attempt.score_percentage() == null || answers.isEmpty()) {
            throw new IllegalStateException("Submitted attempt violates V004");
        }
        requireCapturedAnswers(attempt, answers, questionsById);
        List<GradedAnswerView> gradedViews = new ArrayList<>(answers.size());
        int prevPosition = 0;
        for (AttemptAnswer answer : answers) {
            if (answer.position() == null || answer.position() <= prevPosition) {
                throw new IllegalStateException("Captured attempt positions must be positive and strictly increasing");
            }
            prevPosition = answer.position();
            Question q = question(questionsById, answer.question_id());
            if (q == null || answer.selected_options() == null
                    || answer.selected_options().length != q.options().length
                    || answer.is_correct() == null) {
                throw new IllegalStateException("Submitted attempt answer violates V004");
            }
            gradedViews.add(new GradedAnswerView(
                    q.id(),
                    answer.position(),
                    q.prompt(),
                    q.kind(),
                    List.of(q.options()),
                    List.of(answer.selected_options()),
                    answer.is_correct()
            ));
        }
        return new AttemptResult(
                attempt.id(),
                attempt.quiz_id(),
                courseId,
                attempt.attempt_number(),
                attempt.started_at(),
                attempt.expires_at(),
                STATUS_SUBMITTED,
                attempt.submitted_at(),
                attempt.score_percentage(),
                gradedViews
        );
    }

    private static String deriveStatus(Attempt attempt, Instant now) {
        if (attempt.submitted_at() != null) {
            return STATUS_SUBMITTED;
        }
        return now.isBefore(attempt.expires_at()) ? STATUS_ACTIVE : STATUS_OVERDUE;
    }

    private static void requireStoredAttempt(UUID quizId, UUID expectedUserId, Attempt attempt) {
        if (attempt == null || attempt.id() == null || !quizId.equals(attempt.quiz_id())
                || expectedUserId == null || !expectedUserId.equals(attempt.user_id())
                || attempt.attempt_number() == null || attempt.attempt_number() < 1
                || attempt.started_at() == null || attempt.expires_at() == null
                || !attempt.expires_at().isAfter(attempt.started_at())
                || (attempt.submitted_at() == null) != (attempt.score_percentage() == null)
                || attempt.submitted_at() != null && (attempt.submitted_at().isBefore(attempt.started_at())
                    || attempt.submitted_at().isAfter(attempt.expires_at())
                    || attempt.score_percentage().compareTo(ZERO_SCORE) < 0
                    || attempt.score_percentage().compareTo(HUNDRED_SCORE) > 0)) {
            throw new IllegalStateException("Stored attempt violates V004");
        }
    }

    private UUID requireSelfLearnerId() {
        var identity = identities.requireCurrentIdentity();
        if (identity == null || identity.userId() == null) {
            throw new IllegalStateException("Missing current identity");
        }
        if (identity.role() != UserAccountService.IdentityRole.LEARNER) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        return identity.userId();
    }

    private Instant currentMicros() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static void requireId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Identifier must not be null");
        }
    }

    private static ResourceNotFoundException unavailable() {
        return new ResourceNotFoundException("Quiz or attempt not available");
    }

    private static boolean hasNonWhitespaceText(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean isValidStoredScore(BigDecimal score) {
        return score != null
                && score.scale() <= 2
                && score.compareTo(ZERO_SCORE) >= 0
                && score.compareTo(HUNDRED_SCORE) <= 0;
    }
}
