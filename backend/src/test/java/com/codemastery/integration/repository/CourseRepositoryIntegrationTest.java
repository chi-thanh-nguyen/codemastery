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
import org.springframework.dao.InvalidDataAccessApiUsageException;
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
        // Every discovered migration (V001 onward, contiguous) is applied and validated;
        // a new versioned migration needs no edit here, but gaps or pending ones fail.
        MigrationInfo[] appliedMigrations = flyway.info().applied();
        List<String> versions = Arrays.stream(appliedMigrations)
                .map(info -> info.getVersion().getVersion())
                .toList();
        List<String> discovered = Arrays.stream(flyway.info().all())
                .map(info -> info.getVersion().getVersion())
                .toList();
        assertEquals(discovered, versions);
        assertEquals(0, flyway.info().pending().length);
        assertEquals(List.of("001", "002", "003", "004", "005", "006"), versions.subList(0, 6));
        for (int index = 0; index < versions.size(); index++) {
            assertEquals(String.format("%03d", index + 1), versions.get(index));
        }

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

    // CO03.content (CT21.authoring, ADR005 §3.1 structure, CT09 rows) and CO14 storage, on PostgreSQL.

    @Test
    void ownerCourseRowsRoundTripAcrossPublicationStatesInFixedOwnerOrder() {
        UUID owner = instructor();
        UUID other = instructor();
        UUID sameLow = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
        UUID sameHigh = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
        UUID alphaUpper = UUID.randomUUID();
        UUID alphaLower = UUID.randomUUID();
        UUID beta = UUID.randomUUID();
        Map<String, Object> settings = Map.of("opaque", List.of(1, 2));
        courseRepository.insert(new Course(beta, owner, "beta", "Draft", null, PublishStatus.HIDDEN, false, null));
        courseRepository.insert(new Course(sameHigh, owner, "Same", "Second", "Needs basics", PublishStatus.PUBLISHED, false, null));
        courseRepository.insert(new Course(alphaLower, owner, "alpha", "Lower", null, PublishStatus.HIDDEN, false, settings));
        courseRepository.insert(new Course(sameLow, owner, "Same", "First", null, PublishStatus.HIDDEN, false, null));
        courseRepository.insert(new Course(alphaUpper, owner, "Alpha", "Upper", null, PublishStatus.PUBLISHED, false, null));
        courseRepository.insert(new Course(UUID.randomUUID(), other, "Another owner", "Foreign", null, PublishStatus.PUBLISHED, false, null));

        Course draft = courseRepository.findById(alphaLower).orElseThrow();
        assertEquals(new Course(alphaLower, owner, "alpha", "Lower", null, PublishStatus.HIDDEN, false, settings), draft);
        assertThrows(UnsupportedOperationException.class, () -> draft.adaptiveSettings().put("x", 1));
        assertEquals(Optional.empty(), courseRepository.findById(UUID.randomUUID()));

        Page<Course> first = courseRepository.findByInstructorId(owner, PageRequest.of(0, 10, Sort.by("id").descending()));
        assertEquals(List.of(alphaUpper, sameLow, sameHigh, alphaLower, beta), first.map(Course::id).getContent());
        assertEquals(5, first.getTotalElements());
        Page<Course> second = courseRepository.findByInstructorId(owner, PageRequest.of(1, 2));
        assertEquals(List.of(sameHigh, alphaLower), second.map(Course::id).getContent());
        assertEquals(3, second.getTotalPages());

        courseRepository.updateMetadata(beta, "Beta", "Edited", "Variables");
        courseRepository.updatePublicationStatus(beta, PublishStatus.PUBLISHED);
        assertEquals(new Course(beta, owner, "Beta", "Edited", "Variables", PublishStatus.PUBLISHED, false, null),
                courseRepository.findById(beta).orElseThrow());
        assertThrows(InvalidDataAccessApiUsageException.class, () -> courseRepository.updateMetadata(UUID.randomUUID(), "T", "D", null));
        assertThrows(InvalidDataAccessApiUsageException.class,
                () -> courseRepository.updatePublicationStatus(UUID.randomUUID(), PublishStatus.HIDDEN));
        assertConstraintViolation(() -> courseRepository.updateMetadata(beta, "   ", "Edited", null));
        assertConstraintViolation(() -> courseRepository.insert(
                new Course(UUID.randomUUID(), owner, "Enabled", "No policy", null, PublishStatus.HIDDEN, true, null)));
    }

    @Test
    void contentReadsIncludeHiddenDraftsInCanonicalOrderAndStayScopedToTheirCourse() {
        UUID owner = instructor();
        UUID course = course(owner);
        UUID foreignCourse = course(owner);
        UUID chapterOne = chapter(course, 1);
        UUID chapterTwo = chapter(course, 2);
        UUID foreignChapter = chapter(foreignCourse, 1);
        UUID secondHidden = lesson(chapterOne, 2, "hidden");
        UUID firstPublished = lesson(chapterOne, 1, "published");
        UUID laterHidden = lesson(chapterTwo, 1, "hidden");
        UUID foreignLesson = lesson(foreignChapter, 1, "published");

        assertEquals(List.of(firstPublished, secondHidden, laterHidden),
                courseContentRepository.findLessonsByCourseId(course).stream().map(Lesson::id).toList());
        assertEquals(List.of(firstPublished),
                courseContentRepository.findPublishedLessonsByCourseId(course).stream().map(Lesson::id).toList());
        assertEquals(new Chapter(chapterTwo, course, "Chapter 2", 2), courseContentRepository.findChapter(course, chapterTwo).orElseThrow());
        assertEquals(Optional.empty(), courseContentRepository.findChapter(foreignCourse, chapterTwo));
        assertEquals(new Lesson(secondHidden, chapterOne, "Lesson 2", "# Lesson 2\n\n    indented", 2, PublishStatus.HIDDEN),
                courseContentRepository.findLesson(course, secondHidden).orElseThrow());
        assertEquals(Optional.empty(), courseContentRepository.findLesson(course, foreignLesson));
        assertEquals(List.of(), courseContentRepository.findLessonsByCourseId(UUID.randomUUID()));
    }

    @Test
    void appendsUseTheNextSiblingPositionAndRefuseUnrepresentablePositions() {
        UUID course = course(instructor());
        UUID first = UUID.randomUUID();
        assertEquals(Optional.of(new Chapter(first, course, "First", 1)), courseContentRepository.appendChapter(course, first, "First"));
        jdbcTemplate.update("UPDATE public.chapters SET position = 10 WHERE id = ?", first);
        UUID next = UUID.randomUUID();
        assertEquals(11, courseContentRepository.appendChapter(course, next, "Next").orElseThrow().position());

        UUID lesson = UUID.randomUUID();
        assertEquals(Optional.of(new Lesson(lesson, next, "Draft", null, 1, PublishStatus.HIDDEN)),
                courseContentRepository.appendLesson(next, lesson, "Draft", null, PublishStatus.HIDDEN));
        assertEquals(new Lesson(lesson, next, "Draft", null, 1, PublishStatus.HIDDEN),
                courseContentRepository.findLesson(course, lesson).orElseThrow());

        jdbcTemplate.update("UPDATE public.chapters SET position = 2147483647 WHERE id = ?", next);
        jdbcTemplate.update("UPDATE public.lessons SET position = 2147483647 WHERE id = ?", lesson);
        assertEquals(Optional.empty(), courseContentRepository.appendChapter(course, UUID.randomUUID(), "Overflow"));
        assertEquals(Optional.empty(), courseContentRepository.appendLesson(next, UUID.randomUUID(), "Overflow", null, PublishStatus.HIDDEN));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT count(*) FROM public.chapters WHERE course_id = ?", Integer.class, course));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM public.lessons WHERE chapter_id = ?", Integer.class, next));
        assertConstraintViolation(() -> courseContentRepository.appendChapter(UUID.randomUUID(), UUID.randomUUID(), "No course"));
    }

    @Test
    void reorderingAssignsContiguousPositionsAtomicallyAndRestoresImmediateUniqueness() {
        UUID course = course(instructor());
        UUID a = chapter(course, 5);
        UUID b = chapter(course, 7);
        UUID c = chapter(course, 9);
        courseContentRepository.reorderChapters(course, List.of(c, a, b));
        assertEquals(List.of(new Chapter(c, course, "Chapter 9", 1), new Chapter(a, course, "Chapter 5", 2),
                new Chapter(b, course, "Chapter 7", 3)), courseContentRepository.findChaptersByCourseId(course));
        courseContentRepository.reorderChapters(course, List.of(a, b, c));
        assertEquals(List.of(a, b, c), courseContentRepository.findChaptersByCourseId(course).stream().map(Chapter::id).toList());
        // Uniqueness is immediate again once the reorder returns.
        assertConstraintViolation(() -> jdbcTemplate.update("UPDATE public.chapters SET position = 2 WHERE id = ?", a));

        UUID foreign = chapter(course(instructor()), 1);
        for (List<UUID> invalid : List.of(List.of(a, b), List.of(a, b, b), List.of(a, b, foreign), Arrays.asList(a, b, null))) {
            assertThrows(InvalidDataAccessApiUsageException.class, () -> courseContentRepository.reorderChapters(course, invalid));
        }
        assertEquals(List.of(1, 2, 3), courseContentRepository.findChaptersByCourseId(course).stream().map(Chapter::position).toList());

        UUID first = lesson(a, 1, "published");
        UUID second = lesson(a, 2, "hidden");
        courseContentRepository.reorderLessons(a, List.of(second, first));
        assertEquals(List.of(second, first), courseContentRepository.findLessonsByCourseId(course).stream().map(Lesson::id).toList());
        assertThrows(InvalidDataAccessApiUsageException.class, () -> courseContentRepository.reorderLessons(a, List.of(first)));
    }

    @Test
    void materialsKeepExclusivePayloadsVisibilityAndAnImmutableTypeAndLesson() {
        UUID course = course(instructor());
        UUID chapter = chapter(course, 1);
        UUID lesson = lesson(chapter, 1, "published");
        UUID laterLesson = lesson(chapter, 2, "published");
        UUID textId = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
        UUID videoId = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
        UUID fileId = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
        UUID laterId = UUID.fromString("00000000-0000-4000-8000-0000000000b0");
        LearningMaterial text = new LearningMaterial(textId, lesson, "Notes", MaterialType.TEXT, PublishStatus.PUBLISHED,
                "  indented\ntext", null, null, null);
        LearningMaterial video = new LearningMaterial(videoId, lesson, "Video", MaterialType.EXTERNAL_VIDEO, PublishStatus.HIDDEN,
                null, null, null, "https://video.example.test/watch?v=1");
        LearningMaterial file = new LearningMaterial(fileId, lesson, "Slides", MaterialType.FILE, PublishStatus.PUBLISHED,
                null, "courses/key.pdf", "slides.pdf", null);
        LearningMaterial later = new LearningMaterial(laterId, laterLesson, "Later", MaterialType.TEXT, PublishStatus.PUBLISHED,
                "Later", null, null, null);
        for (LearningMaterial material : List.of(text, video, file, later)) {
            courseContentRepository.insertMaterial(material);
        }

        assertEquals(List.of(video, file, text, later), courseContentRepository.findMaterialsByCourseId(course));
        assertEquals(List.of(file, text), courseContentRepository.findPublishedMaterialsByLessonId(lesson));
        assertEquals(Optional.of(video), courseContentRepository.findMaterial(lesson, videoId));
        assertEquals(Optional.empty(), courseContentRepository.findMaterial(laterLesson, videoId));

        LearningMaterial edited = new LearningMaterial(textId, lesson, "Edited notes", MaterialType.TEXT, PublishStatus.PUBLISHED,
                "Replaced", null, null, null);
        courseContentRepository.replaceMaterialContent(edited);
        assertEquals(Optional.of(edited), courseContentRepository.findMaterial(lesson, textId));
        assertThrows(InvalidDataAccessApiUsageException.class, () -> courseContentRepository.replaceMaterialContent(new LearningMaterial(
                textId, lesson, "Type change", MaterialType.EXTERNAL_VIDEO, PublishStatus.PUBLISHED, null, null, null, "https://x.test")));
        assertThrows(InvalidDataAccessApiUsageException.class, () -> courseContentRepository.replaceMaterialContent(new LearningMaterial(
                textId, laterLesson, "Move", MaterialType.TEXT, PublishStatus.PUBLISHED, "Moved", null, null, null)));
        assertEquals(Optional.of(edited), courseContentRepository.findMaterial(lesson, textId));

        courseContentRepository.updateMaterialPublicationStatus(lesson, videoId, PublishStatus.PUBLISHED);
        assertEquals(List.of(videoId, fileId, textId),
                courseContentRepository.findPublishedMaterialsByLessonId(lesson).stream().map(LearningMaterial::id).toList());
        assertThrows(InvalidDataAccessApiUsageException.class,
                () -> courseContentRepository.updateMaterialPublicationStatus(laterLesson, videoId, PublishStatus.HIDDEN));
        assertConstraintViolation(() -> courseContentRepository.insertMaterial(new LearningMaterial(UUID.randomUUID(), lesson,
                "Mixed", MaterialType.TEXT, PublishStatus.HIDDEN, "Text", null, null, "https://mixed.example.test")));
        assertConstraintViolation(() -> courseContentRepository.replaceMaterialContent(new LearningMaterial(
                fileId, lesson, "Lost key", MaterialType.FILE, PublishStatus.PUBLISHED, null, null, "slides.pdf", null)));

        courseContentRepository.updateLesson(chapter, lesson, "Renamed", null);
        courseContentRepository.updateLessonPublicationStatus(chapter, lesson, PublishStatus.HIDDEN);
        courseContentRepository.updateChapterTitle(course, chapter, "Renamed chapter");
        assertEquals(new Lesson(lesson, chapter, "Renamed", null, 1, PublishStatus.HIDDEN),
                courseContentRepository.findLesson(course, lesson).orElseThrow());
        assertEquals("Renamed chapter", courseContentRepository.findChapter(course, chapter).orElseThrow().title());
        assertThrows(InvalidDataAccessApiUsageException.class, () -> courseContentRepository.updateChapterTitle(UUID.randomUUID(), chapter, "X"));
        assertThrows(InvalidDataAccessApiUsageException.class, () -> courseContentRepository.updateLesson(UUID.randomUUID(), lesson, "X", null));
    }

    @Test
    void adaptiveFieldsAreReadFreshAndReplacedOnlyOnAStructuralMatchUnderTheRootLock() {
        UUID course = course(instructor());
        jdbcTemplate.update("UPDATE public.courses SET adaptive_settings = CAST(? AS jsonb) WHERE id = ?",
                "{\"b\": {\"y\": [1, 2.0]},   \"a\": \"text\"}", course);
        assertEquals("read committed", courseRepository.currentTransactionFacts().isolation());
        assertTrue(courseRepository.currentTransactionFacts().writeIdentityAssigned());
        assertFalse(courseRepository.currentTransactionFacts().readOnly());
        assertTrue(courseRepository.lockCourseRoot(course));
        assertTrue(courseRepository.lockCourseRootNoWait(course));
        assertFalse(courseRepository.lockCourseRoot(UUID.randomUUID()));
        assertFalse(courseRepository.lockCourseRootNoWait(UUID.randomUUID()));

        CourseRepository.StoredAdaptiveConfiguration stored = courseRepository.findAdaptiveConfiguration(course).orElseThrow();
        Map<String, Object> expected = new java.util.LinkedHashMap<>();
        expected.put("a", "text");
        expected.put("b", Map.of("y", List.of(1, new java.math.BigDecimal("2.0"))));
        assertEquals(new CourseRepository.StoredAdaptiveConfiguration(course, false, expected), stored);
        assertThrows(UnsupportedOperationException.class, () -> stored.adaptiveSettings().clear());
        assertEquals(Optional.empty(), courseRepository.findAdaptiveConfiguration(UUID.randomUUID()));

        assertFalse(courseRepository.replaceAdaptiveConfiguration(course, true, expected, true, Map.of("n", 1)));
        assertFalse(courseRepository.replaceAdaptiveConfiguration(course, false, Map.of("a", "text"), true, Map.of("n", 1)));
        Map<String, Object> reordered = new java.util.LinkedHashMap<>();
        reordered.put("b", Map.of("y", List.of(1, 2)));
        reordered.put("a", "text");
        assertTrue(courseRepository.replaceAdaptiveConfiguration(course, false, reordered, true, Map.of("n", 1)));
        assertEquals(new CourseRepository.StoredAdaptiveConfiguration(course, true, Map.of("n", 1)),
                courseRepository.findAdaptiveConfiguration(course).orElseThrow());
        assertConstraintViolation(() -> courseRepository.replaceAdaptiveConfiguration(course, true, Map.of("n", 1), true, null));
        assertTrue(courseRepository.replaceAdaptiveConfiguration(course, true, Map.of("n", 1), false, null));
        assertEquals(new CourseRepository.StoredAdaptiveConfiguration(course, false, null),
                courseRepository.findAdaptiveConfiguration(course).orElseThrow());

        assertFalse(courseRepository.hasPendingPersistenceChanges());
        persistInstructor(UUID.randomUUID(), "pending-" + UUID.randomUUID() + "@example.test", "Pending");
        assertTrue(courseRepository.hasPendingPersistenceChanges());
        assertThrows(InvalidDataAccessApiUsageException.class, courseRepository::discardCleanPersistenceState);
        entityManager.flush();
        CourseEntity managed = entityManager.find(CourseEntity.class, course);
        courseRepository.detachManagedCourse(course);
        assertFalse(entityManager.contains(managed));
        courseRepository.discardCleanPersistenceState();
    }

    private UUID instructor() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO public.users (id,email,password_hash,display_name,role,account_status) "
                + "VALUES (?,?,'not-a-login-fixture','CO03 Instructor','instructor','active')", id, id + "@example.test");
        return id;
    }

    private UUID course(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO public.courses (id,instructor_id,title,description,publication_status,adaptive_enabled) "
                + "VALUES (?,?,'Course','Description','hidden',false)", id, owner);
        return id;
    }

    private UUID chapter(UUID course, int position) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO public.chapters (id,course_id,title,position) VALUES (?,?,?,?)",
                id, course, "Chapter " + position, position);
        return id;
    }

    private UUID lesson(UUID chapter, int position, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO public.lessons (id,chapter_id,title,content_markdown,position,publication_status) "
                + "VALUES (?,?,?,?,?,?)", id, chapter, "Lesson " + position, "# Lesson " + position + "\n\n    indented",
                position, status);
        return id;
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
