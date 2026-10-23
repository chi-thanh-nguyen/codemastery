package com.codemastery.modules.course.domain.repository;

import com.codemastery.modules.course.domain.model.Course;
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
}
