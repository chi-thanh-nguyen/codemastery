package com.codemastery.modules.course.domain.repository;

import com.codemastery.modules.course.domain.model.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * Course-internal read/write port for Course aggregate.
 */
public interface CourseRepository {

    /** Finds course by ID in either publication state (for ownership and preview checks). */
    Optional<Course> findById(UUID id);

    /** CO03.catalogue / CO03.access: finds published course detail; hidden or missing returns empty. */
    Optional<Course> findPublishedById(UUID id);

    /**
     * CO03.catalogue (CT03 / CT21): published courses only, filtered before paging and counting,
     * ordered by title ascending and id ascending.
     */
    Page<Course> findPublishedCourses(Pageable pageable);

    /** Overload for int page, int size parameters. */
    default Page<Course> findPublishedCourses(int page, int size) {
        return findPublishedCourses(PageRequest.of(page, size));
    }

    /** Alias for findPublishedCourses. */
    default Page<Course> findAllPublished(Pageable pageable) {
        return findPublishedCourses(pageable);
    }

    /** Checks whether a course exists by ID. */
    boolean existsById(UUID id);

    /** Persists a course. */
    Course save(Course course);
}
