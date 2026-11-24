package com.codemastery.modules.course;

import com.codemastery.common.exception.GlobalExceptionHandler;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.course.api.controller.EnrollmentController;
import com.codemastery.modules.course.api.dto.CourseProgressResponse;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.EnrollmentService;
import com.codemastery.modules.course.application.service.LessonProgressService;
import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.domain.repository.CourseRepository.StoredAdaptiveConfiguration;
import com.codemastery.modules.course.domain.repository.CourseRepository.TransactionFacts;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository.LessonPlacement;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository.LessonFlags;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository.ProgressSummary;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository.VisitPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** CT22.progress/resume use-case rules (ADR006 §§3, 4, 7.2; oracles B2-O01–O06, B2-O11). */
@ExtendWith(MockitoExtension.class)
class LessonProgressServiceTest {
    @Mock EnrollmentRepository repository;
    @Mock UserAccountService identities;
    @Mock CourseAccessService access;
    @Mock CourseRepository courses;
    LessonProgressService service;

    private final UUID actor = UUID.randomUUID(), course = UUID.randomUUID(), row = UUID.randomUUID();
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @BeforeEach void setup() { service = new LessonProgressService(repository, identities, access, courses); }

    private void learner() { when(identities.requireCurrentIdentity()).thenReturn(new CurrentIdentity(actor, IdentityRole.LEARNER)); }
    private Enrollment enrollment(EnrollmentStatus status, UUID resume) { return new Enrollment(row, actor, course, status, resume); }
    private void joined(EnrollmentStatus status, UUID resume) {
        when(repository.find(actor, course)).thenReturn(Optional.of(enrollment(status, resume)));
    }
    private void lessons(LessonFlags... flags) { when(repository.publishedLessonFlags(course, row)).thenReturn(List.of(flags)); }
    private static LessonFlags flags(UUID id, boolean completed, boolean skipped) { return new LessonFlags(id, completed, skipped); }

    // ---- B2-O01 / B2-O02: calculation over the published required set R

    @Test void publishedRequiredSetCountsCompletedAndSkippedOnceWithFlooredPercentageAndCanonicalNext() {
        learner(); joined(EnrollmentStatus.ACTIVE, null); when(repository.courseTitle(course)).thenReturn(Optional.of("Alpha"));
        // Hidden D is never returned by the repository: R = {A, B, C} in canonical order.
        lessons(flags(a, true, false), flags(b, true, true), flags(c, false, false));
        var result = service.progress(course);
        assertEquals(2, result.completedLessons()); assertEquals(3, result.totalLessons());
        assertEquals(66, result.progressPercentage()); assertFalse(result.isComplete()); assertEquals(c, result.nextLessonId());
        assertEquals("active", result.membershipStatus()); assertEquals("Alpha", result.title());
        assertEquals(List.of(a, b, c), result.lessons().stream().map(LessonProgressService.LessonProgressEntry::lessonId).toList());
        assertTrue(result.lessons().get(1).masterySkipped() && result.lessons().get(1).completed());
    }

    @Test void zeroPublishedLessonsIsZeroPercentNeverCompleteAndHasNoNextLesson() {
        learner(); joined(EnrollmentStatus.COMPLETED, null); when(repository.courseTitle(course)).thenReturn(Optional.of("Empty"));
        lessons();
        var result = service.progress(course);
        assertEquals(0, result.completedLessons()); assertEquals(0, result.totalLessons()); assertEquals(0, result.progressPercentage());
        assertFalse(result.isComplete()); assertNull(result.nextLessonId());
        // Stored membership and computed completion are exposed separately.
        assertEquals("completed", result.membershipStatus());
    }

    @Test void allCompletedIsOneHundredPercentCompleteWithNullNext() {
        learner(); joined(EnrollmentStatus.ACTIVE, null); when(repository.courseTitle(course)).thenReturn(Optional.of("Alpha"));
        lessons(flags(a, true, false), flags(b, true, true), flags(c, true, false));
        var result = service.progress(course);
        assertEquals(100, result.progressPercentage()); assertTrue(result.isComplete()); assertNull(result.nextLessonId());
        assertEquals("active", result.membershipStatus(), "a rejoined completed Course may differ until a qualifying mutation");
    }

    @Test void percentageIsFlooredIntegerArithmetic() {
        var many = new LessonFlags[7];
        for (int i = 0; i < many.length; i++) many[i] = flags(UUID.randomUUID(), i < 6, false);
        learner(); joined(EnrollmentStatus.ACTIVE, null); when(repository.courseTitle(course)).thenReturn(Optional.of("Alpha"));
        lessons(many);
        assertEquals(85, service.progress(course).progressPercentage()); // floor(600 / 7) = 85
    }

    @Test void skippedWithoutCompletedIsAnInvariantFailure() {
        learner(); joined(EnrollmentStatus.ACTIVE, null); lessons(flags(a, false, true));
        assertThrows(IllegalStateException.class, () -> service.progress(course));
    }

