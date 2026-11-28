package com.codemastery.modules.admin.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.admin.application.service.AdminCourseService.AdminCourseOutcome;
import com.codemastery.modules.admin.domain.model.AuditAction;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.auth.application.service.UserAccountService.UserSummary;
import com.codemastery.modules.course.application.service.CourseManagementService;
import com.codemastery.modules.course.application.service.CourseManagementService.AdminCourseView;
import com.codemastery.modules.course.application.service.CourseManagementService.CourseChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/** IN20 unit tests of {@link AdminCourseService} (CT12.course, CT31); the owning modules are doubles. */
@ExtendWith(MockitoExtension.class)
class AdminCourseServiceTest {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000ad");
    private static final UUID COURSE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID INSTRUCTOR = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Mock
    private CourseManagementService courses;
    @Mock
    private UserAccountService identities;
    @Mock
    private AuditLogService audit;

    private AdminCourseService service;

    @BeforeEach
    void setUp() {
        service = new AdminCourseService(courses, identities, audit);
    }

    private void signedInAs(IdentityRole role) {
        when(identities.requireCurrentIdentity()).thenReturn(new CurrentIdentity(ADMIN, role));
    }

    private static AdminCourseView view(String status) {
        return new AdminCourseView(COURSE, "Python", status, INSTRUCTOR);
    }

