package com.codemastery.modules.course.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EnrollmentService {
    private final EnrollmentRepository enrollments;
    private final UserAccountService identities;
    private final CourseAccessService access;
    public EnrollmentService(EnrollmentRepository enrollments, UserAccountService identities, CourseAccessService access) {
        this.enrollments = enrollments; this.identities = identities; this.access = access;
    }

    /** ADR010 §3.1: owner-admitted page IDs only, including retained LEFT memberships. */
    @Transactional(readOnly = true)
    public Map<UUID, String> learnerDisplayNames(UUID courseId, Set<UUID> learnerIds) {
        access.requireCourseOwnership(courseId);
        Set<UUID> ids = Set.copyOf(learnerIds);
        if (ids.size() > 100) {
            throw new IllegalArgumentException("At most 100 learner IDs per call");
        }
        Set<UUID> members = ids.stream()
                .filter(id -> enrollments.find(id, courseId).isPresent())
                .collect(Collectors.toUnmodifiableSet());
        return members.isEmpty() ? Map.of() : identities.learnerDisplayNames(members);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Membership membership(UUID courseId) {
        var actor = learner();
        access.requireEnrolmentEligibility(courseId);
        return representation(courseId, enrollments.find(actor.userId(), courseId).orElse(null));
    }

    @Transactional
    public Membership enroll(UUID courseId) { return transition(courseId, true); }

    @Transactional
    public Membership leave(UUID courseId) { return transition(courseId, false); }

    private Membership transition(UUID courseId, boolean joining) {
        var actor = learner();
        if (!enrollments.lockPublishedCourse(courseId)) throw new ResourceNotFoundException("Resource not available");
        var existing = enrollments.find(actor.userId(), courseId).orElse(null);
        if (existing == null) {
            if (!joining) return new Membership(courseId, "none");
            existing = new Enrollment(UUID.randomUUID(), actor.userId(), courseId, EnrollmentStatus.ACTIVE, null);
            enrollments.insert(existing);
        } else {
            var target = joining ? existing.status() == EnrollmentStatus.LEFT ? EnrollmentStatus.ACTIVE : existing.status() : EnrollmentStatus.LEFT;
            if (target != existing.status()) enrollments.updateStatus(existing.id(), target);
            existing = new Enrollment(existing.id(), existing.userId(), courseId, target, existing.resumeLessonId());
        }
        return representation(courseId, existing);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MembershipPage myCourses(int page, int size, String status) {
        var actor = learner();
        var result = enrollments.list(actor.userId(), status, PageRequest.of(page, size));
        var names = identities.instructorDisplayNames(result.stream().map(EnrollmentRepository.MembershipCourse::instructorId).collect(Collectors.toSet()));
        return new MembershipPage(result.stream().map(row -> new MembershipCourse(row.id(), row.title(), row.description(),
                row.instructorId(), names.get(row.instructorId()), row.status().getStatus())).toList(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }
    /**
     * CT13.reads (ADR012 §13.1/§13.2): per-Course enrolment and completion figures for every Course,
     * hidden and published, ordered by title COLLATE "C" then id. Actorless: the only caller, Admin's
     * PlatformMetricsService, admits an active Administrator first and composes this read inside its
     * own read-only REPEATABLE READ transaction. Joined learner IDs are internal and never leave the
     * backend. Set-based Course-owned queries; no row is locked or written.
     */
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public PageResponse<CourseEnrollmentMetrics> readEnrollmentMetrics(int page, int size) {
        PageResponse.validateRequest(page, size);
        var rows = enrollments.enrollmentMetrics(PageRequest.of(page, size));
        var joined = enrollments.joinedLearnerIds(rows.stream().map(EnrollmentRepository.EnrollmentMetricsRow::courseId).toList());
        return new PageResponse<>(rows.stream().map(row -> metrics(row, joined)).toList(), page, size, rows.getTotalElements());
    }

    /** CT13.reads for one Course (hidden or published); empty when the Course does not exist. */
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Optional<CourseEnrollmentMetrics> readEnrollmentMetrics(UUID courseId) {
        if (courseId == null) throw new IllegalArgumentException("Identifier must not be null");
        return enrollments.enrollmentMetrics(courseId).map(row -> metrics(row, enrollments.joinedLearnerIds(List.of(courseId))));
    }

    /**
     * ADR011 §14 (CT33.insights): ACTIVE/COMPLETED learners of an owning Instructor's Course,
     * ordered by learner UUID ascending, with CT22 progress and display names from
     * {@code UserAccountService.learnerDisplayNames}. Joins the caller's read-only REPEATABLE READ snapshot.
     */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<CourseLearner> listJoinedLearners(UUID courseId, int page, int size) {
        requireId(courseId);
        PageResponse.validateRequest(page, size);
        requireReadSnapshot();
        access.requireCourseOwnership(courseId);

        long total = enrollments.countJoinedLearners(courseId);
        if (total < 0) {
            throw new IllegalStateException("Joined learner count cannot be negative");
        }
        if ((long) page * size >= total) {
            return new PageResponse<>(List.of(), page, size, total);
        }

        var rows = enrollments.findJoinedLearners(courseId, page, size);
        if (rows == null || rows.size() > size || rows.size() > total) {
            throw new IllegalStateException("Invalid joined learner page result");
        }

        Set<UUID> learnerIds = rows.stream()
                .map(EnrollmentRepository.JoinedLearnerRow::learnerId)
                .collect(Collectors.toUnmodifiableSet());
        Map<UUID, String> names = learnerIds.isEmpty() ? Map.of() : identities.learnerDisplayNames(learnerIds);
        if (names == null) {
            throw new IllegalStateException("Invalid learner display names provider result");
        }

        List<CourseLearner> content = rows.stream()
                .map(row -> toCourseLearner(row, names.get(row.learnerId())))
                .toList();
        return new PageResponse<>(content, page, size, total);
    }

    /**
     * ADR011 §14 (CT33.insights): one ACTIVE/COMPLETED learner of an owning Instructor's Course;
     * LEFT, absent or foreign learners are concealed as 404. Joins the caller's read-only REPEATABLE READ snapshot.
     */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CourseLearner readJoinedLearner(UUID courseId, UUID learnerId) {
        requireId(courseId);
        requireId(learnerId);
        requireReadSnapshot();
        access.requireCourseOwnership(courseId);

        var row = enrollments.findJoinedLearner(courseId, learnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not available"));
        Map<UUID, String> names = identities.learnerDisplayNames(Set.of(learnerId));
        if (names == null) {
            throw new IllegalStateException("Invalid learner display names provider result");
        }
        return toCourseLearner(row, names.get(learnerId));
    }

    private static CourseLearner toCourseLearner(EnrollmentRepository.JoinedLearnerRow row, String displayName) {
        if (row.status() != EnrollmentStatus.ACTIVE && row.status() != EnrollmentStatus.COMPLETED) {
            throw new IllegalStateException("Joined learner read returned non-joined membership");
        }
        var calc = LessonProgressService.Calculation.of(row.completedLessons(), row.totalLessons());
        return new CourseLearner(
                row.learnerId(),
                displayName,
                row.status().getStatus(),
                row.enrollmentId(),
                Math.toIntExact(calc.completed()),
                Math.toIntExact(calc.total()),
                calc.percentage());
    }

    private static void requireReadSnapshot() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Objects.equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(),
                        Isolation.REPEATABLE_READ.value())) {
            throw new IllegalStateException("Course learner reads require a read-only REPEATABLE READ transaction");
        }
    }

    private static void requireId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Identifier must not be null");
        }
    }

    private static CourseEnrollmentMetrics metrics(EnrollmentRepository.EnrollmentMetricsRow row, Map<UUID, Set<UUID>> joined) {
        var learners = joined.getOrDefault(row.courseId(), Set.of());
        if (learners.size() != row.activeEnrollments() + row.completedEnrollments() || row.completedLearners() > learners.size()) {
            throw new IllegalStateException("Inconsistent enrolment metrics snapshot");
        }
        return new CourseEnrollmentMetrics(row.courseId(), row.title(), row.publicationStatus(), row.activeEnrollments(),
                row.completedEnrollments(), row.leftEnrollments(), row.completedLearners(), learners);
    }

    private CurrentIdentity learner() {
        var actor = identities.requireCurrentIdentity();
        if (actor.role() != IdentityRole.LEARNER) throw new AccessDeniedException("Access denied");
        return actor;
    }
    private static Membership representation(UUID courseId, Enrollment enrollment) {
        return new Membership(courseId, enrollment == null ? "none" : enrollment.status().getStatus());
    }
    public record Membership(UUID courseId, String status) {}
    public record MembershipCourse(UUID id, String title, String description, UUID instructorId, String instructorDisplayName, String status) {}
    public record MembershipPage(List<MembershipCourse> content, int page, int size, long totalElements, int totalPages) {}
    public record CourseEnrollmentMetrics(UUID courseId, String title, String publicationStatus,
            long activeEnrollments, long completedEnrollments, long leftEnrollments,
            long completedLearners, Set<UUID> joinedLearnerIds) {
        public CourseEnrollmentMetrics { joinedLearnerIds = Set.copyOf(joinedLearnerIds); }
    }
    public record CourseLearner(UUID learnerId, String displayName, String membershipStatus,
            UUID enrollmentId, int completedLessons, int totalLessons, int progressPercentage) {
        public CourseLearner {
            Objects.requireNonNull(learnerId, "learnerId");
            Objects.requireNonNull(membershipStatus, "membershipStatus");
            Objects.requireNonNull(enrollmentId, "enrollmentId");
        }
    }
}
