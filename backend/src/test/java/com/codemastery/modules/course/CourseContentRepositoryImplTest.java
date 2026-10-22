package com.codemastery.modules.course;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.infrastructure.persistence.entity.ChapterEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonEntity;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseContentMapper;
import com.codemastery.modules.course.infrastructure.persistence.repository.ChapterJpaRepository;
import com.codemastery.modules.course.infrastructure.persistence.repository.CourseContentRepositoryImpl;
import com.codemastery.modules.course.infrastructure.persistence.repository.LessonJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseContentRepositoryImplTest {

    @Mock
    private ChapterJpaRepository chapters;
    @Mock
    private LessonJpaRepository lessons;

    private CourseContentRepositoryImpl repository;

    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID CHAPTER_1_ID = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private static final UUID CHAPTER_2_ID = UUID.fromString("00000000-0000-4000-8000-000000000012");
    private static final UUID LESSON_1_ID = UUID.fromString("00000000-0000-4000-8000-000000000021");
    private static final UUID LESSON_2_ID = UUID.fromString("00000000-0000-4000-8000-000000000022");

    @BeforeEach
    void setUp() {
        repository = new CourseContentRepositoryImpl(chapters, lessons, new CourseContentMapper());
    }

    @Test
    void nullCourseDoesNotQueryPersistence() {
        assertTrue(repository.findChaptersByCourseId(null).isEmpty());
        assertTrue(repository.findPublishedLessonsByCourseId(null).isEmpty());
        verifyNoInteractions(chapters, lessons);
    }

    @Test
    void chaptersPreserveCourseReferencesAndQueryPositionOrder() {
        var first = new ChapterEntity(CHAPTER_1_ID, COURSE_ID, "Basics", 1);
        var second = new ChapterEntity(CHAPTER_2_ID, COURSE_ID, "Advanced", 2);
        when(chapters.findByCourseIdOrderByPositionAsc(COURSE_ID)).thenReturn(List.of(first, second));

        assertEquals(List.of(new Chapter(CHAPTER_1_ID, COURSE_ID, "Basics", 1),
                        new Chapter(CHAPTER_2_ID, COURSE_ID, "Advanced", 2)),
                repository.findChaptersByCourseId(COURSE_ID));
        verify(chapters).findByCourseIdOrderByPositionAsc(COURSE_ID);
        verifyNoInteractions(lessons);
    }

    @Test
    void publishedLessonsPreserveCanonicalOrderAcrossChapterPositionReset() {
        var first = new LessonEntity(LESSON_1_ID, CHAPTER_1_ID, "L1", null, 2, "published");
        var second = new LessonEntity(LESSON_2_ID, CHAPTER_2_ID, "L2", null, 1, "published");
        when(lessons.findPublishedByCourseIdInCanonicalOrder(COURSE_ID)).thenReturn(List.of(first, second));

        assertEquals(List.of(new Lesson(LESSON_1_ID, CHAPTER_1_ID, "L1", null, 2, PublishStatus.PUBLISHED),
                        new Lesson(LESSON_2_ID, CHAPTER_2_ID, "L2", null, 1, PublishStatus.PUBLISHED)),
                repository.findPublishedLessonsByCourseId(COURSE_ID));
        verify(lessons).findPublishedByCourseIdInCanonicalOrder(COURSE_ID);
        verifyNoInteractions(chapters);
    }

    @Test
    void missingCourseOrNoPublishedLessonsReturnsEmptyCollections() {
        when(chapters.findByCourseIdOrderByPositionAsc(COURSE_ID)).thenReturn(List.of());
        when(lessons.findPublishedByCourseIdInCanonicalOrder(COURSE_ID)).thenReturn(List.of());
        assertTrue(repository.findChaptersByCourseId(COURSE_ID).isEmpty());
        assertTrue(repository.findPublishedLessonsByCourseId(COURSE_ID).isEmpty());
    }

    @Test
    void syllabusQueryDeclaresCoursePublicationAndCanonicalOrderingConstraints() throws Exception {
        // Query contract regression; real database execution remains QA02.
        Query query = LessonJpaRepository.class.getMethod("findPublishedByCourseIdInCanonicalOrder", UUID.class)
                .getAnnotation(Query.class);
        assertNotNull(query);
        assertEquals("select l from LessonEntity l join ChapterEntity c on c.id = l.chapterId "
                + "where c.courseId = :courseId and l.publicationStatus = 'published' "
                + "order by c.position asc, l.position asc", query.value());
    }
}
