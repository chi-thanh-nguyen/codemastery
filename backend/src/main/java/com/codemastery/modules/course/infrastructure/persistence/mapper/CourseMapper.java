package com.codemastery.modules.course.infrastructure.persistence.mapper;

import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.infrastructure.persistence.entity.CourseEntity;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class CourseMapper {

    public CourseEntity toEntity(Course domain) {
        if (domain == null) {
            return null;
        }
        String publicationStatus = domain.publicationStatus() != null ? domain.publicationStatus().getStatus() : null;
        return new CourseEntity(
                domain.id(),
                domain.instructorId(),
                domain.title(),
                domain.description(),
                domain.prerequisiteDescription(),
                publicationStatus,
                domain.adaptiveEnabled(),
                domain.adaptiveSettings()
        );
    }

    public Course toDomain(CourseEntity entity) {
        if (entity == null) {
            return null;
        }
        PublishStatus publicationStatus = toPublishStatus(entity.getPublicationStatus());
        return new Course(
                entity.getId(),
                entity.getInstructorId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getPrerequisiteDescription(),
                publicationStatus,
                entity.isAdaptiveEnabled(),
                entity.getAdaptiveSettings()
        );
    }

    private PublishStatus toPublishStatus(String status) {
        if (status == null) {
            return null;
        }
        for (PublishStatus ps : PublishStatus.values()) {
            if (ps.getStatus().equalsIgnoreCase(status) || ps.name().equalsIgnoreCase(status)) {
                return ps;
            }
        }
        throw new IllegalArgumentException("Unknown publication status: " + status);
    }
}