    @Test void progressReadIsReadOnlyAndNeverWrites() throws Exception {
        learner(); joined(EnrollmentStatus.ACTIVE, a); when(repository.courseTitle(course)).thenReturn(Optional.of("Alpha"));
        lessons(flags(a, true, false));
        service.progress(course);
        verify(repository, never()).updateStatus(any(), any()); verify(repository, never()).markCompleted(any(), any());
        verify(repository, never()).recordVisit(any(), any()); verify(repository, never()).lockCourse(any());
        assertTransaction("progress", true, Isolation.REPEATABLE_READ, UUID.class);
    }

    // ---- access and identity precedence

    @Test void authenticationFailurePrecedesEveryAccessOrPersistence() {
        when(identities.requireCurrentIdentity()).thenThrow(new BadCredentialsException("Authentication failed"));
        assertThrows(BadCredentialsException.class, () -> service.complete(course, a));
        assertThrows(BadCredentialsException.class, () -> service.visit(course, a));
        assertThrows(BadCredentialsException.class, () -> service.myResume());
        verifyNoInteractions(repository, access);
    }

    @ParameterizedTest @EnumSource(value = IdentityRole.class, names = {"INSTRUCTOR", "ADMINISTRATOR"})
    void applicationEnforcesSelfLearnerIndependentlyOfRest(IdentityRole role) {
        when(identities.requireCurrentIdentity()).thenReturn(new CurrentIdentity(actor, role));
        assertThrows(AccessDeniedException.class, () -> service.progress(course));
        assertThrows(AccessDeniedException.class, () -> service.complete(course, a));
        assertThrows(AccessDeniedException.class, () -> service.myCourseProgress(0, 20));
        assertThrows(AccessDeniedException.class, () -> service.resume(course));
        assertThrows(AccessDeniedException.class, () -> service.visit(course, a));
        assertThrows(AccessDeniedException.class, () -> service.myResume());
        verifyNoInteractions(repository, access);
    }

    @Test void deniedOrConcealedReadsNeverLookUpProgress() {
        learner();
        doThrow(new ResourceNotFoundException("Unavailable")).when(access).requireLearnerCourseAccess(course);
        assertThrows(ResourceNotFoundException.class, () -> service.progress(course));
        assertThrows(ResourceNotFoundException.class, () -> service.resume(course));
        verifyNoInteractions(repository);
    }

    @Test void nullIdentifiersAreCallerErrors() {
        assertThrows(IllegalArgumentException.class, () -> service.progress(null));
        assertThrows(IllegalArgumentException.class, () -> service.complete(course, null));
        assertThrows(IllegalArgumentException.class, () -> service.visit(null, a));
        verifyNoInteractions(repository, access, identities);
    }

    // ---- B2-O02 / B2-O03: manual completion and reconciliation

    @Test void completionLocksRootThenRechecksAccessThenLocksEnrollmentThenWrites() throws Exception {
        learner(); when(repository.lockCourse(course)).thenReturn(true);
        when(repository.lockEnrollment(actor, course)).thenReturn(Optional.of(enrollment(EnrollmentStatus.ACTIVE, null)));
        when(repository.courseTitle(course)).thenReturn(Optional.of("Alpha"));
        lessons(flags(a, true, false), flags(b, false, false));
        var result = service.complete(course, a);
        InOrder order = inOrder(repository, access);
        order.verify(repository).lockCourse(course);
        order.verify(access).requireLearnerLessonAccess(course, a);
        order.verify(repository).lockEnrollment(actor, course);
        order.verify(repository).markCompleted(row, a);
        order.verify(repository).publishedLessonFlags(course, row);
        verify(repository, never()).updateStatus(any(), any());
        assertEquals(50, result.progressPercentage()); assertEquals("active", result.membershipStatus()); assertEquals(b, result.nextLessonId());
        assertTransaction("complete", false, Isolation.READ_COMMITTED, UUID.class, UUID.class);
    }

    @Test void completingTheLastRequiredLessonStoresCompleted() {
        learner(); when(repository.lockCourse(course)).thenReturn(true);
        when(repository.lockEnrollment(actor, course)).thenReturn(Optional.of(enrollment(EnrollmentStatus.ACTIVE, null)));
        when(repository.courseTitle(course)).thenReturn(Optional.of("Alpha"));
        lessons(flags(a, true, true), flags(b, true, false));
        var result = service.complete(course, b);
        verify(repository).updateStatus(row, EnrollmentStatus.COMPLETED);
        assertEquals("completed", result.membershipStatus()); assertTrue(result.isComplete()); assertNull(result.nextLessonId());
    }

    @Test void completionOnAStaleCompletedEnrollmentWithAnIncompleteLessonReconcilesToActive() {
        learner(); when(repository.lockCourse(course)).thenReturn(true);
        when(repository.lockEnrollment(actor, course)).thenReturn(Optional.of(enrollment(EnrollmentStatus.COMPLETED, null)));
        when(repository.courseTitle(course)).thenReturn(Optional.of("Alpha"));
        lessons(flags(a, true, false), flags(b, false, false)); // a newly published Lesson b is incomplete
        assertEquals("active", service.complete(course, a).membershipStatus());
        verify(repository).updateStatus(row, EnrollmentStatus.ACTIVE);
    }

