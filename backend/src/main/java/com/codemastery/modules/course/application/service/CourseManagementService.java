package com.codemastery.modules.course.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedChapter;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedCourse;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedCourseSummary;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedLesson;
import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseContentRepository;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import com.codemastery.modules.course.domain.repository.CourseRepository.StoredAdaptiveConfiguration;
import com.codemastery.modules.course.domain.repository.CourseRepository.TransactionFacts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import com.codemastery.modules.course.application.event.ContentPublishedEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * CO14: the Course-owned configuration write guard and adaptive-settings storage handoff
 * (ADR005 §§5, 7.1 and 13.1–13.3; CT03-B1, CT06.enablement r2 with N1/R1).
 *
 * <p>Both guard entries authenticate the owning Instructor through CT03, lock the existing
 * Course row (the root shared with membership, progress and authoring writers), recheck
 * ownership, then run the callback in one READ COMMITTED read-write transaction that holds
 * the lock until commit or rollback. Transactions are programmatic, so same-bean calls get
 * the same semantics as external calls. A compatible outer transaction is joined only when
 * its actual isolation is READ COMMITTED, it is not read-only, and it has neither written
 * nor locked rows nor queued persistence changes before the root; a nested call for another
 * Course is rejected. The held root is recorded transaction-locally in PostgreSQL, so a
 * savepoint rollback that releases the row lock also releases the marker, and a REQUIRES_NEW
 * entry behind a suspended holder on the same thread fails instead of waiting for itself.
 * The marker is rechecked when the callback returns and before the transaction commits, so work
 * done after a savepoint rollback released the root can never commit.
 * Entities loaded before entry are detached after the lock: re-read them inside the callback.
 * Contract violations are internal errors (500). Any failure rolls the
 * whole unit back; an outer caller must still commit before reporting success. There is no
 * REQUIRES_NEW, retry, sleep, SKIP LOCKED or timeout selection here.
 *
 * <p>Course stores and returns its own two fields only; Adaptive owns policy validation,
 * readiness, ETags and HTTP conditions.
 */
@Service
public class CourseManagementService {

    private static final String READ_COMMITTED = "read committed";
    private static final String LOCK_NOT_AVAILABLE = "55P03";
    /** Roots taken by transactions on this thread, including suspended ones, until they complete. */
    private static final ThreadLocal<List<UUID>> THREAD_ROOTS = new ThreadLocal<>();

    private final CourseRepository courses;
    private final CourseAccessService access;
    private final UserAccountService identities;
    private final CourseContentRepository content;
    private final EnrollmentRepository enrollments;
    private final ApplicationEventPublisher events;
    private final Clock publicationClock;
    @PersistenceContext private EntityManager publicationPersistence;
    private final TransactionTemplate writeUnit;
    private final TransactionTemplate snapshotRead;

    @Autowired
    public CourseManagementService(CourseRepository courses, CourseAccessService access, UserAccountService identities,
                                   CourseContentRepository content, EnrollmentRepository enrollments,
                                   PlatformTransactionManager transactionManager, ApplicationEventPublisher events) {
        this(courses, access, identities, content, enrollments, transactionManager, events, Clock.systemUTC());
    }

    // Operation-local clock seam: CT39 deliberately selects no global Clock bean.
    CourseManagementService(CourseRepository courses, CourseAccessService access, UserAccountService identities,
                            CourseContentRepository content, EnrollmentRepository enrollments,
                            PlatformTransactionManager transactionManager, ApplicationEventPublisher events, Clock clock) {
        this.events = Objects.requireNonNull(events);
        this.publicationClock = Objects.requireNonNull(clock);
        this.courses = Objects.requireNonNull(courses);
        this.access = Objects.requireNonNull(access);
        this.identities = Objects.requireNonNull(identities);
        this.content = Objects.requireNonNull(content);
        this.enrollments = Objects.requireNonNull(enrollments);
        Objects.requireNonNull(transactionManager);
        this.writeUnit = new TransactionTemplate(transactionManager);
        writeUnit.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        writeUnit.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        writeUnit.setReadOnly(false);
        this.snapshotRead = new TransactionTemplate(transactionManager);
        snapshotRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        snapshotRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshotRead.setReadOnly(true);
    }

