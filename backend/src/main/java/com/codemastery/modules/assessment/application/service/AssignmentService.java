package com.codemastery.modules.assessment.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.assessment.domain.model.Assignment;
import com.codemastery.modules.assessment.domain.model.Submission;
import com.codemastery.modules.assessment.domain.repository.AssignmentRepository;
import com.codemastery.modules.assessment.domain.repository.AssignmentRepository.SubmissionCounts;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.course.application.service.CourseAccessService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * CT10.rest assignment authoring, owner reads and learner reads (ADR010 §§8, 10, 12, 14).
 *
 * <p>Assignments do not participate in adaptive invariants, so ADR005/ADR007 Course-root guards are
 * not used (ADR010 §10). Every mutation runs at READ COMMITTED, locking the Assignment row
 * {@code FOR UPDATE} on edits and re-verifying CT03 ownership/Lesson scope before writing. Reads run
 * in a single read-only REPEATABLE READ snapshot covering admission, rows and submission counts.
 */
@Service
public class AssignmentService {

    static final String HIDDEN = "hidden";
    static final String PUBLISHED = "published";
    private static final Set<String> STATUSES = Set.of(HIDDEN, PUBLISHED);

    public static final Instant EARLIEST_REPRESENTABLE_DEADLINE = Instant.parse("0000-01-01T00:00:00Z");
    public static final Instant LATEST_REPRESENTABLE_DEADLINE = Instant.parse("9999-12-31T23:59:59.999999Z");

    public record AssignmentInput(String title, String instructions, Instant deadlineAt, UUID lessonId) {
    }

    public record OwnedAssignmentView(
            UUID id,
            UUID courseId,
            UUID lessonId,
            String title,
            String instructions,
            Instant deadlineAt,
            String publicationStatus,
            int submissionCount,
            int gradedCount) {
    }

    public record LearnerAssignmentSummaryView(
            UUID id,
            UUID courseId,
            UUID lessonId,
            String title,
            Instant deadlineAt,
            boolean open,
            String submissionStatus) {
    }

    public record LearnerSubmissionView(
            String originalFilename,
            Instant submittedAt,
            boolean graded,
            BigDecimal gradePercentage,
            String feedback,
            Instant gradedAt) {
    }

    public record LearnerAssignmentView(
            UUID id,
            UUID courseId,
            UUID lessonId,
            String title,
            String instructions,
            Instant deadlineAt,
            boolean open,
            LearnerSubmissionView submission) {
    }

    private final AssignmentRepository assignments;
    private final CourseAccessService courseAccess;
    private final UserAccountService identities;
    private final Clock clock;

    public AssignmentService(AssignmentRepository assignments, CourseAccessService courseAccess) {
        this(assignments, courseAccess, null, Clock.systemUTC());
    }

    @Autowired
    public AssignmentService(AssignmentRepository assignments,
                             CourseAccessService courseAccess,
                             UserAccountService identities) {
        this(assignments, courseAccess, identities, Clock.systemUTC());
    }

    public AssignmentService(AssignmentRepository assignments,
                             CourseAccessService courseAccess,
                             UserAccountService identities,
                             Clock clock) {
        this.assignments = Objects.requireNonNull(assignments, "assignments");
        this.courseAccess = Objects.requireNonNull(courseAccess, "courseAccess");
        this.identities = identities;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public record ReminderCursor(Instant deadlineAt, UUID assignmentId) {
        public ReminderCursor {
            Objects.requireNonNull(deadlineAt, "deadlineAt");
            Objects.requireNonNull(assignmentId, "assignmentId");
        }
    }

    public record ReminderCandidate(UUID assignmentId, UUID courseId, UUID lessonId,
            String assignmentTitle, Instant deadlineAt, Set<UUID> submittedLearnerIds) {
        public ReminderCandidate { submittedLearnerIds = Set.copyOf(submittedLearnerIds); }
    }

    /** Actorless producer read; only DeadlineReminderScheduler may discover candidates (ADR012 §3.3). */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ReminderCandidate> findReminderCandidates(Instant now, Instant windowEnd, ReminderCursor after, int limit) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(windowEnd, "windowEnd");
        if (limit < 1) throw new IllegalArgumentException("Limit must be positive");
        return assignments.findReminderCandidates(now, windowEnd,
                        after == null ? now : after.deadlineAt(),
                        after == null ? new UUID(0, 0) : after.assignmentId(), limit).stream()
                .map(this::reminderCandidate).toList();
    }

