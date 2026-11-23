package com.codemastery.modules.course.domain.repository;

import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.PublishStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Course-internal persistence for the Course root: published catalogue reads (CO03.catalogue),
 * CT03 access facts (CO03.access), owner-scoped authoring rows (CO03.content, CT21.authoring)
 * and the ADR005 Course-root guard and stored adaptive configuration (CO14).
 * Never exported through the Course application boundary. A lost row is an internal failure,
 * surfaced by the repository's exception translation as InvalidDataAccessApiUsageException.
 * Writes require the caller's transaction and reject pending JPA changes (flush them first);
 * any write failure marks that transaction rollback-only.
 */
public interface CourseRepository {

    /** Published course detail; hidden or missing returns empty. */
    Optional<Course> findPublishedById(UUID id);

    /**
     * Published courses, filtered before paging/counting and ordered by PostgreSQL
     * title COLLATE "C" ASC, id ASC. Caller sorting cannot change this order.
     */
    Page<Course> findPublishedCourses(Pageable pageable);

    Page<Course> searchPublishedCourses(Pageable pageable, String query, String prerequisites);

    /** One Course-owned query snapshot; optional descendants stay scoped to this Course. */
    Optional<AccessSnapshot> findCourseAccessSnapshot(
            UUID actorId, UUID courseId, UUID lessonId, UUID materialId);

    /** Privately resolves Lesson ancestry and admission facts in one query snapshot. */
    Optional<AccessSnapshot> findLessonAccessSnapshot(UUID actorId, UUID lessonId);

    /** Internal admission facts only; never exported through the Course application boundary. */
    record AccessSnapshot(
            UUID courseId, UUID instructorId, PublishStatus courseStatus,
            UUID lessonId, PublishStatus lessonStatus,
            UUID materialId, PublishStatus materialStatus, EnrollmentStatus enrollmentStatus) {}

    // CT21.authoring (ADR006 §5). Callers enforce CT03 ownership and, for existing Courses,
    // hold the ADR005 Course-root guard; these operations choose no policy or default.

    /** One Course of any publication status, read by a fresh statement; empty when absent. */
    Optional<Course> findById(UUID courseId);

    /** Courses of one Instructor (hidden and published), ordered by title COLLATE "C" ASC, then id ASC. */
    Page<Course> findByInstructorId(UUID instructorId, Pageable pageable);

    /**
     * CT12.course: every Course (hidden and published) whose title contains {@code query}
     * case-insensitively, optionally of one publication status, ordered by title COLLATE "C", then id.
     */
    Page<Course> findForAdministration(String query, PublishStatus publicationStatus, Pageable pageable);

    /** Inserts exactly the supplied Course row, including its publication and adaptive fields. */
    void insert(Course course);

    /** Full metadata replacement; the instructor, publication and adaptive fields are untouched. */
    void updateMetadata(UUID courseId, String title, String description, String prerequisiteDescription);

    void updatePublicationStatus(UUID courseId, PublishStatus publicationStatus);

    // ADR005 §§5, 7.1, 13 (CO14): the Course row is the serialization root shared with
    // membership, progress and authoring writers. Callers run inside a write transaction.

    /** Locks the Course root with SELECT ... FOR UPDATE (any publication status); false when absent. */
    boolean lockCourseRoot(UUID courseId);

    /**
     * Same row with SELECT ... FOR UPDATE NOWAIT. A row lock held by another transaction
     * surfaces as the provider's lock-not-available failure (PostgreSQL SQLSTATE 55P03).
     */
    boolean lockCourseRootNoWait(UUID courseId);

    /** The two stored adaptive fields, read by a fresh statement; settings are deeply immutable. */
    Optional<StoredAdaptiveConfiguration> findAdaptiveConfiguration(UUID courseId);

    /**
     * Replaces both adaptive fields only when the stored pair structurally equals the expected
     * pair (jsonb equality, not text formatting). Returns false, changing nothing, otherwise.
     */
    boolean replaceAdaptiveConfiguration(UUID courseId,
            boolean expectedEnabled, Map<String, Object> expectedSettings,
            boolean adaptiveEnabled, Map<String, Object> adaptiveSettings);

    /** Facts about the caller's current database transaction, read in one statement. */
    TransactionFacts currentTransactionFacts();

    /**
     * Records, transaction-locally in PostgreSQL, that this transaction holds the Course root.
     * The marker is undone by exactly what releases the row lock: commit, rollback, or a
     * rollback to a savepoint taken before the lock.
     */
    void markCourseRootHeld(UUID courseId);

    /** True when the bound persistence context would execute DML if it were flushed now. */
    boolean hasPendingPersistenceChanges();

    /**
     * Detaches every managed entity of a persistence context that has no pending change, so
     * later reads load committed state instead of entities read before the Course root lock.
     */
    void discardCleanPersistenceState();

    /** Detaches a managed copy of the Course row after a direct write; no-op when none is managed. */
    void detachManagedCourse(UUID courseId);

    record StoredAdaptiveConfiguration(UUID courseId, boolean adaptiveEnabled, Map<String, Object> adaptiveSettings) {}

    /**
     * {@code isolation} is PostgreSQL's transaction_isolation text; {@code writeIdentityAssigned}
     * is true once the transaction has written or row-locked anything; {@code heldCourseRoot} is
     * the Course recorded by {@link #markCourseRootHeld}, or null when none is currently held.
     */
    record TransactionFacts(String isolation, boolean readOnly, boolean writeIdentityAssigned, UUID heldCourseRoot) {}
}
