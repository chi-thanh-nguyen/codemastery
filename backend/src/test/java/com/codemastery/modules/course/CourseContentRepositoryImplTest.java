package com.codemastery.modules.course;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.MaterialType;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.infrastructure.persistence.entity.ChapterEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LearningMaterialEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonEntity;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseContentMapper;
import com.codemastery.modules.course.infrastructure.persistence.repository.ChapterJpaRepository;
import com.codemastery.modules.course.infrastructure.persistence.repository.CourseContentRepositoryImpl;
import com.codemastery.modules.course.infrastructure.persistence.repository.LearningMaterialJpaRepository;
import com.codemastery.modules.course.infrastructure.persistence.repository.LessonJpaRepository;
import com.codemastery.support.fixtures.CourseFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourseContentRepositoryImpl Unit Tests")
class CourseContentRepositoryImplTest {

    @Mock
    private ChapterJpaRepository chapterJpaRepository;

    @Mock
    private LessonJpaRepository lessonJpaRepository;

    @Mock
    private LearningMaterialJpaRepository learningMaterialJpaRepository;

    private CourseContentMapper mapper;
    private CourseContentRepositoryImpl repository;

    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID CHAPTER_1_ID = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private static final UUID CHAPTER_2_ID = UUID.fromString("00000000-0000-4000-8000-000000000012");
    private static final UUID LESSON_1_ID = UUID.fromString("00000000-0000-4000-8000-000000000021");
    private static final UUID LESSON_2_ID = UUID.fromString("00000000-0000-4000-8000-000000000022");
    private static final UUID MATERIAL_ID = UUID.fromString("00000000-0000-4000-8000-000000000031");

    @BeforeEach
    void setUp() {
        mapper = new CourseContentMapper();
        repository = new CourseContentRepositoryImpl(chapterJpaRepository, lessonJpaRepository, learningMaterialJpaRepository, mapper);
    }

    @Test
    @DisplayName("findChapterById handles null safely and returns mapped chapter")
    void findChapterById() {
        assertTrue(repository.findChapterById(null).isEmpty());
        verify(chapterJpaRepository, never()).findById(any());

        ChapterEntity entity = new ChapterEntity(CHAPTER_1_ID, COURSE_ID, "Introduction", 1);
        when(chapterJpaRepository.findById(CHAPTER_1_ID)).thenReturn(Optional.of(entity));

        Optional<Chapter> result = repository.findChapterById(CHAPTER_1_ID);
        assertTrue(result.isPresent());
        assertEquals("Introduction", result.get().title());
        assertEquals(1, result.get().position());
    }

    @Test
    @DisplayName("findChaptersByCourseId queries in position ascending order")
    void findChaptersByCourseId() {
        assertTrue(repository.findChaptersByCourseId(null).isEmpty());

        ChapterEntity ch1 = new ChapterEntity(CHAPTER_1_ID, COURSE_ID, "Basics", 1);
        ChapterEntity ch2 = new ChapterEntity(CHAPTER_2_ID, COURSE_ID, "Advanced", 2);
        when(chapterJpaRepository.findByCourseIdOrderByPositionAsc(COURSE_ID)).thenReturn(List.of(ch1, ch2));

        List<Chapter> chapters = repository.findChaptersByCourseId(COURSE_ID);
        assertEquals(2, chapters.size());
        assertEquals(1, chapters.get(0).position());
        assertEquals(2, chapters.get(1).position());
    }

    @Test
    @DisplayName("findLessonById handles null safely and returns mapped lesson")
    void findLessonById() {
        assertTrue(repository.findLessonById(null).isEmpty());

        LessonEntity entity = new LessonEntity(LESSON_1_ID, CHAPTER_1_ID, "Setup", "# Setup", 1, "published");
        when(lessonJpaRepository.findById(LESSON_1_ID)).thenReturn(Optional.of(entity));

        Optional<Lesson> result = repository.findLessonById(LESSON_1_ID);
        assertTrue(result.isPresent());
        assertEquals("Setup", result.get().title());
    }

    @Test
    @DisplayName("findPublishedLessonById queries with 'published' status")
    void findPublishedLessonById() {
        assertTrue(repository.findPublishedLessonById(null).isEmpty());

        LessonEntity entity = new LessonEntity(LESSON_1_ID, CHAPTER_1_ID, "Setup", null, 1, "published");
        when(lessonJpaRepository.findByIdAndPublicationStatus(LESSON_1_ID, "published"))
                .thenReturn(Optional.of(entity));

        Optional<Lesson> result = repository.findPublishedLessonById(LESSON_1_ID);
        assertTrue(result.isPresent());
        assertEquals(PublishStatus.PUBLISHED, result.get().publicationStatus());
    }

