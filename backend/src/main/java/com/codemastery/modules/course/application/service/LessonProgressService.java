package com.codemastery.modules.course.application.service;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository.LessonFlags;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CT22.progress and CT22.resume (ADR006 §§3, 4, 7.2): Course-owned manual completion,
 * progress calculation and visit/resume for the current self Learner.
 *
 * <p>Writes lock the Course root first, recheck CT03 Lesson access and membership, then
 * lock the Enrollment and progress rows, at READ COMMITTED with every lock held until
 * commit. This is private Course persistence, never the Instructor-only configuration
 * guard. Reads are REPEATABLE READ and never change completion, membership or resume.
 * Manual completion never consults or changes mastery and never calls Adaptive.
 */
@Service
public class LessonProgressService {
    private final EnrollmentRepository enrollments;
    private final UserAccountService identities;
    private final CourseAccessService access;

    public LessonProgressService(EnrollmentRepository enrollments, UserAccountService identities, CourseAccessService access) {
        this.enrollments = Objects.requireNonNull(enrollments);
        this.identities = Objects.requireNonNull(identities);
        this.access = Objects.requireNonNull(access);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CourseProgress progress(UUID courseId) {
        requireId(courseId);
        var actor = learner();
        access.requireLearnerCourseAccess(courseId);
        var enrollment = joined(enrollments.find(actor.userId(), courseId).orElse(null));
        return assemble(courseId, enrollment.status(), enrollments.publishedLessonFlags(courseId, enrollment.id()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CourseProgress complete(UUID courseId, UUID lessonId) {
        requireId(courseId);
        requireId(lessonId);
        var actor = learner();
        if (!enrollments.lockCourse(courseId)) throw unavailable();
        access.requireLearnerLessonAccess(courseId, lessonId);
        var enrollment = joined(enrollments.lockEnrollment(actor.userId(), courseId).orElse(null));
        enrollments.markCompleted(enrollment.id(), lessonId);
        var lessons = enrollments.publishedLessonFlags(courseId, enrollment.id());
        var target = calculate(lessons).isComplete() ? EnrollmentStatus.COMPLETED : EnrollmentStatus.ACTIVE;
        if (target != enrollment.status()) enrollments.updateStatus(enrollment.id(), target);
        return assemble(courseId, target, lessons);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CourseProgressPage myCourseProgress(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page request");
        var actor = learner();
        var result = enrollments.activeProgress(actor.userId(), PageRequest.of(page, size));
        return new CourseProgressPage(result.stream().map(row -> {
            var totals = Calculation.of(row.completedLessons(), row.totalLessons());
            return new CourseProgressSummary(row.courseId(), row.title(), EnrollmentStatus.ACTIVE.getStatus(),
                    row.completedLessons(), row.totalLessons(), totals.percentage(), totals.isComplete(), row.nextLessonId());
        }).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CourseResume resume(UUID courseId) {
        requireId(courseId);
        var actor = learner();
        access.requireLearnerCourseAccess(courseId);
        var enrollment = joined(enrollments.find(actor.userId(), courseId).orElse(null));
        return resumeOf(courseId, enrollment, enrollment.resumeLessonId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CourseResume visit(UUID courseId, UUID lessonId) {
        requireId(courseId);
        requireId(lessonId);
        var actor = learner();
        if (!enrollments.lockCourse(courseId)) throw unavailable();
        access.requireLearnerLessonAccess(courseId, lessonId);
        var enrollment = joined(enrollments.lockEnrollment(actor.userId(), courseId).orElse(null));
        enrollments.recordVisit(enrollment.id(), lessonId);
        return resumeOf(courseId, enrollment, lessonId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LearnerResume myResume() {
        var actor = learner();
        return new LearnerResume(enrollments.latestVisit(actor.userId())
                .map(row -> new LearnerResumePosition(row.courseId(), row.lessonId(), row.lastVisitedAt())).orElse(null));
    }

    /** CT22.progress integer calculation over the published required set R, in canonical order. */
    static Calculation calculate(List<LessonFlags> lessons) {
        long completed = 0;
        for (var lesson : lessons) {
            if (lesson.masterySkipped() && !lesson.completed()) throw new IllegalStateException("Skipped Lesson must be completed");
            if (lesson.completed()) completed++;
        }
        return Calculation.of(completed, lessons.size());
    }

    private CourseResume resumeOf(UUID courseId, Enrollment enrollment, UUID pointer) {
        UUID resume = pointer != null && enrollments.isPublishedCourseLesson(courseId, pointer) ? pointer : null;
        return new CourseResume(courseId, resume, nextLesson(enrollments.publishedLessonFlags(courseId, enrollment.id())));
    }

    private CourseProgress assemble(UUID courseId, EnrollmentStatus status, List<LessonFlags> lessons) {
        var totals = calculate(lessons);
        String title = enrollments.courseTitle(courseId).orElseThrow(() -> new IllegalStateException("Course row disappeared"));
        return new CourseProgress(courseId, title, status.getStatus(), totals.completed(), totals.total(), totals.percentage(),
                totals.isComplete(), nextLesson(lessons),
                lessons.stream().map(l -> new LessonProgressEntry(l.lessonId(), l.completed(), l.masterySkipped())).toList());
    }

    private static UUID nextLesson(List<LessonFlags> lessons) {
        return lessons.stream().filter(lesson -> !lesson.completed()).map(LessonFlags::lessonId).findFirst().orElse(null);
    }

    /** CT03 admission already proved ACTIVE/COMPLETED; anything else here is an invariant failure. */
    private static Enrollment joined(Enrollment enrollment) {
        if (enrollment == null || enrollment.status() == EnrollmentStatus.LEFT) {
            throw new IllegalStateException("Admitted Learner has no joined Enrollment");
        }
        return enrollment;
    }

    private CurrentIdentity learner() {
        var actor = identities.requireCurrentIdentity();
        if (actor == null || actor.userId() == null || actor.role() == null) {
            throw new IllegalStateException("Invalid CT01.identity provider result");
        }
        if (actor.role() != IdentityRole.LEARNER) throw new AccessDeniedException("Access denied");
        return actor;
    }

    private static ResourceNotFoundException unavailable() { return new ResourceNotFoundException("Resource not available"); }

    private static void requireId(UUID id) {
        if (id == null) throw new IllegalArgumentException("Identifier must not be null");
    }

    /** completed ≤ total; zero total is 0%, never complete; floor(100·C/|R|) without overflow. */
    record Calculation(long completed, long total, int percentage, boolean isComplete) {
        static Calculation of(long completed, long total) {
            if (completed < 0 || total < 0 || completed > total) throw new IllegalStateException("Invalid progress counts");
            if (total == 0) return new Calculation(0, 0, 0, false);
            return new Calculation(completed, total, (int) (Math.multiplyExact(100L, completed) / total), completed == total);
        }
    }

    public record LessonProgressEntry(UUID lessonId, boolean completed, boolean masterySkipped) {}
    public record CourseProgress(UUID courseId, String title, String membershipStatus, long completedLessons,
            long totalLessons, int progressPercentage, boolean isComplete, UUID nextLessonId, List<LessonProgressEntry> lessons) {
        public CourseProgress { lessons = List.copyOf(lessons); }
    }
    public record CourseProgressSummary(UUID courseId, String title, String membershipStatus, long completedLessons,
            long totalLessons, int progressPercentage, boolean isComplete, UUID nextLessonId) {}
    public record CourseProgressPage(List<CourseProgressSummary> content, int page, int size, long totalElements, int totalPages) {
        public CourseProgressPage { content = List.copyOf(content); }
    }
    public record CourseResume(UUID courseId, UUID resumeLessonId, UUID nextLessonId) {}
    public record LearnerResumePosition(UUID courseId, UUID lessonId, Instant lastVisitedAt) {}
    public record LearnerResume(LearnerResumePosition position) {}
}
