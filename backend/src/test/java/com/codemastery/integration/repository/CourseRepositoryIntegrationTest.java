package com.codemastery.integration.repository;

import com.codemastery.integration.AbstractIntegrationTest;
import com.codemastery.modules.auth.infrastructure.persistence.entity.UserEntity;
import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.MaterialType;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseContentRepository;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.domain.repository.CourseRepository.AccessSnapshot;
import com.codemastery.modules.course.infrastructure.persistence.entity.ChapterEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.CourseEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LearningMaterialEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonEntity;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseContentMapper;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseMapper;
import com.codemastery.support.fixtures.CourseFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Transactional
class CourseRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final String JWT_SECRET = generateEphemeralJwtSecret();

    private static String generateEphemeralJwtSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @DynamicPropertySource
    static void registerTestProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseContentRepository courseContentRepository;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseContentMapper courseContentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void shouldPersistAndRetrieveCourseHierarchySuccessfully() {
        // Verify Flyway migrations V001 through V006 are applied and validated
        MigrationInfo[] appliedMigrations = flyway.info().applied();
        List<String> versions = Arrays.stream(appliedMigrations)
                .map(info -> info.getVersion().getVersion())
                .toList();
        assertEquals(List.of("001", "002", "003", "004", "005", "006"), versions);

        UUID authorId = UUID.randomUUID();
        persistInstructor(authorId, "author-" + authorId + "@example.test", "Dr. Ada Lovelace");

        UUID courseId = UUID.randomUUID();
        Map<String, Object> adaptiveSettings = Map.of("weakThreshold", 0.5, "masteredThreshold", 0.75);
        Course courseDomain = CourseFixtures.course(
                courseId,
                authorId,
                "Foundations of Computing",
                "Structured introduction to algorithms and data representation.",
                "Basic secondary school algebra",
                PublishStatus.PUBLISHED,
                true,
                adaptiveSettings);

        UUID chapter1Id = UUID.randomUUID();
        UUID chapter2Id = UUID.randomUUID();
        Chapter chapter2 = CourseFixtures.chapter(chapter2Id, courseId, "Control Structures", 2);
        Chapter chapter1 = CourseFixtures.chapter(chapter1Id, courseId, "Variables and Expressions", 1);

        UUID lesson1Ch1Id = UUID.randomUUID();
        UUID lesson2Ch1Id = UUID.randomUUID();
        UUID hiddenLessonCh1Id = UUID.randomUUID();
        UUID lesson1Ch2Id = UUID.randomUUID();

        Lesson lesson2Ch1 = CourseFixtures.lesson(
                lesson2Ch1Id,
                chapter1Id,
                "Arithmetic Operators",
                "# Operators\nAddition and multiplication.",
                2,
                PublishStatus.PUBLISHED);
        Lesson lesson1Ch1 = CourseFixtures.lesson(
                lesson1Ch1Id,
                chapter1Id,
                "Declaring Variables",
                "# Variables\nBinding names to values.",
                1,
                PublishStatus.PUBLISHED);
        Lesson hiddenLessonCh1 = CourseFixtures.hiddenLesson(
                hiddenLessonCh1Id,
                chapter1Id,
                "Draft Instructor Notes",
                3);
        Lesson lesson1Ch2 = CourseFixtures.publishedLesson(
                lesson1Ch2Id,
                chapter2Id,
                "Branching with Conditionals",
                1);

        UUID textMaterialId = UUID.randomUUID();
        UUID fileMaterialId = UUID.randomUUID();
        UUID videoMaterialId = UUID.randomUUID();
        LearningMaterial textMaterial = CourseFixtures.textMaterial(
                textMaterialId,
                lesson1Ch1Id,
                "Concept Reading",
                PublishStatus.PUBLISHED,
                "Variables store typed data references.");
        LearningMaterial fileMaterial = CourseFixtures.fileMaterial(
                fileMaterialId,
                lesson2Ch1Id,
                "Lecture Slides",
                PublishStatus.PUBLISHED,
                "courses/" + courseId + "/slides-01.pdf",
                "slides-01.pdf");
        LearningMaterial videoMaterial = CourseFixtures.externalVideoMaterial(
                videoMaterialId,
                lesson1Ch2Id,
                "Walkthrough Video",
                PublishStatus.HIDDEN,
                "https://example.test/videos/branching");

        // Persist hierarchy: User (Author) -> Course -> Chapter -> Lesson (-> LearningMaterial)
        entityManager.persist(courseMapper.toEntity(courseDomain));
        entityManager.persist(courseContentMapper.toEntity(chapter2));
        entityManager.persist(courseContentMapper.toEntity(chapter1));
        entityManager.persist(courseContentMapper.toEntity(lesson2Ch1));
        entityManager.persist(courseContentMapper.toEntity(lesson1Ch1));
        entityManager.persist(courseContentMapper.toEntity(hiddenLessonCh1));
        entityManager.persist(courseContentMapper.toEntity(lesson1Ch2));
        entityManager.persist(courseContentMapper.toEntity(textMaterial));
        entityManager.persist(courseContentMapper.toEntity(fileMaterial));
        entityManager.persist(courseContentMapper.toEntity(videoMaterial));

        flushAndClear();

        // Retrieve Course via CourseRepository and verify full scalar & JSONB round-trip
        Optional<Course> retrievedCourseOpt = courseRepository.findPublishedById(courseId);
        assertTrue(retrievedCourseOpt.isPresent(), "Published course should be retrievable by ID");
        Course retrievedCourse = retrievedCourseOpt.get();
        assertAll(
                () -> assertEquals(courseId, retrievedCourse.id()),
                () -> assertEquals(authorId, retrievedCourse.instructorId()),
                () -> assertEquals("Foundations of Computing", retrievedCourse.title()),
                () -> assertEquals("Structured introduction to algorithms and data representation.", retrievedCourse.description()),
                () -> assertEquals("Basic secondary school algebra", retrievedCourse.prerequisiteDescription()),
                () -> assertEquals(PublishStatus.PUBLISHED, retrievedCourse.publicationStatus()),
                () -> assertTrue(retrievedCourse.adaptiveEnabled()),
                () -> assertEquals(0.5, ((Number) retrievedCourse.adaptiveSettings().get("weakThreshold")).doubleValue()),
                () -> assertEquals(0.75, ((Number) retrievedCourse.adaptiveSettings().get("masteredThreshold")).doubleValue())
        );

        // Retrieve Chapters ordered by position ASC
        List<Chapter> retrievedChapters = courseContentRepository.findChaptersByCourseId(courseId);
        assertEquals(List.of(chapter1, chapter2), retrievedChapters);

        // Retrieve published Lessons in canonical order (chapter.position ASC, lesson.position ASC)
        List<Lesson> publishedLessons = courseContentRepository.findPublishedLessonsByCourseId(courseId);
        assertEquals(List.of(lesson1Ch1, lesson2Ch1, lesson1Ch2), publishedLessons);
        assertFalse(publishedLessons.stream().anyMatch(l -> l.id().equals(hiddenLessonCh1Id)),
                "Hidden lessons must not be returned by findPublishedLessonsByCourseId");

        // Verify LearningMaterials round-trip and foreign key linkage
        LearningMaterial retrievedTextMaterial = courseContentMapper.toDomain(
                entityManager.find(LearningMaterialEntity.class, textMaterialId));
        LearningMaterial retrievedFileMaterial = courseContentMapper.toDomain(
                entityManager.find(LearningMaterialEntity.class, fileMaterialId));
        LearningMaterial retrievedVideoMaterial = courseContentMapper.toDomain(
                entityManager.find(LearningMaterialEntity.class, videoMaterialId));
        assertAll(
                () -> assertEquals(textMaterial, retrievedTextMaterial),
                () -> assertEquals(fileMaterial, retrievedFileMaterial),
                () -> assertEquals(videoMaterial, retrievedVideoMaterial),
                () -> assertEquals(MaterialType.TEXT, retrievedTextMaterial.materialType()),
                () -> assertEquals(MaterialType.FILE, retrievedFileMaterial.materialType()),
                () -> assertEquals(MaterialType.EXTERNAL_VIDEO, retrievedVideoMaterial.materialType())
        );

        // Verify AccessSnapshot resolves ancestry (Course -> Chapter -> Lesson -> Material) without Enrollment or Progress
        UUID unmatedActorId = UUID.randomUUID();
        AccessSnapshot courseSnapshot = courseRepository.findCourseAccessSnapshot(
                unmatedActorId, courseId, lesson1Ch1Id, textMaterialId).orElseThrow();
        assertAll(
                () -> assertEquals(courseId, courseSnapshot.courseId()),
                () -> assertEquals(authorId, courseSnapshot.instructorId()),
                () -> assertEquals(PublishStatus.PUBLISHED, courseSnapshot.courseStatus()),
                () -> assertEquals(lesson1Ch1Id, courseSnapshot.lessonId()),
                () -> assertEquals(PublishStatus.PUBLISHED, courseSnapshot.lessonStatus()),
                () -> assertEquals(textMaterialId, courseSnapshot.materialId()),
                () -> assertEquals(PublishStatus.PUBLISHED, courseSnapshot.materialStatus()),
                () -> assertNull(courseSnapshot.enrollmentStatus(), "Catalogue hierarchy must remain decoupled from Enrollment")
        );

        AccessSnapshot lessonSnapshot = courseRepository.findLessonAccessSnapshot(
                unmatedActorId, lesson1Ch2Id).orElseThrow();
        assertAll(
                () -> assertEquals(courseId, lessonSnapshot.courseId()),
                () -> assertEquals(authorId, lessonSnapshot.instructorId()),
                () -> assertEquals(PublishStatus.PUBLISHED, lessonSnapshot.courseStatus()),
                () -> assertEquals(lesson1Ch2Id, lessonSnapshot.lessonId()),
                () -> assertEquals(PublishStatus.PUBLISHED, lessonSnapshot.lessonStatus()),
                () -> assertNull(lessonSnapshot.materialId()),
                () -> assertNull(lessonSnapshot.enrollmentStatus())
        );

        // Confirm zero rows in enrollments and lesson_progress tables
        assertEquals(0, countRows("public.enrollments"));
        assertEquals(0, countRows("public.lesson_progress"));
    }

    @Test
    void shouldFilterOnlyPublishedCoursesInCatalogue() {
        UUID authorId = UUID.randomUUID();
        persistInstructor(authorId, "catalogue-author-" + authorId + "@example.test", "Prof. Niklaus Wirth");

        // Create published courses with deterministic tie-breaking UUIDs and titles
        UUID publishedAlphaLowId = UUID.fromString("00000000-0000-4000-8000-000000000010");
        UUID publishedAlphaHighId = UUID.fromString("00000000-0000-4000-8000-000000000020");
        UUID publishedBetaId = UUID.fromString("00000000-0000-4000-8000-000000000005");
        UUID hiddenDraftCourse1Id = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID hiddenDraftCourse2Id = UUID.fromString("00000000-0000-4000-8000-000000000002");

        Course publishedAlphaHigh = CourseFixtures.course(
                publishedAlphaHighId,
                authorId,
                "Algorithms and Data Structures",
                "Core fundamental algorithms for beginners.",
                null,
                PublishStatus.PUBLISHED);
        Course publishedAlphaLow = CourseFixtures.course(
                publishedAlphaLowId,
                authorId,
                "Algorithms and Data Structures",
                "Core fundamental algorithms with prerequisites.",
                "Introductory Python",
                PublishStatus.PUBLISHED);
        Course publishedBeta = CourseFixtures.publishedCourse(
                publishedBetaId,
                authorId,
                "Backend Engineering with Java",
                "Building modular monoliths with Spring Boot.");
        Course hiddenDraft1 = CourseFixtures.hiddenCourse(
                hiddenDraftCourse1Id,
                authorId,
                "AAA Unpublished Draft Course",
                "Core fundamental algorithms in draft state.");
        Course hiddenDraft2 = CourseFixtures.course(
                hiddenDraftCourse2Id,
                authorId,
                "Advanced Distributed Consensus",
                "Draft syllabus for internal review.",
                "Introductory Python",
                PublishStatus.HIDDEN);

        entityManager.persist(courseMapper.toEntity(publishedAlphaHigh));
        entityManager.persist(courseMapper.toEntity(publishedAlphaLow));
        entityManager.persist(courseMapper.toEntity(publishedBeta));
        entityManager.persist(courseMapper.toEntity(hiddenDraft1));
        entityManager.persist(courseMapper.toEntity(hiddenDraft2));

        flushAndClear();

        // 1. Public catalogue page must return ONLY published courses, ordered by title COLLATE "C" ASC, id ASC
        // Caller sorting (e.g. descending by title) must be ignored
        Page<Course> cataloguePage = courseRepository.findPublishedCourses(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "title")));

        assertEquals(3, cataloguePage.getTotalElements(), "Only the 3 published courses must be counted");
        assertEquals(
                List.of(publishedAlphaLowId, publishedAlphaHighId, publishedBetaId),
                cataloguePage.getContent().stream().map(Course::id).toList(),
                "Published courses must be ordered by title COLLATE \"C\" ASC, then id ASC");
        assertTrue(cataloguePage.getContent().stream()
                .allMatch(c -> c.publicationStatus() == PublishStatus.PUBLISHED));

        // 2. Detail lookup by ID must return published course and Optional.empty() for hidden/draft or missing courses
        assertTrue(courseRepository.findPublishedById(publishedAlphaLowId).isPresent());
        assertTrue(courseRepository.findPublishedById(publishedBetaId).isPresent());
        assertTrue(courseRepository.findPublishedById(hiddenDraftCourse1Id).isEmpty(),
                "Hidden/draft course must not be returned by findPublishedById");
        assertTrue(courseRepository.findPublishedById(hiddenDraftCourse2Id).isEmpty(),
                "Hidden/draft course must not be returned by findPublishedById");
        assertTrue(courseRepository.findPublishedById(UUID.randomUUID()).isEmpty(),
                "Non-existent course ID must return empty");

        // 3. Search and prerequisite filtering on public catalogue must also exclude hidden/draft courses
        Page<Course> searchAlgorithmsAll = courseRepository.searchPublishedCourses(
                PageRequest.of(0, 20), "fundamental algorithms", "any");
        assertEquals(
                List.of(publishedAlphaLowId, publishedAlphaHighId),
                searchAlgorithmsAll.getContent().stream().map(Course::id).toList(),
                "Hidden draft matching the search query must never appear in search results");

        Page<Course> searchWithPrerequisites = courseRepository.searchPublishedCourses(
                PageRequest.of(0, 20), "algorithms", "described");
        assertEquals(
                List.of(publishedAlphaLowId),
                searchWithPrerequisites.getContent().stream().map(Course::id).toList());

        Page<Course> searchWithoutPrerequisites = courseRepository.searchPublishedCourses(
                PageRequest.of(0, 20), "algorithms", "none");
        assertEquals(
                List.of(publishedAlphaHighId),
                searchWithoutPrerequisites.getContent().stream().map(Course::id).toList());

        // Verify catalogue remains completely decoupled from Enrollment and LessonProgress
        assertEquals(0, countRows("public.enrollments"));
        assertEquals(0, countRows("public.lesson_progress"));
    }

    @Test
    void shouldRespectDatabaseConstraintsAndReferentialIntegrity() {
        UUID authorId = UUID.randomUUID();
        persistInstructor(authorId, "integrity-author-" + authorId + "@example.test", "Dr. Barbara Liskov");

        UUID courseId = UUID.randomUUID();
        Course validCourse = CourseFixtures.publishedCourse(
                courseId,
                authorId,
                "Software Abstractions",
                "Program design, specifications, and invariants.");
        entityManager.persist(courseMapper.toEntity(validCourse));

        UUID chapterId = UUID.randomUUID();
        Chapter validChapter = CourseFixtures.chapter(chapterId, courseId, "Specifications", 1);
        entityManager.persist(courseContentMapper.toEntity(validChapter));

        UUID lessonId = UUID.randomUUID();
        Lesson validLesson = CourseFixtures.publishedLesson(lessonId, chapterId, "Preconditions and Postconditions", 1);
        entityManager.persist(courseContentMapper.toEntity(validLesson));
        entityManager.flush();

        // 1. Foreign Key constraint: Course referencing a non-existent instructor_id
        assertConstraintViolation(() -> {
            CourseEntity orphanCourse = courseMapper.toEntity(CourseFixtures.publishedCourse(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Orphan Course",
                    "Course with non-existent instructor"));
            entityManager.persist(orphanCourse);
            entityManager.flush();
        });

        // 2. Foreign Key constraint: Chapter referencing a non-existent course_id
        assertConstraintViolation(() -> {
            ChapterEntity orphanChapter = courseContentMapper.toEntity(
                    CourseFixtures.chapter(UUID.randomUUID(), UUID.randomUUID(), "Orphan Chapter", 1));
            entityManager.persist(orphanChapter);
            entityManager.flush();
        });

        // 3. Foreign Key constraint: Lesson referencing a non-existent chapter_id
        assertConstraintViolation(() -> {
            LessonEntity orphanLesson = courseContentMapper.toEntity(
                    CourseFixtures.publishedLesson(UUID.randomUUID(), UUID.randomUUID(), "Orphan Lesson", 1));
            entityManager.persist(orphanLesson);
            entityManager.flush();
        });

        // 4. Unique constraint: Duplicate Chapter position within the same Course (uq_chapters_course_position)
        assertConstraintViolation(() -> {
            ChapterEntity duplicatePositionChapter = courseContentMapper.toEntity(
                    CourseFixtures.chapter(UUID.randomUUID(), courseId, "Duplicate Position Chapter", 1));
            entityManager.persist(duplicatePositionChapter);
            entityManager.flush();
        });

        // 5. Unique constraint: Duplicate Lesson position within the same Chapter (uq_lessons_chapter_position)
        assertConstraintViolation(() -> {
            LessonEntity duplicatePositionLesson = courseContentMapper.toEntity(
                    CourseFixtures.publishedLesson(UUID.randomUUID(), chapterId, "Duplicate Position Lesson", 1));
            entityManager.persist(duplicatePositionLesson);
            entityManager.flush();
        });

        // 6. Check constraint: Invalid publication_status value on Course (ck_courses_publication_status: only 'hidden', 'published')
        assertConstraintViolation(() -> {
            CourseEntity invalidStatusCourse = new CourseEntity(
                    UUID.randomUUID(),
                    authorId,
                    "Invalid Status Course",
                    "Description",
                    null,
                    "draft",
                    false,
                    null);
            entityManager.persist(invalidStatusCourse);
            entityManager.flush();
        });

        // 7. Check constraint: Adaptive course enabled without adaptive_settings JSONB (ck_courses_adaptive_configuration)
        assertConstraintViolation(() -> {
            CourseEntity invalidAdaptiveCourse = courseMapper.toEntity(
                    CourseFixtures.adaptiveCourse(
                            UUID.randomUUID(),
                            authorId,
                            "Invalid Adaptive Course",
                            "Adaptive enabled but null settings",
                            PublishStatus.PUBLISHED,
                            null));
            entityManager.persist(invalidAdaptiveCourse);
            entityManager.flush();
        });

        // 8. Check constraint: Non-positive Chapter or Lesson position (ck_chapters_position / ck_lessons_position)
        assertConstraintViolation(() -> {
            ChapterEntity zeroPositionChapter = courseContentMapper.toEntity(
                    CourseFixtures.chapter(UUID.randomUUID(), courseId, "Zero Position Chapter", 0));
            entityManager.persist(zeroPositionChapter);
            entityManager.flush();
        });

        // 9. Referential integrity ON DELETE NO ACTION: Deleting a Course that still has Chapters must fail
        assertConstraintViolation(() ->
                jdbcTemplate.update("DELETE FROM public.courses WHERE id = ?", courseId));
    }

    private void persistInstructor(UUID id, String email, String displayName) {
        UserEntity instructor = new UserEntity(
                id,
                email,
                "$2b$14$synthetic-qa02-instructor-password-hash-placeholder",
                displayName,
                "instructor",
                "active");
        entityManager.persist(instructor);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private int countRows(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private void assertConstraintViolation(Runnable action) {
        String savepoint = "sp_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.execute("SAVEPOINT " + savepoint);
        try {
            assertThrows(Exception.class, () -> {
                try {
                    action.run();
                } catch (PersistenceException | DataIntegrityViolationException ex) {
                    throw ex;
                }
            });
        } finally {
            jdbcTemplate.execute("ROLLBACK TO SAVEPOINT " + savepoint);
            jdbcTemplate.execute("RELEASE SAVEPOINT " + savepoint);
            entityManager.clear();
        }
    }
}
