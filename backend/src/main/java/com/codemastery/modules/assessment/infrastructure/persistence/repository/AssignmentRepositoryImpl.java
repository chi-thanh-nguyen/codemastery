package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.domain.model.Assignment;
import com.codemastery.modules.assessment.domain.model.Submission;
import com.codemastery.modules.assessment.domain.repository.AssignmentRepository;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.AssignmentEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.SubmissionEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.mapper.AssignmentMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * V004 assignment and submission persistence (ADR010 §§8, 10, 12; ADR012 §19.2).
 * Callers own transactions: writes run at READ COMMITTED under explicit row locks where required,
 * and reads run inside a single read-only REPEATABLE READ snapshot.
 */
@Repository
public class AssignmentRepositoryImpl implements AssignmentRepository {

    private final AssignmentJpaRepository assignments;
    private final SubmissionJpaRepository submissions;
    private final EntityManager entityManager;

    public AssignmentRepositoryImpl(AssignmentJpaRepository assignments,
                                    SubmissionJpaRepository submissions,
                                    EntityManager entityManager) {
        this.assignments = Objects.requireNonNull(assignments, "assignments");
        this.submissions = Objects.requireNonNull(submissions, "submissions");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public List<Assignment> findReminderCandidates(Instant now, Instant windowEnd, Instant afterDeadline, UUID afterId, int limit) {
        return assignments.reminderCandidates(now, windowEnd, afterDeadline, afterId, limit).stream()
                .map(AssignmentMapper::toDomain).toList();
    }

    @Override
    public Optional<Assignment> lockNotificationState(UUID assignmentId) {
        return assignments.lockNotificationState(assignmentId).map(entity -> {
            entityManager.refresh(entity);
            return AssignmentMapper.toDomain(entity);
        });
    }

    @Override
    public Set<UUID> submittedLearnerIds(UUID assignmentId) {
        return Set.copyOf(submissions.submittedLearnerIds(assignmentId));
    }

    @Override
    public Assignment insert(Assignment assignment) {
        AssignmentEntity entity = AssignmentMapper.toEntity(assignment);
        entityManager.persist(entity);
        entityManager.flush();
        return AssignmentMapper.toDomain(entity);
    }

    @Override
    public void updateDefinition(UUID assignmentId, UUID courseId, UUID lessonId, String title,
                                 String instructions, Instant deadlineAt) {
        Objects.requireNonNull(assignmentId, "assignmentId");
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(instructions, "instructions");
        Objects.requireNonNull(deadlineAt, "deadlineAt");
        if (assignments.updateDefinition(assignmentId, courseId, lessonId, title, instructions, deadlineAt) != 1) {
            throw new IllegalStateException("Assignment replacement lost its row or named another Course");
        }
    }

    @Override
    public void updatePublicationStatus(UUID assignmentId, UUID courseId, String publicationStatus) {
        Objects.requireNonNull(assignmentId, "assignmentId");
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(publicationStatus, "publicationStatus");
        if (assignments.updatePublicationStatus(assignmentId, courseId, publicationStatus) != 1) {
            throw new IllegalStateException("Assignment publication update lost its row or named another Course");
        }
    }

    @Override
    public boolean existsInCourse(UUID assignmentId, UUID courseId) {
        return assignments.existsInCourse(assignmentId, courseId);
    }

    @Override
    public Optional<Assignment> findInCourse(UUID assignmentId, UUID courseId) {
        return assignments.findInCourse(assignmentId, courseId).map(AssignmentMapper::toDomain);
    }

    @Override
    public Optional<Assignment> findInCourseForUpdate(UUID assignmentId, UUID courseId) {
        return assignments.lockInCourse(assignmentId, courseId).map(entity -> {
            entityManager.refresh(entity);
            return AssignmentMapper.toDomain(entity);
        });
    }

    @Override
    public long countInCourse(UUID courseId) {
        return assignments.countInCourse(courseId);
    }

    @Override
    public List<Assignment> findPageInCourse(UUID courseId, int page, int size) {
        long offset = (long) page * size;
        return assignments.findPageInCourse(courseId, size, offset).stream()
                .map(AssignmentMapper::toDomain)
                .toList();
    }

    @Override
    public long countPublishedInCourse(UUID courseId) {
        return assignments.countPublishedInCourse(courseId);
    }

    @Override
    public List<Assignment> findPublishedPageInCourse(UUID courseId, int page, int size) {
        long offset = (long) page * size;
        return assignments.findPublishedPageInCourse(courseId, size, offset).stream()
                .map(AssignmentMapper::toDomain)
                .toList();
    }

    @Override
    public Map<UUID, SubmissionCounts> countSubmissions(Collection<UUID> assignmentIds) {
        if (assignmentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SubmissionCounts> counts = new HashMap<>();
        for (Object[] row : submissions.countByAssignmentIds(Set.copyOf(assignmentIds))) {
            counts.put(
                    (UUID) row[0],
                    new SubmissionCounts(Math.toIntExact((Long) row[1]), Math.toIntExact((Long) row[2])));
        }
        return Map.copyOf(counts);
    }

    @Override
    public Optional<Submission> findSubmissionByAssignmentAndLearner(UUID assignmentId, UUID userId) {
        return submissions.findByAssignmentAndLearner(assignmentId, userId).map(AssignmentRepositoryImpl::toDomain);
    }

    @Override
    public Optional<Submission> findSubmissionByAssignmentAndLearnerForUpdate(UUID assignmentId, UUID userId) {
        return submissions.lockByAssignmentAndLearner(assignmentId, userId).map(entity -> {
            entityManager.refresh(entity);
            return toDomain(entity);
        });
    }

    @Override
    public Map<UUID, Submission> findSubmissionsByAssignmentsAndLearner(Collection<UUID> assignmentIds, UUID userId) {
        if (assignmentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Submission> byAssignment = new HashMap<>();
        for (SubmissionEntity entity : submissions.findByAssignmentsAndLearner(Set.copyOf(assignmentIds), userId)) {
            byAssignment.put(entity.getAssignmentId(), toDomain(entity));
        }
        return Map.copyOf(byAssignment);
    }

    @Override
    public Optional<Submission> findSubmissionInAssignment(UUID submissionId, UUID assignmentId) {
        return submissions.findInAssignment(submissionId, assignmentId).map(AssignmentRepositoryImpl::toDomain);
    }

    @Override
    public Optional<Submission> findSubmissionInAssignmentForUpdate(UUID submissionId, UUID assignmentId) {
        return submissions.lockInAssignment(submissionId, assignmentId).map(entity -> {
            entityManager.refresh(entity);
            return toDomain(entity);
        });
    }

    @Override
    public long countSubmissionsByAssignment(UUID assignmentId) {
        return submissions.countByAssignment(assignmentId);
    }

    @Override
    public List<Submission> findSubmissionPageByAssignment(UUID assignmentId, int page, int size) {
        long offset = (long) page * size;
        return submissions.findPageByAssignment(assignmentId, size, offset).stream()
                .map(AssignmentRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public Submission insertSubmission(Submission submission) {
        Objects.requireNonNull(submission, "submission");
        SubmissionEntity entity = new SubmissionEntity(
                submission.id(),
                submission.assignment_id(),
                submission.user_id(),
                submission.object_key(),
                submission.original_filename(),
                submission.submitted_at(),
                submission.grade_percentage(),
                submission.feedback(),
                submission.graded_at()
        );
        entityManager.persist(entity);
        entityManager.flush();
        return toDomain(entity);
    }

    @Override
    public void replaceSubmissionFile(UUID submissionId, UUID assignmentId, UUID userId,
                                      String objectKey, String originalFilename, Instant submittedAt) {
        if (submissions.replaceFile(submissionId, assignmentId, userId, objectKey, originalFilename, submittedAt) != 1) {
            throw new IllegalStateException("Submission file replacement lost its row");
        }
    }

    @Override
    public void gradeSubmission(UUID submissionId, UUID assignmentId,
                                BigDecimal gradePercentage, String feedback, Instant gradedAt) {
        if (submissions.updateGrade(submissionId, assignmentId, gradePercentage, feedback, gradedAt) != 1) {
            throw new IllegalStateException("Submission grade update lost its row");
        }
    }

    private static Submission toDomain(SubmissionEntity entity) {
        return new Submission(
                entity.getId(),
                entity.getAssignmentId(),
                entity.getUserId(),
                entity.getObjectKey(),
                entity.getOriginalFilename(),
                entity.getSubmittedAt(),
                entity.getGradePercentage(),
                entity.getFeedback(),
                entity.getGradedAt()
        );
    }
}