    /** CT27 listener/reminder admission: first row lock in the caller's read-write transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ReminderCandidate> lockNotificationState(UUID assignmentId) {
        requireId(assignmentId);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Notification admission requires a read-write transaction");
        }
        return assignments.lockNotificationState(assignmentId)
                .filter(row -> PUBLISHED.equals(row.publication_status())).map(this::reminderCandidate);
    }

    private ReminderCandidate reminderCandidate(Assignment row) {
        requireStoredAssignment(row.course_id(), row);
        return new ReminderCandidate(row.id(), row.course_id(), row.lesson_id(), row.title(), row.deadline_at(),
                assignments.submittedLearnerIds(row.id()));
    }

    /** Stage-3 admission check for course-level assignment authoring before reading the request body. */
    @Transactional(readOnly = true)
    public void requireOwnedCourse(UUID courseId) {
        requireId(courseId);
        courseAccess.requireCourseOwnership(courseId);
    }

    /** Stage-3 admission and assignment-in-course check before reading the request body on PUT. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void requireOwnedAssignment(UUID courseId, UUID assignmentId) {
        requireId(courseId);
        requireId(assignmentId);
        courseAccess.requireCourseOwnership(courseId);
        if (!assignments.existsInCourse(assignmentId, courseId)) {
            throw unavailable();
        }
    }

    /** Creates a hidden assignment owned by the Course's Instructor. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnedAssignmentView create(UUID courseId, AssignmentInput input) {
        requireId(courseId);
        courseAccess.requireCourseOwnership(courseId);
        AssignmentInput normalized = normalizeInput(input);
        if (normalized.lessonId() != null) {
            courseAccess.requireLessonOwnership(courseId, normalized.lessonId());
        }
        Assignment created = assignments.insert(new Assignment(
                UUID.randomUUID(),
                courseId,
                normalized.lessonId(),
                normalized.title(),
                normalized.instructions(),
                normalized.deadlineAt(),
                HIDDEN));
        return view(courseId, created, SubmissionCounts.ZERO);
    }

    /**
     * Full replacement of title, instructions, deadlineAt and lessonId while locking the Assignment
     * row {@code FOR UPDATE} (ADR010 §§8, 10, 12). Publication status is preserved.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnedAssignmentView replace(UUID courseId, UUID assignmentId, AssignmentInput input) {
        requireId(courseId);
        requireId(assignmentId);
        courseAccess.requireCourseOwnership(courseId);
        Assignment current = lockedAssignment(courseId, assignmentId);
        AssignmentInput normalized = normalizeInput(input);
        if (normalized.lessonId() != null) {
            courseAccess.requireLessonOwnership(courseId, normalized.lessonId());
        } else {
            courseAccess.requireCourseOwnership(courseId);
        }
        assignments.updateDefinition(
                current.id(),
                courseId,
                normalized.lessonId(),
                normalized.title(),
                normalized.instructions(),
                normalized.deadlineAt());
        Assignment updated = new Assignment(
                current.id(),
                courseId,
                normalized.lessonId(),
                normalized.title(),
                normalized.instructions(),
                normalized.deadlineAt(),
                current.publication_status());
        return view(courseId, updated, countsFor(current.id()));
    }

    /** Explicit idempotent publication change (`hidden` or `published`) under the Assignment lock. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnedAssignmentView setPublication(UUID courseId, UUID assignmentId, String publicationStatus) {
        requireId(courseId);
        requireId(assignmentId);
        courseAccess.requireCourseOwnership(courseId);
        Assignment current = lockedAssignment(courseId, assignmentId);
        if (publicationStatus == null || !STATUSES.contains(publicationStatus)) {
            throw invalid("Invalid publication status");
        }
        courseAccess.requireCourseOwnership(courseId);
        Assignment updated = current;
        if (!publicationStatus.equals(current.publication_status())) {
            assignments.updatePublicationStatus(current.id(), courseId, publicationStatus);
            updated = new Assignment(
                    current.id(),
                    courseId,
                    current.lesson_id(),
                    current.title(),
                    current.instructions(),
                    current.deadline_at(),
                    publicationStatus);
        }
        return view(courseId, updated, countsFor(current.id()));
    }

    /** Coherent REPEATABLE READ owner read of a single assignment and its submission counts. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OwnedAssignmentView readOwned(UUID courseId, UUID assignmentId) {
        requireId(courseId);
        requireId(assignmentId);
        courseAccess.requireCourseOwnership(courseId);
        Assignment row = assignments.findInCourse(assignmentId, courseId).orElseThrow(AssignmentService::unavailable);
        return view(courseId, row, countsFor(row.id()));
    }

    /** Coherent REPEATABLE READ owner page ordered by {@code title COLLATE "C", id}. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<OwnedAssignmentView> listOwned(UUID courseId, int page, int size) {
        requireId(courseId);
        PageResponse.validateRequest(page, size);
        courseAccess.requireCourseOwnership(courseId);
        long total = assignments.countInCourse(courseId);
        List<Assignment> rows = (long) page * size >= total
                ? List.of()
                : assignments.findPageInCourse(courseId, page, size);
        Map<UUID, SubmissionCounts> counts = assignments.countSubmissions(
                rows.stream().map(Assignment::id).toList());
        List<OwnedAssignmentView> content = rows.stream()
                .map(row -> view(courseId, row, counts.getOrDefault(row.id(), SubmissionCounts.ZERO)))
                .toList();
        return new PageResponse<>(content, page, size, total);
    }

    /** Coherent REPEATABLE READ learner page ordered by {@code deadline_at ASC, id ASC} (ADR010 §12). */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<LearnerAssignmentSummaryView> listForLearner(UUID courseId, int page, int size) {
        requireId(courseId);
        PageResponse.validateRequest(page, size);
        courseAccess.requireLearnerCourseAccess(courseId);
        UUID learnerId = requireSelfLearnerId();

        // Filter before pagination/counts, without querying Course-owned tables from Assessment.
        // The Course-level list admits even an empty result. Each bound Lesson uses its strongest
        // CT03 gate once in this same snapshot; hidden/foreign/missing Lessons are omitted.
        long candidates = assignments.countPublishedInCourse(courseId);
        long offset = (long) page * size;
        long total = 0;
        List<Assignment> rows = new java.util.ArrayList<>();
        Map<UUID, Boolean> lessonVisibility = new java.util.HashMap<>();
        for (int candidatePage = 0; (long) candidatePage * PageResponse.MAX_SIZE < candidates; candidatePage++) {
            for (Assignment candidate : assignments.findPublishedPageInCourse(courseId, candidatePage, PageResponse.MAX_SIZE)) {
                requireStoredAssignment(courseId, candidate);
                boolean visible = candidate.lesson_id() == null || lessonVisibility.computeIfAbsent(candidate.lesson_id(), lesson -> {
                    try {
                        courseAccess.requireLearnerLessonAccess(courseId, lesson);
                        return true;
                    } catch (ResourceNotFoundException concealed) {
                        return false;
                    }
                });
                if (visible) {
                    if (total >= offset && rows.size() < size) rows.add(candidate);
                    total++;
                }
            }
        }
        Map<UUID, Submission> ownSubmissions = assignments.findSubmissionsByAssignmentsAndLearner(
                rows.stream().map(Assignment::id).toList(), learnerId);

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<LearnerAssignmentSummaryView> content = rows.stream()
                .map(row -> {
                    requireStoredAssignment(courseId, row);
                    if (!PUBLISHED.equals(row.publication_status())) {
                        throw new IllegalStateException("Non-published assignment reached learner list");
                    }
                    Submission sub = ownSubmissions.get(row.id());
                    String status = (sub == null)
                            ? "none"
                            : (sub.graded_at() != null ? "graded" : "submitted");
                    return new LearnerAssignmentSummaryView(
                            row.id(),
                            row.course_id(),
                            row.lesson_id(),
                            row.title(),
                            row.deadline_at(),
                            now.isBefore(row.deadline_at()),
                            status);
                })
                .toList();
        return new PageResponse<>(content, page, size, total);
    }

