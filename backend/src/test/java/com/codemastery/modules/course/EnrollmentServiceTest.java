package com.codemastery.modules.course.application.service;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.EnrollmentService;
import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {
    @Mock EnrollmentRepository repository;
    @Mock UserAccountService identities;
    @Mock CourseAccessService access;
    EnrollmentService service;
    private final UUID actor = UUID.randomUUID(), course = UUID.randomUUID(), row = UUID.randomUUID(), resume = UUID.randomUUID();
    @BeforeEach void setup() { service = new EnrollmentService(repository, identities, access); }
    private void learner() { when(identities.requireCurrentIdentity()).thenReturn(new CurrentIdentity(actor, IdentityRole.LEARNER)); }
    @Test void authenticationFailurePrecedesPersistence() {
        when(identities.requireCurrentIdentity()).thenThrow(new BadCredentialsException("Authentication failed"));
        assertThrows(BadCredentialsException.class, () -> service.enroll(course)); verifyNoInteractions(repository, access);
    }
    @ParameterizedTest @EnumSource(value = IdentityRole.class, names = {"INSTRUCTOR", "ADMINISTRATOR"})
    void applicationEnforcesLearnerIndependentlyOfRest(IdentityRole role) {
        when(identities.requireCurrentIdentity()).thenReturn(new CurrentIdentity(actor, role));
        assertThrows(AccessDeniedException.class, () -> service.enroll(course));
        assertThrows(AccessDeniedException.class, () -> service.leave(course));
        assertThrows(AccessDeniedException.class, () -> service.membership(course));
        assertThrows(AccessDeniedException.class, () -> service.myCourses(0,20,"joined")); verifyNoInteractions(repository, access);
    }
    @Test void hiddenOrMissingCourseDoesNotLookUpOrWriteMembership() {
        learner(); when(repository.lockPublishedCourse(course)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.enroll(course)); verify(repository).lockPublishedCourse(course); verifyNoMoreInteractions(repository);
    }
    @ParameterizedTest @EnumSource(EnrollmentStatus.class)
    void joinOnlyReactivatesLeftAndNeverReplacesExistingIdentityOrResume(EnrollmentStatus status) {
        learner(); when(repository.lockPublishedCourse(course)).thenReturn(true);
        when(repository.find(actor, course)).thenReturn(Optional.of(new Enrollment(row, actor, course, status, resume)));
        assertEquals(status == EnrollmentStatus.LEFT ? "active" : status.getStatus(), service.enroll(course).status());
        if (status == EnrollmentStatus.LEFT) verify(repository).updateStatus(row, EnrollmentStatus.ACTIVE);
        else verify(repository, never()).updateStatus(any(), any());
        verify(repository, never()).insert(any());
    }
    @Test void repeatedLeaveOfAbsentMembershipDoesNotCreateHistory() {
        learner(); when(repository.lockPublishedCourse(course)).thenReturn(true); when(repository.find(actor, course)).thenReturn(Optional.empty());
        assertEquals("none", service.leave(course).status()); verify(repository, never()).insert(any()); verify(repository, never()).updateStatus(any(),any());
    }
    @Test void membershipReadUsesApprovedVisibilityCheckBeforeLookingUpCurrentLearner() {
        learner(); doThrow(new ResourceNotFoundException("Unavailable")).when(access).requireEnrolmentEligibility(course);
        assertThrows(ResourceNotFoundException.class, () -> service.membership(course)); verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void malformedUuidIsRejectedBeforeCallingMembershipApplicationCode(String method) throws Exception {
        var application = mock(EnrollmentService.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.codemastery.modules.course.api.controller.EnrollmentController(application))
                .setControllerAdvice(new com.codemastery.common.exception.GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), "/api/courses/0-0-4000-8000-2/membership"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        verifyNoInteractions(application);
    }

}
