package com.codemastery.modules.course;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.LessonProgress;
import com.codemastery.modules.course.domain.model.MaterialType;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.support.fixtures.CourseFixtures;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** FT01 structural consumer only; these local sentinels are not seed identities or business scenarios. */
class CourseFixturesTest {
    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID INSTRUCTOR_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID CHAPTER_ID = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID LESSON_ID = UUID.fromString("00000000-0000-4000-8000-000000000004");
    private static final UUID MATERIAL_ID = UUID.fromString("00000000-0000-4000-8000-000000000005");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000006");
    private static final UUID ENROLLMENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000007");
    private static final UUID PROGRESS_ID = UUID.fromString("00000000-0000-4000-8000-000000000008");

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesCourseValuesThroughFactoryAndBuilder(boolean adaptiveEnabled) {
        // Opaque caller JSON content: this does not define any runtime settings key or policy.
        Map<String, Object> settings = Map.of("fixture-marker", "caller value");
        Course course = CourseFixtures.course(COURSE_ID, INSTRUCTOR_ID, "Caller title", "Caller description",
                "Caller prerequisites", PublishStatus.PUBLISHED, adaptiveEnabled, settings);

        assertAll(
                () -> assertEquals(COURSE_ID, course.id()),
                () -> assertEquals(INSTRUCTOR_ID, course.instructorId()),
                () -> assertEquals("Caller title", course.title()),
                () -> assertEquals("Caller description", course.description()),
                () -> assertEquals("Caller prerequisites", course.prerequisiteDescription()),
                () -> assertEquals(PublishStatus.PUBLISHED, course.publicationStatus()),
                () -> assertEquals(adaptiveEnabled, course.adaptiveEnabled()),
                () -> assertEquals(settings, course.adaptiveSettings()));

        assertEquals(course, CourseFixtures.courseBuilder(adaptiveEnabled)
                .id(COURSE_ID).instructorId(INSTRUCTOR_ID)
                .title("Caller title").description("Caller description")
                .prerequisiteDescription("Caller prerequisites")
                .publicationStatus(PublishStatus.PUBLISHED).adaptiveSettings(settings).build());
    }

    @Test
    void preservesChapterValuesThroughFactoryAndBuilder() {
        Chapter chapter = CourseFixtures.chapter(CHAPTER_ID, COURSE_ID, "Caller chapter", 7);

        assertAll(
                () -> assertEquals(CHAPTER_ID, chapter.id()),
                () -> assertEquals(COURSE_ID, chapter.courseId()),
                () -> assertEquals("Caller chapter", chapter.title()),
                () -> assertEquals(7, chapter.position()));

        assertEquals(chapter, CourseFixtures.chapterBuilder(7)
                .id(CHAPTER_ID).courseId(COURSE_ID).title("Caller chapter").build());
    }

    @Test
    void preservesLessonValuesThroughFactoryAndBuilder() {
        Lesson lesson = CourseFixtures.lesson(LESSON_ID, CHAPTER_ID, "Caller lesson", "Caller Markdown",
                11, PublishStatus.HIDDEN);

        assertAll(
                () -> assertEquals(LESSON_ID, lesson.id()),
                () -> assertEquals(CHAPTER_ID, lesson.chapterId()),
                () -> assertEquals("Caller lesson", lesson.title()),
                () -> assertEquals("Caller Markdown", lesson.contentMarkdown()),
                () -> assertEquals(11, lesson.position()),
                () -> assertEquals(PublishStatus.HIDDEN, lesson.publicationStatus()));

        assertEquals(lesson, CourseFixtures.lessonBuilder(11)
                .id(LESSON_ID).chapterId(CHAPTER_ID).title("Caller lesson")
                .contentMarkdown("Caller Markdown").publicationStatus(PublishStatus.HIDDEN).build());
    }

