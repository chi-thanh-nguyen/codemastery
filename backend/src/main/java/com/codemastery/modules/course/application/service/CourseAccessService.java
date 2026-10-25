package com.codemastery.modules.course.application.service;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.domain.repository.CourseRepository.AccessSnapshot;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/** CT03 admission checks, not durable permissions or content delivery. */
@Service
public class CourseAccessService {
    private final CourseRepository courseRepository;
    private final UserAccountService identities;

    public CourseAccessService(CourseRepository courseRepository, UserAccountService identities) {
        this.courseRepository = Objects.requireNonNull(courseRepository);
        this.identities = Objects.requireNonNull(identities);
    }

    public void requireLearnerCourseAccess(UUID courseId) {
        requireId(courseId);
        CurrentIdentity actor = requireActor(IdentityRole.LEARNER);
        AccessSnapshot snapshot = requireCourseSnapshot(actor, courseId, null, null);
        requireCourseAdmission(actor, snapshot, true);
    }

    public void requireLearnerLessonAccess(UUID courseId, UUID lessonId) {
        requireId(courseId);
        requireId(lessonId);
        CurrentIdentity actor = requireActor(IdentityRole.LEARNER);
        AccessSnapshot snapshot = requireCourseSnapshot(actor, courseId, lessonId, null);
        requireCourseAdmission(actor, snapshot, true);
        requireLesson(actor, snapshot, lessonId);
    }

    public void requireCourseOwnership(UUID courseId) {
        requireId(courseId);
        CurrentIdentity actor = requireActor(IdentityRole.INSTRUCTOR);
        AccessSnapshot snapshot = requireCourseSnapshot(actor, courseId, null, null);
        requireCourseAdmission(actor, snapshot, false);
    }

    public void requireLessonOwnership(UUID courseId, UUID lessonId) {
        requireId(courseId);
        requireId(lessonId);
        CurrentIdentity actor = requireActor(IdentityRole.INSTRUCTOR);
        AccessSnapshot snapshot = requireCourseSnapshot(actor, courseId, lessonId, null);
        requireCourseAdmission(actor, snapshot, false);
        requireLesson(actor, snapshot, lessonId);
    }

    public void requireLessonReadAccess(UUID lessonId) {
        requireId(lessonId);
        CurrentIdentity actor = requireActor(null);
        AccessSnapshot snapshot = courseRepository.findLessonAccessSnapshot(actor.userId(), lessonId)
                .orElseThrow(CourseAccessService::unavailable);
        // No caller-supplied Course scope: all resource-dependent denials are concealed.
        boolean admitted = actor.role() == IdentityRole.INSTRUCTOR
                ? actor.userId().equals(snapshot.instructorId())
                : snapshot.courseStatus() == PublishStatus.PUBLISHED && permitsLearning(snapshot);
        if (!admitted) throw unavailable();
        requireLesson(actor, snapshot, lessonId);
    }

    void requireEnrolmentEligibility(UUID courseId) {
        requireId(courseId);
        CurrentIdentity actor = requireActor(IdentityRole.LEARNER);
        AccessSnapshot snapshot = requireCourseSnapshot(actor, courseId, null, null);
        requireCourseAdmission(actor, snapshot, false);
    }

    void requireMaterialReadAccess(UUID courseId, UUID lessonId, UUID materialId) {
        requireId(courseId);
        requireId(lessonId);
        requireId(materialId);
        CurrentIdentity actor = requireActor(null);
        AccessSnapshot snapshot = requireCourseSnapshot(actor, courseId, lessonId, materialId);
        requireCourseAdmission(actor, snapshot, true);
        requireLesson(actor, snapshot, lessonId);
        if (!materialId.equals(snapshot.materialId())
                || actor.role() == IdentityRole.LEARNER && snapshot.materialStatus() != PublishStatus.PUBLISHED) {
            throw unavailable();
        }
    }

    private CurrentIdentity requireActor(IdentityRole requiredRole) {
        // AuthenticationException from a trusted producer propagates; provider failure stays 500.
        CurrentIdentity actor = identities.requireCurrentIdentity();
        if (actor == null || actor.userId() == null || actor.role() == null) {
            throw new IllegalStateException("Invalid CT01.identity provider result");
        }
        if (requiredRole != null ? actor.role() != requiredRole
                : actor.role() != IdentityRole.LEARNER && actor.role() != IdentityRole.INSTRUCTOR) {
            throw new AccessDeniedException("Access denied");
        }
        return actor;
    }

    private AccessSnapshot requireCourseSnapshot(
            CurrentIdentity actor, UUID courseId, UUID lessonId, UUID materialId) {
        AccessSnapshot snapshot = courseRepository.findCourseAccessSnapshot(
                actor.userId(), courseId, lessonId, materialId).orElseThrow(CourseAccessService::unavailable);
        if (!courseId.equals(snapshot.courseId())) {
            throw new IllegalStateException("Invalid Course access snapshot scope");
        }
        return snapshot;
    }

    private static void requireCourseAdmission(CurrentIdentity actor, AccessSnapshot snapshot, boolean membership) {
        boolean owner = actor.role() == IdentityRole.INSTRUCTOR && actor.userId().equals(snapshot.instructorId());
        if (snapshot.courseStatus() != PublishStatus.PUBLISHED && !owner) throw unavailable();
        if (actor.role() == IdentityRole.INSTRUCTOR ? !owner : membership && !permitsLearning(snapshot)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private static void requireLesson(CurrentIdentity actor, AccessSnapshot snapshot, UUID lessonId) {
        if (!lessonId.equals(snapshot.lessonId())
                || actor.role() == IdentityRole.LEARNER && snapshot.lessonStatus() != PublishStatus.PUBLISHED) {
            throw unavailable();
        }
    }

    private static boolean permitsLearning(AccessSnapshot snapshot) {
        return snapshot.enrollmentStatus() == EnrollmentStatus.ACTIVE
                || snapshot.enrollmentStatus() == EnrollmentStatus.COMPLETED;
    }

    private static ResourceNotFoundException unavailable() {
        return new ResourceNotFoundException("Resource not available");
    }

    private static void requireId(UUID id) {
        if (id == null) throw new IllegalArgumentException("Identifier must not be null");
    }
}
