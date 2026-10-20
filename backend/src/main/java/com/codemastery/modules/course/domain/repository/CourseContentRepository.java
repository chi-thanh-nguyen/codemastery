package com.codemastery.modules.course.domain.repository;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.Lesson;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseContentRepository {

    Optional<Chapter> findChapterById(UUID id);

    List<Chapter> findChaptersByCourseId(UUID courseId);

    Optional<Lesson> findLessonById(UUID id);

    Optional<Lesson> findPublishedLessonById(UUID id);

    List<Lesson> findPublishedLessonsByChapterId(UUID chapterId);

    List<Lesson> findPublishedLessonsByChapterIds(Collection<UUID> chapterIds);

    List<Lesson> findPublishedLessonsByCourseId(UUID courseId);

    Chapter saveChapter(Chapter chapter);

    Lesson saveLesson(Lesson lesson);
}
