package com.codemastery.modules.assessment.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.assessment.application.service.QuizGradingService.Grade;
import com.codemastery.modules.assessment.application.service.QuizGradingService.GradedItem;
import com.codemastery.modules.assessment.application.service.QuizGradingService.GradingItem;
import com.codemastery.modules.assessment.domain.model.Attempt;
import com.codemastery.modules.assessment.domain.model.AttemptAnswer;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.model.Quiz;
import com.codemastery.modules.assessment.domain.repository.AttemptRepository;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.assessment.domain.repository.QuizRepository.Member;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.course.application.service.CourseAccessService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate readCommittedTx;
    private final Clock clock;

    @Autowired
    public AttemptService(AttemptRepository attempts,
                          QuizRepository quizzes,
                          QuizGradingService gradingService,
                          CourseAccessService courseAccess,
                          UserAccountService identities,
                          PlatformTransactionManager transactionManager) {
        this(attempts, quizzes, gradingService, courseAccess, identities, transactionManager, Clock.systemUTC());
    }

    public AttemptService(AttemptRepository attempts,
                          QuizRepository quizzes,
                          QuizGradingService gradingService,
                          CourseAccessService courseAccess,
                          UserAccountService identities,
                          PlatformTransactionManager transactionManager,
                          Clock clock) {
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.quizzes = Objects.requireNonNull(quizzes, "quizzes");
        this.gradingService = Objects.requireNonNull(gradingService, "gradingService");
        this.courseAccess = Objects.requireNonNull(courseAccess, "courseAccess");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (transactionManager == null) {
            this.readCommittedTx = null;
        } else {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            this.readCommittedTx = tx;
        }
    }

    /** Starts a new attempt (201) or returns an existing active unsubmitted attempt (200). */
    public StartOutcome start(UUID courseId, UUID quizId) {
        requireId(courseId);
        requireId(quizId);
        courseAccess.requireLearnerCourseAccess(courseId);
        UUID learnerId = requireSelfLearnerId();

        Quiz precheckQuiz = requirePublishedQuiz(courseId, quizId);
        rejectDiagnostic(precheckQuiz);

        return withLearnerAssessmentWrite(courseId, () -> {
            courseAccess.requireLearnerCourseAccess(courseId);
            Quiz quiz = quizzes.findInCourseForUpdate(quizId, courseId).orElseThrow(AttemptService::unavailable);
            requirePublishedQuizState(courseId, quiz);
            rejectDiagnostic(quiz);

            List<Member> members = requireValidQuizMembers(quizId);
            Map<UUID, Question> questionsById = mapQuestions(members);

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
        requireId(courseId);
        requireId(quizId);
        requireId(attemptId);
        courseAccess.requireLearnerCourseAccess(courseId);
        UUID learnerId = requireSelfLearnerId();

        requirePublishedQuiz(courseId, quizId);
        Attempt attempt = attempts.findInQuizForLearner(attemptId, quizId, learnerId)
                .orElseThrow(AttemptService::unavailable);
        requireStoredAttempt(quizId, learnerId, attempt);

        Map<UUID, Question> questionsById = loadQuestionsForAttempt(quizId);
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
            Map<UUID, Question> questionsById = loadQuestionsForAttempt(quizId);
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
            Map<UUID, Question> questionsById = loadQuestionsForAttempt(quizId);
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

            return gradeAndFinalize(courseId, attempt, capturedAnswers, questionsById, validatedMasks, now);
        });
    }

    /** Learner read of own submitted result; 409 before submission (including overdue). */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AttemptResult readResult(UUID courseId, UUID quizId, UUID attemptId) {
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

        Map<UUID, Question> questionsById = loadQuestionsForAttempt(quizId);
        List<AttemptAnswer> answers = attempts.findAnswersInPositionOrder(attempt.id());
        return toAttemptResult(courseId, attempt, answers, questionsById);
    }

    /** Owning Instructor read of a submitted result in their Course (hidden quiz allowed); no keys. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AttemptResult readOwnedResult(UUID courseId, UUID quizId, UUID attemptId) {
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

        Map<UUID, Question> questionsById = loadQuestionsForAttempt(quizId);
        List<AttemptAnswer> answers = attempts.findAnswersInPositionOrder(attempt.id());
        return toAttemptResult(courseId, attempt, answers, questionsById);
    }

    /** Learner's own attempts on a published quiz ordered by {@code attempt_number DESC}. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<AttemptSummary> history(UUID courseId, UUID quizId, int page, int size) {
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
        if (lookupAttempt == null || lookupAttempt.submitted_at() != null) {
            return;
        }
        Quiz lookupQuiz = quizzes.findById(lookupAttempt.quiz_id()).orElse(null);
        if (lookupQuiz == null || lookupQuiz.course_id() == null) {
            throw new IllegalStateException("Stored attempt references missing quiz or course");
        }
        UUID courseId = lookupQuiz.course_id();
        UUID quizId = lookupQuiz.id();

        withAssessmentExpiryWrite(courseId, () -> {
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
            Map<UUID, Question> questionsById = loadQuestionsForAttempt(quizId);

            Map<UUID, List<Boolean>> savedMasks = new HashMap<>(capturedAnswers.size());
            for (AttemptAnswer answer : capturedAnswers) {
                Question q = questionsById.get(answer.question_id());
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

            gradeAndFinalize(courseId, lockedAttempt, capturedAnswers, questionsById, savedMasks,
                    lockedAttempt.expires_at());
            return null;
        });
    }

    /**
     * READ COMMITTED write unit for Learner assessment mutations (CT03.assessment-guard, ADR007 §3.1).
     */
    protected <T> T withLearnerAssessmentWrite(UUID courseId, Supplier<T> work) {
        return runInReadCommitted(work);
    }

    /**
     * READ COMMITTED write unit for internal expiry finalization (CT03.assessment-guard, ADR007 §3.1).
     */
    protected <T> T withAssessmentExpiryWrite(UUID courseId, Supplier<T> work) {
        return runInReadCommitted(work);
    }

    private <T> T runInReadCommitted(Supplier<T> work) {
        if (readCommittedTx == null) {
            return work.get();
        }
        return readCommittedTx.execute(status -> work.get());
    }

    private AttemptResult gradeAndFinalize(UUID courseId,
                                           Attempt attempt,
                                           List<AttemptAnswer> capturedAnswers,
                                           Map<UUID, Question> questionsById,
                                           Map<UUID, List<Boolean>> masksByQuestionId,
                                           Instant submittedAt) {
        if (capturedAnswers.isEmpty()) {
            throw new IllegalStateException("Captured attempt has no answers");
        }
        List<GradingItem> gradingItems = new ArrayList<>(capturedAnswers.size());
        int prevPosition = 0;
        for (AttemptAnswer answer : capturedAnswers){
            if (answer.position() == null || answer.position() <= prevPosition) {
                throw new IllegalStateException("Captured attempt positions must be positive and strictly increasing");
            }
            prevPosition = answer.position();
            Question question = questionsById.get(answer.question_id());
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

        List<AttemptAnswer> gradedAnswers = new ArrayList<>(capturedAnswers.size());
        for (int index = 0; index < capturedAnswers.size(); index++) {
            AttemptAnswer existing = capturedAnswers.get(index);
            GradedItem gradedItem = grade.items().get(index);
            if (gradedItem == null || !existing.question_id().equals(gradedItem.questionId())) {
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
        return toAttemptResult(courseId, submittedAttempt, gradedAnswers, questionsById);
    }

    private Map<UUID, List<Boolean>> validateAndCanonicalizeAnswers(AnswersInput input,
                                                                    List<AttemptAnswer> capturedAnswers,
                                                                    Map<UUID, Question> questionsById) {
        requireNonEmptyInputShape(input);
        if (input.answers().size() != capturedAnswers.size()) {
            throw new BusinessRuleException("Answers must match captured quiz questions exactly");
        }

        Map<UUID, Question> capturedQuestions = new HashMap<>(capturedAnswers.size());
        for (AttemptAnswer captured : capturedAnswers) {
            Question question = questionsById.get(captured.question_id());
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

    private static void rejectDiagnostic(Quiz quiz) {
        if (DIAGNOSTIC.equals(quiz.kind())) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Diagnostic quiz start is not available");
        }
    }

    private List<Member> requireValidQuizMembers(UUID quizId) {
        List<Member> members = quizzes.findMembers(List.of(quizId)).getOrDefault(quizId, List.of());
        if (members.isEmpty()) {
            throw new IllegalStateException("Published quiz has no membership questions");
        }
        int prevPosition = 0;
        for (Member member : members) {
            if (member.position() <= prevPosition) {
                throw new IllegalStateException("Stored quiz membership positions must be positive and strictly increasing");
            }
            prevPosition = member.position();
            QuestionSelectionService.requireValidDefinition(member.question());
        }
        return members;
    }

    private Map<UUID, Question> loadQuestionsForAttempt(UUID quizId) {
        List<Member> members = quizzes.findMembers(List.of(quizId)).getOrDefault(quizId, List.of());
        return mapQuestions(members);
    }

    private static Map<UUID, Question> mapQuestions(List<Member> members) {
        Map<UUID, Question> byId = new HashMap<>(members.size());
        for (Member member : members) {
            Question q = QuestionSelectionService.requireValidDefinition(member.question());
            byId.put(q.id(), q);
        }
        return byId;
    }

    private static AttemptView toAttemptView(UUID courseId,
                                             Attempt attempt,
                                             List<AttemptAnswer> answers,
                                             Map<UUID, Question> questionsById,
                                             Instant now) {
        if (answers.isEmpty()) {
            throw new IllegalStateException("Captured attempt has no answers");
        }
        List<AttemptQuestionView> questionViews = new ArrayList<>(answers.size());
        int prevPosition = 0;
        for (AttemptAnswer answer : answers) {
            if (answer.position() == null || answer.position() <= prevPosition) {
                throw new IllegalStateException("Captured attempt positions must be positive and strictly increasing");
            }
            prevPosition = answer.position();
            Question q = questionsById.get(answer.question_id());
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
                                                 Map<UUID, Question> questionsById) {
        if (attempt.submitted_at() == null || attempt.score_percentage() == null || answers.isEmpty()) {
            throw new IllegalStateException("Submitted attempt violates V004");
        }
        List<GradedAnswerView> gradedViews = new ArrayList<>(answers.size());
        int prevPosition = 0;
        for (AttemptAnswer answer : answers) {
            if (answer.position() == null || answer.position() <= prevPosition) {
                throw new IllegalStateException("Captured attempt positions must be positive and strictly increasing");
            }
            prevPosition = answer.position();
            Question q = questionsById.get(answer.question_id());
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
                || (attempt.submitted_at() == null) != (attempt.score_percentage() == null)) {
            throw new IllegalStateException("Stored attempt violates V004");
        }
    }

    private UUID requireSelfLearnerId() {
        var identity = identities.requireCurrentIdentity();
        if (identity == null || identity.userId() == null) {
            throw new IllegalStateException("Missing current identity");
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
}