    @Test
    @DisplayName("findPublishedLessonsByChapterId queries published lessons ordered by position")
    void findPublishedLessonsByChapterId() {
        assertTrue(repository.findPublishedLessonsByChapterId(null).isEmpty());

        LessonEntity l1 = new LessonEntity(LESSON_1_ID, CHAPTER_1_ID, "L1", null, 1, "published");
        LessonEntity l2 = new LessonEntity(LESSON_2_ID, CHAPTER_1_ID, "L2", null, 2, "published");
        when(lessonJpaRepository.findByChapterIdAndPublicationStatusOrderByPositionAsc(CHAPTER_1_ID, "published"))
                .thenReturn(List.of(l1, l2));

        List<Lesson> lessons = repository.findPublishedLessonsByChapterId(CHAPTER_1_ID);
        assertEquals(2, lessons.size());
        assertEquals(1, lessons.get(0).position());
        assertEquals(2, lessons.get(1).position());
    }

    @Test
    @DisplayName("findPublishedLessonsByChapterIds queries multiple chapters safely")
    void findPublishedLessonsByChapterIds() {
        assertTrue(repository.findPublishedLessonsByChapterIds(null).isEmpty());
        assertTrue(repository.findPublishedLessonsByChapterIds(List.of()).isEmpty());

        LessonEntity l1 = new LessonEntity(LESSON_1_ID, CHAPTER_1_ID, "L1", null, 1, "published");
        when(lessonJpaRepository.findByChapterIdInAndPublicationStatusOrderByPositionAsc(List.of(CHAPTER_1_ID), "published"))
                .thenReturn(List.of(l1));

        List<Lesson> lessons = repository.findPublishedLessonsByChapterIds(List.of(CHAPTER_1_ID));
        assertEquals(1, lessons.size());
        assertEquals("L1", lessons.get(0).title());
    }

    @Test
    @DisplayName("findPublishedLessonsByCourseId retrieves lessons in canonical course order")
    void findPublishedLessonsByCourseId() {
        assertTrue(repository.findPublishedLessonsByCourseId(null).isEmpty());

        LessonEntity l1 = new LessonEntity(LESSON_1_ID, CHAPTER_1_ID, "L1", null, 1, "published");
        LessonEntity l2 = new LessonEntity(LESSON_2_ID, CHAPTER_2_ID, "L2", null, 1, "published");
        when(lessonJpaRepository.findPublishedByCourseIdInCanonicalOrder(COURSE_ID))
                .thenReturn(List.of(l1, l2));

        List<Lesson> lessons = repository.findPublishedLessonsByCourseId(COURSE_ID);
        assertEquals(2, lessons.size());
        assertEquals(CHAPTER_1_ID, lessons.get(0).chapterId());
        assertEquals(CHAPTER_2_ID, lessons.get(1).chapterId());
    }

    @Test
    @DisplayName("findLessonInCourse verifies ancestry and delegates safely")
    void findLessonInCourse() {
        assertTrue(repository.findLessonInCourse(null, LESSON_1_ID).isEmpty());
        assertTrue(repository.findLessonInCourse(COURSE_ID, null).isEmpty());

        LessonEntity entity = new LessonEntity(LESSON_1_ID, CHAPTER_1_ID, "L1", null, 1, "published");
        when(lessonJpaRepository.findByIdWithinCourse(COURSE_ID, LESSON_1_ID)).thenReturn(Optional.of(entity));

        Optional<Lesson> result = repository.findLessonInCourse(COURSE_ID, LESSON_1_ID);
        assertTrue(result.isPresent());
        assertEquals("L1", result.get().title());
    }

    @Test
    @DisplayName("findCourseIdByLessonId resolves course privately")
    void findCourseIdByLessonId() {
        assertTrue(repository.findCourseIdByLessonId(null).isEmpty());

        when(lessonJpaRepository.findCourseIdByLessonId(LESSON_1_ID)).thenReturn(Optional.of(COURSE_ID));
        Optional<UUID> result = repository.findCourseIdByLessonId(LESSON_1_ID);
        assertTrue(result.isPresent());
        assertEquals(COURSE_ID, result.get());
    }

