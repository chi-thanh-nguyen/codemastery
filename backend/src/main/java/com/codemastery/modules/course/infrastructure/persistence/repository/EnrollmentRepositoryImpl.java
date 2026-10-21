package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.LessonProgress;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository;
import com.codemastery.modules.course.infrastructure.persistence.entity.EnrollmentEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonProgressEntity;
import com.codemastery.modules.course.infrastructure.persistence.mapper.EnrollmentMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class EnrollmentRepositoryImpl implements EnrollmentRepository {

    private final EnrollmentJpaRepository enrollmentJpaRepository;
    private final LessonProgressJpaRepository lessonProgressJpaRepository;
    private final EnrollmentMapper enrollmentMapper;

    public EnrollmentRepositoryImpl(
            EnrollmentJpaRepository enrollmentJpaRepository,
            LessonProgressJpaRepository lessonProgressJpaRepository,
            EnrollmentMapper enrollmentMapper) {
        this.enrollmentJpaRepository = enrollmentJpaRepository;
        this.lessonProgressJpaRepository = lessonProgressJpaRepository;
        this.enrollmentMapper = enrollmentMapper;
    }

    @Override
    public Optional<Enrollment> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return enrollmentJpaRepository.findById(id).map(enrollmentMapper::toDomain);
    }

    @Override
    public Optional<Enrollment> findByUserIdAndCourseId(UUID userId, UUID courseId) {
        if (userId == null || courseId == null) {
            return Optional.empty();
        }
        return enrollmentJpaRepository.findByUserIdAndCourseId(userId, courseId)
                .map(enrollmentMapper::toDomain);
    }

    @Override
    public boolean existsByUserIdAndCourseIdAndStatusIn(UUID userId, UUID courseId, Set<EnrollmentStatus> statuses) {
        if (userId == null || courseId == null || statuses == null || statuses.isEmpty()) {
            return false;
        }
        List<String> statusCodes = statuses.stream()
                .filter(Objects::nonNull)
                .map(EnrollmentStatus::getStatus)
                .sorted()
                .toList();
        if (statusCodes.isEmpty()) {
            return false;
        }
        return enrollmentJpaRepository.existsByUserIdAndCourseIdAndStatusIn(userId, courseId, statusCodes);
    }

    @Override
    public Enrollment save(Enrollment enrollment) {
        if (enrollment == null) {
            return null;
        }
        EnrollmentEntity entity = enrollmentMapper.toEntity(enrollment);
        EnrollmentEntity saved = enrollmentJpaRepository.save(entity);
        return enrollmentMapper.toDomain(saved);
    }

    @Override
    public Optional<LessonProgress> findProgressByEnrollmentIdAndLessonId(UUID enrollmentId, UUID lessonId) {
        if (enrollmentId == null || lessonId == null) {
            return Optional.empty();
        }
        return lessonProgressJpaRepository.findByEnrollmentIdAndLessonId(enrollmentId, lessonId)
                .map(enrollmentMapper::toDomain);
    }

    @Override
    public List<LessonProgress> findProgressByEnrollmentId(UUID enrollmentId) {
        if (enrollmentId == null) {
            return List.of();
        }
        return lessonProgressJpaRepository.findByEnrollmentId(enrollmentId).stream()
                .map(enrollmentMapper::toDomain)
                .toList();
    }

    @Override
    public LessonProgress saveLessonProgress(LessonProgress progress) {
        if (progress == null) {
            return null;
        }
        LessonProgressEntity entity = enrollmentMapper.toEntity(progress);
        LessonProgressEntity saved = lessonProgressJpaRepository.save(entity);
        return enrollmentMapper.toDomain(saved);
    }
}
