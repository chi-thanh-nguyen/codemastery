package com.codemastery.modules.course.application.service;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseContentRepository;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;


@Service
public class CourseAccessService {

    private static final Set<EnrollmentStatus> PERMITTED_LEARNING_STATUSES =
            EnumSet.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);

    private final CourseRepository courseRepository;
    private final CourseContentRepository courseContentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final Supplier<CurrentActor> identitySupplier;

    /**
     * Bounded constructor for public catalogue/detail visibility checks.
     * Identity-dependent checks remain explicit unresolved bindings when no identity producer is supplied.
     */
    public CourseAccessService(
            CourseRepository courseRepository,
            CourseContentRepository courseContentRepository) {
        this(courseRepository, courseContentRepository, null, CourseAccessService::unresolvedIdentityBinding);
    }

    /**
     * Spring-managed constructor binding Course-internal repositories.
     */
    @Autowired
    public CourseAccessService(
            CourseRepository courseRepository,
            CourseContentRepository courseContentRepository,
            @Autowired(required = false) EnrollmentRepository enrollmentRepository) {
        this(courseRepository, courseContentRepository, enrollmentRepository, CourseAccessService::unresolvedIdentityBinding);
    }

    /**
     * Package-private constructor allowing explicit identity supplier binding
     * without coupling to unimplemented cross-module internals.
     */
    CourseAccessService(
            CourseRepository courseRepository,
            CourseContentRepository courseContentRepository,
            EnrollmentRepository enrollmentRepository,
            Supplier<CurrentActor> identitySupplier) {
        this.courseRepository = Objects.requireNonNull(courseRepository, "courseRepository must not be null");
        this.courseContentRepository = Objects.requireNonNull(courseContentRepository, "courseContentRepository must not be null");
        this.enrollmentRepository = enrollmentRepository;
        this.identitySupplier = Objects.requireNonNull(identitySupplier, "identitySupplier must not be null");
    }

    // =========================================================================
    //  Public Catalogue / Detail Visibility Checks
    // =========================================================================

    /**
     * Checks whether a course is publicly visible (published).
     * Null, missing, or hidden courses return {@code false}.
     */
    public boolean isPublishedCourseVisible(UUID courseId) {
        if (courseId == null) {
            return false;
        }
        return courseRepository.findPublishedById(courseId)
                .filter(course -> course.publicationStatus() == PublishStatus.PUBLISHED)
                .isPresent();
    }

    /**
     * Asserts that a course is published and visible in the public catalogue/detail.
     * Missing and hidden courses throw the same {@link ResourceNotFoundException} (404 concealment).
     */
    public void requirePublishedCourseVisible(UUID courseId) {
        requirePublishedCourse(courseId);
    }

    /**
     * Package-private Course helper returning the published {@link Course}
     * for internal catalogue/detail assembly, or throwing uniform {@link ResourceNotFoundException} (404)
     * if missing or hidden.
     */
    Course requirePublishedCourse(UUID courseId) {
        requireNonNullId(courseId, "courseId");
        return courseRepository.findPublishedById(courseId)
                .filter(course -> course.publicationStatus() == PublishStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found or not published"));
    }

    /**
     * Checks whether a lesson is published and belongs to a published course.
     * Null, missing, foreign, or hidden course/lesson returns {@code false}.
     */
    public boolean isPublishedLessonInCourseVisible(UUID courseId, UUID lessonId) {
        if (courseId == null || lessonId == null) {
            return false;
        }
        if (!isPublishedCourseVisible(courseId)) {
            return false;
        }
        return courseContentRepository.findLessonInCourse(courseId, lessonId)
                .filter(lesson -> lesson.publicationStatus() == PublishStatus.PUBLISHED)
                .isPresent();
    }

    /**
     * Asserts that both the course and the scoped lesson (via Lesson -> Chapter -> Course
     * ancestry) are published and publicly visible, throwing uniform {@link ResourceNotFoundException} (404) otherwise.
     */
    public void requirePublishedLessonInCourse(UUID courseId, UUID lessonId) {
        requireNonNullId(courseId, "courseId");
        requireNonNullId(lessonId, "lessonId");
        requirePublishedCourse(courseId);
        Lesson lesson = courseContentRepository.findLessonInCourse(courseId, lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found in course"));
        if (lesson.publicationStatus() != PublishStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Lesson is not published");
        }
    }

    // =========================================================================
    // Identity-Dependent & Approved Cross-Module Checks
    // =========================================================================

    /**
     * Asserts Learner access to a published course with ACTIVE or COMPLETED enrollment.
     *
     * <p>Stage order:
     * 1. Authentication (401)
     * 2. Role == LEARNER (403)
     * 3. Course published & present (404)
     * 4. Enrollment ACTIVE or COMPLETED (403)
     */
    public void requireLearnerCourseAccess(UUID courseId) {
        requireNonNullId(courseId, "courseId");
        CurrentActor actor = requireAuthenticatedActor();
        requireRole(actor, ActorRole.LEARNER);
        Course course = requirePublishedCourseStage3(courseId);
        requireActiveOrCompletedMembership(actor.userId(), course.id());
    }

    /**
     * Asserts Learner access to a published lesson within a published course.
     * Does not consult skill mastery, prerequisite completion, or adaptive state (no hard-block rule).
     *
     * <p>Stage order:
     * 1. Authentication (401)
     * 2. Role == LEARNER (403)
     * 3. Course published & present (404)
     * 4. Enrollment ACTIVE or COMPLETED (403)
     * 5. Scoped Lesson -> Chapter -> Course ancestry & Lesson published (404)
     */
    public void requireLearnerLessonAccess(UUID courseId, UUID lessonId) {
        requireNonNullId(courseId, "courseId");
        requireNonNullId(lessonId, "lessonId");
        CurrentActor actor = requireAuthenticatedActor();
        requireRole(actor, ActorRole.LEARNER);
        Course course = requirePublishedCourseStage3(courseId);
        requireActiveOrCompletedMembership(actor.userId(), course.id());
        Lesson lesson = courseContentRepository.findLessonInCourse(course.id(), lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found in course"));
        if (lesson.publicationStatus() != PublishStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Lesson is not published");
        }
    }

    /**
     * Asserts Instructor ownership of a course (hidden or published).
     * Administrator role does not inherit course ownership (403 at Stage 2).
     *
     * <p>Stage order:
     * 1. Authentication (401)
     * 2. Role == INSTRUCTOR (403)
     * 3. Course existence & hidden concealment for non-owner (404)
     * 4. Instructor ownership on published course (403)
     */
    public void requireCourseOwnership(UUID courseId) {
        requireNonNullId(courseId, "courseId");
        CurrentActor actor = requireAuthenticatedActor();
        requireRole(actor, ActorRole.INSTRUCTOR);
        requireOwnedCourse(actor.userId(), courseId);
    }

    /**
     * Asserts Instructor ownership of a course and valid Lesson -> Chapter -> Course
     * ancestry for the specified lesson (hidden or published allowed).
     *
     * <p>Stage order:
     * 1. Authentication (401)
     * 2. Role == INSTRUCTOR (403)
     * 3. Course existence & hidden concealment for non-owner (404)
     * 4. Instructor ownership on published course (403)
     * 5. Scoped Lesson -> Chapter -> Course ancestry (404)
     */
    public void requireLessonOwnership(UUID courseId, UUID lessonId) {
        requireNonNullId(courseId, "courseId");
        requireNonNullId(lessonId, "lessonId");
        CurrentActor actor = requireAuthenticatedActor();
        requireRole(actor, ActorRole.INSTRUCTOR);
        Course course = requireOwnedCourse(actor.userId(), courseId);
        if (courseContentRepository.findLessonInCourse(course.id(), lessonId).isEmpty()) {
            throw new ResourceNotFoundException("Lesson not found in course");
        }
    }

    /**
     * Lesson-only general read assertion for cross-module consumers (e.g. Interaction)
     * that hold only a lesson ID.
     *
     * <p>Applies Stages 1-2 first (401 for unauthenticated, 403 for unsupported role such as ADMINISTRATOR
     * before any Course data lookup). Then privately resolves Lesson -> Chapter -> Course ancestry and
     * evaluates general read access (active/completed Learner on published Course + published Lesson,
     * OR owning Instructor previewing either publication state). Any resource-dependent denial (missing lesson,
     * broken ancestry, hidden course/lesson for learner, absent/LEFT membership, or non-owning instructor)
     * yields the same safe {@link ResourceNotFoundException} (404).
     */
    public void requireLessonReadAccess(UUID lessonId) {
        requireNonNullId(lessonId, "lessonId");
        CurrentActor actor = requireAuthenticatedActor();
        requireLearnerOrInstructorRole(actor);

        UUID courseId = courseContentRepository.findCourseIdByLessonId(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found"));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        Lesson lesson = courseContentRepository.findLessonInCourse(course.id(), lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found in course"));

        if (actor.role() == ActorRole.INSTRUCTOR) {
            if (!actor.userId().equals(course.instructorId())) {
                throw new ResourceNotFoundException("Lesson not accessible");
            }
            return;
        }

        // Learner path: conceal hidden course/lesson and missing/LEFT membership with uniform 404
        if (course.publicationStatus() != PublishStatus.PUBLISHED
                || lesson.publicationStatus() != PublishStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Lesson not found or not published");
        }
        if (!hasActiveOrCompletedMembership(actor.userId(), course.id())) {
            throw new ResourceNotFoundException("Lesson not accessible");
        }
    }

    // =========================================================================
    // Package-Private Course-Only Assertions (CT03 §12.5)
    // =========================================================================

    /**
     * Package-private assertion for {@link EnrollmentService}.
     * Requires Learner role and a published Course. Does not create an enrollment or reject
     * existing ACTIVE/COMPLETED/LEFT rows (CT22 owns enrollment lifecycle rules).
     */
    void requireEnrolmentEligibility(UUID courseId) {
        requireNonNullId(courseId, "courseId");
        CurrentActor actor = requireAuthenticatedActor();
        requireRole(actor, ActorRole.LEARNER);
        requirePublishedCourseStage3(courseId);
    }

    /**
     * Package-private assertion for {@link LearningMaterialService}.
     * Requires general lesson read access (Learner with ACTIVE/COMPLETED membership on published Course/Lesson,
     * OR owning Instructor preview) plus actual Material -> Lesson -> Chapter -> Course ancestry.
     * Learners additionally require the material to be published; owning Instructor preview permits hidden materials.
     */
    void requireMaterialReadAccess(UUID courseId, UUID lessonId, UUID materialId) {
        requireNonNullId(courseId, "courseId");
        requireNonNullId(lessonId, "lessonId");
        requireNonNullId(materialId, "materialId");
        CurrentActor actor = requireAuthenticatedActor();
        requireLearnerOrInstructorRole(actor);

        if (actor.role() == ActorRole.INSTRUCTOR) {
            Course course = requireOwnedCourse(actor.userId(), courseId);
            if (courseContentRepository.findLessonInCourse(course.id(), lessonId).isEmpty()) {
                throw new ResourceNotFoundException("Lesson not found in course");
            }
            if (courseContentRepository.findMaterialInLesson(course.id(), lessonId, materialId).isEmpty()) {
                throw new ResourceNotFoundException("Learning material not found in lesson");
            }
            return;
        }

        // Learner path: Stages 3 -> 4 -> 5
        Course course = requirePublishedCourseStage3(courseId);
        requireActiveOrCompletedMembership(actor.userId(), course.id());
        Lesson lesson = courseContentRepository.findLessonInCourse(course.id(), lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found in course"));
        if (lesson.publicationStatus() != PublishStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Lesson is not published");
        }
        LearningMaterial material = courseContentRepository.findMaterialInLesson(course.id(), lessonId, materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Learning material not found in lesson"));
        if (material.publicationStatus() != PublishStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Learning material is not published");
        }
    }

    // =========================================================================
    // Internal Stage Helpers & Explicit Identity Boundary Binding
    // =========================================================================

    private static void requireNonNullId(UUID id, String parameterName) {
        if (id == null) {
            throw new IllegalArgumentException(parameterName + " must not be null");
        }
    }

    private CurrentActor requireAuthenticatedActor() {
        CurrentActor actor = identitySupplier.get();
        if (actor == null || actor.userId() == null || actor.role() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return actor;
    }

    private static void requireRole(CurrentActor actor, ActorRole requiredRole) {
        if (actor.role() != requiredRole) {
            throw new AccessDeniedException("Access denied for role");
        }
    }

    private static void requireLearnerOrInstructorRole(CurrentActor actor) {
        if (actor.role() != ActorRole.LEARNER && actor.role() != ActorRole.INSTRUCTOR) {
            throw new AccessDeniedException("Access denied for role");
        }
    }

    private Course requirePublishedCourseStage3(UUID courseId) {
        return courseRepository.findPublishedById(courseId)
                .filter(course -> course.publicationStatus() == PublishStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found or not published"));
    }

    private Course requireOwnedCourse(UUID instructorId, UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        boolean isOwner = instructorId.equals(course.instructorId());
        if (!isOwner) {
            if (course.publicationStatus() != PublishStatus.PUBLISHED) {
                // Stage 3: conceal hidden course from non-owning instructor with 404
                throw new ResourceNotFoundException("Course not found");
            }
            // Stage 4: non-owner instructor on a published course receives 403
            throw new AccessDeniedException("Instructor does not own this course");
        }
        return course;
    }

    private void requireActiveOrCompletedMembership(UUID learnerId, UUID courseId) {
        if (!hasActiveOrCompletedMembership(learnerId, courseId)) {
            throw new AccessDeniedException("Learner does not have active or completed enrollment in course");
        }
    }

    private boolean hasActiveOrCompletedMembership(UUID learnerId, UUID courseId) {
        if (enrollmentRepository == null) {
            throw new IllegalStateException(
                    "Unresolved EnrollmentRepository binding for identity-dependent membership check");
        }
        Optional<Boolean> exists = Optional.of(
                enrollmentRepository.existsByUserIdAndCourseIdAndStatusIn(
                        learnerId, courseId, PERMITTED_LEARNING_STATUSES));
        return exists.orElse(false);
    }

    /**
     * Explicit unresolved identity binding when Auth's UserAccountService
     * producer is not yet wired. Fails closed without fabricating identity or reading security internals.
     */
    private static CurrentActor unresolvedIdentityBinding() throws AuthenticationException {
        throw new AuthenticationCredentialsNotFoundException(
                "CT01.identity (U01) current-identity producer is not bound");
    }

    /**
     * Internal representation of the authenticated actor.
     */
    enum ActorRole {
        LEARNER,
        INSTRUCTOR,
        ADMINISTRATOR
    }

    record CurrentActor(UUID userId, ActorRole role) {
        CurrentActor {
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(role, "role must not be null");
        }
    }
}