    @Test
    void admissionChecksRoleBeforeCourseExistenceAndNeverMutates() {
        signedInAs(IdentityRole.ADMINISTRATOR);
        service.requireCourseForHiding(COURSE);
        verify(courses).requireCourseForAdministration(COURSE);
        verifyNoInteractions(audit);
        verify(courses, never()).hideCourseAsAdministrator(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = IdentityRole.class, names = {"LEARNER", "INSTRUCTOR"})
    void admissionRefusesOtherRolesBeforeTheCourseRead(IdentityRole role) {
        signedInAs(role);
        assertThrows(AccessDeniedException.class, () -> service.requireCourseForHiding(COURSE));
        verifyNoInteractions(courses, audit);
    }

    @Test
    void invalidDatabaseTextIs400BeforeRoleLookupAndValidUnicodeIsPreserved() {
        for (String query : List.of("nul" + (char) 0, "bad" + (char) 0xd800, "bad" + (char) 0xdc00)) {
            assertThrows(BusinessRuleException.class, () -> service.list(query, null, 0, 20));
        }
        verifyNoInteractions(identities, courses);
    }

    @Test
    void namesAreBoundedToTheHundredDistinctPageInstructorIdsIncludingFormerInstructors() {
        signedInAs(IdentityRole.ADMINISTRATOR);
        var rows = java.util.stream.IntStream.range(0, 100).mapToObj(i ->
                new AdminCourseView(UUID.randomUUID(), "Row " + i, "hidden", UUID.randomUUID())).toList();
        Set<UUID> ids = rows.stream().map(AdminCourseView::instructorId).collect(java.util.stream.Collectors.toSet());
        when(courses.listCoursesForAdministration(null, null, 0, 100)).thenReturn(new PageResponse<>(rows, 0, 100, 100));
        when(identities.userSummaries(ids)).thenReturn(Map.of(rows.getFirst().instructorId(),
                new UserSummary(rows.getFirst().instructorId(), "Former instructor", "learner")));
        var result = service.list(null, null, 0, 100);
        assertEquals("Former instructor", result.content().getFirst().instructorDisplayName());
        assertNull(result.content().get(1).instructorDisplayName());
        verify(identities).userSummaries(ids);
        verify(identities, never()).instructorDisplayNames(any());
    }

    // ------------------------------------------------------------------------------- hide

    @Test
    void nonAdministratorsAreRefusedBeforeAnyCourseOrAuditCall() {
        signedInAs(IdentityRole.INSTRUCTOR);

        assertThrows(AccessDeniedException.class, () -> service.hideCourse(COURSE));
        verifyNoInteractions(courses, audit);
    }

    @Test
    void anActualChangeHidesThenAuditsOnceAndNamesTheInstructor() {
        signedInAs(IdentityRole.ADMINISTRATOR);
        when(courses.hideCourseAsAdministrator(COURSE)).thenReturn(new CourseChange(view("hidden"), true));
        when(identities.userSummaries(Set.of(INSTRUCTOR)))
                .thenReturn(Map.of(INSTRUCTOR, new UserSummary(INSTRUCTOR, "Ada Lovelace", "instructor")));

        AdminCourseOutcome outcome = service.hideCourse(COURSE);

        assertTrue(outcome.changed());
        assertEquals("hidden", outcome.course().publicationStatus());
        assertEquals("Ada Lovelace", outcome.course().instructorDisplayName());
        InOrder order = inOrder(courses, audit);
        order.verify(courses).hideCourseAsAdministrator(COURSE);
        order.verify(audit).record(AuditAction.COURSE_HIDDEN, COURSE);
        order.verifyNoMoreInteractions();
    }

    @Test
    void anAlreadyHiddenCourseIsANoOpWithoutAnAuditRow() {
        signedInAs(IdentityRole.ADMINISTRATOR);
        when(courses.hideCourseAsAdministrator(COURSE)).thenReturn(new CourseChange(view("hidden"), false));
        when(identities.userSummaries(Set.of(INSTRUCTOR))).thenReturn(Map.of());

        AdminCourseOutcome outcome = service.hideCourse(COURSE);

        assertFalse(outcome.changed());
        assertNull(outcome.course().instructorDisplayName(), "a missing account has a null name");
        verify(audit, never()).record(any(), any());
    }

    @Test
    void aMissingCourseIsNotAuditedAndItsErrorPropagates() {
        signedInAs(IdentityRole.ADMINISTRATOR);
        when(courses.hideCourseAsAdministrator(COURSE)).thenThrow(new ResourceNotFoundException("missing"));

        assertThrows(ResourceNotFoundException.class, () -> service.hideCourse(COURSE));
        verifyNoInteractions(audit);
    }

    @Test
    void anAuditFailurePropagatesSoTheCallersTransactionRollsBack() {
        signedInAs(IdentityRole.ADMINISTRATOR);
        when(courses.hideCourseAsAdministrator(COURSE)).thenReturn(new CourseChange(view("hidden"), true));
        doThrow(new IllegalStateException("audit insert failed")).when(audit).record(AuditAction.COURSE_HIDDEN, COURSE);

        assertThrows(IllegalStateException.class, () -> service.hideCourse(COURSE));
        verify(identities, never()).userSummaries(any());
    }

    // ------------------------------------------------------------------------------- list

    @Test
    void listUsesTheApprovedPagingDefaultsAndNamesEachInstructorOnceForThePage() {
        signedInAs(IdentityRole.ADMINISTRATOR);
        UUID second = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        when(courses.listCoursesForAdministration("py", "published", 0, 20)).thenReturn(new PageResponse<>(List.of(
                view("published"), new AdminCourseView(second, "Python 2", "published", INSTRUCTOR)), 0, 20, 2));
        when(identities.userSummaries(Set.of(INSTRUCTOR)))
                .thenReturn(Map.of(INSTRUCTOR, new UserSummary(INSTRUCTOR, "Ada Lovelace", "instructor")));

        PageResponse<AdminCourseService.AdminCourseItem> page = service.list("py", "published", null, null);

        assertEquals(2, page.content().size());
        assertEquals(2L, page.totalElements());
        assertTrue(page.content().stream().allMatch(row -> "Ada Lovelace".equals(row.instructorDisplayName())));
        verify(identities).userSummaries(Set.of(INSTRUCTOR));
    }

    @Test
    void anEmptyPageDoesNotLookUpAnyUser() {
        signedInAs(IdentityRole.ADMINISTRATOR);
        when(courses.listCoursesForAdministration(null, null, 3, 5)).thenReturn(new PageResponse<>(List.of(), 3, 5, 0));

        assertEquals(0, service.list(null, null, 3, 5).content().size());
        verify(identities, never()).userSummaries(any());
    }

    @Test
    void malformedValuesAreRejectedBeforeTheRoleDecision() {
        // ADR012 section 12: 400 for invalid query values (stage 2) precedes the role 403 (stage 3).
        assertThrows(BusinessRuleException.class, () -> service.list(null, null, -1, 20));
        assertThrows(BusinessRuleException.class, () -> service.list(null, null, 0, 0));
        assertThrows(BusinessRuleException.class, () -> service.list(null, null, 0, 101));
        assertThrows(BusinessRuleException.class, () -> service.list(null, "draft", 0, 20));
        verifyNoInteractions(identities, courses);
    }

    @Test
    void nonAdministratorsCannotList() {
        signedInAs(IdentityRole.LEARNER);

        assertThrows(AccessDeniedException.class, () -> service.list(null, null, 0, 20));
        verifyNoInteractions(courses);
    }
}
