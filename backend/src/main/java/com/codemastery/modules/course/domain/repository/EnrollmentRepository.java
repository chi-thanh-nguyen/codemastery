package com.codemastery.modules.course.domain.repository;

import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.LessonProgress;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Course-internal repository port for Enrollment and LessonProgress.
 */
public interface EnrollmentRepository {

    /** Finds enrollment by its primary ID. */
    Optional<Enrollment> findById(UUID id);

    /** CO03.enrolment-progress: finds enrollment row for learner and course. */
    Optional<Enrollment> findByUserIdAndCourseId(UUID userId, UUID courseId);

    /**
     * CO03.access (CT03 stage 4): checks if learner has an enrollment in one of the given statuses
     * (e.g. ACTIVE or COMPLETED).
     */
    boolean existsByUserIdAndCourseIdAndStatusIn(UUID userId, UUID courseId, Set<EnrollmentStatus> statuses);

    /** Persists an enrollment. */
    Enrollment save(Enrollment enrollment);

    /** CO03.enrolment-progress: finds progress for a specific lesson within an enrollment. */
    Optional<LessonProgress> findProgressByEnrollmentIdAndLessonId(UUID enrollmentId, UUID lessonId);

    /** CO03.enrolment-progress: finds all lesson progress records for an enrollment. */
    List<LessonProgress> findProgressByEnrollmentId(UUID enrollmentId);

    /** Persists a lesson progress record. */
    LessonProgress saveLessonProgress(LessonProgress progress);
}
