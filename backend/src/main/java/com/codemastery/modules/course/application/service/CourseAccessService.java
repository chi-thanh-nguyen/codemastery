package com.codemastery.modules.course.application.service;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
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
        this(courseRepository, courseContentRepository, enrollmentRepository,
                CourseAccessService::unresolvedIdentityBinding);
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
        this.courseContentRepository = Objects.requireNonNull(
                courseContentRepository, "courseContentRepository must not be null");
        this.enrollmentRepository = enrollmentRepository;
        this.identitySupplier = Objects.requireNonNull(identitySupplier, "identitySupplier must not be null");
    }

    // =========================================================================
    //  Public Catalogue / Detail Visibility Checks
    // =========================================================================

    /**
     * Checks whether a course is publicly visible (published).
     * Null, missing, or hidden courses return false.
     */
    public boolean isPublishedCourseVisible(UUID courseId) {
        return findPublishedCourse(courseId).isPresent();
    }

    /**
     * Asserts that a course is published and visible in the public catalogue/detail.
     * Missing and hidden courses throw the same ResourceNotFoundException (404 concealment).
     */
    public void requirePublishedCourseVisible(UUID courseId) {
        requirePublishedCourse(courseId);
    }

    /**
     * Package-private helper returning the published Course for catalogue/detail assembly,
     * or throwing the uniform ResourceNotFoundException (404) if missing or hidden.
     */
    Course requirePublishedCourse(UUID courseId) {
        requireNonNullId(courseId, "courseId");
        return findPublishedCourse(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found or not published"));
    }

    /**
     * Checks whether a lesson is published and belongs to a published course.
     * Null, missing, foreign, or hidden course/lesson returns false.
     */
    public boolean isPublishedLessonInCourseVisible(UUID courseId, UUID lessonId) {
        if (courseId == null || lessonId == null) {
            return false;
        }
        return findPublishedCourse(courseId)
                .flatMap(course -> findPublishedLesson(course.id(), lessonId))
                .isPresent();
    }

    /**
     * Asserts that both the course and the scoped lesson (Lesson to Chapter to Course ancestry)
     * are published and publicly visible, throwing uniform ResourceNotFoundException (404) otherwise.
     */
    public void requirePublishedLessonInCourse(UUID courseId, UUID lessonId) {
        requireNonNullId(courseId, "courseId");
        requireNonNullId(lessonId, "lessonId");
        Course course = requirePublishedCourse(courseId);
        requirePublishedLesson(course.id(), lessonId);
    }

    // =========================================================================
    //  Identity-Dependent Checks
    // =========================================================================

    /**
     * Asserts Learner access to a published course with ACTIVE or COMPLETED enrollment.
     *
     * Stage order: 1. Authentication (401); 2. Role == LEARNER (403);
     * 3. Course published and present (404); 4. Enrollment ACTIVE or COMPLETED (403).
     */
    public void requireLearnerCourseAccess(UUID courseId) {
        requireNonNullId(courseId, "courseId");
        CurrentActor actor = requireAuthenticatedActor();
        requireRole(actor, ActorRole.LEARNER);
        Course course = requirePublishedCourse(courseId);
        requireActiveOrCompletedMembership(actor.userId(), course.id());
    }

    /**
     * Asserts Learner access to a published lesson within a published course.
     * Does not consult skill mastery, prerequisite completion, or adaptive state.
     *
     * Stage order: 1. Authentication (401); 2. Role == LEARNER (403);
     * 3. Course published and present (404); 4. Enrollment ACTIVE or COMPLETED (403);
     * 5. Scoped Lesson to Chapter to Course ancestry and Lesson published (404).
     */
    public void requireLearnerLessonAccess(UUID courseId, UUID lessonId) {
        requireNonNullId(courseId, "courseId");
        requireNonNullId(lessonId, "lessonId");
        CurrentActor actor = requireAuthenticatedActor();
        requireRole(actor, ActorRole.LEARNER);
        Course course = requirePublishedCourse(courseId);
        requireActiveOrCompletedMembership(actor.userId(), course.id());
        requirePublishedLesson(course.id(), lessonId);
    }

    /**
     * Asserts Instructor ownership of a course.
     * Administrator role does not inherit course ownership (403 at Stage 2).
     *
     * Stage order: 1. Authentication (401); 2. Role == INSTRUCTOR (403);
     * 3. Course visible through the published-course port, otherwise concealed (404);
     * 4. Instructor owns the published course (403).
     *
     * Unresolved binding: owner preview of a non-published course requires a CO03 course-by-id port that
     * does not exist yet; such courses are concealed with 404 (fail closed).
     */
    public void requireCourseOwnership(UUID courseId) {
        requireNonNullId(courseId, "courseId");
        CurrentActor actor = requireAuthenticatedActor();
        requireRole(actor, ActorRole.INSTRUCTOR);
        requireOwnedPublishedCourse(actor.userId(), courseId);
    }

    /**
     * Asserts Instructor ownership of a course and valid Lesson to Chapter to Course ancestry
     * for the specified lesson.
     *
     * Stage order: 1. Authentication (401); 2. Role == INSTRUCTOR (403);
     * 3. Course visible, otherwise concealed (404); 4. Instructor owns the course (403);
     * 5. Scoped lesson ancestry (404).
     *
     * Unresolved binding: hidden lessons/courses need CO03 scoped lookups that do not exist yet; they are
     * concealed with 404 (fail closed).
     */
    public void requireLessonOwnership(UUID courseId, UUID lessonId) {
        requireNonNullId(courseId, "courseId");
        requireNonNullId(lessonId, "lessonId");
        CurrentActor actor = requireAuthenticatedActor();
        requireRole(actor, ActorRole.INSTRUCTOR);
        Course course = requireOwnedPublishedCourse(actor.userId(), courseId);
        requirePublishedLesson(course.id(), lessonId);
    }

    /**
     * Lesson-only general read assertion for cross-module consumers that hold only a lesson ID.
     *
     * Applies Stages 1-2 (401 unauthenticated, 403 unsupported role such as ADMINISTRATOR) before any
     * Course data lookup. Resolving Lesson to Chapter to Course from a lesson ID has no CO03 port yet, so the
     * check then fails with an explicit unresolved-binding IllegalStateException instead of guessing.
     */
    public void requireLessonReadAccess(UUID lessonId) {
        requireNonNullId(lessonId, "lessonId");
        CurrentActor actor = requireAuthenticatedActor();
        requireLearnerOrInstructorRole(actor);
        throw unresolvedCo03Binding("Lesson-to-Course ancestry lookup by lessonId");
    }

    // =========================================================================
    //  Package-Private Course-Only Assertions
    // =========================================================================

    /**
     * Package-private assertion for {@code EnrollmentService}.
     * Requires Learner role and a published Course. Does not create an enrollment or reject
     * existing ACTIVE/COMPLETED/LEFT rows.
     */
    void requireEnrolmentEligibility(UUID courseId) {
        requireNonNullId(courseId, "courseId");
        CurrentActor actor = requireAuthenticatedActor();
        requireRole(actor, ActorRole.LEARNER);
        requirePublishedCourse(courseId);
    }

    /**
     * Package-private assertion for LearningMaterialService.
     * Stages 1-2 are applied first. Material to Lesson ancestry has no CO03 port yet, so the check fails
     * with an explicit unresolved-binding IllegalStateException instead of guessing.
     */
    void requireMaterialReadAccess(UUID courseId, UUID lessonId, UUID materialId) {
        requireNonNullId(courseId, "courseId");
        requireNonNullId(lessonId, "lessonId");
        requireNonNullId(materialId, "materialId");
        CurrentActor actor = requireAuthenticatedActor();
        requireLearnerOrInstructorRole(actor);
        throw unresolvedCo03Binding("Learning material ancestry lookup");
    }

    // =========================================================================
    //  Internal Helpers & Explicit Identity Boundary Binding
    // =========================================================================

    private Optional<Course> findPublishedCourse(UUID courseId) {
        if (courseId == null) {
            return Optional.empty();
        }
        return courseRepository.findPublishedById(courseId)
                .filter(course -> course.publicationStatus() == PublishStatus.PUBLISHED);
    }

    private Optional<Lesson> findPublishedLesson(UUID courseId, UUID lessonId) {
        return courseContentRepository.findPublishedLessonsByCourseId(courseId).stream()
                .filter(lesson -> lessonId.equals(lesson.id()))
                .filter(lesson -> lesson.publicationStatus() == PublishStatus.PUBLISHED)
                .findFirst();
    }

    private void requirePublishedLesson(UUID courseId, UUID lessonId) {
        if (findPublishedLesson(courseId, lessonId).isEmpty()) {
            throw new ResourceNotFoundException("Lesson not found or not published in course");
        }
    }

    private Course requireOwnedPublishedCourse(UUID instructorId, UUID courseId) {
        Course course = requirePublishedCourse(courseId);
        if (!instructorId.equals(course.instructorId())) {
            throw new AccessDeniedException("Instructor does not own this course");
        }
        return course;
    }

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

    private void requireActiveOrCompletedMembership(UUID learnerId, UUID courseId) {
        if (enrollmentRepository == null) {
            throw unresolvedCo03Binding("EnrollmentRepository membership check");
        }
        if (!enrollmentRepository.existsByUserIdAndCourseIdAndStatusIn(
                learnerId, courseId, PERMITTED_LEARNING_STATUSES)) {
            throw new AccessDeniedException("Learner does not have active or completed enrollment in course");
        }
    }

    private static IllegalStateException unresolvedCo03Binding(String capability) {
        return new IllegalStateException("Unresolved CO03 binding: " + capability);
    }

    /**
     * Explicit unresolved identity binding when Auth's current-identity producer is not yet wired.
     * Fails closed without fabricating identity or reading security internals.
     */
    private static CurrentActor unresolvedIdentityBinding() throws AuthenticationException {
        throw new AuthenticationCredentialsNotFoundException(
                "CT01.identity (U01) current-identity producer is not bound");
    }

    /** Internal representation of the authenticated actor. */
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
