package com.codemastery.modules.assessment.infrastructure.persistence.mapper;

import com.codemastery.modules.assessment.domain.model.Assignment;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.AssignmentEntity;
import java.util.Objects;

public final class AssignmentMapper {

    private AssignmentMapper() {
    }

    public static AssignmentEntity toEntity(Assignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        return new AssignmentEntity(
                Objects.requireNonNull(assignment.id(), "id"),
                Objects.requireNonNull(assignment.course_id(), "course_id"),
                assignment.lesson_id(),
                Objects.requireNonNull(assignment.title(), "title"),
                Objects.requireNonNull(assignment.instructions(), "instructions"),
                Objects.requireNonNull(assignment.deadline_at(), "deadline_at"),
                Objects.requireNonNull(assignment.publication_status(), "publication_status"));
    }

    public static Assignment toDomain(AssignmentEntity entity) {
        Objects.requireNonNull(entity, "entity");
        return new Assignment(
                Objects.requireNonNull(entity.getId(), "id"),
                Objects.requireNonNull(entity.getCourseId(), "course_id"),
                entity.getLessonId(),
                Objects.requireNonNull(entity.getTitle(), "title"),
                Objects.requireNonNull(entity.getInstructions(), "instructions"),
                Objects.requireNonNull(entity.getDeadlineAt(), "deadline_at"),
                Objects.requireNonNull(entity.getPublicationStatus(), "publication_status"));
    }
}
