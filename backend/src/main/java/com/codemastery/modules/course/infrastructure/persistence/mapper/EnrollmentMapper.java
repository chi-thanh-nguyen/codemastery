package com.codemastery.modules.course.infrastructure.persistence.mapper;

import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.LessonProgress;
import com.codemastery.modules.course.infrastructure.persistence.entity.EnrollmentEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonProgressEntity;
import org.springframework.stereotype.Component;


@Component
public class EnrollmentMapper {

    public EnrollmentEntity toEntity(Enrollment domain) {
        if (domain == null) {
            return null;
        }
        return new EnrollmentEntity(
                domain.id(),
                domain.userId(),
                domain.courseId(),
                toCode(domain.status()),
                domain.resumeLessonId());
    }

    public Enrollment toDomain(EnrollmentEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Enrollment(
                entity.getId(),
                entity.getUserId(),
                entity.getCourseId(),
                toEnrollmentStatus(entity.getStatus()),
                entity.getResumeLessonId());
    }

    public LessonProgressEntity toEntity(LessonProgress domain) {
        if (domain == null) {
            return null;
        }
        return new LessonProgressEntity(
                domain.id(),
                domain.enrollmentId(),
                domain.lessonId(),
                domain.completed(),
                domain.masterySkipped());
    }

    public LessonProgress toDomain(LessonProgressEntity entity) {
        if (entity == null) {
            return null;
        }
        return new LessonProgress(
                entity.getId(),
                entity.getEnrollmentId(),
                entity.getLessonId(),
                entity.isCompleted(),
                entity.isMasterySkipped());
    }

    static String toCode(EnrollmentStatus status) {
        return status == null ? null : status.getStatus();
    }

    /** Accepts only the exact V002 codes ('active', 'completed', 'left'). */
    static EnrollmentStatus toEnrollmentStatus(String code) {
        if (code == null) {
            return null;
        }
        for (EnrollmentStatus status : EnrollmentStatus.values()) {
            if (status.getStatus().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown enrollment status code");
    }
}
