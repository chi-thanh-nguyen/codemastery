package com.codemastery.modules.assessment.domain.repository;

import com.codemastery.modules.assessment.domain.model.Assignment;
import com.codemastery.modules.assessment.domain.model.Submission;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository {

    record SubmissionCounts(int submissionCount, int gradedCount) {
        public static final SubmissionCounts ZERO = new SubmissionCounts(0, 0);

        public SubmissionCounts {
            if (submissionCount < 0 || gradedCount < 0 || gradedCount > submissionCount) {
                throw new IllegalArgumentException("Invalid submission counts");
            }
        }
    }

    Assignment insert(Assignment assignment);

    void updateDefinition(UUID assignmentId, UUID courseId, UUID lessonId, String title,
                          String instructions, Instant deadlineAt);

    void updatePublicationStatus(UUID assignmentId, UUID courseId, String publicationStatus);

    boolean existsInCourse(UUID assignmentId, UUID courseId);

    Optional<Assignment> findInCourse(UUID assignmentId, UUID courseId);

    Optional<Assignment> findInCourseForUpdate(UUID assignmentId, UUID courseId);

    long countInCourse(UUID courseId);

    List<Assignment> findPageInCourse(UUID courseId, int page, int size);

    /** Published assignments in a Course (excluding assignments bound to hidden lessons). */
    long countPublishedVisibleInCourse(UUID courseId);

    /** Learner page ordered by {@code deadline_at ASC, id ASC} (ADR010 §12). */
    List<Assignment> findPublishedVisiblePageInCourse(UUID courseId, int page, int size);

    Map<UUID, SubmissionCounts> countSubmissions(Collection<UUID> assignmentIds);

    Optional<Submission> findSubmissionByAssignmentAndLearner(UUID assignmentId, UUID userId);

    /** Locks the learner's submission row {@code FOR UPDATE} after the Assignment lock is held. */
    Optional<Submission> findSubmissionByAssignmentAndLearnerForUpdate(UUID assignmentId, UUID userId);

    Map<UUID, Submission> findSubmissionsByAssignmentsAndLearner(Collection<UUID> assignmentIds, UUID userId);

    Optional<Submission> findSubmissionInAssignment(UUID submissionId, UUID assignmentId);

    /** Locks the submission row {@code FOR UPDATE} after the Assignment lock is held. */
    Optional<Submission> findSubmissionInAssignmentForUpdate(UUID submissionId, UUID assignmentId);

    long countSubmissionsByAssignment(UUID assignmentId);

    /** Owner submission page ordered by {@code submitted_at ASC, id ASC} (ADR010 §12). */
    List<Submission> findSubmissionPageByAssignment(UUID assignmentId, int page, int size);

    Submission insertSubmission(Submission submission);

    void replaceSubmissionFile(UUID submissionId, UUID assignmentId, UUID userId,
                               String objectKey, String originalFilename, Instant submittedAt);

    void gradeSubmission(UUID submissionId, UUID assignmentId,
                         BigDecimal gradePercentage, String feedback, Instant gradedAt);
}