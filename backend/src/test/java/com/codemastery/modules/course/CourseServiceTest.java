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

}
