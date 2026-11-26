package com.codemastery.modules.course.application.service;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.domain.repository.CourseRepository.AccessSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.codemastery.modules.course.domain.model.PublishStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Isolated Course policy tests. The producer double does not verify Auth or JWT wiring. */
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COURSE = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID LESSON = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID MATERIAL = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Mock CourseRepository repository;
    @Mock UserAccountService identity;
    private CourseAccessService service;

    @BeforeEach void setUp() { service = new CourseAccessService(repository, identity); }

    private List<Runnable> assertions() {
        return List.of(() -> service.requireLearnerCourseAccess(COURSE),
                () -> service.requireLearnerLessonAccess(COURSE, LESSON),
                () -> service.requireCourseOwnership(COURSE),
                () -> service.requireLessonOwnership(COURSE, LESSON),
                () -> service.requireLessonReadAccess(LESSON),
                () -> service.requireEnrolmentEligibility(COURSE),
                () -> service.requireMaterialReadAccess(COURSE, LESSON, MATERIAL));
    }

    private void actor(IdentityRole role) { when(identity.requireCurrentIdentity()).thenReturn(new CurrentIdentity(ACTOR, role)); }
    private AccessSnapshot snapshot(UUID owner, PublishStatus course, PublishStatus lesson,
                                    PublishStatus material, EnrollmentStatus membership) {
        return new AccessSnapshot(COURSE, owner, course, lesson == null ? null : LESSON, lesson,
                material == null ? null : MATERIAL, material, membership);
    }
    private void course(AccessSnapshot snapshot, UUID lesson, UUID material) {
        when(repository.findCourseAccessSnapshot(ACTOR, COURSE, lesson, material)).thenReturn(Optional.of(snapshot));
    }
    private void lesson(AccessSnapshot snapshot) {
        when(repository.findLessonAccessSnapshot(ACTOR, LESSON)).thenReturn(Optional.of(snapshot));
    }

    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4,5,6})
    void authenticationRejectionIncludingLockedAccountPrecedesAllCourseReads(int index) {
        // Auth owns detecting invalid credentials/account locks; only propagation is tested here.
        var denial = new AuthenticationCredentialsNotFoundException("Authentication required");
        when(identity.requireCurrentIdentity()).thenThrow(denial);
        assertSame(denial, assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> assertions().get(index).run()));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4,5,6})
    void providerFailureIsNotConcealedAsMissingResourceOrAuthentication(int index) {
        var failure = new IllegalStateException("provider unavailable");
        when(identity.requireCurrentIdentity()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> assertions().get(index).run()));
        verifyNoInteractions(repository);
    }

    @Test void malformedProviderResultsFailClosed() {
        for (CurrentIdentity invalid : Arrays.asList(null, new CurrentIdentity(null, IdentityRole.LEARNER),
                new CurrentIdentity(ACTOR, null))) {
            when(identity.requireCurrentIdentity()).thenReturn(invalid);
            assertThrows(IllegalStateException.class, () -> service.requireLearnerCourseAccess(COURSE));
        }
        verifyNoInteractions(repository);
    }

    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4,5,6})
    void administratorNeverInheritsLearnerOrInstructorPermissions(int index) {
        actor(IdentityRole.ADMINISTRATOR);
        assertThrows(AccessDeniedException.class, () -> assertions().get(index).run());
        verifyNoInteractions(repository);
    }

    @Test void instructorCannotUseLearnerChecksAndLearnerCannotUseOwnership() {
        actor(IdentityRole.INSTRUCTOR);
        assertThrows(AccessDeniedException.class, () -> service.requireLearnerCourseAccess(COURSE));
        assertThrows(AccessDeniedException.class, () -> service.requireLearnerLessonAccess(COURSE, LESSON));
        assertThrows(AccessDeniedException.class, () -> service.requireEnrolmentEligibility(COURSE));
        actor(IdentityRole.LEARNER);
        assertThrows(AccessDeniedException.class, () -> service.requireCourseOwnership(COURSE));
        assertThrows(AccessDeniedException.class, () -> service.requireLessonOwnership(COURSE, LESSON));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest @EnumSource(value = EnrollmentStatus.class, names = {"ACTIVE", "COMPLETED"})
    void learnerCourseAccessAllowsBothMembershipStatesWithOneRead(EnrollmentStatus membership) {
        actor(IdentityRole.LEARNER);
        course(snapshot(OTHER, PUBLISHED, null, null, membership), null, null);
        assertDoesNotThrow(() -> service.requireLearnerCourseAccess(COURSE));
        verify(repository).findCourseAccessSnapshot(ACTOR, COURSE, null, null);
        verifyNoMoreInteractions(repository);
    }

    @Test void missingAndHiddenCoursePrecedeMembershipDenial() {
        actor(IdentityRole.LEARNER);
        when(repository.findCourseAccessSnapshot(ACTOR, COURSE, null, null)).thenReturn(Optional.empty());
        var missing = assertThrows(ResourceNotFoundException.class, () -> service.requireLearnerCourseAccess(COURSE));
        course(snapshot(OTHER, HIDDEN, null, null, EnrollmentStatus.LEFT), null, null);
        var hidden = assertThrows(ResourceNotFoundException.class, () -> service.requireLearnerCourseAccess(COURSE));
        assertEquals(missing.getMessage(), hidden.getMessage());
    }

    @Test void absentAndLeftMembershipDenyBeforeMissingLesson() {
        actor(IdentityRole.LEARNER);
        for (EnrollmentStatus membership : Arrays.asList(null, EnrollmentStatus.LEFT)) {
            course(snapshot(OTHER, PUBLISHED, null, null, membership), LESSON, null);
            assertThrows(AccessDeniedException.class, () -> service.requireLearnerLessonAccess(COURSE, LESSON));
        }
    }

    @ParameterizedTest @EnumSource(value = EnrollmentStatus.class, names = {"ACTIVE", "COMPLETED"})
    void learnerLessonUsesSingleScopedSnapshotWithoutMasteryGate(EnrollmentStatus membership) {
        actor(IdentityRole.LEARNER);
        course(snapshot(OTHER, PUBLISHED, PUBLISHED, null, membership), LESSON, null);
        assertDoesNotThrow(() -> service.requireLearnerLessonAccess(COURSE, LESSON));
        verify(repository).findCourseAccessSnapshot(ACTOR, COURSE, LESSON, null);
        verifyNoMoreInteractions(repository); // No prerequisites/progress/adaptive provider exists here.
    }

    @Test void learnerCannotReadHiddenMissingOrForeignLesson() {
        actor(IdentityRole.LEARNER);
        for (AccessSnapshot row : List.of(snapshot(OTHER, PUBLISHED, HIDDEN, null, EnrollmentStatus.ACTIVE),
                snapshot(OTHER, PUBLISHED, null, null, EnrollmentStatus.ACTIVE),
                new AccessSnapshot(COURSE, OTHER, PUBLISHED, OTHER, PUBLISHED, null, null, EnrollmentStatus.ACTIVE))) {
            course(row, LESSON, null);
            assertThrows(ResourceNotFoundException.class, () -> service.requireLearnerLessonAccess(COURSE, LESSON));
        }
    }

    @ParameterizedTest @EnumSource(PublishStatus.class)
    void instructorOwnsHiddenOrPublishedCourseAndLesson(PublishStatus publication) {
        actor(IdentityRole.INSTRUCTOR);
        course(snapshot(ACTOR, publication, null, null, null), null, null);
        service.requireCourseOwnership(COURSE);
        course(snapshot(ACTOR, publication, publication, null, null), LESSON, null);
        service.requireLessonOwnership(COURSE, LESSON);
        verify(repository).findCourseAccessSnapshot(ACTOR, COURSE, null, null);
        verify(repository).findCourseAccessSnapshot(ACTOR, COURSE, LESSON, null);
        verifyNoMoreInteractions(repository);
    }

    @Test void nonOwnerPublished403Hidden404AndOwnerForeignLesson404() {
        actor(IdentityRole.INSTRUCTOR);
        course(snapshot(OTHER, PUBLISHED, null, null, null), LESSON, null);
        assertThrows(AccessDeniedException.class, () -> service.requireLessonOwnership(COURSE, LESSON));
        course(snapshot(OTHER, HIDDEN, null, null, null), LESSON, null);
        assertThrows(ResourceNotFoundException.class, () -> service.requireLessonOwnership(COURSE, LESSON));
        course(snapshot(ACTOR, HIDDEN, null, null, null), LESSON, null);
        assertThrows(ResourceNotFoundException.class, () -> service.requireLessonOwnership(COURSE, LESSON));
    }

    @ParameterizedTest @EnumSource(value = EnrollmentStatus.class, names = {"ACTIVE", "COMPLETED"})
    void lessonOnlyLearnerAccessPassesWithOneAncestryRead(EnrollmentStatus membership) {
        actor(IdentityRole.LEARNER);
        lesson(snapshot(OTHER, PUBLISHED, PUBLISHED, null, membership));
        service.requireLessonReadAccess(LESSON);
        verify(repository).findLessonAccessSnapshot(ACTOR, LESSON);
        verifyNoMoreInteractions(repository);
    }

    @Test void lessonOnlyConcealsEveryResourceDependentLearnerDenial() {
        actor(IdentityRole.LEARNER);
        List<Optional<AccessSnapshot>> rows = List.of(Optional.empty(),
                Optional.of(snapshot(OTHER, HIDDEN, PUBLISHED, null, EnrollmentStatus.ACTIVE)),
                Optional.of(snapshot(OTHER, PUBLISHED, HIDDEN, null, EnrollmentStatus.ACTIVE)),
                Optional.of(snapshot(OTHER, PUBLISHED, PUBLISHED, null, EnrollmentStatus.LEFT)),
                Optional.of(snapshot(OTHER, PUBLISHED, PUBLISHED, null, null)));
        for (var row : rows) {
            when(repository.findLessonAccessSnapshot(ACTOR, LESSON)).thenReturn(row);
            assertEquals("Resource not available", assertThrows(ResourceNotFoundException.class,
                    () -> service.requireLessonReadAccess(LESSON)).getMessage());
        }
    }

    @Test void lessonOnlyInstructorPreviewAndNonOwnerConcealment() {
        actor(IdentityRole.INSTRUCTOR);
        lesson(snapshot(ACTOR, HIDDEN, HIDDEN, null, null));
        service.requireLessonReadAccess(LESSON);
        for (PublishStatus publication : PublishStatus.values()) {
            lesson(snapshot(OTHER, publication, PUBLISHED, null, null));
            assertThrows(ResourceNotFoundException.class, () -> service.requireLessonReadAccess(LESSON));
        }
    }

    @Test void eligibilityIgnoresAllMembershipStatesAndMakesNoMutation() {
        actor(IdentityRole.LEARNER);
        for (EnrollmentStatus membership : Arrays.asList(null, EnrollmentStatus.ACTIVE,
                EnrollmentStatus.COMPLETED, EnrollmentStatus.LEFT)) {
            course(snapshot(OTHER, PUBLISHED, null, null, membership), null, null);
            service.requireEnrolmentEligibility(COURSE);
        }
        course(snapshot(OTHER, HIDDEN, null, null, null), null, null);
        assertThrows(ResourceNotFoundException.class, () -> service.requireEnrolmentEligibility(COURSE));
        verify(repository, times(5)).findCourseAccessSnapshot(ACTOR, COURSE, null, null);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest @EnumSource(value = EnrollmentStatus.class, names = {"ACTIVE", "COMPLETED"})
    void materialReadLearnerRequiresScopedPublishedMaterial(EnrollmentStatus membership) {
        actor(IdentityRole.LEARNER);
        course(snapshot(OTHER, PUBLISHED, PUBLISHED, PUBLISHED, membership), LESSON, MATERIAL);
        service.requireMaterialReadAccess(COURSE, LESSON, MATERIAL);
        verify(repository).findCourseAccessSnapshot(ACTOR, COURSE, LESSON, MATERIAL);
        verifyNoMoreInteractions(repository);
    }

    @Test void materialDenialPrecedenceAndVisibility() {
        actor(IdentityRole.LEARNER);
        course(snapshot(OTHER, HIDDEN, null, null, null), LESSON, MATERIAL);
        assertThrows(ResourceNotFoundException.class, () -> service.requireMaterialReadAccess(COURSE, LESSON, MATERIAL));
        course(snapshot(OTHER, PUBLISHED, null, null, EnrollmentStatus.LEFT), LESSON, MATERIAL);
        assertThrows(AccessDeniedException.class, () -> service.requireMaterialReadAccess(COURSE, LESSON, MATERIAL));
        for (AccessSnapshot row : List.of(snapshot(OTHER, PUBLISHED, HIDDEN, PUBLISHED, EnrollmentStatus.ACTIVE),
                snapshot(OTHER, PUBLISHED, PUBLISHED, HIDDEN, EnrollmentStatus.ACTIVE),
                snapshot(OTHER, PUBLISHED, PUBLISHED, null, EnrollmentStatus.ACTIVE),
                new AccessSnapshot(COURSE, OTHER, PUBLISHED, LESSON, PUBLISHED, OTHER, PUBLISHED, EnrollmentStatus.ACTIVE))) {
            course(row, LESSON, MATERIAL);
            assertThrows(ResourceNotFoundException.class, () -> service.requireMaterialReadAccess(COURSE, LESSON, MATERIAL));
        }
    }

    @Test void instructorCanPreviewHiddenMaterialButCannotCrossAncestry() {
        actor(IdentityRole.INSTRUCTOR);
        course(snapshot(ACTOR, HIDDEN, HIDDEN, HIDDEN, null), LESSON, MATERIAL);
        service.requireMaterialReadAccess(COURSE, LESSON, MATERIAL);
        course(snapshot(ACTOR, HIDDEN, HIDDEN, null, null), LESSON, MATERIAL);
        assertThrows(ResourceNotFoundException.class, () -> service.requireMaterialReadAccess(COURSE, LESSON, MATERIAL));
    }

    @Test void dataProviderFailurePropagatesForBothQueryShapes() {
        actor(IdentityRole.LEARNER);
        var failure = new DataAccessResourceFailureException("unavailable");
        when(repository.findCourseAccessSnapshot(ACTOR, COURSE, null, null)).thenThrow(failure);
        when(repository.findLessonAccessSnapshot(ACTOR, LESSON)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> service.requireLearnerCourseAccess(COURSE)));
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> service.requireLessonReadAccess(LESSON)));
    }

    @Test void inconsistentCourseRootIsProviderFailure() {
        actor(IdentityRole.LEARNER);
        course(new AccessSnapshot(OTHER, OTHER, PUBLISHED, null, null, null, null, EnrollmentStatus.ACTIVE), null, null);
        assertThrows(IllegalStateException.class, () -> service.requireLearnerCourseAccess(COURSE));
    }

    @Test void nullIdsAreInternalCallerErrorsWithoutProviderCalls() {
        assertThrows(IllegalArgumentException.class, () -> service.requireLearnerCourseAccess(null));
        assertThrows(IllegalArgumentException.class, () -> service.requireLearnerLessonAccess(COURSE, null));
        assertThrows(IllegalArgumentException.class, () -> service.requireCourseOwnership(null));
        assertThrows(IllegalArgumentException.class, () -> service.requireLessonOwnership(null, LESSON));
        assertThrows(IllegalArgumentException.class, () -> service.requireLessonReadAccess(null));
        assertThrows(IllegalArgumentException.class, () -> service.requireEnrolmentEligibility(null));
        assertThrows(IllegalArgumentException.class, () -> service.requireMaterialReadAccess(COURSE, LESSON, null));
        verifyNoInteractions(identity, repository);
    }

    @Test void exactApprovedCallableSurface() throws Exception {
        Set<String> publicMethods = Arrays.stream(CourseAccessService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(m -> m.getName()).collect(Collectors.toSet());
        assertEquals(Set.of("requireLearnerCourseAccess", "requireLearnerLessonAccess", "requireCourseOwnership",
                "requireLessonOwnership", "requireLessonReadAccess"), publicMethods);
        for (var method : CourseAccessService.class.getDeclaredMethods()) {
            if (publicMethods.contains(method.getName()) || method.getName().equals("requireEnrolmentEligibility")
                    || method.getName().equals("requireMaterialReadAccess")) {
                assertEquals(void.class, method.getReturnType());
                assertTrue(Arrays.stream(method.getParameterTypes()).allMatch(t -> t == UUID.class));
            }
        }
        assertEquals(0, CourseAccessService.class.getDeclaredMethod("requireEnrolmentEligibility", UUID.class)
                .getModifiers() & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE));
        assertEquals(0, CourseAccessService.class.getDeclaredMethod("requireMaterialReadAccess", UUID.class, UUID.class, UUID.class)
                .getModifiers() & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE));
    }
    @Test
    void catalogueServicePreservesLargePageAndFilteredCountInTheRestRepresentation() {
        var page = org.springframework.data.domain.PageRequest.of(107374183, 20);
        when(repository.searchPublishedCourses(page, "basics", "described"))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(), page, 1));
        when(identity.instructorDisplayNames(Set.of())).thenReturn(java.util.Map.of());
        var content = mock(com.codemastery.modules.course.domain.repository.CourseContentRepository.class);
        var catalogue = new CourseCatalogueService(repository, content, identity);
        var result = catalogue.list(107374183, 20, "basics", "described");
        assertTrue(result.content().isEmpty()); assertEquals(107374183, result.page()); assertEquals(20, result.size());
        assertEquals(1, result.totalElements()); assertEquals(1, result.totalPages());
        verify(repository).searchPublishedCourses(page, "basics", "described"); verifyNoInteractions(content);
    }

    @Test
    void publicDetailRejectsMalformedUuidBeforeCallingCatalogueApplicationCode() throws Exception {
        var application = mock(CourseCatalogueService.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.codemastery.modules.course.api.controller.CourseCatalogueController(application))
                .setControllerAdvice(new com.codemastery.common.exception.GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/courses/0-0-4000-8000-2"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        verifyNoInteractions(application);
    }

    @Test void catalogueMetadataPreservesTotalPagesBeyondTheSpringPageIntegerCeiling() {
        var content = mock(com.codemastery.modules.course.domain.repository.CourseContentRepository.class);
        var paging = org.springframework.data.domain.PageRequest.of(0, 1);
        long total = (long) Integer.MAX_VALUE + 1;
        when(repository.searchPublishedCourses(paging, "", "any"))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(catalogueCourse(COURSE, PUBLISHED)), paging, total));
        when(identity.instructorDisplayNames(Set.of(ACTOR))).thenReturn(java.util.Map.of());
        var result = new CourseCatalogueService(repository, content, identity).list(0, 1, "", "any");
        assertEquals(total, result.totalElements()); assertEquals(total, result.totalPages());
    }

    @Test void catalogueResolvesOnlyDistinctInstructorsOnTheSelectedPublishedPage() {
        var content = mock(com.codemastery.modules.course.domain.repository.CourseContentRepository.class);
        var catalogue = new CourseCatalogueService(repository, content, identity);
        var paging = org.springframework.data.domain.PageRequest.of(1, 2);
        var first = catalogueCourse(COURSE, PUBLISHED);
        var second = catalogueCourse(OTHER, PUBLISHED);
        when(repository.searchPublishedCourses(paging, "%_", "none"))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(first, second), paging, 5));
        when(identity.instructorDisplayNames(Set.of(ACTOR))).thenReturn(java.util.Map.of(ACTOR, "Public name"));
        var result = catalogue.list(1, 2, "%_", "none");
        assertEquals(List.of(COURSE, OTHER), result.content().stream().map(row -> row.id()).toList());
        assertTrue(result.content().stream().allMatch(row -> "Public name".equals(row.instructorDisplayName())));
        assertEquals(5, result.totalElements()); assertEquals(3, result.totalPages());
        verify(identity).instructorDisplayNames(Set.of(ACTOR));
        verify(repository).searchPublishedCourses(paging, "%_", "none");
        verifyNoMoreInteractions(identity, repository); verifyNoInteractions(content);
    }

    @Test void catalogueFailsClosedBeforeIdentityLookupIfProviderReturnsHiddenContent() {
        var content = mock(com.codemastery.modules.course.domain.repository.CourseContentRepository.class);
        var paging = org.springframework.data.domain.PageRequest.of(0, 20);
        when(repository.searchPublishedCourses(paging, "", "any"))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(catalogueCourse(COURSE, HIDDEN))));
        assertThrows(IllegalStateException.class,
                () -> new CourseCatalogueService(repository, content, identity).list(0, 20, "", "any"));
        verifyNoInteractions(identity, content);
    }

    @Test void detailUsesCanonicalOrderAndOmitsHiddenForeignAndEmptySyllabusEntries() {
        var content = mock(com.codemastery.modules.course.domain.repository.CourseContentRepository.class);
        var catalogue = new CourseCatalogueService(repository, content, identity);
        when(repository.findPublishedById(COURSE)).thenReturn(Optional.of(catalogueCourse(COURSE, PUBLISHED)));
        when(identity.instructorDisplayNames(Set.of(ACTOR))).thenReturn(java.util.Map.of());
        when(content.findChaptersByCourseId(COURSE)).thenReturn(List.of(
                new com.codemastery.modules.course.domain.model.Chapter(OTHER, COURSE, "Second", 1),
                new com.codemastery.modules.course.domain.model.Chapter(ACTOR, COURSE, "First", 1),
                new com.codemastery.modules.course.domain.model.Chapter(MATERIAL, COURSE, "Empty", 2),
                new com.codemastery.modules.course.domain.model.Chapter(LESSON, OTHER, "Foreign", 0)));
        when(content.findPublishedLessonsByCourseId(COURSE)).thenReturn(List.of(
                new com.codemastery.modules.course.domain.model.Lesson(MATERIAL, ACTOR, "Later tie", "private body", 1, PUBLISHED),
                new com.codemastery.modules.course.domain.model.Lesson(LESSON, ACTOR, "Earlier tie", "private body", 1, PUBLISHED),
                new com.codemastery.modules.course.domain.model.Lesson(ACTOR, OTHER, "Second chapter", "body", 1, PUBLISHED),
                new com.codemastery.modules.course.domain.model.Lesson(OTHER, MATERIAL, "Hidden", "secret", 1, HIDDEN),
                new com.codemastery.modules.course.domain.model.Lesson(COURSE, LESSON, "Foreign", "secret", 1, PUBLISHED)));
        var result = catalogue.detail(COURSE);
        assertNull(result.instructorDisplayName());
        assertEquals(List.of(ACTOR, OTHER), result.syllabus().stream().map(row -> row.id()).toList());
        assertEquals(List.of(LESSON, MATERIAL), result.syllabus().getFirst().lessons().stream().map(row -> row.id()).toList());
        assertThrows(UnsupportedOperationException.class, () -> result.syllabus().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.syllabus().getFirst().lessons().clear());
        verify(identity).instructorDisplayNames(Set.of(ACTOR)); verifyNoMoreInteractions(identity);
    }

    @Test void hiddenAndMissingDetailHaveTheSameFailureWithoutIdentityOrSyllabusReads() {
        var content = mock(com.codemastery.modules.course.domain.repository.CourseContentRepository.class);
        var catalogue = new CourseCatalogueService(repository, content, identity);
        when(repository.findPublishedById(COURSE)).thenReturn(Optional.empty());
        var missing = assertThrows(ResourceNotFoundException.class, () -> catalogue.detail(COURSE));
        when(repository.findPublishedById(COURSE)).thenReturn(Optional.of(catalogueCourse(COURSE, HIDDEN)));
        var hidden = assertThrows(ResourceNotFoundException.class, () -> catalogue.detail(COURSE));
        assertEquals(missing.getMessage(), hidden.getMessage()); verifyNoInteractions(identity, content);
    }

    @Test void nullablePublicFieldsArePresentAndResponsesContainOnlyContractProperties() throws Exception {
        var content = mock(com.codemastery.modules.course.domain.repository.CourseContentRepository.class);
        var catalogue = new CourseCatalogueService(repository, content, identity);
        var paging = org.springframework.data.domain.PageRequest.of(0, 20);
        when(repository.findPublishedById(COURSE)).thenReturn(Optional.of(catalogueCourse(COURSE, PUBLISHED)));
        when(repository.searchPublishedCourses(paging, "", "any"))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(catalogueCourse(COURSE, PUBLISHED))));
        when(identity.instructorDisplayNames(Set.of(ACTOR))).thenReturn(java.util.Map.of());
        when(content.findChaptersByCourseId(COURSE)).thenReturn(List.of());
        when(content.findPublishedLessonsByCourseId(COURSE)).thenReturn(List.of());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.codemastery.modules.course.api.controller.CourseCatalogueController(catalogue))
                .setControllerAdvice(new com.codemastery.common.exception.GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/courses/" + COURSE))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json(
                        "{\"id\":\"" + COURSE + "\",\"title\":\"Title\",\"description\":\"Description\",\"instructorId\":\"" + ACTOR
                        + "\",\"instructorDisplayName\":null,\"prerequisiteDescription\":null,\"syllabus\":[]}",
                        org.springframework.test.json.JsonCompareMode.STRICT));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/courses"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].instructorDisplayName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].length()").value(5));
    }

    private com.codemastery.modules.course.domain.model.Course catalogueCourse(UUID id, PublishStatus publication) {
        return new com.codemastery.modules.course.domain.model.Course(id, ACTOR, "Title", "Description", null,
                publication, true, java.util.Map.of("private", "settings"));
    }

    // CO14 CourseManagementService: local orchestration only. Real transactions, locks, isolation
    // and PostgreSQL behavior are verified in integration/AdaptiveLearningFlowIntegrationTest.

    private CourseManagementService management(org.springframework.transaction.PlatformTransactionManager transactions) {
        return new CourseManagementService(repository, service, identity,
                mock(com.codemastery.modules.course.domain.repository.CourseContentRepository.class),
                mock(com.codemastery.modules.course.domain.repository.EnrollmentRepository.class), transactions, mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    @Test void managementGuardsRejectNullInputsBeforeAnyTransactionOrLookup() {
        var transactions = mock(org.springframework.transaction.PlatformTransactionManager.class);
        var management = management(transactions);
        assertThrows(IllegalArgumentException.class, () -> management.withCourseConfigurationWrite(null, () -> 1));
        assertThrows(IllegalArgumentException.class, () -> management.withCourseConfigurationWrite(COURSE, null));
        assertThrows(IllegalArgumentException.class, () -> management.tryWithCourseConfigurationWrite(null, () -> 1));
        assertThrows(IllegalArgumentException.class, () -> management.tryWithCourseConfigurationWrite(COURSE, null));
        assertThrows(IllegalArgumentException.class, () -> management.readAdaptiveConfiguration(null));
        verifyNoInteractions(transactions, repository, identity);
    }

    @Test void guardRefusesAnUnsynchronizedTransactionBeforeOwnershipOrLockingAndRollsItBack() {
        var transactions = mock(org.springframework.transaction.PlatformTransactionManager.class);
        var status = new org.springframework.transaction.support.SimpleTransactionStatus(true);
        when(transactions.getTransaction(any())).thenReturn(status);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(IllegalStateException.class, () -> management(transactions).withCourseConfigurationWrite(COURSE, calls::incrementAndGet));
        var definition = org.mockito.ArgumentCaptor.forClass(org.springframework.transaction.TransactionDefinition.class);
        verify(transactions).getTransaction(definition.capture());
        assertEquals(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED, definition.getValue().getIsolationLevel());
        assertEquals(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED, definition.getValue().getPropagationBehavior());
        assertFalse(definition.getValue().isReadOnly());
        verify(transactions).rollback(status);
        assertEquals(0, calls.get());
        verifyNoInteractions(repository, identity);
    }

    @Test void readAdmitsTheOwnerBeforeReadingStoredFieldsInARepeatableReadSnapshot() {
        var transactions = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus(true));
        actor(IdentityRole.INSTRUCTOR);
        course(snapshot(ACTOR, HIDDEN, null, null, null), null, null);
        var stored = new java.util.HashMap<String, Object>();
        stored.put("policy", new java.util.ArrayList<>(List.of(1, 2)));
        when(repository.findAdaptiveConfiguration(COURSE))
                .thenReturn(Optional.of(new CourseRepository.StoredAdaptiveConfiguration(COURSE, true, stored)));
        var result = management(transactions).readAdaptiveConfiguration(COURSE);
        assertEquals(new CourseManagementService.AdaptiveConfiguration(COURSE, true, java.util.Map.of("policy", List.of(1, 2))), result);
        var order = inOrder(repository);
        order.verify(repository).findCourseAccessSnapshot(ACTOR, COURSE, null, null);
        order.verify(repository).findAdaptiveConfiguration(COURSE);
        var definition = org.mockito.ArgumentCaptor.forClass(org.springframework.transaction.TransactionDefinition.class);
        verify(transactions).getTransaction(definition.capture());
        assertTrue(definition.getValue().isReadOnly());
        assertEquals(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ, definition.getValue().getIsolationLevel());
    }

    @Test void readFailsClosedForEnabledIntentWithoutSettingsAndForNonOwners() {
        var transactions = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus(true));
        actor(IdentityRole.INSTRUCTOR);
        course(snapshot(ACTOR, PUBLISHED, null, null, null), null, null);
        when(repository.findAdaptiveConfiguration(COURSE))
                .thenReturn(Optional.of(new CourseRepository.StoredAdaptiveConfiguration(COURSE, true, null)));
        assertThrows(IllegalStateException.class, () -> management(transactions).readAdaptiveConfiguration(COURSE));

        reset(repository);
        course(snapshot(OTHER, PUBLISHED, null, null, null), null, null);
        assertThrows(AccessDeniedException.class, () -> management(transactions).readAdaptiveConfiguration(COURSE));
        verify(repository, never()).findAdaptiveConfiguration(any());
    }

    @Test void settingsWritesValidateTheirShapeAndRequireTheHeldGuardBeforeTouchingStorage() {
        var transactions = mock(org.springframework.transaction.PlatformTransactionManager.class);
        var management = management(transactions);
        var current = new CourseManagementService.AdaptiveConfiguration(COURSE, false, null);
        assertThrows(IllegalArgumentException.class, () -> management.writeAdaptiveConfiguration(null, current, false, null));
        assertThrows(IllegalArgumentException.class, () -> management.writeAdaptiveConfiguration(COURSE, null, false, null));
        assertThrows(IllegalArgumentException.class, () -> management.writeAdaptiveConfiguration(COURSE,
                new CourseManagementService.AdaptiveConfiguration(OTHER, false, null), false, null));
        assertThrows(IllegalArgumentException.class, () -> management.writeAdaptiveConfiguration(COURSE, current, true, null));
        assertThrows(IllegalArgumentException.class, () -> management.writeAdaptiveConfiguration(COURSE, current, false,
                java.util.Map.of("value", Double.NaN)));
        assertThrows(IllegalStateException.class, () -> management.writeAdaptiveConfiguration(COURSE, current, false,
                java.util.Map.of("value", 1)));
        verifyNoInteractions(transactions, repository, identity);
    }

    @Test void adaptiveConfigurationSnapshotsAreDeeplyImmutableJsonCopies() {
        var nested = new java.util.ArrayList<Object>(List.of("a"));
        var settings = new java.util.HashMap<String, Object>();
        settings.put("list", nested);
        settings.put("nothing", null);
        var snapshot = new CourseManagementService.AdaptiveConfiguration(COURSE, true, settings);
        nested.add("mutated later");
        settings.put("added", true);
        assertEquals(List.of("a"), snapshot.adaptiveSettings().get("list"));
        assertEquals(2, snapshot.adaptiveSettings().size());
        assertTrue(snapshot.adaptiveSettings().containsKey("nothing"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.adaptiveSettings().put("x", 1));
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) snapshot.adaptiveSettings().get("list")).clear());
        assertThrows(IllegalArgumentException.class, () -> new CourseManagementService.AdaptiveConfiguration(COURSE, true, null));
        assertThrows(IllegalArgumentException.class, () -> new CourseManagementService.AdaptiveConfiguration(null, false, null));
        assertThrows(IllegalArgumentException.class, () -> new CourseManagementService.AdaptiveConfiguration(COURSE, false,
                java.util.Map.of("bad", new StringBuilder("mutable"))));
        assertThrows(IllegalArgumentException.class, () -> new CourseManagementService.AdaptiveConfiguration(COURSE, false,
                java.util.Map.of("bad", Float.POSITIVE_INFINITY)));
        assertNull(new CourseManagementService.AdaptiveConfiguration(COURSE, false, null).adaptiveSettings());
    }

    @Test void configurationBusyCarriesNoDiagnosticState() {
        var busy = new CourseManagementService.ConfigurationBusy();
        assertEquals("Course configuration is busy", busy.getMessage());
        assertNull(busy.getCause());
        assertEquals(0, busy.getStackTrace().length);
    }

    // CO06 CT21.authoring and CT12.course local rules. Real guard, SQL and HTTP security are
    // verified by the integration suites; these doubles prove ordering and validation only.

    @Test void authoringTextIsTrimmedNonblankAndMarkdownIsVerbatim() {
        assertEquals("Title", CourseManagementService.requiredText("  Title \n"));
        assertNull(CourseManagementService.optionalText(null));
        assertEquals("    code", CourseManagementService.markdown("    code"));
        assertNull(CourseManagementService.markdown(null));
        for (Runnable invalid : List.<Runnable>of(() -> CourseManagementService.requiredText(null),
                () -> CourseManagementService.requiredText(" \t"), () -> CourseManagementService.optionalText(" "),
                () -> CourseManagementService.markdown("\n "), () -> CourseManagementService.requiredText("A\u0000B"),
                () -> CourseManagementService.optionalText("\u0000"), () -> CourseManagementService.markdown("    code\u0000"))) {
            assertEquals(400, assertThrows(com.codemastery.common.exception.BusinessRuleException.class, invalid::run)
                    .getStatus().value());
        }
    }

    /** F1/F2: blank follows Java, OpenAPI (ECMA-262 \s) and PostgreSQL [:space:] together; unstorable UTF-16 is refused. */
    @Test void blankAndUnstorableTextFollowsTheContractAndDatabaseDefinitions() {
        for (String blank : List.of("\u0085", " \u0085 ", " ", "  ", "﻿", "　\t", " ")) {
            for (Runnable invalid : List.<Runnable>of(() -> CourseManagementService.requiredText(blank),
                    () -> CourseManagementService.optionalText(blank), () -> CourseManagementService.markdown(blank))) {
                assertEquals(400, assertThrows(com.codemastery.common.exception.BusinessRuleException.class, invalid::run).getStatus().value());
            }
        }
        for (String unstorable : List.of("A\uD800B", "A\uDC00B", "\uD83D", "x\uDE00", "\uDE00\uD83D")) {
            assertFalse(CourseManagementService.isStorable(unstorable));
            for (Runnable invalid : List.<Runnable>of(() -> CourseManagementService.requiredText(unstorable),
                    () -> CourseManagementService.markdown(unstorable))) {
                assertEquals(400, assertThrows(com.codemastery.common.exception.BusinessRuleException.class, invalid::run).getStatus().value());
            }
        }
        // Valid Unicode, including a surrogate pair, is kept; descriptive text is trimmed at its edges only.
        assertEquals("Café 😀 中", CourseManagementService.requiredText("\u0085  Café 😀 中﻿ "));
        assertEquals("a b", CourseManagementService.requiredText("a b"));
        assertEquals("\u0085  😀 code\n", CourseManagementService.markdown("\u0085  😀 code\n"));
        assertTrue(CourseManagementService.isStorable("😀"));
        assertTrue(CourseManagementService.isSpace(0x0085));
        assertFalse(CourseManagementService.isSpace('a'));
    }

    /** F3: the controllers' JSON-only check, shared by every JSON-body operation. */
    @Test void onlyAnApplicationJsonContentTypeIsAccepted() throws Exception {
        com.codemastery.modules.course.api.dto.CourseRequest.requireJson("application/json");
        com.codemastery.modules.course.api.dto.CourseRequest.requireJson("application/json;charset=UTF-8");
        for (String type : java.util.Arrays.asList(null, "", "text/plain", "application/xml", "application/*", "*/*",
                "application/problem+json", "not a media type")) {
            assertThrows(org.springframework.web.HttpMediaTypeNotSupportedException.class,
                    () -> com.codemastery.modules.course.api.dto.CourseRequest.requireJson(type), String.valueOf(type));
        }
    }

    // CT40-LESSON-READ-2026-11-08-r1 (ADR005 §3.1): local projection rules. Real admission, snapshot
    // isolation and PostgreSQL ancestry are verified in integration/EnrollmentIntegrationTest.

    private static final UUID CHAPTER_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID CHAPTER_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID L1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID L2 = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID L3 = UUID.fromString("00000000-0000-0000-0000-000000000013");

    private com.codemastery.modules.course.domain.repository.CourseContentRepository structureContent;

    private CourseStructureService structure() {
        structureContent = mock(com.codemastery.modules.course.domain.repository.CourseContentRepository.class);
        return new CourseStructureService(mock(CourseManagementService.class), service, structureContent,
                mock(com.codemastery.modules.course.domain.repository.EnrollmentRepository.class), identity);
    }
    private static com.codemastery.modules.course.domain.model.Chapter chapter(UUID id, UUID course, int position) {
        return new com.codemastery.modules.course.domain.model.Chapter(id, course, "Chapter title", position);
    }
    private static com.codemastery.modules.course.domain.model.Lesson structureLesson(UUID id, UUID chapter, int position, PublishStatus status) {
        return new com.codemastery.modules.course.domain.model.Lesson(id, chapter, "Lesson title", "Body", position, status);
    }
    private void structureRows(List<com.codemastery.modules.course.domain.model.Chapter> chapters,
                               List<com.codemastery.modules.course.domain.model.Lesson> lessons) {
        when(structureContent.findChaptersByCourseId(COURSE)).thenReturn(chapters);
        when(structureContent.findLessonsByCourseId(COURSE)).thenReturn(lessons);
    }

    @Test void structureProjectionSortsByChapterThenLessonPositionKeepsGapsAndFiltersHiddenForLearners() {
        var structure = structure();
        // Provider rows deliberately out of canonical order, with gaps.
        var chapters = List.of(chapter(CHAPTER_B, COURSE, 7), chapter(CHAPTER_A, COURSE, 2));
        var lessons = new java.util.ArrayList<>(List.of(structureLesson(L3, CHAPTER_B, 1, PUBLISHED),
                structureLesson(L2, CHAPTER_A, 9, HIDDEN), structureLesson(L1, CHAPTER_A, 3, PUBLISHED)));
        actor(IdentityRole.INSTRUCTOR);
        course(snapshot(ACTOR, HIDDEN, null, null, null), null, null);
        structureRows(chapters, lessons);
        var owner = structure.readStructure(COURSE);
        assertEquals(new CourseStructureService.StructureSnapshot(COURSE, List.of(
                new CourseStructureService.LessonReference(L1, CHAPTER_A, 2, 3, true),
                new CourseStructureService.LessonReference(L2, CHAPTER_A, 2, 9, false),
                new CourseStructureService.LessonReference(L3, CHAPTER_B, 7, 1, true))), owner);
        lessons.clear();
        assertEquals(3, owner.lessons().size(), "the snapshot is a defensive copy");
        assertThrows(UnsupportedOperationException.class, () -> owner.lessons().remove(0));

        reset(identity, repository);
        actor(IdentityRole.LEARNER);
        course(snapshot(OTHER, PUBLISHED, null, null, EnrollmentStatus.ACTIVE), null, null);
        lessons.addAll(List.of(structureLesson(L1, CHAPTER_A, 3, PUBLISHED), structureLesson(L2, CHAPTER_A, 9, HIDDEN)));
        assertEquals(List.of(new CourseStructureService.LessonReference(L1, CHAPTER_A, 2, 3, true)),
                structure.readStructure(COURSE).lessons(), "a Learner never receives a hidden Lesson");

        reset(structureContent);
        structureRows(List.of(), List.of());
        assertEquals(new CourseStructureService.StructureSnapshot(COURSE, List.of()), structure.readStructure(COURSE), "empty, not missing");
        assertThrows(NullPointerException.class, () -> new CourseStructureService.StructureSnapshot(COURSE, null));
        assertThrows(NullPointerException.class, () -> new CourseStructureService.LessonReference(null, CHAPTER_A, 1, 1, true));
    }

    @Test void structureProjectionRefusesOtherRolesAndFailedAdmissionBeforeAnyStructureRead() {
        var structure = structure();
        actor(IdentityRole.ADMINISTRATOR);
        assertThrows(AccessDeniedException.class, () -> structure.readStructure(COURSE));
        verifyNoInteractions(repository, structureContent);

        reset(identity);
        actor(IdentityRole.INSTRUCTOR);
        course(snapshot(OTHER, PUBLISHED, null, null, null), null, null);
        assertThrows(AccessDeniedException.class, () -> structure.readStructure(COURSE));
        reset(identity, repository);
        actor(IdentityRole.LEARNER);
        course(snapshot(OTHER, HIDDEN, null, null, EnrollmentStatus.ACTIVE), null, null);
        assertThrows(ResourceNotFoundException.class, () -> structure.readStructure(COURSE));
        verifyNoInteractions(structureContent);

        reset(identity);
        var denial = new AuthenticationCredentialsNotFoundException("Authentication required");
        when(identity.requireCurrentIdentity()).thenThrow(denial);
        assertSame(denial, assertThrows(AuthenticationCredentialsNotFoundException.class, () -> structure.readStructure(COURSE)));
        assertThrows(IllegalArgumentException.class, () -> structure.readStructure(null));
        verifyNoInteractions(structureContent);
    }

    @Test void structureProjectionFailsClosedOnProviderFailureOrInconsistentAncestry() {
        var structure = structure();
        when(identity.requireCurrentIdentity()).thenReturn(new CurrentIdentity(ACTOR, IdentityRole.INSTRUCTOR));
        when(repository.findCourseAccessSnapshot(ACTOR, COURSE, null, null)).thenReturn(Optional.of(snapshot(ACTOR, PUBLISHED, null, null, null)));
        UUID foreignCourse = UUID.randomUUID();
        var ok = chapter(CHAPTER_A, COURSE, 1);
        for (var rows : List.<List<?>>of(
                List.of(List.of(chapter(CHAPTER_A, foreignCourse, 1)), List.of()),                       // foreign Chapter
                List.of(List.of(ok), List.of(structureLesson(L1, CHAPTER_B, 1, PUBLISHED))),              // Lesson outside the Course
                List.of(List.of(ok), List.of(structureLesson(L1, CHAPTER_A, 1, PUBLISHED), structureLesson(L1, CHAPTER_A, 2, HIDDEN))),
                List.of(List.of(ok, ok), List.of()),                                                      // duplicate Chapter
                List.of(List.of(chapter(CHAPTER_A, COURSE, 0)), List.of()),                               // impossible position
                List.of(List.of(ok), List.of(structureLesson(L1, CHAPTER_A, 1, null))))) {
            reset(structureContent);
            @SuppressWarnings("unchecked") var chapters = (List<com.codemastery.modules.course.domain.model.Chapter>) rows.get(0);
            @SuppressWarnings("unchecked") var lessons = (List<com.codemastery.modules.course.domain.model.Lesson>) rows.get(1);
            when(structureContent.findChaptersByCourseId(COURSE)).thenReturn(chapters);
            lenient().when(structureContent.findLessonsByCourseId(COURSE)).thenReturn(lessons);
            assertThrows(IllegalStateException.class, () -> structure.readStructure(COURSE));
        }
        reset(structureContent);
        when(structureContent.findChaptersByCourseId(COURSE)).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> structure.readStructure(COURSE));
        reset(structureContent);
        var failure = new DataAccessResourceFailureException("provider down");
        when(structureContent.findChaptersByCourseId(COURSE)).thenReturn(List.of(ok));
        when(structureContent.findLessonsByCourseId(COURSE)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> structure.readStructure(COURSE)));
    }

    @Test void structureProjectionHasExactlyTheApprovedShape() throws Exception {
        var method = CourseStructureService.class.getMethod("readStructure", UUID.class);
        assertEquals(CourseStructureService.StructureSnapshot.class, method.getReturnType());
        var transactional = method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertTrue(transactional.readOnly());
        assertEquals(org.springframework.transaction.annotation.Isolation.REPEATABLE_READ, transactional.isolation());
        assertEquals(List.of("courseId:UUID", "lessons:List"), components(CourseStructureService.StructureSnapshot.class));
        assertEquals(List.of("lessonId:UUID", "chapterId:UUID", "chapterPosition:int", "lessonPosition:int", "published:boolean"),
                components(CourseStructureService.LessonReference.class));
        assertEquals(CourseStructureService.class, CourseStructureService.LessonReference.class.getEnclosingClass());
    }
    private static List<String> components(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(c -> c.getName() + ":" + c.getType().getSimpleName()).toList();
    }

    @Test void siblingOrderMustBeACompletePermutationOfCurrentSiblings() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        CourseStructureService.requirePermutation(List.of(b, a), List.of(a, b));
        CourseStructureService.requirePermutation(List.of(), List.of());
        assertEquals(400, assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                () -> CourseStructureService.requirePermutation(List.of(a, a), List.of(a, b))).getStatus().value());
        assertEquals(400, assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                () -> CourseStructureService.requirePermutation(List.of(a), List.of(a, b))).getStatus().value());
        assertThrows(ResourceNotFoundException.class,
                () -> CourseStructureService.requirePermutation(List.of(a, UUID.randomUUID()), List.of(a, b)));
    }

    @Test void authoringBodiesAreStrictJsonWithExactlyTheDeclaredFields() {
        assertEquals(Optional.of(new com.codemastery.modules.course.api.dto.CourseRequest.OwnedCourseWrite("T", "D", null)),
                com.codemastery.modules.course.api.dto.CourseRequest.courseWrite(
                        "{\"title\":\"T\",\"description\":\"D\",\"prerequisiteDescription\":null}"));
        for (String invalid : List.of("", "[]", "{\"title\":\"T\",\"description\":\"D\"}",
                "{\"title\":\"T\",\"description\":\"D\",\"prerequisiteDescription\":null,\"instructorId\":\"x\"}",
                "{\"title\":\"T\",\"title\":\"U\",\"description\":\"D\",\"prerequisiteDescription\":null}",
                "{\"title\":1,\"description\":\"D\",\"prerequisiteDescription\":null}")) {
            assertTrue(com.codemastery.modules.course.api.dto.CourseRequest.courseWrite(invalid).isEmpty(), invalid);
        }
        assertEquals(Optional.of(HIDDEN), com.codemastery.modules.course.api.dto.CourseRequest.publication("{\"publicationStatus\":\"hidden\"}"));
        assertTrue(com.codemastery.modules.course.api.dto.CourseRequest.publication("{\"publicationStatus\":\"draft\"}").isEmpty());
        UUID id = UUID.randomUUID();
        assertEquals(Optional.of(List.of(id)), com.codemastery.modules.course.api.dto.CourseStructureRequests
                .siblingOrder("{\"orderedIds\":[\"" + id + "\"]}"));
        assertTrue(com.codemastery.modules.course.api.dto.CourseStructureRequests.siblingOrder("{\"orderedIds\":[\"not-a-uuid\"]}").isEmpty());
        assertTrue(com.codemastery.modules.course.api.dto.CourseStructureRequests.lessonWrite("{\"title\":\"T\"}").isEmpty());
    }

    @Test void createdCourseIsHiddenOwnedByTheTrustedInstructorWithAdaptiveDisabled() {
        var content = mock(com.codemastery.modules.course.domain.repository.CourseContentRepository.class);
        var management = new CourseManagementService(repository, service, identity, content,
                mock(com.codemastery.modules.course.domain.repository.EnrollmentRepository.class),
                mock(org.springframework.transaction.PlatformTransactionManager.class), mock(org.springframework.context.ApplicationEventPublisher.class));
        actor(IdentityRole.INSTRUCTOR);
        var created = management.createOwnedCourse(" Title ", "Description", null);
        var stored = org.mockito.ArgumentCaptor.forClass(com.codemastery.modules.course.domain.model.Course.class);
        verify(repository).insert(stored.capture());
        assertEquals(ACTOR, stored.getValue().instructorId());
        assertEquals("Title", stored.getValue().title());
        assertEquals(HIDDEN, stored.getValue().publicationStatus());
        assertFalse(stored.getValue().adaptiveEnabled());
        assertNull(stored.getValue().adaptiveSettings());
        assertEquals("hidden", created.publicationStatus());
        assertEquals(List.of(), created.chapters());
    }

    @ParameterizedTest @EnumSource(value = IdentityRole.class, names = {"LEARNER", "ADMINISTRATOR"})
    void onlyAnInstructorCreatesAndOnlyAnAdministratorHidesBeforeAnyLookupOrWrite(IdentityRole role) {
        var management = management(mock(org.springframework.transaction.PlatformTransactionManager.class));
        actor(role);
        assertThrows(AccessDeniedException.class, () -> management.createOwnedCourse("T", "D", null));
        if (role == IdentityRole.LEARNER) {
            assertThrows(AccessDeniedException.class, () -> management.hideCourseAsAdministrator(COURSE));
            assertThrows(AccessDeniedException.class, () -> management.hideLessonAsAdministrator(LESSON));
            assertThrows(AccessDeniedException.class, () -> management.listCoursesForAdministration(null, null, 0, 20));
        }
        verifyNoInteractions(repository);
    }

    @Test void administratorListRejectsAnUnknownPublicationStatus() {
        var management = management(mock(org.springframework.transaction.PlatformTransactionManager.class));
        actor(IdentityRole.ADMINISTRATOR);
        assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                () -> management.listCoursesForAdministration(null, "draft", 0, 20));
        // PostgreSQL text cannot hold NUL: malformed input is 400, never a driver failure (500).
        assertEquals(400, assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                () -> management.listCoursesForAdministration("a\u0000b", null, 0, 20)).getStatus().value());
        for (String unpaired : List.of("\uD800", "a\uDC00"))
            assertEquals(400, assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                    () -> management.listCoursesForAdministration(unpaired, null, 0, 20)).getStatus().value());
        verifyNoInteractions(repository);
    }

    @Test void co12PayloadUsesOperationClockUnsignedUuidOrderAndFlushBeforeEvent() {
        var joined = mock(com.codemastery.modules.course.domain.repository.EnrollmentRepository.class);
        var events = mock(org.springframework.context.ApplicationEventPublisher.class);
        var persistence = mock(jakarta.persistence.EntityManager.class);
        java.time.Instant instant = java.time.Instant.parse("2026-11-25T10:00:00.123456789Z");
        var producer = new CourseManagementService(repository, service, identity,
                mock(com.codemastery.modules.course.domain.repository.CourseContentRepository.class), joined,
                mock(org.springframework.transaction.PlatformTransactionManager.class), events,
                java.time.Clock.fixed(instant, java.time.ZoneOffset.UTC));
        org.springframework.test.util.ReflectionTestUtils.setField(producer, "publicationPersistence", persistence);
        when(repository.currentTransactionFacts()).thenReturn(new com.codemastery.modules.course.domain.repository.CourseRepository.TransactionFacts("read committed", false, true, COURSE));
        when(repository.findById(COURSE)).thenReturn(Optional.of(catalogueCourse(COURSE, PUBLISHED)));
        UUID lower = UUID.fromString("00000000-0000-4000-8000-000000000001"), upper = UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff");
        when(joined.joinedLearnerIds(List.of(COURSE))).thenReturn(java.util.Map.of(COURSE,java.util.Set.of(upper,lower)));
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            producer.publishLessonPublished(COURSE, OTHER);
            var captured = org.mockito.ArgumentCaptor.forClass(com.codemastery.modules.course.application.event.ContentPublishedEvent.class);
            var order = org.mockito.Mockito.inOrder(persistence, joined, events);
            order.verify(persistence).flush(); order.verify(joined).joinedLearnerIds(List.of(COURSE)); order.verify(events).publishEvent(captured.capture());
            assertEquals(instant.truncatedTo(java.time.temporal.ChronoUnit.MICROS),captured.getValue().publishedAt());
            assertEquals(List.of(lower,upper),captured.getValue().recipientLearnerIds());
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false); }
        assertThrows(IllegalStateException.class,()->producer.publishLessonPublished(COURSE,OTHER));
    }
}
