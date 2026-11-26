package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.infrastructure.persistence.entity.SubmissionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionJpaRepository extends JpaRepository<SubmissionEntity, UUID> {

    @Query("SELECT s.assignment_id, COUNT(s), COUNT(s.graded_at) FROM SubmissionEntity s "
            + "WHERE s.assignment_id IN :assignmentIds GROUP BY s.assignment_id")
    List<Object[]> countByAssignmentIds(@Param("assignmentIds") Collection<UUID> assignmentIds);

    @Query("SELECT s FROM SubmissionEntity s WHERE s.assignment_id = :assignmentId AND s.user_id = :userId")
    Optional<SubmissionEntity> findByAssignmentAndLearner(@Param("assignmentId") UUID assignmentId,
                                                          @Param("userId") UUID userId);

    @Query(value = "SELECT * FROM public.submissions WHERE assignment_id = :assignmentId AND user_id = :userId FOR UPDATE",
            nativeQuery = true)
    Optional<SubmissionEntity> lockByAssignmentAndLearner(@Param("assignmentId") UUID assignmentId,
                                                          @Param("userId") UUID userId);

    @Query("SELECT s FROM SubmissionEntity s WHERE s.assignment_id IN :assignmentIds AND s.user_id = :userId")
    List<SubmissionEntity> findByAssignmentsAndLearner(@Param("assignmentIds") Collection<UUID> assignmentIds,
                                                       @Param("userId") UUID userId);

    @Query("SELECT s FROM SubmissionEntity s WHERE s.id = :submissionId AND s.assignment_id = :assignmentId")
    Optional<SubmissionEntity> findInAssignment(@Param("submissionId") UUID submissionId,
                                                @Param("assignmentId") UUID assignmentId);

    @Query(value = "SELECT * FROM public.submissions WHERE id = :submissionId AND assignment_id = :assignmentId FOR UPDATE",
            nativeQuery = true)
    Optional<SubmissionEntity> lockInAssignment(@Param("submissionId") UUID submissionId,
                                                @Param("assignmentId") UUID assignmentId);

    @Query("SELECT COUNT(s) FROM SubmissionEntity s WHERE s.assignment_id = :assignmentId")
    long countByAssignment(@Param("assignmentId") UUID assignmentId);

    /** Ordered by submitted_at ASC, id ASC (ADR010 §12). */
    @Query(value = "SELECT * FROM public.submissions WHERE assignment_id = :assignmentId "
            + "ORDER BY submitted_at ASC, id ASC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<SubmissionEntity> findPageByAssignment(@Param("assignmentId") UUID assignmentId,
                                                @Param("limit") int limit,
                                                @Param("offset") long offset);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SubmissionEntity s SET s.object_key = :objectKey, "
            + "s.original_filename = :originalFilename, s.submitted_at = :submittedAt "
            + "WHERE s.id = :id AND s.assignment_id = :assignmentId AND s.user_id = :userId")
    int replaceFile(@Param("id") UUID id,
                    @Param("assignmentId") UUID assignmentId,
                    @Param("userId") UUID userId,
                    @Param("objectKey") String objectKey,
                    @Param("originalFilename") String originalFilename,
                    @Param("submittedAt") Instant submittedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SubmissionEntity s SET s.grade_percentage = :gradePercentage, "
            + "s.feedback = :feedback, s.graded_at = :gradedAt "
            + "WHERE s.id = :id AND s.assignment_id = :assignmentId")
    int updateGrade(@Param("id") UUID id,
                    @Param("assignmentId") UUID assignmentId,
                    @Param("gradePercentage") BigDecimal gradePercentage,
                    @Param("feedback") String feedback,
                    @Param("gradedAt") Instant gradedAt);
}