    @ParameterizedTest
    @EnumSource(MaterialType.class)
    void preservesMaterialValuesAndVariantPayloadPlacement(MaterialType type) {
        String text = type == MaterialType.TEXT ? "Caller material text" : null;
        String objectKey = type == MaterialType.FILE ? "caller/object-key" : null;
        String filename = type == MaterialType.FILE ? "caller-material.pdf" : null;
        String videoUrl = type == MaterialType.EXTERNAL_VIDEO ? "https://example.test/caller-video" : null;
        LearningMaterial material = CourseFixtures.material(MATERIAL_ID, LESSON_ID, "Caller material", type,
                PublishStatus.PUBLISHED, text, objectKey, filename, videoUrl);

        assertAll(
                () -> assertEquals(MATERIAL_ID, material.id()),
                () -> assertEquals(LESSON_ID, material.lessonId()),
                () -> assertEquals("Caller material", material.title()),
                () -> assertEquals(type, material.materialType()),
                () -> assertEquals(PublishStatus.PUBLISHED, material.publicationStatus()),
                () -> assertEquals(text, material.textContent()),
                () -> assertEquals(objectKey, material.objectKey()),
                () -> assertEquals(filename, material.originalFileName()),
                () -> assertEquals(videoUrl, material.externalVideoUrl()));

        assertEquals(material, CourseFixtures.materialBuilder()
                .id(MATERIAL_ID).lessonId(LESSON_ID).title("Caller material")
                .materialType(type).publicationStatus(PublishStatus.PUBLISHED)
                .textContent(text).objectKey(objectKey).originalFileName(filename)
                .externalVideoUrl(videoUrl).build());

        LearningMaterial shortcut = switch (type) {
            case TEXT -> CourseFixtures.textMaterial(MATERIAL_ID, LESSON_ID, "Caller material",
                    PublishStatus.PUBLISHED, text);
            case FILE -> CourseFixtures.fileMaterial(MATERIAL_ID, LESSON_ID, "Caller material",
                    PublishStatus.PUBLISHED, objectKey, filename);
            case EXTERNAL_VIDEO -> CourseFixtures.externalVideoMaterial(MATERIAL_ID, LESSON_ID, "Caller material",
                    PublishStatus.PUBLISHED, videoUrl);
        };
        assertEquals(material, shortcut);
    }

    @ParameterizedTest
    @EnumSource(EnrollmentStatus.class)
    void preservesEnrollmentValuesThroughFactoryAndBuilder(EnrollmentStatus status) {
        Enrollment enrollment = CourseFixtures.enrollment(ENROLLMENT_ID, USER_ID, COURSE_ID, status, LESSON_ID);

        assertAll(
                () -> assertEquals(ENROLLMENT_ID, enrollment.id()),
                () -> assertEquals(USER_ID, enrollment.userId()),
                () -> assertEquals(COURSE_ID, enrollment.courseId()),
                () -> assertEquals(status, enrollment.status()),
                () -> assertEquals(LESSON_ID, enrollment.resumeLessonId()));

        assertEquals(enrollment, CourseFixtures.enrollmentBuilder()
                .id(ENROLLMENT_ID).userId(USER_ID).courseId(COURSE_ID)
                .status(status).resumeLessonId(LESSON_ID).build());
    }

