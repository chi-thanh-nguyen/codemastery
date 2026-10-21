package com.codemastery.modules.course;

import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.LessonProgress;
import com.codemastery.modules.course.infrastructure.persistence.entity.EnrollmentEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonProgressEntity;
import com.codemastery.modules.course.infrastructure.persistence.mapper.EnrollmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EnrollmentMapper Unit Tests")
class EnrollmentMapperTest {

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
    void handlesNullInputs() {
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
