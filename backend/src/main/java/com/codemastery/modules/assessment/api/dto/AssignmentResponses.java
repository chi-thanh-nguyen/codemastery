package com.codemastery.modules.assessment.api.dto;

import com.codemastery.modules.assessment.application.service.AssignmentService.LearnerAssignmentSummaryView;
import com.codemastery.modules.assessment.application.service.AssignmentService.LearnerAssignmentView;
import com.codemastery.modules.assessment.application.service.AssignmentService.LearnerSubmissionView;
import com.codemastery.modules.assessment.application.service.AssignmentService.OwnedAssignmentView;
import com.codemastery.modules.assessment.application.service.SubmissionService.SubmissionSummaryView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * CT10.rest assignment and submission responses (OpenAPI B5OwnedAssignment,
 * B5LearnerAssignmentSummary, B5LearnerAssignment, B5LearnerSubmission, B5SubmissionSummary).
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

    public record LearnerAssignmentSummaryResponse(
            UUID id,
            UUID courseId,
            @JsonInclude(JsonInclude.Include.ALWAYS) UUID lessonId,
            String title,
            String deadlineAt,
            boolean open,
            String submissionStatus) {

        public static LearnerAssignmentSummaryResponse from(LearnerAssignmentSummaryView view) {
            Objects.requireNonNull(view, "view");
            return new LearnerAssignmentSummaryResponse(
                    view.id(),
                    view.courseId(),
                    view.lessonId(),
                    view.title(),
                    view.deadlineAt().toString(),
                    view.open(),
                    view.submissionStatus());
        }
    }

    public record LearnerSubmissionResponse(
            String originalFilename,
            String submittedAt,
            boolean graded,
            @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal gradePercentage,
            @JsonInclude(JsonInclude.Include.ALWAYS) String feedback,
            @JsonInclude(JsonInclude.Include.ALWAYS) String gradedAt) {

        public static LearnerSubmissionResponse from(LearnerSubmissionView view) {
            Objects.requireNonNull(view, "view");
            return new LearnerSubmissionResponse(
                    view.originalFilename(),
                    view.submittedAt().toString(),
                    view.graded(),
                    view.gradePercentage(),
                    view.feedback(),
                    view.gradedAt() == null ? null : view.gradedAt().toString());
        }
    }

    public record LearnerAssignmentResponse(
            UUID id,
            UUID courseId,
            @JsonInclude(JsonInclude.Include.ALWAYS) UUID lessonId,
            String title,
            String instructions,
            String deadlineAt,
            boolean open,
            @JsonInclude(JsonInclude.Include.ALWAYS) LearnerSubmissionResponse submission) {

        public static LearnerAssignmentResponse from(LearnerAssignmentView view) {
            Objects.requireNonNull(view, "view");
            return new LearnerAssignmentResponse(
                    view.id(),
                    view.courseId(),
                    view.lessonId(),
                    view.title(),
                    view.instructions(),
                    view.deadlineAt().toString(),
                    view.open(),
                    view.submission() == null ? null : LearnerSubmissionResponse.from(view.submission()));
        }
    }

    public record SubmissionSummaryResponse(
            UUID id,
            UUID assignmentId,
            UUID learnerId,
            @JsonInclude(JsonInclude.Include.ALWAYS) String learnerDisplayName,
            String originalFilename,
            String submittedAt,
            boolean graded,
            @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal gradePercentage,
            @JsonInclude(JsonInclude.Include.ALWAYS) String feedback,
            @JsonInclude(JsonInclude.Include.ALWAYS) String gradedAt) {

        public static SubmissionSummaryResponse from(SubmissionSummaryView view) {
            Objects.requireNonNull(view, "view");
            return new SubmissionSummaryResponse(
                    view.id(),
                    view.assignmentId(),
                    view.learnerId(),
                    view.learnerDisplayName(),
                    view.originalFilename(),
                    view.submittedAt().toString(),
                    view.graded(),
                    view.gradePercentage(),
                    view.feedback(),
                    view.gradedAt() == null ? null : view.gradedAt().toString());
        }
    }
}