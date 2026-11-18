package com.codemastery.modules.course.domain.repository;

import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Course-internal CT22.membership/progress/resume persistence; callers own the transaction. */
public interface EnrollmentRepository {
    // CT13.reads (ADR012 §13.1/§13.2): set-based reads over Course-owned tables only.

    /** One row per Course (hidden and published) of the requested page, ordered by title COLLATE "C", then id. */
    Page<EnrollmentMetricsRow> enrollmentMetrics(Pageable page);

    /** The same figures for one Course of any publication status; empty when it does not exist. */
    Optional<EnrollmentMetricsRow> enrollmentMetrics(UUID courseId);

    /** Learners whose Enrollment is active or completed, per requested Course, in one query. */
    java.util.Map<UUID, java.util.Set<UUID>> joinedLearnerIds(java.util.Collection<UUID> courseIds);

    /**
     * Stored status counts plus completedLearners: joined learners for whom CT22.progress isComplete
     * would hold (R = currently published Lessons; a zero-Lesson Course completes nobody).
     */
    record EnrollmentMetricsRow(UUID courseId, String title, String publicationStatus, long activeEnrollments,
            long completedEnrollments, long leftEnrollments, long completedLearners) {}

    boolean lockPublishedCourse(UUID courseId);
    Optional<Enrollment> find(UUID userId, UUID courseId);
    void insert(Enrollment enrollment);
    void updateStatus(UUID enrollmentId, EnrollmentStatus status);
    Page<MembershipCourse> list(UUID userId, String status, Pageable page);

    record MembershipCourse(UUID id, String title, String description, UUID instructorId, EnrollmentStatus status) {}

    // CT22.progress / CT22.resume (ADR006 §§3, 4, 7.2)

    /** Locks the Course root row (any publication status); false when the Course does not exist. */
    boolean lockCourse(UUID courseId);

    /** Locks the learner's Enrollment row after the Course root; empty when absent. */
    Optional<Enrollment> lockEnrollment(UUID userId, UUID courseId);

    Optional<String> courseTitle(UUID courseId);

    /** Currently published Lessons of the Course in canonical order; absent progress rows are false/false. */
    List<LessonFlags> publishedLessonFlags(UUID courseId, UUID enrollmentId);

    /** Idempotently sets completed=true for the pair, preserving any mastery_skipped flag. */
    void markCompleted(UUID enrollmentId, UUID lessonId);

    /** True only when the Lesson is currently published and belongs to the Course. */
    boolean isPublishedCourseLesson(UUID courseId, UUID lessonId);

    /** Replaces the pointer and records one PostgreSQL clock_timestamp() in the same statement. */
    void recordVisit(UUID enrollmentId, UUID lessonId);

    /** One summary per ACTIVE Enrollment of a published Course, title COLLATE "C" then id. */
    Page<ProgressSummary> activeProgress(UUID userId, Pageable page);

    /** Latest valid recorded visit: ACTIVE, published Course, published same-Course Lesson, non-null time. */
    Optional<VisitPosition> latestVisit(UUID userId);

    record LessonFlags(UUID lessonId, boolean completed, boolean masterySkipped) {}
    record ProgressSummary(UUID courseId, String title, long totalLessons, long completedLessons, UUID nextLessonId) {}
    record VisitPosition(UUID courseId, UUID lessonId, Instant lastVisitedAt) {}
}
