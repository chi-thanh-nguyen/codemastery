package com.codemastery.modules.course.infrastructure.persistence.mapper;

import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.infrastructure.persistence.entity.CourseEntity;
import org.springframework.stereotype.Component;


@Component
public class CourseMapper {

    public CourseEntity toEntity(Course domain) {
        if (domain == null) {
            return null;
        }
        return new CourseEntity(
                domain.id(),
                domain.instructorId(),
                domain.title(),
                domain.description(),
                domain.prerequisiteDescription(),
                toCode(domain.publicationStatus()),
                domain.adaptiveEnabled(),
                domain.adaptiveSettings());
    }

    public Course toDomain(CourseEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Course(
                entity.getId(),
                entity.getInstructorId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getPrerequisiteDescription(),
                toPublishStatus(entity.getPublicationStatus()),
                entity.isAdaptiveEnabled(),
                entity.getAdaptiveSettings());
    }

    static String toCode(PublishStatus status) {
        return status == null ? null : status.getStatus();
    }

    /** Converts string code to PublishStatus, case-insensitively. */
    static PublishStatus toPublishStatus(String code) {
        if (code == null) {
            return null;
        }
        for (PublishStatus status : PublishStatus.values()) {
            if (status.getStatus().equalsIgnoreCase(code) || status.name().equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown publication status code: " + code);
    }
}