    /** Coherent REPEATABLE READ learner detail with own submission & grade only (ADR010 §12). */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LearnerAssignmentView readForLearner(UUID courseId, UUID assignmentId) {
        requireId(courseId);
        requireId(assignmentId);
        UUID learnerId = requireSelfLearnerId();

        Assignment row = assignments.findInCourse(assignmentId, courseId).orElse(null);
        if (row != null && row.lesson_id() != null) {
            courseAccess.requireLearnerLessonAccess(courseId, row.lesson_id());
        } else {
            courseAccess.requireLearnerCourseAccess(courseId);
        }
        if (row == null) throw unavailable();
        requireStoredAssignment(courseId, row);
        if (!PUBLISHED.equals(row.publication_status())) throw unavailable();

        Submission sub = assignments.findSubmissionByAssignmentAndLearner(row.id(), learnerId).orElse(null);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LearnerSubmissionView submissionView = (sub == null) ? null : toLearnerSubmissionView(sub);
        return new LearnerAssignmentView(
                row.id(),
                row.course_id(),
                row.lesson_id(),
                row.title(),
                row.instructions(),
                row.deadline_at(),
                now.isBefore(row.deadline_at()),
                submissionView);
    }

    private UUID requireSelfLearnerId() {
        if (identities == null) {
            throw new IllegalStateException("UserAccountService not configured");
        }
        var actor = identities.requireCurrentIdentity();
        if (actor == null || actor.userId() == null) {
            throw new IllegalStateException("Missing current identity");
        }
        if (actor.role() != UserAccountService.IdentityRole.LEARNER) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        return actor.userId();
    }

