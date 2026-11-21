package com.codemastery.modules.course.domain.repository;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.PublishStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Course-internal syllabus and content persistence. CO03.catalogue reads feed the public
 * catalogue; CO03.content operations serve CT21.authoring (ADR006 §5), the CT40 structure
 * projection (ADR005 §3.1) and CT09 material rows. Public DTO assembly, CT03 authorization,
 * input validation and the ADR005 Course-root guard stay with the calling application
 * service. Writes run inside the caller's transaction and choose no policy or default.
 * A lost row or an invalid sibling order is an internal failure, surfaced by the
 * repository's exception translation as Spring's InvalidDataAccessApiUsageException.
 * Writes require the caller's transaction and reject pending JPA changes (flush them first);
 * any write failure marks that transaction rollback-only.
 */
public interface CourseContentRepository {

    /** Chapters of one course ordered by position ascending. */
    List<Chapter> findChaptersByCourseId(UUID courseId);

    /** Published lessons of one course, ordered by chapter.position then lesson.position. */
    List<Lesson> findPublishedLessonsByCourseId(UUID courseId);

    // CO03.content reads: fresh statements, scoped to the stated Course or Lesson.

    /** Every lesson of one course, hidden and published, in canonical chapter/lesson position order. */
    List<Lesson> findLessonsByCourseId(UUID courseId);

    /** The chapter only when it belongs to the course. */
    Optional<Chapter> findChapter(UUID courseId, UUID chapterId);

    /** The lesson only when its chapter belongs to the course. */
    Optional<Lesson> findLesson(UUID courseId, UUID lessonId);

    /** Every material of one course (all types and statuses), by canonical lesson order, then material id. */
    List<LearningMaterial> findMaterialsByCourseId(UUID courseId);

    /** Published materials of one lesson ordered by material id (V002 has no material position). */
    List<LearningMaterial> findPublishedMaterialsByLessonId(UUID lessonId);

    /** The material only when it belongs to the lesson. */
    Optional<LearningMaterial> findMaterial(UUID lessonId, UUID materialId);

    // CO03.content writes.

    /**
     * Appends a chapter after the maximum sibling position (1 when the course has none).
     * Empty, and nothing is written, when that position is not a representable positive int.
     */
    Optional<Chapter> appendChapter(UUID courseId, UUID chapterId, String title);

    /** Appends a lesson within its chapter like {@link #appendChapter}; the status is the caller's. */
    Optional<Lesson> appendLesson(UUID chapterId, UUID lessonId, String title, String contentMarkdown,
                                  PublishStatus publicationStatus);

    /** Replaces the title of a chapter of the course; the parent and position are unchanged. */
    void updateChapterTitle(UUID courseId, UUID chapterId, String title);

    /** Replaces title and nullable Markdown of a lesson of the chapter; parent, position and status unchanged. */
    void updateLesson(UUID chapterId, UUID lessonId, String title, String contentMarkdown);

    void updateLessonPublicationStatus(UUID chapterId, UUID lessonId, PublishStatus publicationStatus);

    /**
     * Sets chapter positions to 1..N in the given order, atomically within the caller's
     * transaction. The list must name every current chapter of the course exactly once.
     */
    void reorderChapters(UUID courseId, List<UUID> orderedChapterIds);

    /** Same contract as {@link #reorderChapters} for the lessons of one chapter. */
    void reorderLessons(UUID chapterId, List<UUID> orderedLessonIds);

    /** Inserts exactly the supplied material row; V002 enforces the type-exclusive payload. */
    void insertMaterial(LearningMaterial material);

    /** Full title/payload replacement; the material's lesson and type cannot change. */
    void replaceMaterialContent(LearningMaterial material);

    void updateMaterialPublicationStatus(UUID lessonId, UUID materialId, PublishStatus publicationStatus);
}