    @Test
    @DisplayName("findMaterialsByLessonId and findPublishedMaterialsByLessonId return mapped domain")
    void materialQueries() {
        assertTrue(repository.findMaterialsByLessonId(null).isEmpty());
        assertTrue(repository.findPublishedMaterialsByLessonId(null).isEmpty());

        LearningMaterialEntity mat = new LearningMaterialEntity(
                MATERIAL_ID, LESSON_1_ID, "Slide Deck", "file", "published", null, "s3/key", "slides.pdf", null
        );
        when(learningMaterialJpaRepository.findByLessonId(LESSON_1_ID)).thenReturn(List.of(mat));
        when(learningMaterialJpaRepository.findByLessonIdAndPublicationStatus(LESSON_1_ID, "published"))
                .thenReturn(List.of(mat));

        List<LearningMaterial> all = repository.findMaterialsByLessonId(LESSON_1_ID);
        assertEquals(1, all.size());
        assertEquals("Slide Deck", all.get(0).title());
        assertEquals(MaterialType.FILE, all.get(0).materialType());

        List<LearningMaterial> published = repository.findPublishedMaterialsByLessonId(LESSON_1_ID);
        assertEquals(1, published.size());
        assertEquals(PublishStatus.PUBLISHED, published.get(0).publicationStatus());
    }

    @Test
    @DisplayName("findMaterialInLesson verifies full ancestry")
    void findMaterialInLesson() {
        assertTrue(repository.findMaterialInLesson(null, LESSON_1_ID, MATERIAL_ID).isEmpty());
        assertTrue(repository.findMaterialInLesson(COURSE_ID, null, MATERIAL_ID).isEmpty());
        assertTrue(repository.findMaterialInLesson(COURSE_ID, LESSON_1_ID, null).isEmpty());

        LearningMaterialEntity mat = new LearningMaterialEntity(
                MATERIAL_ID, LESSON_1_ID, "Text Note", "text", "published", "Hello", null, null, null
        );
        when(learningMaterialJpaRepository.findByIdWithinLesson(COURSE_ID, LESSON_1_ID, MATERIAL_ID))
                .thenReturn(Optional.of(mat));

        Optional<LearningMaterial> result = repository.findMaterialInLesson(COURSE_ID, LESSON_1_ID, MATERIAL_ID);
        assertTrue(result.isPresent());
        assertEquals("Text Note", result.get().title());
    }

    @Test
    @DisplayName("saveChapter, saveLesson and saveMaterial map and delegate to JPA repositories")
    void saveOperations() {
        assertNull(repository.saveChapter(null));
        assertNull(repository.saveLesson(null));
        assertNull(repository.saveMaterial(null));

        Chapter chapterDomain = CourseFixtures.chapter(CHAPTER_1_ID, COURSE_ID, "Title", 1);
        ChapterEntity chapterEntity = new ChapterEntity(CHAPTER_1_ID, COURSE_ID, "Title", 1);
        when(chapterJpaRepository.save(any(ChapterEntity.class))).thenReturn(chapterEntity);

        Chapter savedChapter = repository.saveChapter(chapterDomain);
        assertNotNull(savedChapter);
        assertEquals(CHAPTER_1_ID, savedChapter.id());

        Lesson lessonDomain = CourseFixtures.publishedLesson(LESSON_1_ID, CHAPTER_1_ID, "Lesson Title", 1);
        LessonEntity lessonEntity = new LessonEntity(LESSON_1_ID, CHAPTER_1_ID, "Lesson Title", null, 1, "published");
        when(lessonJpaRepository.save(any(LessonEntity.class))).thenReturn(lessonEntity);

        Lesson savedLesson = repository.saveLesson(lessonDomain);
        assertNotNull(savedLesson);
        assertEquals(LESSON_1_ID, savedLesson.id());

        LearningMaterial materialDomain = new LearningMaterial(
                MATERIAL_ID, LESSON_1_ID, "Video", MaterialType.EXTERNAL_VIDEO, PublishStatus.PUBLISHED,
                null, null, null, "https://example.com/v"
        );
        LearningMaterialEntity materialEntity = new LearningMaterialEntity(
                MATERIAL_ID, LESSON_1_ID, "Video", "external_video", "published", null, null, null, "https://example.com/v"
        );
        when(learningMaterialJpaRepository.save(any(LearningMaterialEntity.class))).thenReturn(materialEntity);

        LearningMaterial savedMat = repository.saveMaterial(materialDomain);
        assertNotNull(savedMat);
        assertEquals(MATERIAL_ID, savedMat.id());
    }
}
