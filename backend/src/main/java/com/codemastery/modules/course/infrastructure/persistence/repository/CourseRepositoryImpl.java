package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.infrastructure.persistence.entity.CourseEntity;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class CourseRepositoryImpl implements CourseRepository {

    private static final Sort DEFAULT_CATALOGUE_SORT = Sort.by(
            Sort.Order.asc("title"),
            Sort.Order.asc("id")
    );
    private static final String STATUS_PUBLISHED = "published";

    private final CourseJpaRepository courseJpaRepository;
    private final CourseMapper courseMapper;

    public CourseRepositoryImpl(CourseJpaRepository courseJpaRepository, CourseMapper courseMapper) {
        this.courseJpaRepository = courseJpaRepository;
        this.courseMapper = courseMapper;
    }

    @Override
    public Optional<Course> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return courseJpaRepository.findById(id).map(courseMapper::toDomain);
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
        Pageable effectivePageable;
        if (pageable == null) {
            effectivePageable = PageRequest.of(0, 20, DEFAULT_CATALOGUE_SORT);
        } else if (pageable.isPaged() && pageable.getSort().isUnsorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_CATALOGUE_SORT);
        } else if (!pageable.isPaged() && pageable.getSort().isUnsorted()) {
            effectivePageable = Pageable.unpaged(DEFAULT_CATALOGUE_SORT);
        } else {
            effectivePageable = pageable;
        }
        return courseJpaRepository.findByPublicationStatus(STATUS_PUBLISHED, effectivePageable)
                .map(courseMapper::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        if (id == null) {
            return false;
        }
        return courseJpaRepository.existsById(id);
    }

    @Override
    public Course save(Course course) {
        if (course == null) {
            return null;
        }
        CourseEntity entity = courseMapper.toEntity(course);
        CourseEntity saved = courseJpaRepository.save(entity);
        return courseMapper.toDomain(saved);
    }
}
