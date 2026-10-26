package com.codemastery.modules.course.domain.repository;

import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.PublishStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/** Course-internal published catalogue reads (CO03.catalogue). */
public interface CourseRepository {

    /** Published course detail; hidden or missing returns empty. */
    Optional<Course> findPublishedById(UUID id);

    /**
     * Published courses, filtered before paging/counting and ordered by PostgreSQL
     * title COLLATE "C" ASC, id ASC. Caller sorting cannot change this order.
     */
    Page<Course> findPublishedCourses(Pageable pageable);

    Page<Course> searchPublishedCourses(Pageable pageable, String query, String prerequisites);

    /** One Course-owned query snapshot; optional descendants stay scoped to this Course. */
    Optional<AccessSnapshot> findCourseAccessSnapshot(
            UUID actorId, UUID courseId, UUID lessonId, UUID materialId);

    /** Privately resolves Lesson ancestry and admission facts in one query snapshot. */
    Optional<AccessSnapshot> findLessonAccessSnapshot(UUID actorId, UUID lessonId);

    /** Internal admission facts only; never exported through the Course application boundary. */
    record AccessSnapshot(
            UUID courseId, UUID instructorId, PublishStatus courseStatus,
            UUID lessonId, PublishStatus lessonStatus,
            UUID materialId, PublishStatus materialStatus, EnrollmentStatus enrollmentStatus) {}
}
