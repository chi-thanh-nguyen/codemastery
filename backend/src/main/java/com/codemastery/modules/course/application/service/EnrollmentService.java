package com.codemastery.modules.course.application.service;

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
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
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
}
