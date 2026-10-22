package com.codemastery.modules.course;

import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.LessonProgress;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.infrastructure.persistence.entity.CourseEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.EnrollmentEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonProgressEntity;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseMapper;
import com.codemastery.modules.course.infrastructure.persistence.mapper.EnrollmentMapper;
import com.codemastery.support.fixtures.CourseFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CourseMapper Unit Tests")
class CourseMapperTest {

    private CourseMapper mapper;

    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID INSTRUCTOR_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @BeforeEach
    void setUp() {
        mapper = new CourseMapper();
    }

    @Test
    @DisplayName("toEntity and toDomain safely handle null inputs")
    void handlesNullInputs() {
        assertNull(mapper.toEntity(null));
        assertNull(mapper.toDomain(null));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Maps all scalar fields bidirectionally with adaptive settings")
    void bidirectionalMappingFullCourse(boolean adaptiveEnabled) {
        Map<String, Object> settings = Map.of("key", "val", "num", 42);
        Course original = CourseFixtures.course(
                COURSE_ID,
                INSTRUCTOR_ID,
                "Introduction to Java",
                "Learn core Java programming",
                "Basic programming knowledge",
                PublishStatus.PUBLISHED,
                adaptiveEnabled,
                settings
        );

        CourseEntity entity = mapper.toEntity(original);
        assertNotNull(entity);
        assertAll(
                () -> assertEquals(COURSE_ID, entity.getId()),
                () -> assertEquals(INSTRUCTOR_ID, entity.getInstructorId()),
                () -> assertEquals("Introduction to Java", entity.getTitle()),
                () -> assertEquals("Learn core Java programming", entity.getDescription()),
                () -> assertEquals("Basic programming knowledge", entity.getPrerequisiteDescription()),
                () -> assertEquals("published", entity.getPublicationStatus()),
                () -> assertEquals(adaptiveEnabled, entity.isAdaptiveEnabled()),
                () -> assertEquals(settings, entity.getAdaptiveSettings())
        );

        Course mappedBack = mapper.toDomain(entity);
        assertEquals(original, mappedBack);
    }

    @Test
    @DisplayName("Maps hidden course with nullable fields properly")
    void bidirectionalMappingHiddenCourseWithNulls() {
        Course original = CourseFixtures.hiddenCourse(
                COURSE_ID,
                INSTRUCTOR_ID,
                "Draft Course",
                "Work in progress"
        );

        CourseEntity entity = mapper.toEntity(original);
        assertNotNull(entity);
        assertAll(
                () -> assertEquals(COURSE_ID, entity.getId()),
                () -> assertEquals(INSTRUCTOR_ID, entity.getInstructorId()),
                () -> assertEquals("Draft Course", entity.getTitle()),
                () -> assertEquals("Work in progress", entity.getDescription()),
                () -> assertNull(entity.getPrerequisiteDescription()),
                () -> assertEquals("hidden", entity.getPublicationStatus()),
                () -> assertFalse(entity.isAdaptiveEnabled()),
                () -> assertNull(entity.getAdaptiveSettings())
        );

        Course mappedBack = mapper.toDomain(entity);
        assertEquals(original, mappedBack);
    }

    @Test
    @DisplayName("Handles case-insensitive publication status string in toDomain")
    void handlesCaseInsensitivePublicationStatus() {
        CourseEntity entityUpper = new CourseEntity(
                COURSE_ID,
                INSTRUCTOR_ID,
                "Title",
                "Desc",
                null,
                "PUBLISHED",
                false,
                null
        );
        Course domain = mapper.toDomain(entityUpper);
        assertEquals(PublishStatus.PUBLISHED, domain.publicationStatus());

        CourseEntity entityHiddenUpper = new CourseEntity(
                COURSE_ID,
                INSTRUCTOR_ID,
                "Title",
                "Desc",
                null,
                "HIDDEN",
                false,
                null
        );
        Course domainHidden = mapper.toDomain(entityHiddenUpper);
        assertEquals(PublishStatus.HIDDEN, domainHidden.publicationStatus());
    }

    @Test
    @DisplayName("Throws IllegalArgumentException for invalid publication status string")
    void throwsOnUnknownPublicationStatus() {
        CourseEntity invalidEntity = new CourseEntity(
                COURSE_ID,
                INSTRUCTOR_ID,
                "Title",
                "Desc",
                null,
                "ARCHIVED",
                false,
                null
        );
        assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(invalidEntity));
    }

