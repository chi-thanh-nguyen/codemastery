package com.codemastery.modules.course;

import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.infrastructure.persistence.entity.CourseEntity;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseMapper;
import com.codemastery.modules.course.infrastructure.persistence.repository.CourseJpaRepository;
import com.codemastery.modules.course.infrastructure.persistence.repository.CourseRepositoryImpl;
import com.codemastery.support.fixtures.CourseFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
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
@DisplayName("CourseRepositoryImpl Unit Tests")
class CourseRepositoryImplTest {

    @Mock
    private CourseJpaRepository courseJpaRepository;

    private CourseMapper courseMapper;
    private CourseRepositoryImpl repository;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID INSTRUCTOR_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @BeforeEach
    void setUp() {
        courseMapper = new CourseMapper();
        repository = new CourseRepositoryImpl(courseJpaRepository, courseMapper);
    }

    @Test
    @DisplayName("findById returns empty when id is null")
    void findByIdNullReturnsEmpty() {
        Optional<Course> result = repository.findById(null);
        assertTrue(result.isEmpty());
        verify(courseJpaRepository, never()).findById(any());
    }

    @Test
    @DisplayName("findById queries JPA repository and maps entity to domain")
    void findByIdReturnsMappedDomain() {
        CourseEntity entity = new CourseEntity(
                COURSE_ID,
                INSTRUCTOR_ID,
                "Java Course",
                "Description",
                null,
                "published",
                false,
                null
        );
        when(courseJpaRepository.findById(COURSE_ID)).thenReturn(Optional.of(entity));

        Optional<Course> result = repository.findById(COURSE_ID);
        assertTrue(result.isPresent());
        assertEquals("Java Course", result.get().title());
        assertEquals(PublishStatus.PUBLISHED, result.get().publicationStatus());
    }

    @Test
    @DisplayName("findPublishedById returns empty when id is null")
    void findPublishedByIdNullReturnsEmpty() {
        Optional<Course> result = repository.findPublishedById(null);
        assertTrue(result.isEmpty());
        verify(courseJpaRepository, never()).findByIdAndPublicationStatus(any(), any());
    }

    @Test
    @DisplayName("findPublishedById queries with 'published' status")
    void findPublishedByIdQueriesWithPublishedStatus() {
        CourseEntity entity = new CourseEntity(
                COURSE_ID,
                INSTRUCTOR_ID,
                "Published Java",
                "Description",
                null,
                "published",
                false,
                null
        );
        when(courseJpaRepository.findByIdAndPublicationStatus(COURSE_ID, "published"))
                .thenReturn(Optional.of(entity));

        Optional<Course> result = repository.findPublishedById(COURSE_ID);
        assertTrue(result.isPresent());
        assertEquals("Published Java", result.get().title());
    }

    @Test
    @DisplayName("findPublishedCourses applies default title ASC and id ASC sorting when unsorted")
    void findPublishedCoursesAppliesDefaultSorting() {
        CourseEntity entity = new CourseEntity(
                COURSE_ID,
                INSTRUCTOR_ID,
                "Algorithms",
                "Algorithms 101",
                null,
                "published",
                false,
                null
        );
        Page<CourseEntity> entityPage = new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1);
        when(courseJpaRepository.findByPublicationStatus(eq("published"), any(Pageable.class)))
                .thenReturn(entityPage);

        Page<Course> result = repository.findPublishedCourses(PageRequest.of(0, 20));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Algorithms", result.getContent().get(0).title());

        verify(courseJpaRepository).findByPublicationStatus(eq("published"), pageableCaptor.capture());
        Pageable passedPageable = pageableCaptor.getValue();
        Sort sort = passedPageable.getSort();
        assertNotNull(sort.getOrderFor("title"));
        assertNotNull(sort.getOrderFor("id"));
        assertEquals(Sort.Direction.ASC, sort.getOrderFor("title").getDirection());
        assertEquals(Sort.Direction.ASC, sort.getOrderFor("id").getDirection());
    }

    @Test
    @DisplayName("findPublishedCourses handles null pageable with default page=0, size=20 and default sort")
    void findPublishedCoursesHandlesNullPageable() {
        when(courseJpaRepository.findByPublicationStatus(eq("published"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Course> result = repository.findPublishedCourses(null);
        assertNotNull(result);

        verify(courseJpaRepository).findByPublicationStatus(eq("published"), pageableCaptor.capture());
        Pageable passed = pageableCaptor.getValue();
        assertEquals(0, passed.getPageNumber());
        assertEquals(20, passed.getPageSize());
        assertEquals(Sort.Direction.ASC, passed.getSort().getOrderFor("title").getDirection());
        assertEquals(Sort.Direction.ASC, passed.getSort().getOrderFor("id").getDirection());
    }

    @Test
    @DisplayName("findPublishedCourses preserves explicit sort if already provided")
    void findPublishedCoursesPreservesExplicitSort() {
        when(courseJpaRepository.findByPublicationStatus(eq("published"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Sort customSort = Sort.by(Sort.Direction.DESC, "title");
        repository.findPublishedCourses(PageRequest.of(1, 10, customSort));

        verify(courseJpaRepository).findByPublicationStatus(eq("published"), pageableCaptor.capture());
        Pageable passed = pageableCaptor.getValue();
        assertEquals(customSort, passed.getSort());
    }

    @Test
    @DisplayName("existsById delegates to JPA repository safely")
    void existsByIdDelegates() {
        assertFalse(repository.existsById(null));

        when(courseJpaRepository.existsById(COURSE_ID)).thenReturn(true);
        assertTrue(repository.existsById(COURSE_ID));
    }

    @Test
    @DisplayName("save maps domain to entity, invokes JPA save, and maps back to domain")
    void saveCourse() {
        assertNull(repository.save(null));

        Course domain = CourseFixtures.publishedCourse(COURSE_ID, INSTRUCTOR_ID, "New Course", "New Desc");
        CourseEntity savedEntity = new CourseEntity(
                COURSE_ID,
                INSTRUCTOR_ID,
                "New Course",
                "New Desc",
                null,
                "published",
                false,
                null
        );
        when(courseJpaRepository.save(any(CourseEntity.class))).thenReturn(savedEntity);

        Course saved = repository.save(domain);
        assertNotNull(saved);
        assertEquals(COURSE_ID, saved.id());
        assertEquals("New Course", saved.title());
        assertEquals(PublishStatus.PUBLISHED, saved.publicationStatus());
    }
}