    /** ADR005 §5: waits for the Course row lock with SELECT ... FOR UPDATE. */
    public <T> T withCourseConfigurationWrite(UUID courseId, Supplier<T> work) {
        return guarded(courseId, work, false);
    }

    /**
     * ADR005 §13.2, settings only: the same guard acquired with FOR UPDATE NOWAIT. Only the
     * guard's own PostgreSQL row-lock-not-available outcome (SQLSTATE 55P03) becomes
     * {@link ConfigurationBusy}, after the unit is rolled back (or marked rollback-only when an
     * outer transaction owns completion). Every other SQL, timeout or provider failure propagates.
     */
    public <T> T tryWithCourseConfigurationWrite(UUID courseId, Supplier<T> work) {
        return guarded(courseId, work, true);
    }

    /**
     * ADR005 §7.1: the owner's immutable snapshot of the stored fields. A legacy Course may
     * return null settings only while disabled. No default or policy is supplied.
     */
    public AdaptiveConfiguration readAdaptiveConfiguration(UUID courseId) {
        requireId(courseId);
        return snapshotRead.execute(status -> {
            access.requireCourseOwnership(courseId);
            StoredAdaptiveConfiguration stored = courses.findAdaptiveConfiguration(courseId)
                    .orElseThrow(CourseManagementService::unavailable);
            if (!courseId.equals(stored.courseId())
                    || stored.adaptiveEnabled() && stored.adaptiveSettings() == null) {
                throw new IllegalStateException("Invalid stored adaptive configuration");
            }
            return new AdaptiveConfiguration(courseId, stored.adaptiveEnabled(), stored.adaptiveSettings());
        });
    }

