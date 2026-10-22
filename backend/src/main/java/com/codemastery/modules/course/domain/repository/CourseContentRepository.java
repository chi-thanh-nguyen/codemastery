package com.codemastery.modules.course.domain.repository;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.Lesson;

import java.util.List;
import java.util.UUID;

/** Course-internal syllabus reads (CO03.catalogue); public DTO assembly stays downstream. */
public interface CourseContentRepository {

    /** Chapters of one course ordered by position ascending. */
    List<Chapter> findChaptersByCourseId(UUID courseId);

    /** Published lessons of one course, ordered by chapter.position then lesson.position. */
    List<Lesson> findPublishedLessonsByCourseId(UUID courseId);
}
