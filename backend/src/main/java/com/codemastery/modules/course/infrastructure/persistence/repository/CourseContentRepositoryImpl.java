package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.repository.CourseContentRepository;
import com.codemastery.modules.course.infrastructure.persistence.entity.ChapterEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LearningMaterialEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonEntity;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseContentMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CourseContentRepositoryImpl implements CourseContentRepository {

    private static final String STATUS_PUBLISHED = "published";

    private final ChapterJpaRepository chapterJpaRepository;
    private final LessonJpaRepository lessonJpaRepository;
    private final LearningMaterialJpaRepository learningMaterialJpaRepository;
    private final CourseContentMapper courseContentMapper;

    public CourseContentRepositoryImpl(
            ChapterJpaRepository chapterJpaRepository,
            LessonJpaRepository lessonJpaRepository,
            LearningMaterialJpaRepository learningMaterialJpaRepository,
            CourseContentMapper courseContentMapper) {
        this.chapterJpaRepository = chapterJpaRepository;
        this.lessonJpaRepository = lessonJpaRepository;
        this.learningMaterialJpaRepository = learningMaterialJpaRepository;
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
    public List<Lesson> findLessonsByChapterId(UUID chapterId) {
        if (chapterId == null) {
            return List.of();
        }
        return lessonJpaRepository.findByChapterIdOrderByPositionAsc(chapterId).stream()
                .map(courseContentMapper::toDomain)
                .toList();
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
        return lessonJpaRepository.findPublishedByCourseIdInCanonicalOrder(courseId).stream()
                .map(courseContentMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Lesson> findLessonInCourse(UUID courseId, UUID lessonId) {
        if (courseId == null || lessonId == null) {
            return Optional.empty();
        }
        return lessonJpaRepository.findByIdWithinCourse(courseId, lessonId)
                .map(courseContentMapper::toDomain);
    }

    @Override
    public Optional<UUID> findCourseIdByLessonId(UUID lessonId) {
        if (lessonId == null) {
            return Optional.empty();
        }
        return lessonJpaRepository.findCourseIdByLessonId(lessonId);
    }

    @Override
    public List<LearningMaterial> findMaterialsByLessonId(UUID lessonId) {
        if (lessonId == null) {
            return List.of();
        }
        return learningMaterialJpaRepository.findByLessonId(lessonId).stream()
                .map(courseContentMapper::toDomain)
                .toList();
    }

    @Override
    public List<LearningMaterial> findPublishedMaterialsByLessonId(UUID lessonId) {
        if (lessonId == null) {
            return List.of();
        }
        return learningMaterialJpaRepository.findByLessonIdAndPublicationStatus(lessonId, STATUS_PUBLISHED).stream()
                .map(courseContentMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<LearningMaterial> findMaterialInLesson(UUID courseId, UUID lessonId, UUID materialId) {
        if (courseId == null || lessonId == null || materialId == null) {
            return Optional.empty();
        }
        return learningMaterialJpaRepository.findByIdWithinLesson(courseId, lessonId, materialId)
                .map(courseContentMapper::toDomain);
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

    @Override
    public LearningMaterial saveMaterial(LearningMaterial material) {
        if (material == null) {
            return null;
        }
        LearningMaterialEntity entity = courseContentMapper.toEntity(material);
        LearningMaterialEntity saved = learningMaterialJpaRepository.save(entity);
        return courseContentMapper.toDomain(saved);
    }
}
