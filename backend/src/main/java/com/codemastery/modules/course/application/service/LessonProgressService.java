package com.codemastery.modules.course.application.service;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository.LessonFlags;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository.LessonPlacement;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;


@Service
public class LessonProgressService {
    private final EnrollmentRepository enrollments;
    private final UserAccountService identities;
    private final CourseAccessService access;
    private final CourseRepository courses;

    public LessonProgressService(EnrollmentRepository enrollments, UserAccountService identities, CourseAccessService access,
                                 CourseRepository courses) {
        this.enrollments = Objects.requireNonNull(enrollments);
        this.identities = Objects.requireNonNull(identities);
        this.access = Objects.requireNonNull(access);
        this.courses = Objects.requireNonNull(courses);
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

    public LearnerCourseState readExecutionState(UUID courseId, UUID learnerId) {
        requireId(courseId);
        requireId(learnerId);
        requireHeldRoot(courseId);
        return executionState(courseId, learnerId, enrollments.find(learnerId, courseId).orElse(null));
    }


    public LearnerCourseState applyMasterySkips(UUID courseId, UUID learnerId, Set<UUID> lessonIds) {
        requireId(courseId);
        requireId(learnerId);
        if (lessonIds == null || lessonIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Lesson identifiers must not be null");
        }
        requireHeldRoot(courseId);
        var enrollment = enrollments.lockEnrollment(learnerId, courseId).orElse(null);
        if (enrollment == null || enrollment.status() == EnrollmentStatus.LEFT) {
            throw new IllegalStateException("Mastery skips require a joined Enrollment");
        }
        var lessons = enrollments.publishedLessonPlacements(courseId, enrollment.id());
        Set<UUID> published = new HashSet<>();
        lessons.forEach(lesson -> published.add(lesson.lessonId()));
        if (!published.containsAll(lessonIds)) throw new IllegalArgumentException("Lesson is not a published Lesson of the Course");
        if (lessonIds.isEmpty()) return executionState(courseId, learnerId, enrollment.id(), enrollment.status().getStatus(), lessons);

        enrollments.lockProgress(enrollment.id());
        for (var lesson : lessons) {
            if (lessonIds.contains(lesson.lessonId()) && !lesson.completed()) {
                enrollments.markMasterySkipped(enrollment.id(), lesson.lessonId());
            }
        }
        var after = enrollments.publishedLessonPlacements(courseId, enrollment.id());
        var flags = after.stream().map(l -> new LessonFlags(l.lessonId(), l.completed(), l.masterySkipped())).toList();
        var target = calculate(flags).isComplete() ? EnrollmentStatus.COMPLETED : EnrollmentStatus.ACTIVE;
        if (target != enrollment.status()) enrollments.updateStatus(enrollment.id(), target);
        return executionState(courseId, learnerId, enrollment.id(), target.getStatus(), after);
    }

    private LearnerCourseState executionState(UUID courseId, UUID learnerId, Enrollment enrollment) {
        UUID enrollmentId = enrollment == null ? null : enrollment.id();
        return executionState(courseId, learnerId, enrollmentId, enrollment == null ? "none" : enrollment.status().getStatus(),
                enrollments.publishedLessonPlacements(courseId, enrollmentId));
    }

    private LearnerCourseState executionState(UUID courseId, UUID learnerId, UUID enrollmentId, String membership,
                                              List<LessonPlacement> lessons) {
        var stored = courses.findAdaptiveConfiguration(courseId)
                .orElseThrow(() -> new IllegalStateException("Course row disappeared"));
        if (stored.adaptiveEnabled() && stored.adaptiveSettings() == null) {
            throw new IllegalStateException("Invalid stored adaptive configuration");
        }
        return new LearnerCourseState(courseId, learnerId, enrollmentId, membership, stored.adaptiveEnabled(),
                stored.adaptiveSettings(), lessons.stream().map(LessonProgressService::lessonState).toList());
    }

    private static LessonState lessonState(LessonPlacement lesson) {
        if (lesson.masterySkipped() && !lesson.completed()) throw new IllegalStateException("Skipped Lesson must be completed");
        return new LessonState(lesson.lessonId(), lesson.chapterId(), lesson.chapterPosition(), lesson.lessonPosition(),
                lesson.completed(), lesson.masterySkipped());
    }

    private void requireHeldRoot(UUID courseId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Course execution participation requires the held Course root");
        }
        var facts = courses.currentTransactionFacts();
        if (facts == null || facts.readOnly() || !"read committed".equals(facts.isolation())
                || !courseId.equals(facts.heldCourseRoot())) {
            throw new IllegalStateException("Course execution participation requires the held Course root");
        }
    }

    /** .progress integer calculation over the published required set R, in canonical order. */
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

    /** admission already proved ACTIVE/COMPLETED; anything else here is an invariant failure. */
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
    /** {@code membershipStatus} is active, completed, left or none (then enrollmentId is null). */
    public record LearnerCourseState(UUID courseId, UUID learnerId, UUID enrollmentId,
            String membershipStatus, boolean adaptiveEnabled, Map<String, Object> adaptiveSettings,
            List<LessonState> lessons) {
        public LearnerCourseState { lessons = List.copyOf(lessons); }
    }
    public record LessonState(UUID lessonId, UUID chapterId, int chapterPosition,
            int lessonPosition, boolean completed, boolean masterySkipped) {}
}
