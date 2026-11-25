package com.codemastery.modules.assessment.domain.repository;

import com.codemastery.modules.assessment.domain.model.Assignment;
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

    Map<UUID, SubmissionCounts> countSubmissions(Collection<UUID> assignmentIds);
}
