package com.codemastery.modules.course;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.MaterialType;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.infrastructure.persistence.entity.ChapterEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LearningMaterialEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonEntity;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseContentMapper;
import com.codemastery.support.fixtures.CourseFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("CourseContentMapper Unit Tests")
class CourseContentMapperTest {

    private CourseContentMapper mapper;

    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID CHAPTER_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID LESSON_ID = UUID.fromString("00000000-0000-4000-8000-000000000003");

    @BeforeEach
    void setUp() {
        mapper = new CourseContentMapper();
    }

    @Test
    @DisplayName("Handles null inputs safely for Chapter and Lesson mappers")
    void handlesNullInputs() {
        assertNull(mapper.toEntity((Chapter) null));
        assertNull(mapper.toDomain((ChapterEntity) null));
        assertNull(mapper.toEntity((Lesson) null));
        assertNull(mapper.toDomain((LessonEntity) null));
        assertNull(mapper.toEntity((LearningMaterial) null));
        assertNull(mapper.toDomain((LearningMaterialEntity) null));
    }

    @Test
    @DisplayName("Maps Chapter bidirectionally preserving id, courseId, title, position")
    void bidirectionalMappingChapter() {
        Chapter original = CourseFixtures.chapter(CHAPTER_ID, COURSE_ID, "Getting Started", 1);

        ChapterEntity entity = mapper.toEntity(original);
        assertNotNull(entity);
        assertAll(
                () -> assertEquals(CHAPTER_ID, entity.getId()),
                () -> assertEquals(COURSE_ID, entity.getCourseId()),
                () -> assertEquals("Getting Started", entity.getTitle()),
                () -> assertEquals(1, entity.getPosition())
        );

        Chapter mappedBack = mapper.toDomain(entity);
        assertEquals(original, mappedBack);
    }

    @ParameterizedTest
    @EnumSource(PublishStatus.class)
    @DisplayName("Maps Lesson bidirectionally preserving all scalar fields and statuses")
    void bidirectionalMappingLesson(PublishStatus status) {
        Lesson original = CourseFixtures.lesson(
                LESSON_ID,
                CHAPTER_ID,
                "Variables and Data Types",
                "# Overview\nMarkdown content here.",
                2,
                status
        );

        LessonEntity entity = mapper.toEntity(original);
        assertNotNull(entity);
        assertAll(
                () -> assertEquals(LESSON_ID, entity.getId()),
                () -> assertEquals(CHAPTER_ID, entity.getChapterId()),
                () -> assertEquals("Variables and Data Types", entity.getTitle()),
                () -> assertEquals("# Overview\nMarkdown content here.", entity.getContentMarkdown()),
                () -> assertEquals(2, entity.getPosition()),
                () -> assertEquals(status.getStatus(), entity.getPublicationStatus())
        );

        Lesson mappedBack = mapper.toDomain(entity);
        assertEquals(original, mappedBack);
    }

    @Test
    @DisplayName("Maps Lesson with null contentMarkdown properly")
    void mapsLessonWithNullMarkdown() {
        Lesson original = CourseFixtures.publishedLesson(
                LESSON_ID,
                CHAPTER_ID,
                "Empty Heading",
                3
        );

        LessonEntity entity = mapper.toEntity(original);
        assertNotNull(entity);
        assertNull(entity.getContentMarkdown());
        assertEquals("published", entity.getPublicationStatus());

        Lesson mappedBack = mapper.toDomain(entity);
        assertEquals(original, mappedBack);
    }

    @Test
    @DisplayName("Handles case-insensitive publication status string in toDomain")
    void handlesCaseInsensitivePublicationStatus() {
        LessonEntity entity = new LessonEntity(
                LESSON_ID,
                CHAPTER_ID,
                "Title",
                null,
                1,
                "PUBLISHED"
        );
        Lesson domain = mapper.toDomain(entity);
        assertEquals(PublishStatus.PUBLISHED, domain.publicationStatus());
    }

    @Test
    @DisplayName("Throws IllegalArgumentException for invalid lesson publication status string")
    void throwsOnUnknownLessonPublicationStatus() {
        LessonEntity invalidEntity = new LessonEntity(
                LESSON_ID,
                CHAPTER_ID,
                "Title",
                null,
                1,
                "DRAFT"
        );
        assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(invalidEntity));
    }

    @ParameterizedTest
    @EnumSource(MaterialType.class)
    void materialRoundTripPreservesExclusivePayloadAndParent(MaterialType type) {
        LearningMaterial original = new LearningMaterial(
                UUID.fromString("00000000-0000-4000-8000-000000000004"), LESSON_ID,
                "Material", type, PublishStatus.PUBLISHED,
                type == MaterialType.TEXT ? "Text content" : null,
                type == MaterialType.FILE ? "course/material.pdf" : null,
                type == MaterialType.FILE ? "material.pdf" : null,
                type == MaterialType.EXTERNAL_VIDEO ? "https://example.org/embed/video" : null);

        LearningMaterialEntity entity = mapper.toEntity(original);

        assertAll(
                () -> assertEquals(original.id(), entity.getId()),
                () -> assertEquals(LESSON_ID, entity.getLesson_id()),
                () -> assertEquals("Material", entity.getTitle()),
                () -> assertEquals(type.getType(), entity.getMaterialType()),
                () -> assertEquals("published", entity.getPublicationStatus()),
                () -> assertEquals(original.textContent(), entity.getTextContent()),
                () -> assertEquals(original.objectKey(), entity.getObjectKey()),
                () -> assertEquals(original.originalFileName(), entity.getOriginalFileName()),
                () -> assertEquals(original.externalVideoUrl(), entity.getExternalVideoUrl()));
        assertEquals(original, mapper.toDomain(entity));
    }

    @Test
    void unknownMaterialTypeDoesNotBecomeASilentDefault() {
        var entity = new LearningMaterialEntity(LESSON_ID, LESSON_ID, "Material", "unknown",
                "hidden", null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(entity));
    }
}
