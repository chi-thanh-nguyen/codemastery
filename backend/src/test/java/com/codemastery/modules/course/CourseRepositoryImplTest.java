package com.codemastery.modules.course;

import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.infrastructure.persistence.entity.CourseEntity;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseMapper;
import com.codemastery.modules.course.infrastructure.persistence.repository.CourseJpaRepository;
import com.codemastery.modules.course.infrastructure.persistence.repository.CourseRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseRepositoryImplTest {

    @Mock
    private CourseJpaRepository jpa;

    private CourseRepositoryImpl repository;

    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID INSTRUCTOR_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @BeforeEach
    void setUp() {
        repository = new CourseRepositoryImpl(jpa, new CourseMapper());
    }

    @Test
    void nullDetailIdDoesNotQueryPersistence() {
        assertTrue(repository.findPublishedById(null).isEmpty());
        verifyNoInteractions(jpa);
    }

    @Test
    void detailUsesPublishedPredicateAndPreservesScalarFields() {
        CourseEntity entity = course(COURSE_ID, "Published Java");
        when(jpa.findByIdAndPublicationStatus(COURSE_ID, "published")).thenReturn(Optional.of(entity));

        Course result = repository.findPublishedById(COURSE_ID).orElseThrow();

        assertEquals(new CourseMapper().toDomain(entity), result);
        assertEquals(PublishStatus.PUBLISHED, result.publicationStatus());
        verify(jpa).findByIdAndPublicationStatus(COURSE_ID, "published");
        verifyNoMoreInteractions(jpa);
    }

    @Test
    void hiddenOrMissingDetailHasTheSameEmptyRepositoryOutcome() {
        UUID missingId = UUID.fromString("00000000-0000-4000-8000-000000000099");
        when(jpa.findByIdAndPublicationStatus(COURSE_ID, "published")).thenReturn(Optional.empty());
        when(jpa.findByIdAndPublicationStatus(missingId, "published")).thenReturn(Optional.empty());

        assertTrue(repository.findPublishedById(COURSE_ID).isEmpty());
        assertTrue(repository.findPublishedById(missingId).isEmpty());
    }

    @Test
    void catalogueQueryDeclaresExactPostgresqlOrderAndMatchingCountFilter() throws Exception {
        // Contract regression only: this does not execute SQL against PostgreSQL.
        Query query = CourseJpaRepository.class.getMethod("findPublishedCatalogue", Pageable.class)
                .getAnnotation(Query.class);
        assertNotNull(query);
        assertTrue(query.nativeQuery());
        assertEquals("SELECT * FROM public.courses WHERE publication_status = 'published' "
                + "ORDER BY title COLLATE \"C\" ASC, id ASC", query.value());
        assertEquals("SELECT count(*) FROM public.courses WHERE publication_status = 'published'",
                query.countQuery());
    }

    @Test
    void nullPageableUsesApprovedDefaultPageAndSizeWithoutDynamicSort() {
        Pageable expected = PageRequest.of(0, 20);
        when(jpa.findPublishedCatalogue(expected)).thenReturn(Page.empty(expected));

        Page<Course> result = repository.findPublishedCourses(null);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
        verify(jpa).findPublishedCatalogue(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "id", "publicationStatus", "unsupported"})
    void callerSortIsRemovedWhilePageAndSizeArePreserved(String property) {
        Pageable expected = PageRequest.of(2, 7);
        when(jpa.findPublishedCatalogue(expected)).thenReturn(Page.empty(expected));

        repository.findPublishedCourses(PageRequest.of(2, 7, Sort.by(Sort.Direction.DESC, property)));

        verify(jpa).findPublishedCatalogue(expected);
        verifyNoMoreInteractions(jpa);
    }

    @Test
    void unpagedInputAlsoCannotAppendCallerSort() {
        when(jpa.findPublishedCatalogue(Pageable.unpaged())).thenReturn(Page.empty());

        repository.findPublishedCourses(Pageable.unpaged(Sort.by(Sort.Direction.DESC, "title")));

        verify(jpa).findPublishedCatalogue(Pageable.unpaged());
    }

    @Test
    void mappingPreservesQueryOrderUuidTiesAndCountsAcrossPageBoundaries() {
        // Already ordered query results, NOT an in-memory substitute for PostgreSQL ordering.
        List<CourseEntity> ordered = List.of(
                course(UUID.fromString("00000000-0000-0000-0000-000000000001"), "A"),
                course(UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"), "A"),
                course(UUID.fromString("80000000-0000-0000-0000-000000000000"), "A"),
                course(UUID.fromString("00000000-0000-0000-0000-000000000004"), "Z"),
                course(UUID.fromString("00000000-0000-0000-0000-000000000005"), "é"));
        List<UUID> returnedIds = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            Pageable expected = PageRequest.of(page, 2);
            int start = Math.min(page * 2, ordered.size());
            int end = Math.min(start + 2, ordered.size());
            List<CourseEntity> rows = ordered.subList(start, end);
            when(jpa.findPublishedCatalogue(expected)).thenReturn(new PageImpl<>(rows, expected, 5));

            Page<Course> result = repository.findPublishedCourses(
                    PageRequest.of(page, 2, Sort.by(Sort.Direction.DESC, "title")));

            assertEquals(rows.stream().map(CourseEntity::getId).toList(),
                    result.map(Course::id).getContent());
            assertEquals(5, result.getTotalElements());
            assertEquals(3, result.getTotalPages());
            assertEquals(page, result.getNumber());
            assertEquals(2, result.getSize());
            assertTrue(result.getSort().isUnsorted());
            assertEquals(page >= 2, result.isLast());
            returnedIds.addAll(result.map(Course::id).getContent());
            verify(jpa).findPublishedCatalogue(expected);
        }
        assertEquals(ordered.stream().map(CourseEntity::getId).toList(), returnedIds);
        verifyNoMoreInteractions(jpa);
    }

    @Test
    void emptyCataloguePreservesZeroCountAndPageMetadata() {
        Pageable page = PageRequest.of(0, 2);
        when(jpa.findPublishedCatalogue(page)).thenReturn(Page.empty(page));

        Page<Course> result = repository.findPublishedCourses(page);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getTotalPages());
        assertEquals(page, result.getPageable());
    }

    @Test
    void persistenceFailureIsPropagatedWithoutInventingAnEmptyCatalogue() {
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(jpa.findPublishedCatalogue(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> repository.findPublishedCourses(PageRequest.of(0, 20))));
    }

    private static CourseEntity course(UUID id, String title) {
        return new CourseEntity(id, INSTRUCTOR_ID, title, "Description", null, "published", false, null);
    }
}
