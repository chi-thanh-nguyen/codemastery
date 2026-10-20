package com.codemastery.modules.course.domain.repository;

import com.codemastery.modules.course.domain.model.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {

    Optional<Course> findById(UUID id);

    Optional<Course> findPublishedById(UUID id);

    Page<Course> findPublishedCourses(Pageable pageable);

    default Page<Course> findAllPublished(Pageable pageable) {
        return findPublishedCourses(pageable);
    }

    boolean existsById(UUID id);

    Course save(Course course);
}