    @Test void repeatedCompletionIsIdempotentAndWritesNoStatusWhenAlreadyReconciled() {
        learner(); when(repository.lockCourse(course)).thenReturn(true);
        when(repository.lockEnrollment(actor, course)).thenReturn(Optional.of(enrollment(EnrollmentStatus.COMPLETED, null)));
        when(repository.courseTitle(course)).thenReturn(Optional.of("Alpha"));
        lessons(flags(a, true, true));
        for (int i = 0; i < 2; i++) assertEquals("completed", service.complete(course, a).membershipStatus());
        verify(repository, times(2)).markCompleted(row, a);
        verify(repository, never()).updateStatus(any(), any());
    }

    @Test void missingCourseIsConcealedBeforeAnyAccessOrWrite() {
        learner(); when(repository.lockCourse(course)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.complete(course, a));
        assertThrows(ResourceNotFoundException.class, () -> service.visit(course, a));
        verifyNoInteractions(access); verify(repository, never()).markCompleted(any(), any()); verify(repository, never()).recordVisit(any(), any());
    }

    @Test void deniedLessonAfterTheRootLockWritesNothing() {
        learner(); when(repository.lockCourse(course)).thenReturn(true);
        doThrow(new AccessDeniedException("Access denied")).when(access).requireLearnerLessonAccess(course, a);
        assertThrows(AccessDeniedException.class, () -> service.complete(course, a));
        assertThrows(AccessDeniedException.class, () -> service.visit(course, a));
        verify(repository, never()).lockEnrollment(any(), any()); verify(repository, never()).markCompleted(any(), any());
        verify(repository, never()).recordVisit(any(), any());
    }

    @Test void admittedLearnerWithoutAJoinedRowIsAnInvariantFailureWithoutWrites() {
        learner(); when(repository.lockCourse(course)).thenReturn(true);
        when(repository.lockEnrollment(actor, course)).thenReturn(Optional.of(enrollment(EnrollmentStatus.LEFT, null)));
        assertThrows(IllegalStateException.class, () -> service.complete(course, a));
        verify(repository, never()).markCompleted(any(), any());
    }

    @Test void writeFailurePropagatesSoTheWholeCourseUnitRollsBack() {
        learner(); when(repository.lockCourse(course)).thenReturn(true);
        when(repository.lockEnrollment(actor, course)).thenReturn(Optional.of(enrollment(EnrollmentStatus.ACTIVE, null)));
        doThrow(new IllegalStateException("Progress write lost its row")).when(repository).markCompleted(row, a);
        assertThrows(IllegalStateException.class, () -> service.complete(course, a));
        verify(repository, never()).updateStatus(any(), any());
    }

    // ---- B2-O05: per-Course resume

    @Test void firstResumeIsNullWithCanonicalNext() {
        learner(); joined(EnrollmentStatus.ACTIVE, null); lessons(flags(a, true, false), flags(b, false, false));
        var result = service.resume(course);
        assertNull(result.resumeLessonId()); assertEquals(b, result.nextLessonId());
        verify(repository, never()).isPublishedCourseLesson(any(), any());
    }

    @Test void hiddenOrForeignStoredPointerReadsAsNullWithoutClearingHistory() throws Exception {
        learner(); joined(EnrollmentStatus.COMPLETED, a); lessons(flags(b, false, false));
        when(repository.isPublishedCourseLesson(course, a)).thenReturn(false);
        var result = service.resume(course);
        assertNull(result.resumeLessonId()); assertEquals(b, result.nextLessonId());
        verify(repository, never()).recordVisit(any(), any()); verify(repository, never()).lockEnrollment(any(), any());
        assertTransaction("resume", true, Isolation.REPEATABLE_READ, UUID.class);
    }

    @Test void validStoredPointerIsReturned() {
        learner(); joined(EnrollmentStatus.ACTIVE, a); lessons(flags(a, false, false));
        when(repository.isPublishedCourseLesson(course, a)).thenReturn(true);
        assertEquals(a, service.resume(course).resumeLessonId());
    }

    @Test void visitLocksRootRechecksLessonAccessLocksEnrollmentAndRecordsServerTimeAtomically() throws Exception {
        learner(); when(repository.lockCourse(course)).thenReturn(true);
        when(repository.lockEnrollment(actor, course)).thenReturn(Optional.of(enrollment(EnrollmentStatus.ACTIVE, b)));
        when(repository.isPublishedCourseLesson(course, a)).thenReturn(true);
        lessons(flags(a, false, false), flags(b, false, false));
        var result = service.visit(course, a);
        InOrder order = inOrder(repository, access);
        order.verify(repository).lockCourse(course);
        order.verify(access).requireLearnerLessonAccess(course, a);
        order.verify(repository).lockEnrollment(actor, course);
        order.verify(repository).recordVisit(row, a);
        assertEquals(a, result.resumeLessonId()); assertEquals(a, result.nextLessonId());
        verify(repository, never()).markCompleted(any(), any()); verify(repository, never()).updateStatus(any(), any());
        assertTransaction("visit", false, Isolation.READ_COMMITTED, UUID.class, UUID.class);
    }

