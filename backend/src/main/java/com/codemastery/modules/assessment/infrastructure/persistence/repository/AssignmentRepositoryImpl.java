package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.domain.model.Assignment;
import com.codemastery.modules.assessment.domain.repository.AssignmentRepository;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.AssignmentEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.mapper.AssignmentMapper;
import jakarta.persistence.EntityManager;
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
 * V004 assignment persistence (ADR010 §§8, 10, 12; ADR012 §19.2).
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
            // Refresh after acquiring FOR UPDATE so state reflects the latest committed row (ADR010 §10).
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
}
