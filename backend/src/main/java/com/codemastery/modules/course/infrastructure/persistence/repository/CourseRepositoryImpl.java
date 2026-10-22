package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class CourseRepositoryImpl implements CourseRepository {

    private static final String STATUS_PUBLISHED = "published";

    private final CourseJpaRepository courseJpaRepository;
    private final CourseMapper courseMapper;

    public CourseRepositoryImpl(CourseJpaRepository courseJpaRepository, CourseMapper courseMapper) {
        this.courseJpaRepository = courseJpaRepository;
        this.courseMapper = courseMapper;
    }

    @Override
    public Optional<Course> findPublishedById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return courseJpaRepository.findByIdAndPublicationStatus(id, STATUS_PUBLISHED)
                .map(courseMapper::toDomain);
    }

    @Override
    public Page<Course> findPublishedCourses(Pageable pageable) {
        // SQL owns the fixed CT21 order. Never let Spring Data append caller sorting.
        Pageable pagination = pageable == null ? PageRequest.of(0, 20)
                : pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
                : Pageable.unpaged();
        return courseJpaRepository.findPublishedCatalogue(pagination).map(courseMapper::toDomain);
    }
}
