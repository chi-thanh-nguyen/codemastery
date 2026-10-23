package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.repository.CourseContentRepository;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseContentMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class CourseContentRepositoryImpl implements CourseContentRepository {

    private final ChapterJpaRepository chapterJpaRepository;
    private final LessonJpaRepository lessonJpaRepository;
    private final CourseContentMapper courseContentMapper;

    public CourseContentRepositoryImpl(
            ChapterJpaRepository chapterJpaRepository,
            LessonJpaRepository lessonJpaRepository,
            CourseContentMapper courseContentMapper) {
        this.chapterJpaRepository = chapterJpaRepository;
        this.lessonJpaRepository = lessonJpaRepository;
        this.courseContentMapper = courseContentMapper;
    }

    @Override
    public List<Chapter> findChaptersByCourseId(UUID courseId) {
        if (courseId == null) {
            return List.of();
        }
        return chapterJpaRepository.findByCourseIdOrderByPositionAsc(courseId).stream()
                .map(courseContentMapper::toDomain)
                .toList();
    }

    @Override
    public List<Lesson> findPublishedLessonsByCourseId(UUID courseId) {
        if (courseId == null) {
            return List.of();
        }
        return lessonJpaRepository.findPublishedByCourseIdInCanonicalOrder(courseId).stream()
                .map(courseContentMapper::toDomain)
                .toList();
    }
}