    // ---- B2-O06 / B2-O11: account-level reads

    @Test void myCourseProgressMapsActiveSummariesWithTheSameFormula() throws Exception {
        learner();
        when(repository.activeProgress(actor, PageRequest.of(1, 2))).thenReturn(new PageImpl<>(List.of(
                new ProgressSummary(course, "Alpha", 3, 2, c), new ProgressSummary(b, "Empty", 0, 0, null),
                new ProgressSummary(a, "Done", 4, 4, null)), PageRequest.of(1, 2), 7));
        var page = service.myCourseProgress(1, 2);
        assertEquals(7, page.totalElements()); assertEquals(4, page.totalPages()); assertEquals(1, page.page()); assertEquals(2, page.size());
        assertEquals(66, page.content().get(0).progressPercentage()); assertEquals(c, page.content().get(0).nextLessonId());
        assertFalse(page.content().get(1).isComplete()); assertEquals(0, page.content().get(1).progressPercentage());
        assertTrue(page.content().get(2).isComplete());
        assertTrue(page.content().stream().allMatch(row -> row.membershipStatus().equals("active")));
        assertTransaction("myCourseProgress", true, Isolation.REPEATABLE_READ, int.class, int.class);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 101})
    void invalidPagingIsRejectedBeforeIdentity(int value) {
        int page = value < 0 ? value : 0, size = value < 0 ? 20 : value;
        assertThrows(IllegalArgumentException.class, () -> service.myCourseProgress(page, size));
        verifyNoInteractions(repository, identities);
    }

    @Test void myResumeReturnsTheSelectedPositionOrExplicitNull() throws Exception {
        learner();
        var at = Instant.parse("2026-11-16T03:04:05.123456Z");
        when(repository.latestVisit(actor)).thenReturn(Optional.of(new VisitPosition(course, a, at)), Optional.empty());
        var position = service.myResume().position();
        assertEquals(course, position.courseId()); assertEquals(a, position.lessonId()); assertEquals(at, position.lastVisitedAt());
        assertNull(service.myResume().position());
        assertTransaction("myResume", true, Isolation.REPEATABLE_READ);
    }

    // ---- REST precedence: path 400 before admission; admission before body 400; no write on invalid body

    private MockMvc mvc(LessonProgressService application, CourseAccessService admission) {
        return standaloneSetup(new EnrollmentController(mock(EnrollmentService.class), application, admission))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"completed\":false}", "{\"completed\":\"true\"}", "{\"completed\":1}",
            "{\"completed\":true,\"masterySkipped\":true}", "{\"completed\":true,\"completed\":true}",
            "{\"completed\":true,\"learnerId\":\"00000000-0000-4000-8000-000000000001\"}", "[true]", "true", "{\"completed\":true"})
    void invalidCompletionBodiesAre400AfterAdmissionAndNeverWrite(String body) throws Exception {
        var application = mock(LessonProgressService.class); var admission = mock(CourseAccessService.class);
        mvc(application, admission).perform(put("/api/courses/" + course + "/lessons/" + a + "/completion")
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verify(admission).requireLearnerLessonAccess(course, a);
        verifyNoInteractions(application);
    }

    @Test void invalidCompletionBodyForAConcealedLessonIs404NotBodyValidation() throws Exception {
        var application = mock(LessonProgressService.class); var admission = mock(CourseAccessService.class);
        doThrow(new ResourceNotFoundException("Unavailable")).when(admission).requireLearnerLessonAccess(course, a);
        mvc(application, admission).perform(put("/api/courses/" + course + "/lessons/" + a + "/completion")
                .contentType("application/json").content("{\"completed\":false}")).andExpect(status().isNotFound());
        verifyNoInteractions(application);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"lessonId\":null}", "{\"lessonId\":5}", "{\"lessonId\":\"not-a-uuid\"}",
            "{\"lessonId\":\"0-0-4000-8000-2\"}", "{\"lessonId\":\"00000000-0000-4000-8000-000000000002\",\"visitedAt\":\"2026-01-01T00:00:00Z\"}",
            "{\"lessonId\":\"00000000-0000-4000-8000-000000000002\",\"lessonId\":\"00000000-0000-4000-8000-000000000002\"}"})
    void invalidVisitBodiesAre400AfterAdmissionAndNeverWrite(String body) throws Exception {
        var application = mock(LessonProgressService.class); var admission = mock(CourseAccessService.class);
        mvc(application, admission).perform(put("/api/courses/" + course + "/resume").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        verify(admission).requireLearnerCourseAccess(course);
        verifyNoInteractions(application);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/courses/0-0-4000-8000-2/lessons/%s/completion", "/api/courses/%s/lessons/not-a-uuid/completion"})
    void malformedPathIdsAre400BeforeAdmissionOrApplication(String template) throws Exception {
        var application = mock(LessonProgressService.class); var admission = mock(CourseAccessService.class);
        mvc(application, admission).perform(put(template.formatted(course)).contentType("application/json").content("{\"completed\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(application, admission);
    }

    @Test void validBodiesReachTheApplicationOnce() throws Exception {
        var application = mock(LessonProgressService.class); var admission = mock(CourseAccessService.class);
        when(application.complete(course, a)).thenReturn(new LessonProgressService.CourseProgress(course, "Alpha", "active", 1, 1, 100, true, null, List.of()));
        when(application.visit(course, a)).thenReturn(new LessonProgressService.CourseResume(course, a, null));
        var mvc = mvc(application, admission);
        mvc.perform(put("/api/courses/" + course + "/lessons/" + a + "/completion").contentType("application/json").content(" { \"completed\" : true } "))
                .andExpect(status().isOk());
        mvc.perform(put("/api/courses/" + course + "/resume").contentType("application/json")
                .content("{\"lessonId\":\"" + a.toString().toUpperCase(java.util.Locale.ROOT) + "\"}")).andExpect(status().isOk());
        verify(application).complete(course, a); verify(application).visit(course, a); verifyNoInteractions(admission);
    }

    @Test void strictBodyParsersAcceptOnlyTheApprovedShapes() {
        assertTrue(CourseProgressResponse.isCompletionWrite("{\"completed\":true}"));
        assertFalse(CourseProgressResponse.isCompletionWrite(null));
        assertEquals(Optional.of(a), CourseProgressResponse.visitLessonId("{\"lessonId\":\"" + a + "\"}"));
        assertEquals(Optional.empty(), CourseProgressResponse.visitLessonId("{\"lessonId\":\"{" + a + "}\"}"));
        // Trailing content after the single JSON object is rejected (Jackson 3 FAIL_ON_TRAILING_TOKENS default).
        for (String trailing : List.of(" garbage", "{}", "{\"completed\":true}", "[]", "1", ",", "}", " null"))
            assertFalse(CourseProgressResponse.isCompletionWrite("{\"completed\":true}" + trailing), trailing);
        for (String trailing : List.of(" garbage", "{}", "{\"lessonId\":\"" + a + "\"}", "]", " 0"))
            assertEquals(Optional.empty(), CourseProgressResponse.visitLessonId("{\"lessonId\":\"" + a + "\"}" + trailing), trailing);
        assertTrue(CourseProgressResponse.isCompletionWrite(" {\"completed\":true} \n"));
    }

    // ---- CT22.atomic (ADR011 §3): actorless participant under the caller's held Course root

    private final UUID chapter = UUID.randomUUID();
    private final Map<String, Object> settings = Map.of("masteryThreshold", 0.8);

    @AfterEach void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
    }

    private void readSnapshot() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Isolation.REPEATABLE_READ.value());
    }

    @ParameterizedTest @EnumSource(value = EnrollmentStatus.class, names = {"ACTIVE", "COMPLETED"})
    void learnerStateAdmitsBeforeReadingSelfStateAndReusesTheExecutionProjection(EnrollmentStatus status) throws Exception {
        readSnapshot(); learner(); joined(status, a); adaptive();
        when(repository.publishedLessonPlacements(course, row)).thenReturn(
                List.of(placed(a, 1, true, false), placed(b, 3, true, true), placed(c, 7, false, false)));
        var state = service.readLearnerState(course);
        assertEquals(course, state.courseId()); assertEquals(actor, state.learnerId()); assertEquals(row, state.enrollmentId());
        assertEquals(status.getStatus(), state.membershipStatus());
        assertTrue(state.adaptiveEnabled()); assertEquals(settings, state.adaptiveSettings());
        assertEquals(List.of(new LessonProgressService.LessonState(a, chapter, 1, 1, true, false),
                new LessonProgressService.LessonState(b, chapter, 1, 3, true, true),
                new LessonProgressService.LessonState(c, chapter, 1, 7, false, false)), state.lessons());
        InOrder order = inOrder(access, identities, repository, courses);
        order.verify(access).requireLearnerCourseAccess(course);
        order.verify(identities).requireCurrentIdentity();
        order.verify(repository).find(actor, course);
        order.verify(repository).publishedLessonPlacements(course, row);
        order.verify(courses).findAdaptiveConfiguration(course);
        verifyNoMoreInteractions(access, identities, repository, courses); // no root, locks or writes
        assertTransaction("readLearnerState", true, Isolation.REPEATABLE_READ, UUID.class);
        assertEquals(Propagation.MANDATORY, LessonProgressService.class.getMethod("readLearnerState", UUID.class)
                .getAnnotation(Transactional.class).propagation());
    }

    @Test void learnerStateRejectsAnIncompatibleOrAbsentCallerTransactionBeforeAdmission() {
        assertThrows(IllegalStateException.class, () -> service.readLearnerState(course));
        readSnapshot(); TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        assertThrows(IllegalStateException.class, () -> service.readLearnerState(course));
        readSnapshot(); TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Isolation.READ_COMMITTED.value());
        assertThrows(IllegalStateException.class, () -> service.readLearnerState(course));
        verifyNoInteractions(access, identities, repository, courses);
    }

    @Test void learnerStatePropagatesEveryAdmissionFailureWithoutReadingBusinessData() {
        readSnapshot();
        for (var failure : List.of(new BadCredentialsException("Authentication failed"), new AccessDeniedException("Denied"),
                new ResourceNotFoundException("Unavailable"), new IllegalStateException("Provider failed"))) {
            doThrow(failure).when(access).requireLearnerCourseAccess(course);
            assertSame(failure, assertThrows(failure.getClass(), () -> service.readLearnerState(course)));
        }
        verifyNoInteractions(identities, repository, courses);
    }

    @Test void learnerStatePropagatesRepositoryFailureAndRejectsImpossibleAdmittedMembership() {
        readSnapshot(); learner();
        when(repository.find(actor, course)).thenReturn(Optional.empty(), Optional.of(enrollment(EnrollmentStatus.LEFT, null)));
        assertThrows(IllegalStateException.class, () -> service.readLearnerState(course));
        assertThrows(IllegalStateException.class, () -> service.readLearnerState(course));
        var failure = new IllegalStateException("Database failed");
        when(repository.find(actor, course)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.readLearnerState(course)));
        verify(repository, never()).publishedLessonPlacements(any(), any()); verifyNoInteractions(courses);
    }

    @Test void learnerStateKeepsNullSettingsAndNeverReconcilesAStoredMembershipOnRead() {
        readSnapshot(); learner(); joined(EnrollmentStatus.COMPLETED, a);
        when(courses.findAdaptiveConfiguration(course)).thenReturn(Optional.of(new StoredAdaptiveConfiguration(course, false, null)));
        when(repository.publishedLessonPlacements(course, row)).thenReturn(List.of(placed(a, 1, false, false)));
        var state = service.readLearnerState(course);
        assertNull(state.adaptiveSettings()); assertFalse(state.adaptiveEnabled());
        assertEquals("completed", state.membershipStatus()); assertFalse(state.lessons().getFirst().completed());
        verify(repository, never()).updateStatus(any(), any());
        verify(courses, never()).currentTransactionFacts();
    }

    @Test @SuppressWarnings("unchecked")
    void learnerCourseStateDefensivelyCopiesNestedJsonAndLessonsAtEveryConstructionBoundary() {
        var nested = new LinkedHashMap<String, Object>();
        nested.put("threshold", new java.math.BigDecimal("0.80")); nested.put("nullable", null);
        var array = new ArrayList<Object>(); array.add(nested); array.add(null);
        var source = new LinkedHashMap<String, Object>(); source.put("array", array);
        var lessons = new ArrayList<LessonProgressService.LessonState>();
        lessons.add(new LessonProgressService.LessonState(a, chapter, 1, 1, false, false));
        var state = new LessonProgressService.LearnerCourseState(course, actor, row, "active", false, source, lessons);
        nested.put("threshold", 1); array.clear(); source.clear(); lessons.clear();
        var returnedArray = (List<Object>) state.adaptiveSettings().get("array");
        var returnedObject = (Map<String, Object>) returnedArray.getFirst();
        assertEquals(new java.math.BigDecimal("0.80"), returnedObject.get("threshold"));
        assertTrue(returnedObject.containsKey("nullable")); assertNull(returnedObject.get("nullable"));
        assertNull(returnedArray.get(1)); assertEquals(1, state.lessons().size());
        assertThrows(UnsupportedOperationException.class, () -> state.adaptiveSettings().clear());
        assertThrows(UnsupportedOperationException.class, () -> returnedArray.add("changed"));
        assertThrows(UnsupportedOperationException.class, () -> returnedObject.put("threshold", 2));
        assertThrows(UnsupportedOperationException.class, () -> state.lessons().clear());
    }

    /** CT33 and ADR013 §19.4: only the approved Adaptive read compositions may consume the admitted state. */
    @Test void learnerStateHasOnlyTheApprovedAdaptiveReadCallers() throws Exception {
        Path root = Path.of("src", "main", "java", "com", "codemastery");
        var allowed = Set.of(root.resolve("modules/course/application/service/LessonProgressService.java"),
                root.resolve("modules/adaptive/application/mastery/MasteryQueryService.java"),
                root.resolve("modules/adaptive/application/recommendation/RecommendationService.java"),
                root.resolve("modules/adaptive/application/mastery/PlacementDiagnosticService.java"));
        try (var files = Files.walk(root)) {
            var callers = files.filter(file -> file.toString().endsWith(".java")).filter(file -> {
                try { return Files.readString(file).contains("readLearnerState("); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }).toList();
            assertFalse(callers.isEmpty());
            assertTrue(allowed.containsAll(callers), () -> "Unapproved CT33 learner state callers: " + callers);
        }
    }
    private void heldRoot(String isolation, boolean readOnly, UUID root) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(courses.currentTransactionFacts()).thenReturn(new TransactionFacts(isolation, readOnly, true, root));
    }
    private void heldRoot() { heldRoot("read committed", false, course); }
    private void adaptive() {
        when(courses.findAdaptiveConfiguration(course)).thenReturn(Optional.of(new StoredAdaptiveConfiguration(course, true, settings)));
    }
    private LessonPlacement placed(UUID id, int position, boolean completed, boolean skipped) {
        return new LessonPlacement(id, chapter, 1, position, completed, skipped);
    }

    @Test void participantRefusesWithoutAnActiveTransactionBeforeAnyRead() {
        assertThrows(IllegalStateException.class, () -> service.readExecutionState(course, actor));
        assertThrows(IllegalStateException.class, () -> service.applyMasterySkips(course, actor, Set.of(a)));
        verifyNoInteractions(repository, courses, identities, access);
    }

    @Test void participantRefusesAReadOnlyOuterTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThrows(IllegalStateException.class, () -> service.readExecutionState(course, actor));
        verifyNoInteractions(repository, courses);
    }

    @Test void participantRefusesWrongIsolationReadOnlyOrAnotherCoursesRoot() {
        for (var facts : List.of(new Object[]{"repeatable read", false, course}, new Object[]{"read committed", true, course},
                new Object[]{"read committed", false, UUID.randomUUID()}, new Object[]{"read committed", false, null})) {
            heldRoot((String) facts[0], (Boolean) facts[1], (UUID) facts[2]);
            assertThrows(IllegalStateException.class, () -> service.applyMasterySkips(course, actor, Set.of(a)));
        }
        verifyNoInteractions(repository, identities, access);
        verify(courses, never()).findAdaptiveConfiguration(any());
    }

    @Test void executionStateReportsNoneMembershipWithFalseFlagsSettingsAndCanonicalPlacement() {
        heldRoot(); adaptive();
        when(repository.find(actor, course)).thenReturn(Optional.empty());
        when(repository.publishedLessonPlacements(course, null)).thenReturn(List.of(placed(a, 1, false, false), placed(b, 3, false, false)));
        var state = service.readExecutionState(course, actor);
        assertEquals("none", state.membershipStatus()); assertNull(state.enrollmentId());
        assertTrue(state.adaptiveEnabled()); assertEquals(settings, state.adaptiveSettings());
        assertEquals(List.of(new LessonProgressService.LessonState(a, chapter, 1, 1, false, false),
                new LessonProgressService.LessonState(b, chapter, 1, 3, false, false)), state.lessons());
        verify(repository, never()).lockEnrollment(any(), any());
        verifyNoInteractions(identities, access);
    }

    @Test void executionStateReportsLeftMembershipAndNullSettingsOfAnUnconfiguredCourse() {
        heldRoot();
        when(courses.findAdaptiveConfiguration(course)).thenReturn(Optional.of(new StoredAdaptiveConfiguration(course, false, null)));
        when(repository.find(actor, course)).thenReturn(Optional.of(enrollment(EnrollmentStatus.LEFT, null)));
        when(repository.publishedLessonPlacements(course, row)).thenReturn(List.of());
        var state = service.readExecutionState(course, actor);
        assertEquals("left", state.membershipStatus()); assertEquals(row, state.enrollmentId());
        assertFalse(state.adaptiveEnabled()); assertNull(state.adaptiveSettings());
    }

    @Test void enabledCourseWithoutStoredSettingsIsAnIntegrityFailure() {
        heldRoot();
        when(courses.findAdaptiveConfiguration(course)).thenReturn(Optional.of(new StoredAdaptiveConfiguration(course, true, null)));
        when(repository.find(actor, course)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> service.readExecutionState(course, actor));
    }

    @Test void masterySkipsLockEnrollmentThenProgressSkipOnlyIncompleteLessonsAndRecalculateMembership() {
        heldRoot(); adaptive();
        when(repository.lockEnrollment(actor, course)).thenReturn(Optional.of(enrollment(EnrollmentStatus.ACTIVE, null)));
        when(repository.publishedLessonPlacements(course, row)).thenReturn(
                List.of(placed(a, 1, true, false), placed(b, 2, false, false), placed(c, 3, false, false)),
                List.of(placed(a, 1, true, false), placed(b, 2, true, true), placed(c, 3, true, true)));
        var state = service.applyMasterySkips(course, actor, Set.of(a, b, c));
        InOrder order = inOrder(courses, repository);
        order.verify(courses).currentTransactionFacts();
        order.verify(repository).lockEnrollment(actor, course);
        order.verify(repository).lockProgress(row);
        order.verify(repository).markMasterySkipped(row, b);
        order.verify(repository).markMasterySkipped(row, c);
        order.verify(repository).updateStatus(row, EnrollmentStatus.COMPLETED);
        // A manual completion is never relabelled.
        verify(repository, never()).markMasterySkipped(row, a);
        verify(repository, never()).markCompleted(any(), any());
        assertEquals("completed", state.membershipStatus());
        assertEquals(List.of(false, true, true), state.lessons().stream().map(LessonProgressService.LessonState::masterySkipped).toList());
        verifyNoInteractions(identities, access);
    }

    @Test void completedMembershipStaysCompletedWithoutARedundantStatusWrite() {
        heldRoot(); adaptive();
        when(repository.lockEnrollment(actor, course)).thenReturn(Optional.of(enrollment(EnrollmentStatus.COMPLETED, null)));
        when(repository.publishedLessonPlacements(course, row)).thenReturn(List.of(placed(a, 1, true, false)));
        assertEquals("completed", service.applyMasterySkips(course, actor, Set.of(a)).membershipStatus());
        verify(repository, never()).markMasterySkipped(any(), any());
        verify(repository, never()).updateStatus(any(), any());
    }

    @Test void emptySetIsANoOp() {
        heldRoot(); adaptive();
        when(repository.lockEnrollment(actor, course)).thenReturn(Optional.of(enrollment(EnrollmentStatus.ACTIVE, null)));
        when(repository.publishedLessonPlacements(course, row)).thenReturn(List.of(placed(a, 1, false, false)));
        assertEquals("active", service.applyMasterySkips(course, actor, Set.of()).membershipStatus());
        verify(repository, never()).lockProgress(any());
        verify(repository, never()).markMasterySkipped(any(), any());
        verify(repository, never()).updateStatus(any(), any());
    }

    @Test void absentOrLeftMembershipIsAnInternalErrorWithoutWrites() {
        heldRoot();
        when(repository.lockEnrollment(actor, course)).thenReturn(Optional.empty(), Optional.of(enrollment(EnrollmentStatus.LEFT, null)));
        assertThrows(IllegalStateException.class, () -> service.applyMasterySkips(course, actor, Set.of(a)));
        assertThrows(IllegalStateException.class, () -> service.applyMasterySkips(course, actor, Set.of(a)));
        verify(repository, never()).markMasterySkipped(any(), any());
        verify(repository, never()).updateStatus(any(), any());
    }

    @Test void hiddenOrForeignLessonIsRejectedBeforeAnyWrite() {
        heldRoot();
        when(repository.lockEnrollment(actor, course)).thenReturn(Optional.of(enrollment(EnrollmentStatus.ACTIVE, null)));
        when(repository.publishedLessonPlacements(course, row)).thenReturn(List.of(placed(a, 1, false, false)));
        assertThrows(IllegalArgumentException.class, () -> service.applyMasterySkips(course, actor, Set.of(a, b)));
        verify(repository, never()).lockProgress(any());
        verify(repository, never()).markMasterySkipped(any(), any());
    }

    @Test void nullArgumentsAreInternalErrors() {
        var withNull = new java.util.HashSet<UUID>(); withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> service.readExecutionState(null, actor));
        assertThrows(IllegalArgumentException.class, () -> service.readExecutionState(course, null));
        assertThrows(IllegalArgumentException.class, () -> service.applyMasterySkips(course, actor, null));
        assertThrows(IllegalArgumentException.class, () -> service.applyMasterySkips(course, actor, withNull));
        verifyNoInteractions(repository, courses);
    }

    /** ADR011 §3 allowlist: only Adaptive's execution orchestration may call the participant; no REST or scheduler. */
    @Test void participantMethodsHaveOnlyTheApprovedCallers() throws Exception {
        Path root = Path.of("src", "main", "java", "com", "codemastery");
        var allowed = Set.of(root.resolve("modules/course/application/service/LessonProgressService.java"),
                root.resolve("modules/adaptive/application/mastery/MasteryUpdateService.java"),
                root.resolve("modules/adaptive/application/decision/AdaptiveDecisionService.java"));
        try (var files = Files.walk(root)) {
            var callers = files.filter(file -> file.toString().endsWith(".java")).filter(file -> {
                try {
                    String source = Files.readString(file);
                    return source.contains("readExecutionState(") || source.contains("applyMasterySkips(");
                } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }).toList();
            assertFalse(callers.isEmpty());
            assertTrue(allowed.containsAll(callers), () -> "Unapproved CT22.atomic callers: " + callers);
        }
    }

    @Test void participantMethodsDeclareNoOwnTransaction() throws Exception {
        assertNull(LessonProgressService.class.getMethod("readExecutionState", UUID.class, UUID.class).getAnnotation(Transactional.class));
        assertNull(LessonProgressService.class.getMethod("applyMasterySkips", UUID.class, UUID.class, Set.class).getAnnotation(Transactional.class));
    }

    private static void assertTransaction(String method, boolean readOnly, Isolation isolation, Class<?>... parameters) throws Exception {
        Method target = LessonProgressService.class.getMethod(method, parameters);
        Transactional transactional = target.getAnnotation(Transactional.class);
        assertNotNull(transactional, method + " must be transactional");
        assertEquals(readOnly, transactional.readOnly(), method + " readOnly");
        assertEquals(isolation, transactional.isolation(), method + " isolation");
    }
}
