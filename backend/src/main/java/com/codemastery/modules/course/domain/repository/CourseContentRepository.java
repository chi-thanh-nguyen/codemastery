package com.codemastery.modules.course.domain.repository;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.Lesson;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Course-internal read/write port for Chapter, Lesson, and LearningMaterial structure.
 * Descendant queries are ancestry-scoped so resources are not exposed across parent boundaries.
 */
public interface CourseContentRepository {

    /** Finds chapter by its primary ID. */
    Optional<Chapter> findChapterById(UUID id);

    /** CO03.catalogue / CO03.content: Chapters of a course ordered by position ascending. */
    List<Chapter> findChaptersByCourseId(UUID courseId);

    /** Finds lesson by its primary ID in either publication state. */
    Optional<Lesson> findLessonById(UUID id);

    /** Finds published lesson by its primary ID. */
    Optional<Lesson> findPublishedLessonById(UUID id);

    /** All lessons of a chapter ordered by position ascending. */
    List<Lesson> findLessonsByChapterId(UUID chapterId);

    /** Published lessons of a chapter ordered by position ascending. */
    List<Lesson> findPublishedLessonsByChapterId(UUID chapterId);

    /** Published lessons across multiple chapters ordered by position ascending. */
    List<Lesson> findPublishedLessonsByChapterIds(Collection<UUID> chapterIds);

    /**
     * CO03.catalogue (CT03 / CT21): published lessons of a course in canonical course sequence
     * (chapter.position ASC, then lesson.position ASC).
     */
    List<Lesson> findPublishedLessonsByCourseId(UUID courseId);

    /**
     * CO03.access (CT03 stage 5): the lesson only if its actual Lesson → Chapter ancestry belongs
     * to the given course.
     */
    Optional<Lesson> findLessonInCourse(UUID courseId, UUID lessonId);

    /** CO03.access (CT03 requireLessonReadAccess): Course-private Lesson → Chapter → Course resolution. */
    Optional<UUID> findCourseIdByLessonId(UUID lessonId);

    /** All materials belonging to a lesson. */
    List<LearningMaterial> findMaterialsByLessonId(UUID lessonId);

    /** Published materials belonging to a lesson. */
    List<LearningMaterial> findPublishedMaterialsByLessonId(UUID lessonId);

    /**
     * CO03.access (CT03 material read): the material only if its actual ancestry is
     * material → lesson → chapter → course.
     */
    Optional<LearningMaterial> findMaterialInLesson(UUID courseId, UUID lessonId, UUID materialId);

    /** Persists a chapter. */
    Chapter saveChapter(Chapter chapter);

    /** Persists a lesson. */
    Lesson saveLesson(Lesson lesson);

    /** Persists a learning material. */
    LearningMaterial saveMaterial(LearningMaterial material);
}
