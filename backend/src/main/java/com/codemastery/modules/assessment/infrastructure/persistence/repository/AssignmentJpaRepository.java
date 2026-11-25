package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.infrastructure.persistence.entity.AssignmentEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentJpaRepository extends JpaRepository<AssignmentEntity, UUID> {

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AssignmentEntity a "
            + "WHERE a.id = :id AND a.course_id = :courseId")
    boolean existsInCourse(@Param("id") UUID id, @Param("courseId") UUID courseId);

    @Query("SELECT a FROM AssignmentEntity a WHERE a.id = :id AND a.course_id = :courseId")
    Optional<AssignmentEntity> findInCourse(@Param("id") UUID id, @Param("courseId") UUID courseId);

    /**
     * Native {@code FOR UPDATE} (ADR010 §10): Hibernate's PESSIMISTIC_WRITE renders
     * {@code FOR NO KEY UPDATE} on PostgreSQL, whereas serializing concurrent first submissions
     * against assignment edits requires a full {@code FOR UPDATE} lock on the assignment row.
     */
    @Query(value = "SELECT * FROM public.assignments WHERE id = :id AND course_id = :courseId FOR UPDATE",
            nativeQuery = true)
    Optional<AssignmentEntity> lockInCourse(@Param("id") UUID id, @Param("courseId") UUID courseId);

    @Query("SELECT COUNT(a) FROM AssignmentEntity a WHERE a.course_id = :courseId")
    long countInCourse(@Param("courseId") UUID courseId);

    /** Byte-order title ({@code COLLATE "C"}) then UUID order (ADR010 §12). */
    @Query(value = "SELECT * FROM public.assignments WHERE course_id = :courseId "
            + "ORDER BY title COLLATE \"C\", id LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<AssignmentEntity> findPageInCourse(@Param("courseId") UUID courseId,
                                            @Param("limit") int limit,
                                            @Param("offset") long offset);

    /**
     * Targeted definition replacement (ADR012 §19.2): updates only lesson_id, title, instructions
     * and deadline_at, never {@code course_id} or {@code publication_status}.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AssignmentEntity a SET a.lesson_id = :lessonId, a.title = :title, "
            + "a.instructions = :instructions, a.deadline_at = :deadlineAt "
            + "WHERE a.id = :id AND a.course_id = :courseId")
    int updateDefinition(@Param("id") UUID id,
                         @Param("courseId") UUID courseId,
                         @Param("lessonId") UUID lessonId,
                         @Param("title") String title,
                         @Param("instructions") String instructions,
                         @Param("deadlineAt") Instant deadlineAt);

    /**
     * Targeted publication update (ADR012 §19.2): updates only {@code publication_status},
     * never {@code course_id}.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AssignmentEntity a SET a.publication_status = :publicationStatus "
            + "WHERE a.id = :id AND a.course_id = :courseId")
    int updatePublicationStatus(@Param("id") UUID id,
                                @Param("courseId") UUID courseId,
                                @Param("publicationStatus") String publicationStatus);
}
