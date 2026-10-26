package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.infrastructure.persistence.entity.CourseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CourseJpaRepository extends Repository<CourseEntity, UUID> {

    @Query(value = "SELECT * FROM public.courses WHERE publication_status = 'published' "
            + "ORDER BY title COLLATE \"C\" ASC, id ASC",
            countQuery = "SELECT count(*) FROM public.courses WHERE publication_status = 'published'",
            nativeQuery = true)
    Page<CourseEntity> findPublishedCatalogue(Pageable pageable);

    Optional<CourseEntity> findByIdAndPublicationStatus(
            @Param("id") UUID id, @Param("publicationStatus") String publicationStatus);

    // Keep the Course root even when membership or scoped descendants are missing.
    // One SELECT supplies all CT03 facts from the same PostgreSQL statement snapshot.
    @Query(value = """
            SELECT c.id AS "courseId", c.instructor_id AS "instructorId",
                   c.publication_status AS "courseStatus",
                   scoped.id AS "lessonId", scoped.publication_status AS "lessonStatus",
                   m.id AS "materialId", m.publication_status AS "materialStatus",
                   e.status AS "enrollmentStatus"
            FROM public.courses c
            LEFT JOIN public.enrollments e ON e.course_id = c.id AND e.user_id = :actorId
            LEFT JOIN (
                SELECT l.id, l.publication_status, ch.course_id
                FROM public.lessons l JOIN public.chapters ch ON ch.id = l.chapter_id
            ) scoped ON scoped.course_id = c.id AND scoped.id = CAST(:lessonId AS uuid)
            LEFT JOIN public.learning_materials m
                ON m.lesson_id = scoped.id AND m.id = CAST(:materialId AS uuid)
            WHERE c.id = :courseId
            """, nativeQuery = true)
    Optional<AccessRow> findCourseAccessSnapshot(
            @Param("actorId") UUID actorId, @Param("courseId") UUID courseId,
            @Param("lessonId") UUID lessonId, @Param("materialId") UUID materialId);

    @Query(value = """
            SELECT c.id AS "courseId", c.instructor_id AS "instructorId",
                   c.publication_status AS "courseStatus",
                   l.id AS "lessonId", l.publication_status AS "lessonStatus",
                   CAST(NULL AS uuid) AS "materialId", CAST(NULL AS text) AS "materialStatus",
                   e.status AS "enrollmentStatus"
            FROM public.lessons l
            JOIN public.chapters ch ON ch.id = l.chapter_id
            JOIN public.courses c ON c.id = ch.course_id
            LEFT JOIN public.enrollments e ON e.course_id = c.id AND e.user_id = :actorId
            WHERE l.id = :lessonId
            """, nativeQuery = true)
    Optional<AccessRow> findLessonAccessSnapshot(
            @Param("actorId") UUID actorId, @Param("lessonId") UUID lessonId);

    interface AccessRow {
        UUID getCourseId();
        UUID getInstructorId();
        String getCourseStatus();
        UUID getLessonId();
        String getLessonStatus();
        UUID getMaterialId();
        String getMaterialStatus();
        String getEnrollmentStatus();
    }
}
