package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Objects;
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

    @Override
    public Page<Course> searchPublishedCourses(Pageable pageable, String query, String prerequisites) {
        return courseJpaRepository.searchPublishedCatalogue(
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()), query, prerequisites)
                .map(courseMapper::toDomain);
    }

    @Override
    public Optional<AccessSnapshot> findCourseAccessSnapshot(
            UUID actorId, UUID courseId, UUID lessonId, UUID materialId) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(courseId, "courseId");
        return courseJpaRepository.findCourseAccessSnapshot(actorId, courseId, lessonId, materialId)
                .map(CourseRepositoryImpl::toAccessSnapshot);
    }

    @Override
    public Optional<AccessSnapshot> findLessonAccessSnapshot(UUID actorId, UUID lessonId) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(lessonId, "lessonId");
        return courseJpaRepository.findLessonAccessSnapshot(actorId, lessonId)
                .map(CourseRepositoryImpl::toAccessSnapshot);
    }

    private static AccessSnapshot toAccessSnapshot(CourseJpaRepository.AccessRow row) {
        if (row.getCourseId() == null || row.getInstructorId() == null || row.getCourseStatus() == null
                || (row.getLessonId() == null) != (row.getLessonStatus() == null)
                || (row.getMaterialId() == null) != (row.getMaterialStatus() == null)
                || row.getMaterialId() != null && row.getLessonId() == null) {
            throw new IllegalStateException("Invalid Course access snapshot");
        }
        return new AccessSnapshot(row.getCourseId(), row.getInstructorId(), publication(row.getCourseStatus()),
                row.getLessonId(), publication(row.getLessonStatus()),
                row.getMaterialId(), publication(row.getMaterialStatus()), enrollment(row.getEnrollmentStatus()));
    }

    private static PublishStatus publication(String code) {
        if (code == null) return null;
        return switch (code) {
            case "published" -> PublishStatus.PUBLISHED;
            case "hidden" -> PublishStatus.HIDDEN;
            default -> throw new IllegalStateException("Invalid publication status in Course access snapshot");
        };
    }

    private static EnrollmentStatus enrollment(String code) {
        if (code == null) return null;
        return switch (code) {
            case "active" -> EnrollmentStatus.ACTIVE;
            case "completed" -> EnrollmentStatus.COMPLETED;
            case "left" -> EnrollmentStatus.LEFT;
            default -> throw new IllegalStateException("Invalid enrollment status in Course access snapshot");
        };
    }
}
