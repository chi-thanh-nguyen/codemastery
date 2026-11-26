package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.infrastructure.persistence.entity.SubmissionEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionJpaRepository extends JpaRepository<SubmissionEntity, UUID> {

    @Query("SELECT s.assignment_id, COUNT(s), COUNT(s.graded_at) FROM SubmissionEntity s "
            + "WHERE s.assignment_id IN :assignmentIds GROUP BY s.assignment_id")
    List<Object[]> countByAssignmentIds(@Param("assignmentIds") Collection<UUID> assignmentIds);
}
