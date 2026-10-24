package com.codemastery.modules.course.application.service;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.domain.repository.CourseRepository.AccessSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** CT03 admission checks, not durable permissions or content delivery. */
@Service
public class CourseAccessService {
    private final CourseRepository courseRepository;
    private final Supplier<CurrentActor> identitySupplier;

    /**
     * INCOMPLETE: AU06 has not implemented the approved UserAccountService facade/types.
     * Protected calls fail with an infrastructure error (safe 500), never a fabricated 401.
     * Replace this binding with CT01.identity when Auth delivers its approved facade.
     */
    @Autowired
    public CourseAccessService(CourseRepository courseRepository) {
        this(courseRepository, () -> {
            throw new IllegalStateException("CT01.identity provider is not available");
        });
    }

    // Temporary Course-internal test seam only; never injected as a production identity provider.
    CourseAccessService(CourseRepository courseRepository, Supplier<CurrentActor> identitySupplier) {
        this.courseRepository = Objects.requireNonNull(courseRepository);
        this.identitySupplier = Objects.requireNonNull(identitySupplier);
    }

    public void requireLearnerCourseAccess(UUID courseId) {
        requireId(courseId);
        CurrentActor actor = requireActor(ActorRole.LEARNER);
        AccessSnapshot snapshot = requireCourseSnapshot(actor, courseId, null, null);
        requireCourseAdmission(actor, snapshot, true);
    }

    public void requireLearnerLessonAccess(UUID courseId, UUID lessonId) {
        requireId(courseId);
        requireId(lessonId);
        CurrentActor actor = requireActor(ActorRole.LEARNER);
        AccessSnapshot snapshot = requireCourseSnapshot(actor, courseId, lessonId, null);
        requireCourseAdmission(actor, snapshot, true);
        requireLesson(actor, snapshot, lessonId);
    }

    public void requireCourseOwnership(UUID courseId) {
        requireId(courseId);
        CurrentActor actor = requireActor(ActorRole.INSTRUCTOR);
        AccessSnapshot snapshot = requireCourseSnapshot(actor, courseId, null, null);
        requireCourseAdmission(actor, snapshot, false);
    }

    public void requireLessonOwnership(UUID courseId, UUID lessonId) {
        requireId(courseId);
        requireId(lessonId);
        CurrentActor actor = requireActor(ActorRole.INSTRUCTOR);
        AccessSnapshot snapshot = requireCourseSnapshot(actor, courseId, lessonId, null);
        requireCourseAdmission(actor, snapshot, false);
        requireLesson(actor, snapshot, lessonId);
    }

    public void requireLessonReadAccess(UUID lessonId) {
        requireId(lessonId);
        CurrentActor actor = requireActor(null);
        AccessSnapshot snapshot = courseRepository.findLessonAccessSnapshot(actor.userId(), lessonId)
                .orElseThrow(CourseAccessService::unavailable);
        // No caller-supplied Course scope: all resource-dependent denials are concealed.
        boolean admitted = actor.role() == ActorRole.INSTRUCTOR
                ? actor.userId().equals(snapshot.instructorId())
                : snapshot.courseStatus() == PublishStatus.PUBLISHED && permitsLearning(snapshot);
        if (!admitted) throw unavailable();
        requireLesson(actor, snapshot, lessonId);
    }

    void requireEnrolmentEligibility(UUID courseId) {
        requireId(courseId);
        CurrentActor actor = requireActor(ActorRole.LEARNER);
        AccessSnapshot snapshot = requireCourseSnapshot(actor, courseId, null, null);
        requireCourseAdmission(actor, snapshot, false);
    }

    void requireMaterialReadAccess(UUID courseId, UUID lessonId, UUID materialId) {
        requireId(courseId);
        requireId(lessonId);
        requireId(materialId);
        CurrentActor actor = requireActor(null);
        AccessSnapshot snapshot = requireCourseSnapshot(actor, courseId, lessonId, materialId);
        requireCourseAdmission(actor, snapshot, true);
        requireLesson(actor, snapshot, lessonId);
        if (!materialId.equals(snapshot.materialId())
                || actor.role() == ActorRole.LEARNER && snapshot.materialStatus() != PublishStatus.PUBLISHED) {
            throw unavailable();
        }
    }

    private CurrentActor requireActor(ActorRole requiredRole) {
        // AuthenticationException from a trusted producer propagates; provider failure stays 500.
        CurrentActor actor = identitySupplier.get();
        if (actor == null || actor.userId() == null || actor.role() == null) {
            throw new IllegalStateException("Invalid CT01.identity provider result");
        }
        if (requiredRole != null ? actor.role() != requiredRole
                : actor.role() != ActorRole.LEARNER && actor.role() != ActorRole.INSTRUCTOR) {
            throw new AccessDeniedException("Access denied");
        }
        return actor;
    }

    private AccessSnapshot requireCourseSnapshot(
            CurrentActor actor, UUID courseId, UUID lessonId, UUID materialId) {
        AccessSnapshot snapshot = courseRepository.findCourseAccessSnapshot(
                actor.userId(), courseId, lessonId, materialId).orElseThrow(CourseAccessService::unavailable);
        if (!courseId.equals(snapshot.courseId())) {
            throw new IllegalStateException("Invalid Course access snapshot scope");
        }
        return snapshot;
    }

    private static void requireCourseAdmission(CurrentActor actor, AccessSnapshot snapshot, boolean membership) {
        boolean owner = actor.role() == ActorRole.INSTRUCTOR && actor.userId().equals(snapshot.instructorId());
        if (snapshot.courseStatus() != PublishStatus.PUBLISHED && !owner) throw unavailable();
        if (actor.role() == ActorRole.INSTRUCTOR ? !owner : membership && !permitsLearning(snapshot)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private static void requireLesson(CurrentActor actor, AccessSnapshot snapshot, UUID lessonId) {
        if (!lessonId.equals(snapshot.lessonId())
                || actor.role() == ActorRole.LEARNER && snapshot.lessonStatus() != PublishStatus.PUBLISHED) {
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

    // Temporary internal representations until the real CT01 nested identity types exist.
    enum ActorRole { LEARNER, INSTRUCTOR, ADMINISTRATOR }
    record CurrentActor(UUID userId, ActorRole role) {}
}