    @Test
    @DisplayName("Preserves null publication status safely")
    void preservesNullPublicationStatus() {
        Course courseWithoutStatus = new Course(
                COURSE_ID,
                INSTRUCTOR_ID,
                "Title",
                "Desc",
                null,
                null,
                false,
                null
        );
        CourseEntity entity = mapper.toEntity(courseWithoutStatus);
        assertNull(entity.getPublicationStatus());

        CourseEntity entityWithoutStatus = new CourseEntity(
                COURSE_ID,
                INSTRUCTOR_ID,
                "Title",
                "Desc",
                null,
                null,
                false,
                null
        );
        Course domain = mapper.toDomain(entityWithoutStatus);
        assertNull(domain.publicationStatus());
    }

    // Scalar mapping coverage stays in this authorized CO03.mapping test location.
    @Nested
    class EnrollmentScalarMappings {

        private EnrollmentMapper mapper;

        private static final UUID ENROLLMENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
        private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
        private static final UUID COURSE_ID = UUID.fromString("00000000-0000-4000-8000-000000000003");
        private static final UUID LESSON_ID = UUID.fromString("00000000-0000-4000-8000-000000000004");
        private static final UUID PROGRESS_ID = UUID.fromString("00000000-0000-4000-8000-000000000005");

        @BeforeEach
        void setUp() {
            mapper = new EnrollmentMapper();
        }

        @Test
        @DisplayName("Handles null inputs safely for Enrollment and LessonProgress")
        void handlesNullEnrollmentAndProgressInputs() {
            assertNull(mapper.toEntity((Enrollment) null));
            assertNull(mapper.toDomain((EnrollmentEntity) null));
            assertNull(mapper.toEntity((LessonProgress) null));
            assertNull(mapper.toDomain((LessonProgressEntity) null));
        }

        @ParameterizedTest
        @EnumSource(EnrollmentStatus.class)
        @DisplayName("Maps Enrollment bidirectionally preserving status and nullable resumeLessonId")
        void bidirectionalMappingEnrollment(EnrollmentStatus status) {
            Enrollment domain = new Enrollment(ENROLLMENT_ID, USER_ID, COURSE_ID, status, LESSON_ID);

            EnrollmentEntity entity = mapper.toEntity(domain);
            assertNotNull(entity);
            assertAll(
                    () -> assertEquals(ENROLLMENT_ID, entity.getId()),
                    () -> assertEquals(USER_ID, entity.getUserId()),
                    () -> assertEquals(COURSE_ID, entity.getCourseId()),
                    () -> assertEquals(status.getStatus(), entity.getStatus()),
                    () -> assertEquals(LESSON_ID, entity.getResumeLessonId())
            );

            Enrollment mappedBack = mapper.toDomain(entity);
            assertEquals(domain, mappedBack);
        }

        @Test
        @DisplayName("Maps Enrollment with null resumeLessonId")
        void mapsEnrollmentWithNullResumeLessonId() {
            Enrollment domain = new Enrollment(ENROLLMENT_ID, USER_ID, COURSE_ID, EnrollmentStatus.ACTIVE, null);

            EnrollmentEntity entity = mapper.toEntity(domain);
            assertNotNull(entity);
            assertNull(entity.getResumeLessonId());

            Enrollment mappedBack = mapper.toDomain(entity);
            assertEquals(domain, mappedBack);
        }

        @Test
        @DisplayName("Throws IllegalArgumentException for invalid enrollment status code")
        void throwsOnUnknownEnrollmentStatus() {
            EnrollmentEntity invalidEntity = new EnrollmentEntity(
                    ENROLLMENT_ID, USER_ID, COURSE_ID, "PENDING", null
            );
            assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(invalidEntity));
        }

        @Test
        @DisplayName("Maps LessonProgress bidirectionally preserving completion and skip flags")
        void bidirectionalMappingLessonProgress() {
            LessonProgress domain = new LessonProgress(PROGRESS_ID, ENROLLMENT_ID, LESSON_ID, true, true);

            LessonProgressEntity entity = mapper.toEntity(domain);
            assertNotNull(entity);
            assertAll(
                    () -> assertEquals(PROGRESS_ID, entity.getId()),
                    () -> assertEquals(ENROLLMENT_ID, entity.getEnrollmentId()),
                    () -> assertEquals(LESSON_ID, entity.getLessonId()),
                    () -> assertTrue(entity.isCompleted()),
                    () -> assertTrue(entity.isMasterySkipped())
            );

            LessonProgress mappedBack = mapper.toDomain(entity);
            assertEquals(domain, mappedBack);
        }
    }
}