    private static LearnerSubmissionView toLearnerSubmissionView(Submission sub) {
        boolean graded = sub.graded_at() != null;
        return new LearnerSubmissionView(
                sub.original_filename(),
                sub.submitted_at(),
                graded,
                graded ? sub.grade_percentage() : null,
                graded ? sub.feedback() : null,
                sub.graded_at());
    }

    private Assignment lockedAssignment(UUID courseId, UUID assignmentId) {
        return assignments.findInCourseForUpdate(assignmentId, courseId).orElseThrow(AssignmentService::unavailable);
    }

    private SubmissionCounts countsFor(UUID assignmentId) {
        return assignments.countSubmissions(List.of(assignmentId))
                .getOrDefault(assignmentId, SubmissionCounts.ZERO);
    }

    private static OwnedAssignmentView view(UUID expectedCourseId, Assignment row, SubmissionCounts counts) {
        requireStoredAssignment(expectedCourseId, row);
        return new OwnedAssignmentView(
                row.id(),
                row.course_id(),
                row.lesson_id(),
                row.title(),
                row.instructions(),
                row.deadline_at(),
                row.publication_status(),
                counts.submissionCount(),
                counts.gradedCount());
    }

    private static void requireStoredAssignment(UUID expectedCourseId, Assignment row) {
        if (row == null || row.id() == null || !expectedCourseId.equals(row.course_id())
                || row.title() == null || trim(row.title()).isEmpty() || !isStorable(row.title())
                || row.instructions() == null || trim(row.instructions()).isEmpty() || !isStorable(row.instructions())
                || row.deadline_at() == null || row.deadline_at().getNano() % 1_000 != 0
                || !STATUSES.contains(row.publication_status())) {
            throw new IllegalStateException("Stored assignment violated V004/CT10 invariants");
        }
    }

    private static AssignmentInput normalizeInput(AssignmentInput input) {
        if (input == null) {
            throw invalid("Assignment input must not be null");
        }
        String trimmedTitle = requiredTrimmedText(input.title());
        String verbatimInstructions = requiredVerbatimText(input.instructions());
        Instant deadlineAt = input.deadlineAt();
        if (deadlineAt == null || deadlineAt.getNano() % 1_000 != 0) {
            throw invalid("Assignment deadline must have at most microsecond precision");
        }
        if (!isRepresentableDeadline(deadlineAt)) {
            throw invalid("Assignment deadline must be a UTC instant in years 0000-9999");
        }
        return new AssignmentInput(trimmedTitle, verbatimInstructions, deadlineAt, input.lessonId());
    }

    public static boolean isRepresentableDeadline(Instant deadline) {
        return deadline != null
                && !deadline.isBefore(EARLIEST_REPRESENTABLE_DEADLINE)
                && !deadline.isAfter(LATEST_REPRESENTABLE_DEADLINE);
    }

    private static String requiredTrimmedText(String value) {
        if (value == null || !isStorable(value)) {
            throw invalid("Text must be storable Unicode");
        }
        String trimmed = trim(value);
        if (trimmed.isEmpty()) {
            throw invalid("Title must not be blank");
        }
        return trimmed;
    }

    private static String requiredVerbatimText(String value) {
        if (value == null || !isStorable(value) || trim(value).isEmpty()) {
            throw invalid("Instructions must not be blank");
        }
        return value;
    }

    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0x0085
                || codePoint == 0xFEFF;
    }

    private static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (end > start && isSpace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    private static boolean isStorable(String value) {
        return value.codePoints().noneMatch(codePoint -> codePoint == 0
                || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE));
    }

    private static void requireId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Identifier must not be null");
        }
    }

    private static BusinessRuleException invalid(String reason) {
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, reason);
    }

    private static ResourceNotFoundException unavailable() {
        return new ResourceNotFoundException("Resource not available");
    }
}