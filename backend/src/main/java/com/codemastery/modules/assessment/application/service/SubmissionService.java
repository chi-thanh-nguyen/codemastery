package com.codemastery.modules.assessment.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.infrastructure.storage.FileStorage;
import com.codemastery.infrastructure.storage.FileStorage.ObjectNotFoundException;
import com.codemastery.modules.assessment.application.event.AssignmentGradedEvent;
import com.codemastery.modules.assessment.application.service.AssignmentService.LearnerSubmissionView;
import com.codemastery.modules.assessment.domain.model.Assignment;
import com.codemastery.modules.assessment.domain.model.Submission;
import com.codemastery.modules.assessment.domain.repository.AssignmentRepository;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.course.application.service.CourseAccessService;
import jakarta.persistence.EntityManager;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * CT09/CT10/CT27 assignment submissions, protected storage streaming and grading
 * (ADR010 §§4–12, 14; ADR012 §9.2).
 *
 * <p>Storage calls and binary download streaming run strictly outside database transactions.
 * Manual grading publishes {@link AssignmentGradedEvent} inside the locked transaction and never
 * touches Quiz events, mastery or lesson progress.
 */
@Service
public class SubmissionService {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionService.class);
    private static final String PUBLISHED = "published";
    private static final String MEDIA_PDF = "application/pdf";
    private static final String MEDIA_ZIP = "application/zip";
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_LOCAL_MAGIC = new byte[] {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] ZIP_EMPTY_MAGIC = new byte[] {0x50, 0x4B, 0x05, 0x06};
    private static final BigDecimal ZERO_GRADE = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal HUNDRED_GRADE = BigDecimal.valueOf(100).setScale(2);

    public record FilePart(String originalFilename, long size, InputStream content) {
    }

    public record SubmissionOutcome(LearnerSubmissionView submission, boolean created) {
        public SubmissionOutcome {
            Objects.requireNonNull(submission, "submission");
        }
    }

    public record SubmissionFile(String originalFilename, String mediaType, long contentLength, InputStream content)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            content.close();
        }
    }

    public record SubmissionSummaryView(
            UUID id,
            UUID assignmentId,
            UUID learnerId,
            String learnerDisplayName,
            String originalFilename,
            Instant submittedAt,
            boolean graded,
            BigDecimal gradePercentage,
            String feedback,
            Instant gradedAt) {
    }

    public record GradeInput(BigDecimal gradePercentage, String feedback, Instant submittedAt) {
    }

    private final AssignmentRepository assignments;
    private final CourseAccessService courseAccess;
    private final UserAccountService identities;
    private final FileStorage storage;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;
    private final Clock clock;
    private final long uploadLimit;

    @Autowired
    public SubmissionService(AssignmentRepository assignments,
                             CourseAccessService courseAccess,
                             UserAccountService identities,
                             @Lazy FileStorage storage,
                             EntityManager entityManager,
                             ApplicationEventPublisher eventPublisher,
                             PlatformTransactionManager transactionManager,
                             Environment environment) {
        this(assignments, courseAccess, identities, storage, entityManager, eventPublisher,
                transactionManager, resolveUploadLimit(environment), Clock.systemUTC());
    }

    public SubmissionService(AssignmentRepository assignments,
                             CourseAccessService courseAccess,
                             UserAccountService identities,
                             FileStorage storage,
                             EntityManager entityManager,
                             ApplicationEventPublisher eventPublisher,
                             PlatformTransactionManager transactionManager,
                             long uploadLimit,
                             Clock clock) {
        this.assignments = Objects.requireNonNull(assignments, "assignments");
        this.courseAccess = Objects.requireNonNull(courseAccess, "courseAccess");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.entityManager = entityManager;
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (uploadLimit < 1 || uploadLimit > Integer.MAX_VALUE) {
            throw new IllegalStateException("Invalid MAX_UPLOAD_SIZE");
        }
        this.uploadLimit = uploadLimit;
        if (transactionManager == null) {
            this.readTx = null;
            this.writeTx = null;
        } else {
            TransactionTemplate read = new TransactionTemplate(transactionManager);
            read.setReadOnly(true);
            read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            this.readTx = read;

            TransactionTemplate write = new TransactionTemplate(transactionManager);
            write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            this.writeTx = write;
        }
    }

    private static long resolveUploadLimit(Environment environment) {
        long configured;
        try {
            configured = DataSize.parse(environment.getRequiredProperty("spring.servlet.multipart.max-file-size"))
                    .toBytes();
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Invalid MAX_UPLOAD_SIZE");
        }
        if (configured < 1 || configured > Integer.MAX_VALUE) {
            throw new IllegalStateException("Invalid MAX_UPLOAD_SIZE");
        }
        return configured;
    }

    /** Stage-3 admission check for grade PUT before reading JSON body. */
    public void requireOwnedSubmission(UUID courseId, UUID assignmentId, UUID submissionId) {
        requireId(courseId);
        requireId(assignmentId);
        requireId(submissionId);
        runRead(() -> {
            courseAccess.requireCourseOwnership(courseId);
            if (!assignments.existsInCourse(assignmentId, courseId)) {
                throw unavailable();
            }
            if (assignments.findSubmissionInAssignment(submissionId, assignmentId).isEmpty()) {
                throw unavailable();
            }
            return null;
        });
    }

    /**
     * Learner first submission (201) or ungraded pre-deadline file replacement (200).
     * Precedence strictly follows ADR010 §14 stages 3–8.
     */
    public SubmissionOutcome submitOrReplace(UUID courseId, UUID assignmentId, Supplier<FilePart> partSupplier) {
        requireId(courseId);
        requireId(assignmentId);
        Objects.requireNonNull(partSupplier, "partSupplier");
        requireOutermost();

        // Stage 3: Admission & assignment visibility before reading multipart stream
        UUID learnerId = requireSelfLearnerId();
        Assignment precheckAssignment = runRead(() -> admitLearnerAssignment(courseId, assignmentId));

        // Stage 4: Read and validate file part shape, size, extension and magic bytes
        FilePart part = Objects.requireNonNull(partSupplier.get());
        String freshKey = "submissions/" + UUID.randomUUID();
        Outcome outcome = new Outcome();
        String[] previousKey = {null};
        boolean[] createdFlag = {false};
        boolean storageAttempted = false;

        try {
            try (InputStream rawStream = Objects.requireNonNull(part.content())) {
                String mediaType = validateFilenameAndMediaType(part.originalFilename());
                if (part.size() > uploadLimit) {
                    throw new MaxUploadSizeExceededException(uploadLimit);
                }
                if (part.size() < 1) {
                    throw invalid("Submission file must not be empty");
                }
                BufferedInputStream buffered = new BufferedInputStream(rawStream);
                validateLeadingBytes(buffered, mediaType);

                // Stage 5: Unlocked lifecycle pre-check before writing to storage
                runRead(() -> {
                    Instant preNow = currentMicros();
                    if (!preNow.isBefore(precheckAssignment.deadline_at())) {
                        throw conflict("Assignment deadline has passed");
                    }
                    Submission existing = assignments
                            .findSubmissionByAssignmentAndLearner(assignmentId, learnerId)
                            .orElse(null);
                    if (existing != null && existing.graded_at() != null) {
                        throw conflict("Graded submission cannot be replaced");
                    }
                    return null;
                });

                // Stage 6: Store object outside transaction
                storageAttempted = true;
                storage.store(freshKey, buffered, part.size(), mediaType);
            }

            // Stage 7: Locked authoritative checks and row write
            LearnerSubmissionView view = runWrite(() -> {
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(outcome);
                }
                Assignment lockedAssignment = assignments.findInCourseForUpdate(assignmentId, courseId)
                        .orElseThrow(SubmissionService::unavailable);
                requirePublishedVisibleAssignment(courseId, lockedAssignment);
                if (lockedAssignment.lesson_id() != null) {
                    courseAccess.requireLearnerLessonAccess(courseId, lockedAssignment.lesson_id());
                } else {
                    courseAccess.requireLearnerCourseAccess(courseId);
                }

                Submission existing = assignments
                        .findSubmissionByAssignmentAndLearnerForUpdate(assignmentId, learnerId)
                        .orElse(null);

                Instant now = currentMicros();
                if (!now.isBefore(lockedAssignment.deadline_at())) {
                    throw conflict("Assignment deadline has passed");
                }
                if (existing != null) {
                    requireStoredSubmission(assignmentId, learnerId, existing);
                    if (existing.graded_at() != null) {
                        throw conflict("Graded submission cannot be replaced");
                    }
                    if (!now.isAfter(existing.submitted_at())) {
                        throw new IllegalStateException("Server clock did not advance past previous submission");
                    }
                    validateStoredKey(existing.object_key());
                    previousKey[0] = existing.object_key();
                    createdFlag[0] = false;
                    assignments.replaceSubmissionFile(
                            existing.id(), assignmentId, learnerId, freshKey, part.originalFilename(), now);
                    flushIfManaged();
                    return new LearnerSubmissionView(part.originalFilename(), now, false, null, null, null);
                }

                createdFlag[0] = true;
                assignments.insertSubmission(new Submission(
                        UUID.randomUUID(),
                        assignmentId,
                        learnerId,
                        freshKey,
                        part.originalFilename(),
                        now,
                        null,
                        null,
                        null));
                flushIfManaged();
                return new LearnerSubmissionView(part.originalFilename(), now, false, null, null, null);
            });

            if (writeTx != null && !outcome.committed) {
                throw failure();
            }
            if (previousKey[0] != null) {
                cleanup(previousKey[0], "superseded", assignmentId);
            }
            return new SubmissionOutcome(view, createdFlag[0]);
        } catch (RuntimeException | IOException problem) {
            if (outcome.committed) {
                if (previousKey[0] != null) {
                    cleanup(previousKey[0], "superseded", assignmentId);
                }
                throw failure();
            }
            if (outcome.commitPhase) {
                LOG.error("Submission operation outcome unknown operation={} assignmentId={} correlationId={}",
                        previousKey[0] != null ? "replace-submission" : "create-submission",
                        assignmentId, MDC.get("correlationId"));
                throw failure();
            }
            if (storageAttempted) {
                cleanup(freshKey, "unreferenced", assignmentId);
            }
            if (problem instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw failure();
        }
    }

    /** Two-phase learner download with single concurrent-replacement re-read (ADR010 §11). */
    public SubmissionFile downloadOwnSubmission(UUID courseId, UUID assignmentId) {
        requireId(courseId);
        requireId(assignmentId);
        requireOutermost();
        UUID learnerId = requireSelfLearnerId();

        Submission row = readLearnerDownloadRow(courseId, assignmentId, learnerId);
        FileStorage.StoredObject opened;
        try {
            opened = storage.open(row.object_key());
        } catch (ObjectNotFoundException missing) {
            Submission current = readLearnerDownloadRow(courseId, assignmentId, learnerId);
            if (current.object_key().equals(row.object_key())) {
                throw failure();
            }
            LOG.info("Learner submission download concurrent replacement submissionId={}", current.id());
            row = current;
            opened = storage.open(row.object_key());
        }
        return toSubmissionFile(row, opened);
    }

    /** Owning Instructor paginated submissions ordered by {@code submitted_at ASC, id ASC} (ADR010 §12). */
    public PageResponse<SubmissionSummaryView> listSubmissionsForOwner(UUID courseId, UUID assignmentId,
                                                                     int page, int size) {
        requireId(courseId);
        requireId(assignmentId);
        PageResponse.validateRequest(page, size);

        return runRead(() -> {
            courseAccess.requireCourseOwnership(courseId);
            if (!assignments.existsInCourse(assignmentId, courseId)) {
                throw unavailable();
            }
            long total = assignments.countSubmissionsByAssignment(assignmentId);
            List<Submission> rows = (long) page * size >= total
                    ? List.of()
                    : assignments.findSubmissionPageByAssignment(assignmentId, page, size);
            Set<UUID> learnerIds = rows.stream().map(Submission::user_id).collect(Collectors.toSet());
            Map<UUID, String> names = learnerIds.isEmpty()
                    ? Map.of()
                    : readLearnerDisplayNames(courseId, learnerIds);

            List<SubmissionSummaryView> content = rows.stream()
                    .map(row -> {
                        requireStoredSubmission(assignmentId, row.user_id(), row);
                        return toSummaryView(row, names.get(row.user_id()));
                    })
                    .toList();
            return new PageResponse<>(content, page, size, total);
        });
    }

    /** Two-phase owner download with single concurrent-replacement re-read (ADR010 §11). */
    public SubmissionFile downloadSubmissionForOwner(UUID courseId, UUID assignmentId, UUID submissionId) {
        requireId(courseId);
        requireId(assignmentId);
        requireId(submissionId);
        requireOutermost();

        Submission row = readOwnerDownloadRow(courseId, assignmentId, submissionId);
        FileStorage.StoredObject opened;
        try {
            opened = storage.open(row.object_key());
        } catch (ObjectNotFoundException missing) {
            Submission current = readOwnerDownloadRow(courseId, assignmentId, submissionId);
            if (current.object_key().equals(row.object_key())) {
                throw failure();
            }
            LOG.info("Owner submission download concurrent replacement submissionId={}", current.id());
            row = current;
            opened = storage.open(row.object_key());
        }
        return toSubmissionFile(row, opened);
    }

    /**
     * Owning Instructor save/regrade with microsecond {@code submittedAt} precondition and
     * CT27 {@link AssignmentGradedEvent} publication inside the locked transaction (ADR010 §9, ADR012 §9.2).
     */
    public SubmissionSummaryView gradeSubmission(UUID courseId, UUID assignmentId, UUID submissionId,
                                                 GradeInput input) {
        requireId(courseId);
        requireId(assignmentId);
        requireId(submissionId);
        courseAccess.requireCourseOwnership(courseId);
        GradeInput valid = validateGradeInput(input);

        return runWrite(() -> {
            Assignment lockedAssignment = assignments.findInCourseForUpdate(assignmentId, courseId)
                    .orElseThrow(SubmissionService::unavailable);
            Submission lockedSubmission = assignments.findSubmissionInAssignmentForUpdate(submissionId, assignmentId)
                    .orElseThrow(SubmissionService::unavailable);
            courseAccess.requireCourseOwnership(courseId);
            requireStoredSubmission(assignmentId, lockedSubmission.user_id(), lockedSubmission);

            if (!lockedSubmission.submitted_at().equals(valid.submittedAt())) {
                throw conflict("Submission has changed since it was reviewed");
            }
            Instant gradedAt = currentMicros();
            if (gradedAt.isBefore(lockedSubmission.submitted_at())) {
                throw new IllegalStateException("Server clock regressed before submission timestamp");
            }

            boolean regrade = lockedSubmission.graded_at() != null;
            assignments.gradeSubmission(
                    lockedSubmission.id(), assignmentId, valid.gradePercentage(), valid.feedback(), gradedAt);
            flushIfManaged();

            eventPublisher.publishEvent(new AssignmentGradedEvent(
                    lockedSubmission.id(),
                    lockedAssignment.id(),
                    courseId,
                    lockedAssignment.lesson_id(),
                    lockedSubmission.user_id(),
                    lockedAssignment.title(),
                    PUBLISHED.equals(lockedAssignment.publication_status()),
                    regrade,
                    gradedAt
            ));

            String displayName = readLearnerDisplayNames(courseId, Set.of(lockedSubmission.user_id()))
                    .get(lockedSubmission.user_id());
            Submission updated = new Submission(
                    lockedSubmission.id(),
                    assignmentId,
                    lockedSubmission.user_id(),
                    lockedSubmission.object_key(),
                    lockedSubmission.original_filename(),
                    lockedSubmission.submitted_at(),
                    valid.gradePercentage(),
                    valid.feedback(),
                    gradedAt
            );
            return toSummaryView(updated, displayName);
        });
    }

    /**
     * Seam for CT01.learner-names via Course (ADR010 §3.1); returns empty map until Course/Auth
     * wires {@code EnrollmentService#learnerDisplayNames}, yielding {@code null} display names per OpenAPI.
     */
    protected Map<UUID, String> readLearnerDisplayNames(UUID courseId, Set<UUID> learnerIds) {
        return Map.of();
    }

    private Assignment admitLearnerAssignment(UUID courseId, UUID assignmentId) {
        courseAccess.requireLearnerCourseAccess(courseId);
        Assignment row = assignments.findInCourse(assignmentId, courseId).orElseThrow(SubmissionService::unavailable);
        requirePublishedVisibleAssignment(courseId, row);
        if (row.lesson_id() != null) {
            courseAccess.requireLearnerLessonAccess(courseId, row.lesson_id());
        }
        return row;
    }

    private Submission readLearnerDownloadRow(UUID courseId, UUID assignmentId, UUID learnerId) {
        return runRead(() -> {
            admitLearnerAssignment(courseId, assignmentId);
            Submission row = assignments.findSubmissionByAssignmentAndLearner(assignmentId, learnerId)
                    .orElseThrow(SubmissionService::unavailable);
            requireStoredSubmission(assignmentId, learnerId, row);
            validateStoredKey(row.object_key());
            validateStoredFilename(row.original_filename());
            return row;
        });
    }

    private Submission readOwnerDownloadRow(UUID courseId, UUID assignmentId, UUID submissionId) {
        return runRead(() -> {
            courseAccess.requireCourseOwnership(courseId);
            if (!assignments.existsInCourse(assignmentId, courseId)) {
                throw unavailable();
            }
            Submission row = assignments.findSubmissionInAssignment(submissionId, assignmentId)
                    .orElseThrow(SubmissionService::unavailable);
            requireStoredSubmission(assignmentId, row.user_id(), row);
            validateStoredKey(row.object_key());
            validateStoredFilename(row.original_filename());
            return row;
        });
    }

    private static SubmissionFile toSubmissionFile(Submission row, FileStorage.StoredObject opened) {
        try {
            String expectedMediaType = mediaTypeForFilename(row.original_filename());
            if (opened.contentLength() < 1 || !expectedMediaType.equals(opened.mediaType())) {
                throw failure();
            }
            return new SubmissionFile(row.original_filename(), expectedMediaType, opened.contentLength(), opened.content());
        } catch (RuntimeException problem) {
            try {
                opened.close();
            } catch (IOException close) {
                problem.addSuppressed(close);
            }
            throw problem;
        }
    }

    private static SubmissionSummaryView toSummaryView(Submission row, String learnerDisplayName) {
        boolean graded = row.graded_at() != null;
        return new SubmissionSummaryView(
                row.id(),
                row.assignment_id(),
                row.user_id(),
                learnerDisplayName,
                row.original_filename(),
                row.submitted_at(),
                graded,
                graded ? row.grade_percentage() : null,
                graded ? row.feedback() : null,
                row.graded_at()
        );
    }

    private static GradeInput validateGradeInput(GradeInput input) {
        if (input == null || input.gradePercentage() == null || input.feedback() == null || input.submittedAt() == null) {
            throw invalid("Grade, feedback and submittedAt are required");
        }
        BigDecimal grade = input.gradePercentage();
        if (grade.stripTrailingZeros().scale() > 2
                || grade.compareTo(ZERO_GRADE) < 0
                || grade.compareTo(HUNDRED_GRADE) > 0) {
            throw invalid("Grade must be between 0 and 100 with at most 2 decimal places");
        }
        String feedback = input.feedback();
        if (!isStorable(feedback) || feedback.trim().isEmpty()) {
            throw invalid("Feedback must contain non-whitespace characters");
        }
        Instant submittedAt = input.submittedAt();
        if (submittedAt.getNano() % 1_000 != 0) {
            throw invalid("submittedAt must have at most microsecond precision");
        }
        return new GradeInput(grade.setScale(2), feedback, submittedAt);
    }

    static String validateFilenameAndMediaType(String filename) {
        if (filename == null || !isStorable(filename) || filename.isBlank()
                || filename.getBytes(StandardCharsets.UTF_8).length > 255
                || filename.codePoints().anyMatch(c -> c < 32 || c == 127 || c == '/' || c == '\\')) {
            throw invalid("Invalid submission filename");
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return MEDIA_PDF;
        }
        if (lower.endsWith(".zip")) {
            return MEDIA_ZIP;
        }
        throw invalid("Unsupported submission file extension");
    }

    private static String mediaTypeForFilename(String filename) {
        try {
            return validateFilenameAndMediaType(filename);
        } catch (BusinessRuleException invalid) {
            throw failure();
        }
    }

    private static void validateStoredFilename(String filename) {
        mediaTypeForFilename(filename);
    }

    private static void validateLeadingBytes(BufferedInputStream stream, String mediaType) throws IOException {
        if (MEDIA_PDF.equals(mediaType)) {
            stream.mark(5);
            byte[] head = stream.readNBytes(5);
            if (!Arrays.equals(head, PDF_MAGIC)) {
                throw invalid("File content does not match PDF signature");
            }
            stream.reset();
            return;
        }
        if (MEDIA_ZIP.equals(mediaType)) {
            stream.mark(4);
            byte[] head = stream.readNBytes(4);
            if (!Arrays.equals(head, ZIP_LOCAL_MAGIC) && !Arrays.equals(head, ZIP_EMPTY_MAGIC)) {
                throw invalid("File content does not match ZIP signature");
            }
            stream.reset();
            return;
        }
        throw failure();
    }

    private static void requirePublishedVisibleAssignment(UUID courseId, Assignment row) {
        if (row == null || row.id() == null || !courseId.equals(row.course_id())
                || row.deadline_at() == null || row.deadline_at().getNano() % 1_000 != 0) {
            throw failure();
        }
        if (!PUBLISHED.equals(row.publication_status())) {
            throw unavailable();
        }
    }

    private static void requireStoredSubmission(UUID assignmentId, UUID expectedUserId, Submission row) {
        if (row == null || row.id() == null || !assignmentId.equals(row.assignment_id())
                || expectedUserId == null || !expectedUserId.equals(row.user_id())
                || row.submitted_at() == null || row.submitted_at().getNano() % 1_000 != 0
                || (row.graded_at() == null) != (row.grade_percentage() == null)
                || (row.graded_at() == null) != (row.feedback() == null)) {
            throw failure();
        }
    }

    private static void validateStoredKey(String key) {
        if (key == null
                || !key.matches("submissions/[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
            throw failure();
        }
    }

    private static boolean isStorable(String value) {
        return value.codePoints().noneMatch(cp -> cp == 0
                || (cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE));
    }

    private UUID requireSelfLearnerId() {
        var actor = identities.requireCurrentIdentity();
        if (actor == null || actor.userId() == null) {
            throw new IllegalStateException("Missing current identity");
        }
        return actor.userId();
    }

    private Instant currentMicros() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private <T> T runRead(Supplier<T> work) {
        return readTx == null ? work.get() : readTx.execute(status -> work.get());
    }

    private <T> T runWrite(Supplier<T> work) {
        return writeTx == null ? work.get() : writeTx.execute(status -> work.get());
    }

    private void flushIfManaged() {
        if (entityManager != null) {
            entityManager.flush();
        }
    }

    private void cleanup(String key, String operation, UUID rowId) {
        try {
            storage.delete(key);
        } catch (RuntimeException ignored) {
            LOG.warn("Submission storage cleanup failed operation={} rowId={} correlationId={}",
                    operation, rowId, MDC.get("correlationId"));
        }
    }

    private static void requireOutermost() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw failure();
        }
    }

    private static void requireId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Identifier must not be null");
        }
    }

    private static BusinessRuleException invalid(String reason) {
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, reason);
    }

    private static BusinessRuleException conflict(String reason) {
        return new BusinessRuleException(HttpStatus.CONFLICT, reason);
    }

    private static ResourceNotFoundException unavailable() {
        return new ResourceNotFoundException("Resource not available");
    }

    private static IllegalStateException failure() {
        return new IllegalStateException("Submission operation failed");
    }

    private static final class Outcome implements TransactionSynchronization {
        boolean commitPhase;
        boolean committed;

        @Override
        public void beforeCommit(boolean readOnly) {
            commitPhase = true;
        }

        @Override
        public void afterCompletion(int status) {
            committed = status == STATUS_COMMITTED;
        }
    }
}