package com.codemastery.modules.assessment.api.dto;

import com.codemastery.modules.assessment.application.service.AssignmentService.OwnedAssignmentView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import java.util.UUID;

/**
 * CT10.rest assignment responses (OpenAPI B5OwnedAssignment).
 */
public final class AssignmentResponses {

    private AssignmentResponses() {
    }

    public record OwnedAssignmentResponse(
            UUID id,
            UUID courseId,
            @JsonInclude(JsonInclude.Include.ALWAYS) UUID lessonId,
            String title,
            String instructions,
            String deadlineAt,
            String publicationStatus,
            int submissionCount,
            int gradedCount) {

        public static OwnedAssignmentResponse from(OwnedAssignmentView view) {
            Objects.requireNonNull(view, "view");
            return new OwnedAssignmentResponse(
                    view.id(),
                    view.courseId(),
                    view.lessonId(),
                    view.title(),
                    view.instructions(),
                    view.deadlineAt().toString(),
                    view.publicationStatus(),
                    view.submissionCount(),
                    view.gradedCount());
        }
    }
}
