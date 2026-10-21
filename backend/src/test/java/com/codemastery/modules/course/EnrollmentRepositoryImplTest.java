package com.codemastery.modules.course;

import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.LessonProgress;
import com.codemastery.modules.course.infrastructure.persistence.entity.EnrollmentEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonProgressEntity;
import com.codemastery.modules.course.infrastructure.persistence.mapper.EnrollmentMapper;
import com.codemastery.modules.course.infrastructure.persistence.repository.EnrollmentJpaRepository;
import com.codemastery.modules.course.infrastructure.persistence.repository.EnrollmentRepositoryImpl;
import com.codemastery.modules.course.infrastructure.persistence.repository.LessonProgressJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnrollmentRepositoryImpl Unit Tests")
class EnrollmentRepositoryImplTest {

    @Mock
    private EnrollmentJpaRepository enrollmentJpaRepository;

    @Mock
    private LessonProgressJpaRepository lessonProgressJpaRepository;

    private EnrollmentMapper mapper;
    private EnrollmentRepositoryImpl repository;

    private static final UUID ENROLLMENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID LESSON_ID = UUID.fromString("00000000-0000-4000-8000-000000000004");
    private static final UUID PROGRESS_ID = UUID.fromString("00000000-0000-4000-8000-000000000005");

    @BeforeEach
    void setUp() {
        mapper = new EnrollmentMapper();
        repository = new EnrollmentRepositoryImpl(enrollmentJpaRepository, lessonProgressJpaRepository, mapper);
    }

    @Test
    @DisplayName("findById handles null safely and returns mapped domain")
    void findById() {
        assertTrue(repository.findById(null).isEmpty());
        verify(enrollmentJpaRepository, never()).findById(any());

        EnrollmentEntity entity = new EnrollmentEntity(ENROLLMENT_ID, USER_ID, COURSE_ID, "active", null);
        when(enrollmentJpaRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(entity));

        Optional<Enrollment> result = repository.findById(ENROLLMENT_ID);
        assertTrue(result.isPresent());
        assertEquals(EnrollmentStatus.ACTIVE, result.get().status());
    }

    @Test
    @DisplayName("findByUserIdAndCourseId handles null safely and returns mapped domain")
    void findByUserIdAndCourseId() {
        assertTrue(repository.findByUserIdAndCourseId(null, COURSE_ID).isEmpty());
        assertTrue(repository.findByUserIdAndCourseId(USER_ID, null).isEmpty());

        EnrollmentEntity entity = new EnrollmentEntity(ENROLLMENT_ID, USER_ID, COURSE_ID, "completed", LESSON_ID);
        when(enrollmentJpaRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(Optional.of(entity));

        Optional<Enrollment> result = repository.findByUserIdAndCourseId(USER_ID, COURSE_ID);
        assertTrue(result.isPresent());
        assertEquals(EnrollmentStatus.COMPLETED, result.get().status());
        assertEquals(LESSON_ID, result.get().resumeLessonId());
    }

    @Test
    @DisplayName("existsByUserIdAndCourseIdAndStatusIn maps status set to strings and queries JPA")
    void existsByUserIdAndCourseIdAndStatusIn() {
        assertFalse(repository.existsByUserIdAndCourseIdAndStatusIn(null, COURSE_ID, Set.of(EnrollmentStatus.ACTIVE)));
        assertFalse(repository.existsByUserIdAndCourseIdAndStatusIn(USER_ID, null, Set.of(EnrollmentStatus.ACTIVE)));
        assertFalse(repository.existsByUserIdAndCourseIdAndStatusIn(USER_ID, COURSE_ID, null));
        assertFalse(repository.existsByUserIdAndCourseIdAndStatusIn(USER_ID, COURSE_ID, Set.of()));

        when(enrollmentJpaRepository.existsByUserIdAndCourseIdAndStatusIn(
                eq(USER_ID), eq(COURSE_ID), eq(List.of("active", "completed"))
        )).thenReturn(true);

        boolean exists = repository.existsByUserIdAndCourseIdAndStatusIn(
                USER_ID, COURSE_ID, Set.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED)
        );
        assertTrue(exists);
    }

    @Test
    @DisplayName("save maps domain to entity and returns mapped result")
    void saveEnrollment() {
        assertNull(repository.save(null));

        Enrollment domain = new Enrollment(ENROLLMENT_ID, USER_ID, COURSE_ID, EnrollmentStatus.ACTIVE, null);
        EnrollmentEntity entity = new EnrollmentEntity(ENROLLMENT_ID, USER_ID, COURSE_ID, "active", null);
        when(enrollmentJpaRepository.save(any(EnrollmentEntity.class))).thenReturn(entity);

        Enrollment saved = repository.save(domain);
        assertNotNull(saved);
        assertEquals(ENROLLMENT_ID, saved.id());
    }

    @Test
    @DisplayName("findProgressByEnrollmentIdAndLessonId handles null and maps entity")
    void findProgress() {
        assertTrue(repository.findProgressByEnrollmentIdAndLessonId(null, LESSON_ID).isEmpty());
        assertTrue(repository.findProgressByEnrollmentIdAndLessonId(ENROLLMENT_ID, null).isEmpty());

        LessonProgressEntity entity = new LessonProgressEntity(PROGRESS_ID, ENROLLMENT_ID, LESSON_ID, true, false);
        when(lessonProgressJpaRepository.findByEnrollmentIdAndLessonId(ENROLLMENT_ID, LESSON_ID))
                .thenReturn(Optional.of(entity));

        Optional<LessonProgress> result = repository.findProgressByEnrollmentIdAndLessonId(ENROLLMENT_ID, LESSON_ID);
        assertTrue(result.isPresent());
        assertTrue(result.get().completed());
        assertFalse(result.get().masterySkipped());
    }

    @Test
    @DisplayName("findProgressByEnrollmentId returns mapped list")
    void findProgressList() {
        assertTrue(repository.findProgressByEnrollmentId(null).isEmpty());

        LessonProgressEntity entity = new LessonProgressEntity(PROGRESS_ID, ENROLLMENT_ID, LESSON_ID, true, false);
        when(lessonProgressJpaRepository.findByEnrollmentId(ENROLLMENT_ID)).thenReturn(List.of(entity));

        List<LessonProgress> list = repository.findProgressByEnrollmentId(ENROLLMENT_ID);
        assertEquals(1, list.size());
        assertEquals(PROGRESS_ID, list.get(0).id());
    }

    @Test
    @DisplayName("saveLessonProgress maps domain to entity and returns mapped result")
    void saveLessonProgress() {
        assertNull(repository.saveLessonProgress(null));

        LessonProgress domain = new LessonProgress(PROGRESS_ID, ENROLLMENT_ID, LESSON_ID, true, true);
        LessonProgressEntity entity = new LessonProgressEntity(PROGRESS_ID, ENROLLMENT_ID, LESSON_ID, true, true);
        when(lessonProgressJpaRepository.save(any(LessonProgressEntity.class))).thenReturn(entity);

        LessonProgress saved = repository.saveLessonProgress(domain);
        assertNotNull(saved);
        assertEquals(PROGRESS_ID, saved.id());
        assertTrue(saved.masterySkipped());
    }
}