    /**
     * ADR005 §13.1: persists both fields only inside the held guard of the same Course, and
     * only when the freshly stored pair structurally equals {@code expected}. A mismatch is a
     * safe internal failure that rolls the unit back; it never persists over newer state.
     */
    public void writeAdaptiveConfiguration(UUID courseId, AdaptiveConfiguration expected,
                                           boolean adaptiveEnabled, Map<String, Object> adaptiveSettings) {
        requireId(courseId);
        if (expected == null || !courseId.equals(expected.courseId())) {
            throw new IllegalArgumentException("The expected configuration must belong to the Course");
        }
        if (adaptiveEnabled && adaptiveSettings == null) {
            throw new IllegalArgumentException("Enabled adaptive mode requires stored settings");
        }
        Map<String, Object> settings = adaptiveSettings == null ? null : Course.immutableJsonObject(adaptiveSettings);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Course configuration writes require the held guard of the same Course");
        }
        // Checked inside the participating unit, so a missing or released guard dooms the caller's transaction.
        writeUnit.executeWithoutResult(status -> {
            if (requireCompatibleWriteUnit(courseId) == null) {
                throw new IllegalStateException("Course configuration writes require the held guard of the same Course");
            }
            access.requireCourseOwnership(courseId);
            acquire(courseId, false);
            if (!courses.replaceAdaptiveConfiguration(courseId, expected.adaptiveEnabled(),
                    expected.adaptiveSettings(), adaptiveEnabled, settings)) {
                throw new IllegalStateException("Stored adaptive configuration differs from the expected state");
            }
            courses.detachManagedCourse(courseId);
        });
    }

    // CT21.authoring (ADR006 §5), Course metadata and publication. Existing-Course mutations run
    // inside withCourseConfigurationWrite, so admission precedes validation and every effect.

    public void requireActiveInstructor() {
        actor(IdentityRole.INSTRUCTOR);
    }

    public PageResponse<OwnedCourseSummary> listOwnedCourses(int page, int size) {
        UUID owner = actor(IdentityRole.INSTRUCTOR).userId();
        PageResponse.validateRequest(page, size);
        return snapshotRead.execute(status -> PageResponse.from(
                courses.findByInstructorId(owner, PageRequest.of(page, size)).map(OwnedCourseSummary::of)));
    }

    /** The trusted Instructor owns a new hidden Course with adaptive mode disabled and no settings. */
    public OwnedCourse createOwnedCourse(String title, String description, String prerequisiteDescription) {
        UUID owner = actor(IdentityRole.INSTRUCTOR).userId();
        Course course = new Course(UUID.randomUUID(), owner, requiredText(title), requiredText(description),
                optionalText(prerequisiteDescription), PublishStatus.HIDDEN, false, null);
        writeUnit.executeWithoutResult(status -> courses.insert(course));
        return new OwnedCourse(course.id(), course.title(), course.description(), course.publicationStatus().getStatus(),
                course.prerequisiteDescription(), List.of());
    }

    public OwnedCourse getOwnedCourse(UUID courseId) {
        requireId(courseId);
        return snapshotRead.execute(status -> {
            access.requireCourseOwnership(courseId);
            return ownedCourse(courseId);
        });
    }

    /** Full metadata replacement; the last serialized successful commit wins. */
    public OwnedCourse updateOwnedCourse(UUID courseId, String title, String description, String prerequisiteDescription) {
        return withCourseConfigurationWrite(courseId, () -> {
            courses.updateMetadata(courseId, requiredText(title), requiredText(description),
                    optionalText(prerequisiteDescription));
            return ownedCourse(courseId);
        });
    }

    /**
     * Idempotent and without cascade; a hidden Course still conceals its children. A real change
     * reconciles joined completion in the same unit (ADR006 §5 "Course/Lesson publication/set
     * changes", OpenAPI setOwnedCoursePublication "completion reconciliation"; ADR006 §3).
     */
    public OwnedCourse setOwnedCoursePublication(UUID courseId, PublishStatus publicationStatus) {
        Objects.requireNonNull(publicationStatus, "publicationStatus");
        return withCourseConfigurationWrite(courseId, () -> {
            Course course = courses.findById(courseId).orElseThrow(CourseManagementService::unavailable);
            if (course.publicationStatus() != publicationStatus) {
                courses.updatePublicationStatus(courseId, publicationStatus);
                enrollments.reconcileCompletion(courseId);
            }
            return ownedCourse(courseId);
        });
    }

    /**
     * CO12 / CT26.event (ADR012 §9.1): publishes {@link com.codemastery.modules.course.application.event.ContentPublishedEvent}
     * from inside the held guard of {@code courseId}, after an actual {@code hidden → published} Lesson change and its
     * completion reconciliation are flushed, only while the Course is published; {@code publishedAt} comes from the
     * operation's injected UTC {@code Clock} (microseconds, not persisted) and no event is published when no learner is
     * joined. Course publication, material, Chapter, reorder, content edits and Administrator hiding never call it.
     */
    void publishLessonPublished(UUID courseId, UUID lessonId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Publication requires the Course-root transaction");
        }
        requireRootStillHeld(courseId);
        Course course = courses.findById(courseId).orElseThrow(CourseManagementService::unavailable);
        if (course.publicationStatus() != PublishStatus.PUBLISHED) return;
        publicationPersistence.flush();
        List<UUID> recipients = enrollments.joinedLearnerIds(List.of(courseId))
                .getOrDefault(courseId, java.util.Set.of()).stream()
                .distinct().sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        if (!recipients.isEmpty()) {
            events.publishEvent(new ContentPublishedEvent(courseId, lessonId,
                    publicationClock.instant().truncatedTo(ChronoUnit.MICROS), recipients));
        }
    }

    /** The owner view: every Chapter, Lesson and material in canonical order, hidden ones included. */
    OwnedCourse ownedCourse(UUID courseId) {
        Course course = courses.findById(courseId).orElseThrow(CourseManagementService::unavailable);
        Map<UUID, List<LearningMaterial>> materials = new LinkedHashMap<>();
        content.findMaterialsByCourseId(courseId).forEach(material ->
                materials.computeIfAbsent(material.lessonId(), id -> new ArrayList<>()).add(material));
        Map<UUID, List<OwnedLesson>> lessons = new LinkedHashMap<>();
        for (Lesson lesson : content.findLessonsByCourseId(courseId)) {
            lessons.computeIfAbsent(lesson.chapterId(), id -> new ArrayList<>())
                    .add(OwnedLesson.of(lesson, materials.getOrDefault(lesson.id(), List.of())));
        }
        List<OwnedChapter> chapters = content.findChaptersByCourseId(courseId).stream()
                .map(chapter -> new OwnedChapter(chapter.id(), chapter.title(), chapter.position(),
                        lessons.getOrDefault(chapter.id(), List.of())))
                .toList();
        return new OwnedCourse(course.id(), course.title(), course.description(), course.publicationStatus().getStatus(),
                course.prerequisiteDescription(), chapters);
    }

    // CT12.course (ADR012 §10.2): Administrator list and hide only; the sole caller is Admin
    // AdminCourseService (CT32 also uses the Lesson method). Hiding is a root-lock participant,
    // not the Instructor-only guard, and has exactly the effects of CT21's hidden change.

    public PageResponse<AdminCourseView> listCoursesForAdministration(String query, String publicationStatus,
                                                                      int page, int size) {
        actor(IdentityRole.ADMINISTRATOR);
        PageResponse.validateRequest(page, size);
        PublishStatus status = publicationStatus == null ? null : switch (publicationStatus) {
            case "hidden" -> PublishStatus.HIDDEN;
            case "published" -> PublishStatus.PUBLISHED;
            default -> throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid publication status");
        };
        String title = query == null ? "" : query;
        // NUL or an unpaired surrogate cannot be sent faithfully to PostgreSQL: malformed input, not a 500 or a '?'.
        if (!isStorable(title)) throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid query");
        return snapshotRead.execute(read -> PageResponse.from(
                courses.findForAdministration(title, status, PageRequest.of(page, size)).map(AdminCourseView::of)));
    }

    /** Idempotent; a real change reconciles joined completion exactly as the Instructor hidden change does. */
    public CourseChange hideCourseAsAdministrator(UUID courseId) {
        requireId(courseId);
        actor(IdentityRole.ADMINISTRATOR);
        return writeUnit.execute(status -> {
            participateInRoot(courseId);
            Course course = courses.findById(courseId).orElseThrow(CourseManagementService::unavailable);
            boolean changed = course.publicationStatus() != PublishStatus.HIDDEN;
            if (changed) {
                courses.updatePublicationStatus(courseId, PublishStatus.HIDDEN);
                enrollments.reconcileCompletion(courseId);
            }
            requireRootStillHeld(courseId);
            return new CourseChange(new AdminCourseView(courseId, course.title(), PublishStatus.HIDDEN.getStatus(),
                    course.instructorId()), changed);
        });
    }

    /** Hides one Lesson and reconciles joined completion for its Course in the same unit (CT22). */
    public LessonChange hideLessonAsAdministrator(UUID lessonId) {
        requireId(lessonId);
        UUID administrator = actor(IdentityRole.ADMINISTRATOR).userId();
        return writeUnit.execute(status -> {
            // Plain read of the immutable ancestry only (no reparenting exists); state is re-read under the root.
            UUID courseId = courses.findLessonAccessSnapshot(administrator, lessonId)
                    .map(CourseRepository.AccessSnapshot::courseId).orElseThrow(CourseManagementService::unavailable);
            participateInRoot(courseId);
            Lesson lesson = content.findLesson(courseId, lessonId).orElseThrow(CourseManagementService::unavailable);
            boolean changed = lesson.publicationStatus() != PublishStatus.HIDDEN;
            if (changed) {
                content.updateLessonPublicationStatus(lesson.chapterId(), lessonId, PublishStatus.HIDDEN);
                enrollments.reconcileCompletion(courseId);
            }
            requireRootStillHeld(courseId);
            return new LessonChange(courseId, lessonId, PublishStatus.HIDDEN.getStatus(), changed);
        });
    }

    /**
     * ADR005 §13.3 root participant (ADR012 §10.2): a compatible READ COMMITTED read-write unit with no
     * earlier write or lock, the root first, state re-read after it. Like the guard, the held root is
     * recorded for the physical transaction and the thread, so a same-Course participant or guard in the
     * same unit joins it, another Course or a REQUIRES_NEW unit behind a suspended holder fails instead
     * of waiting for itself, and a savepoint rollback that released the root can never commit. Course
     * reads here are direct statements, so the caller's managed persistence state is left untouched.
     */
    private void participateInRoot(UUID courseId) {
        if (requireCompatibleWriteUnit(courseId) != null) return;
        acquire(courseId, false);
        claimRoot(courseId);
        courses.markCourseRootHeld(courseId);
    }

    private CurrentIdentity actor(IdentityRole role) {
        CurrentIdentity actor = identities.requireCurrentIdentity();
        if (actor.role() != role) throw new AccessDeniedException("Access denied");
        return actor;
    }

    /**
     * CT21 descriptive text: trimmed and nonblank; no length limit is added. Text PostgreSQL cannot
     * store faithfully is malformed input (400) rather than a driver failure or silent change.
     */
    static String requiredText(String value) {
        if (value == null) throw blank();
        requireStorable(value);
        String trimmed = trim(value);
        if (trimmed.isEmpty()) throw blank();
        return trimmed;
    }

    /** Optional descriptive text: null or trimmed nonblank. */
    static String optionalText(String value) {
        return value == null ? null : requiredText(value);
    }

    /** Markdown is null or nonblank and stored verbatim, preserving indentation. */
    static String markdown(String value) {
        if (value == null) return null;
        requireStorable(value);
        if (trim(value).isEmpty()) throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Content must not be blank");
        return value;
    }

    /**
     * Blank means every code point is space under one of the definitions that judge this text: Java
     * whitespace, Unicode space separators and U+FEFF (OpenAPI's ECMA-262 {@code \S} pattern) and
     * U+0085, which the PostgreSQL checks {@code ~ '[^[:space:]]'} treat as space under the image's
     * musl {@code en_US.utf8} locale. Accepted text therefore satisfies both the contract and the database.
     */
    static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
                || codePoint == 0x0085 || codePoint == 0xFEFF;
    }

    private static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.codePointAt(start))) start += Character.charCount(value.codePointAt(start));
        while (end > start && isSpace(value.codePointBefore(end))) end -= Character.charCount(value.codePointBefore(end));
        return value.substring(start, end);
    }

    /**
     * PostgreSQL text cannot hold NUL, and the driver silently replaces an unpaired UTF-16 surrogate,
     * so the stored value would differ from the accepted one. Both are rejected before any write.
     */
    static boolean isStorable(String value) {
        return value.codePoints().noneMatch(codePoint -> codePoint == 0
                || codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE);
    }

    private static void requireStorable(String value) {
        if (!isStorable(value)) throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Text must be storable Unicode");
    }

    private static BusinessRuleException blank() {
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, "Text must not be blank");
    }

    private <T> T guarded(UUID courseId, Supplier<T> work, boolean failFast) {
        requireId(courseId);
        if (work == null) {
            throw new IllegalArgumentException("Configuration work must not be null");
        }
        try {
            return writeUnit.execute(status -> enter(courseId, work, failFast));
        } catch (RootLockUnavailable unavailable) {
            throw new ConfigurationBusy();
        }
    }

    private <T> T enter(UUID courseId, Supplier<T> work, boolean failFast) {
        UUID held = requireCompatibleWriteUnit(courseId);
        access.requireCourseOwnership(courseId);
        acquire(courseId, failFast);
        // READ COMMITTED: this fresh statement observes ownership/existence committed before the lock.
        access.requireCourseOwnership(courseId);
        if (held == null) {
            claimRoot(courseId);
            courses.markCourseRootHeld(courseId);
            courses.discardCleanPersistenceState();
        }
        T result = work.get();
        // A savepoint rollback inside the callback may have released the root; never report success then.
        requireRootStillHeld(courseId);
        return result;
    }

    /** Fails, dooming the unit, unless PostgreSQL still records this transaction's root. */
    private void requireRootStillHeld(UUID courseId) {
        TransactionFacts facts = courses.currentTransactionFacts();
        if (facts == null || !courseId.equals(facts.heldCourseRoot())) {
            throw new IllegalStateException("The Course root was released before the unit completed");
        }
    }

    /**
     * Returns the Course root this physical transaction currently holds, or null on first entry.
     * The marker lives in PostgreSQL, so a savepoint rollback that released the row lock also
     * clears it; re-entry is then a first entry and fails on the transaction's earlier writes.
     */
    private UUID requireCompatibleWriteUnit(UUID courseId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Course configuration writes require a synchronized read-write transaction");
        }
        TransactionFacts facts = courses.currentTransactionFacts();
        if (facts == null || facts.readOnly() || !READ_COMMITTED.equals(facts.isolation())) {
            throw new IllegalStateException("Course configuration writes require a READ COMMITTED read-write transaction");
        }
        UUID held = facts.heldCourseRoot();
        if (held != null) {
            if (!held.equals(courseId)) {
                throw new IllegalStateException("A Course configuration write cannot nest another Course");
            }
            return held;
        }
        if (THREAD_ROOTS.get() != null) {
            // A suspended (REQUIRES_NEW) or savepoint-released holder on this thread: waiting would
            // deadlock on our own lock or continue under a released guard, so fail instead.
            throw new IllegalStateException("This thread's transaction no longer holds, or cannot take, the Course root");
        }
        if (facts.writeIdentityAssigned() || courses.hasPendingPersistenceChanges()) {
            throw new IllegalStateException("Writes or row locks precede the Course root lock");
        }
        return null;
    }

    private void acquire(UUID courseId, boolean failFast) {
        boolean found;
        if (failFast) {
            try {
                found = courses.lockCourseRootNoWait(courseId);
            } catch (RuntimeException failure) {
                if (isLockNotAvailable(failure)) {
                    throw new RootLockUnavailable();
                }
                throw failure;
            }
        } else {
            found = courses.lockCourseRoot(courseId);
        }
        if (!found) {
            throw unavailable();
        }
    }

    private static boolean isLockNotAvailable(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 32; depth++, cause = cause.getCause()) {
            if (cause instanceof SQLException sql && LOCK_NOT_AVAILABLE.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records the root for this thread until the acquiring physical transaction completes, and
     * rechecks it before that transaction commits (a savepoint rollback after the callback returned).
     */
    private void claimRoot(UUID courseId) {
        TransactionSynchronizationManager.registerSynchronization(new HeldRoot(courseId));
        List<UUID> roots = THREAD_ROOTS.get();
        if (roots == null) {
            roots = new ArrayList<>(1);
            THREAD_ROOTS.set(roots);
        }
        roots.add(courseId);
    }

    private static void requireId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Identifier must not be null");
        }
    }

    private static ResourceNotFoundException unavailable() {
        return new ResourceNotFoundException("Resource not available");
    }

    /**
     * Deeply immutable copy of the two Course-owned fields; never a Course entity. Enabled
     * intent always carries a settings object; null settings are legal only while disabled.
     */
    public record AdaptiveConfiguration(UUID courseId, boolean adaptiveEnabled, Map<String, Object> adaptiveSettings) {
        public AdaptiveConfiguration {
            if (courseId == null) {
                throw new IllegalArgumentException("Identifier must not be null");
            }
            if (adaptiveEnabled && adaptiveSettings == null) {
                throw new IllegalArgumentException("Enabled adaptive mode requires stored settings");
            }
            adaptiveSettings = adaptiveSettings == null ? null : Course.immutableJsonObject(adaptiveSettings);
        }
    }

    public record AdminCourseView(UUID courseId, String title, String publicationStatus, UUID instructorId) {
        static AdminCourseView of(Course course) {
            return new AdminCourseView(course.id(), course.title(), course.publicationStatus().getStatus(),
                    course.instructorId());
        }
    }

    public record CourseChange(AdminCourseView course, boolean changed) {}

    public record LessonChange(UUID courseId, UUID lessonId, String publicationStatus, boolean changed) {}

    public static final class ConfigurationBusy extends RuntimeException {
        public ConfigurationBusy() {
            super("Course configuration is busy", null, false, false);
        }
    }

    /**
     * Bound to the physical transaction that took the root. beforeCommit runs before the
     * commit, so a failure there rolls the transaction back; afterCompletion always releases
     * the thread record.
     */
    private final class HeldRoot implements TransactionSynchronization {
        private final UUID courseId;

        private HeldRoot(UUID courseId) {
            this.courseId = courseId;
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            requireRootStillHeld(courseId);
        }

        @Override
        public void afterCompletion(int status) {
            List<UUID> roots = THREAD_ROOTS.get();
            if (roots != null) {
                roots.remove(courseId);
                if (roots.isEmpty()) {
                    THREAD_ROOTS.remove();
                }
            }
        }
    }

    /** Internal signal for the guard's own NOWAIT failure; never escapes this class. */
    private static final class RootLockUnavailable extends RuntimeException {
        private RootLockUnavailable() {
            super(null, null, false, false);
        }
    }
}
