package com.codemastery.modules.course.application.service;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.domain.repository.CourseRepository.StoredAdaptiveConfiguration;
import com.codemastery.modules.course.domain.repository.CourseRepository.TransactionFacts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.ArrayList;
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
    private final TransactionTemplate writeUnit;
    private final TransactionTemplate snapshotRead;

    public CourseManagementService(CourseRepository courses, CourseAccessService access,
                                   PlatformTransactionManager transactionManager) {
        this.courses = Objects.requireNonNull(courses);
        this.access = Objects.requireNonNull(access);
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
            claimThreadRoot(courseId);
            courses.markCourseRootHeld(courseId);
            courses.discardCleanPersistenceState();
        }
        return work.get();
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

    /** Records the root for this thread until the acquiring physical transaction completes. */
    private static void claimThreadRoot(UUID courseId) {
        TransactionSynchronizationManager.registerSynchronization(new ThreadRootRelease(courseId));
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

    public static final class ConfigurationBusy extends RuntimeException {
        public ConfigurationBusy() {
            super("Course configuration is busy", null, false, false);
        }
    }

    /** Removes this transaction's root from the thread record when the transaction completes. */
    private static final class ThreadRootRelease implements TransactionSynchronization {
        private final UUID courseId;

        private ThreadRootRelease(UUID courseId) {
            this.courseId = courseId;
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
