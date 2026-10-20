package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.repository.CourseContentRepository;
import com.codemastery.modules.course.infrastructure.persistence.entity.ChapterEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonEntity;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseContentMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CourseContentRepositoryImpl implements CourseContentRepository {

    private static final String STATUS_PUBLISHED = "published";

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
    public Optional<Chapter> findChapterById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return chapterJpaRepository.findById(id).map(courseContentMapper::toDomain);
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
    public Optional<Lesson> findLessonById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return lessonJpaRepository.findById(id).map(courseContentMapper::toDomain);
    }

    @Override
    public Optional<Lesson> findPublishedLessonById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return lessonJpaRepository.findByIdAndPublicationStatus(id, STATUS_PUBLISHED)
                .map(courseContentMapper::toDomain);
    }

    @Override
    public List<Lesson> findPublishedLessonsByChapterId(UUID chapterId) {
        if (chapterId == null) {
            return List.of();
        }
        return lessonJpaRepository.findByChapterIdAndPublicationStatusOrderByPositionAsc(chapterId, STATUS_PUBLISHED).stream()
                .map(courseContentMapper::toDomain)
                .toList();
    }

    @Override
    public List<Lesson> findPublishedLessonsByChapterIds(Collection<UUID> chapterIds) {
        if (chapterIds == null || chapterIds.isEmpty()) {
            return List.of();
        }
        return lessonJpaRepository.findByChapterIdInAndPublicationStatusOrderByPositionAsc(chapterIds, STATUS_PUBLISHED).stream()
                .map(courseContentMapper::toDomain)
                .toList();
    }

    @Override
    public List<Lesson> findPublishedLessonsByCourseId(UUID courseId) {
        if (courseId == null) {
            return List.of();
        }
        List<ChapterEntity> chapters = chapterJpaRepository.findByCourseIdOrderByPositionAsc(courseId);
        if (chapters.isEmpty()) {
            return List.of();
        }
        List<Lesson> result = new ArrayList<>();
        for (ChapterEntity chapter : chapters) {
            List<LessonEntity> lessons = lessonJpaRepository.findByChapterIdAndPublicationStatusOrderByPositionAsc(
                    chapter.getId(), STATUS_PUBLISHED);
            for (LessonEntity lesson : lessons) {
                result.add(courseContentMapper.toDomain(lesson));
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public Chapter saveChapter(Chapter chapter) {
        if (chapter == null) {
            return null;
        }
        ChapterEntity entity = courseContentMapper.toEntity(chapter);
        ChapterEntity saved = chapterJpaRepository.save(entity);
        return courseContentMapper.toDomain(saved);
    }

    @Override
    public Lesson saveLesson(Lesson lesson) {
        if (lesson == null) {
            return null;
        }
        LessonEntity entity = courseContentMapper.toEntity(lesson);
        LessonEntity saved = lessonJpaRepository.save(entity);
        return courseContentMapper.toDomain(saved);
    }
}