    @ParameterizedTest
    @CsvSource({"false, false", "true, false", "true, true"})
    void preservesExplicitProgressFlagsAndNamedRepresentations(boolean completed, boolean masterySkipped) {
        LessonProgress progress = CourseFixtures.lessonProgress(PROGRESS_ID, ENROLLMENT_ID, LESSON_ID,
                completed, masterySkipped);

        assertAll(
                () -> assertEquals(PROGRESS_ID, progress.id()),
                () -> assertEquals(ENROLLMENT_ID, progress.enrollmentId()),
                () -> assertEquals(LESSON_ID, progress.lessonId()),
                () -> assertEquals(completed, progress.completed()),
                () -> assertEquals(masterySkipped, progress.masterySkipped()));

        assertEquals(progress, CourseFixtures.lessonProgressBuilder(completed, masterySkipped)
                .id(PROGRESS_ID).enrollmentId(ENROLLMENT_ID).lessonId(LESSON_ID).build());

        // Compare stored flags only; no completion or mastery decision is executed.
        LessonProgress shortcut = masterySkipped
                ? CourseFixtures.skippedProgress(PROGRESS_ID, ENROLLMENT_ID, LESSON_ID)
                : completed
                        ? CourseFixtures.completedProgress(PROGRESS_ID, ENROLLMENT_ID, LESSON_ID)
                        : CourseFixtures.inProgress(PROGRESS_ID, ENROLLMENT_ID, LESSON_ID);
        assertEquals(progress, shortcut);
    }

    @Test
    void preservesNullableOptionalFieldsThroughFactoriesAndBuilders() {
        Course course = CourseFixtures.course(COURSE_ID, INSTRUCTOR_ID, "Caller title", "Caller description",
                null, PublishStatus.HIDDEN, false, null);
        Lesson lesson = CourseFixtures.lesson(LESSON_ID, CHAPTER_ID, "Caller lesson", null, 11, PublishStatus.HIDDEN);
        Enrollment enrollment = CourseFixtures.enrollment(ENROLLMENT_ID, USER_ID, COURSE_ID,
                EnrollmentStatus.ACTIVE, null);

        assertAll(
                () -> assertNull(course.prerequisiteDescription()),
                () -> assertNull(course.adaptiveSettings()),
                () -> assertNull(lesson.contentMarkdown()),
                () -> assertNull(enrollment.resumeLessonId()));

        assertAll(
                () -> assertEquals(course, CourseFixtures.courseBuilder(false)
                        .id(COURSE_ID).instructorId(INSTRUCTOR_ID)
                        .title("Caller title").description("Caller description")
                        .publicationStatus(PublishStatus.HIDDEN).build()),
                () -> assertEquals(lesson, CourseFixtures.lessonBuilder(11)
                        .id(LESSON_ID).chapterId(CHAPTER_ID).title("Caller lesson")
                        .publicationStatus(PublishStatus.HIDDEN).build()),
                () -> assertEquals(enrollment, CourseFixtures.enrollmentBuilder()
                        .id(ENROLLMENT_ID).userId(USER_ID).courseId(COURSE_ID)
                        .status(EnrollmentStatus.ACTIVE).build()));
    }

    @Test
    void retainedSettersCanOverrideExplicitBuilderInputs() {
        Course course = CourseFixtures.courseBuilder(true)
                .id(COURSE_ID).instructorId(INSTRUCTOR_ID).title("Caller title")
                .description("Caller description").publicationStatus(PublishStatus.HIDDEN)
                .adaptiveEnabled(false).build();
        Chapter chapter = CourseFixtures.chapterBuilder(7)
                .id(CHAPTER_ID).courseId(COURSE_ID).title("Caller chapter").position(9).build();
        Lesson lesson = CourseFixtures.lessonBuilder(11)
                .id(LESSON_ID).chapterId(CHAPTER_ID).title("Caller lesson")
                .publicationStatus(PublishStatus.HIDDEN).position(13).build();
        LessonProgress progress = CourseFixtures.lessonProgressBuilder(false, false)
                .id(PROGRESS_ID).enrollmentId(ENROLLMENT_ID).lessonId(LESSON_ID)
                .completed(true).masterySkipped(true).build();

        assertAll(
                () -> assertFalse(course.adaptiveEnabled()),
                () -> assertEquals(9, chapter.position()),
                () -> assertEquals(13, lesson.position()),
                () -> assertEquals(CourseFixtures.skippedProgress(PROGRESS_ID, ENROLLMENT_ID, LESSON_ID), progress));
    }
}
