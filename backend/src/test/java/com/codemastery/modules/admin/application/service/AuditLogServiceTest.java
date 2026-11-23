package com.codemastery.modules.admin.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.admin.application.service.AuditLogService.AuditLogView;
import com.codemastery.modules.admin.domain.model.AuditAction;
import com.codemastery.modules.admin.domain.model.AuditLog;
import com.codemastery.modules.admin.domain.repository.AuditLogRepository;
import com.codemastery.modules.admin.domain.repository.AuditLogRepository.AuditLogFilter;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.auth.application.service.UserAccountService.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T05:06:07.123456789Z");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Mock
    AuditLogRepository repository;

    @Mock
    UserAccountService identities;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(repository, identities, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void signedInAs(IdentityRole role) {
        when(identities.requireCurrentIdentity()).thenReturn(new CurrentIdentity(ADMIN, role));
    }

    private static AuditLog row(UUID actor, String action, String targetType) {
        return new AuditLog(UUID.randomUUID(), actor, action, targetType, UUID.randomUUID().toString(),
                NOW.truncatedTo(ChronoUnit.MICROS));
    }

    // ---------------------------------------------------------------- record

    @Test
    void recordWritesOneRowForTheAuthenticatedAdministrator() {
        signedInAs(IdentityRole.ADMINISTRATOR);
        when(repository.append(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID target = UUID.randomUUID();

        AuditLog returned = service.record(AuditAction.USER_LOCKED, target);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).append(captor.capture());
        AuditLog saved = captor.getValue();
        assertSame(saved, returned);
        assertAll(
                () -> assertNotNull(saved.getId()),
                () -> assertEquals(ADMIN, saved.getActorId()),
                () -> assertEquals("user_locked", saved.getAction()),
                () -> assertEquals("user", saved.getTargetType()),
                () -> assertEquals(target.toString(), saved.getTargetReference()),
                () -> assertEquals(saved.getTargetReference().toLowerCase(), saved.getTargetReference()),
                () -> assertEquals(Instant.parse("2026-11-18T05:06:07.123456Z"), saved.getOccurredAt()));
    }

    @ParameterizedTest
    @EnumSource(value = IdentityRole.class, names = {"LEARNER", "INSTRUCTOR"})
    void recordRejectsEveryNonAdministrator(IdentityRole role) {
        signedInAs(role);

        assertThrows(AccessDeniedException.class, () -> service.record(AuditAction.COURSE_HIDDEN, UUID.randomUUID()));

        verifyNoInteractions(repository);
    }

    @Test
    void recordRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> service.record(null, UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> service.record(AuditAction.USER_LOCKED, null));
        verifyNoInteractions(repository);
    }

    // ---------------------------------------------------------------- search

    @Test
    void searchReturnsRowsWithActorNamesAndNullForAMissingAccount() {
        signedInAs(IdentityRole.ADMINISTRATOR);
        UUID known = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        AuditLog first = row(known, "user_locked", "user");
        AuditLog second = row(missing, "course_hidden", "course");
        when(repository.search(any(), any())).thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 20), 2));
        when(identities.userSummaries(Set.of(known, missing)))
                .thenReturn(Map.of(known, new UserSummary(known, "Ada Admin", "admin")));

        PageResponse<AuditLogView> page = service.search(null, null, null, null, null, null, null, null);

        assertAll(
                () -> assertEquals(2, page.totalElements()),
                () -> assertEquals(List.of(first.getId(), second.getId()),
                        page.content().stream().map(AuditLogView::id).toList()),
                () -> assertEquals("Ada Admin", page.content().get(0).actorDisplayName()),
                () -> assertNull(page.content().get(1).actorDisplayName()),
                () -> assertEquals("user_locked", page.content().get(0).action()),
                () -> assertEquals(first.getTargetReference(), page.content().get(0).targetReference()),
                () -> assertEquals(first.getOccurredAt(), page.content().get(0).occurredAt()));
    }

    @Test
    void searchPassesItsFiltersAndPagingToTheRepositoryAndSkipsNamesForAnEmptyPage() {
        signedInAs(IdentityRole.ADMINISTRATOR);
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Instant from = Instant.parse("2026-11-18T01:00:00.000001Z");
        Instant to = Instant.parse("2026-11-18T02:00:00Z");
        when(repository.search(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

        PageResponse<AuditLogView> page = service.search(actor, "user_locked", "user", target, from, to, 1, 5);

        ArgumentCaptor<AuditLogFilter> filter = ArgumentCaptor.forClass(AuditLogFilter.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(filter.capture(), pageable.capture());
        assertAll(
                () -> assertEquals(new AuditLogFilter(actor, "user_locked", "user", target.toString(), from, to),
                        filter.getValue()),
                () -> assertEquals(PageRequest.of(1, 5), pageable.getValue()),
                () -> assertEquals(0, page.content().size()),
                () -> assertEquals(1, page.page()),
                () -> assertEquals(5, page.size()));
        verify(identities, never()).userSummaries(any());
    }

    @Test
    void searchUsesTheApprovedPagingDefaults() {
        signedInAs(IdentityRole.ADMINISTRATOR);
        when(repository.search(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.search(null, null, null, null, null, null, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(any(), pageable.capture());
        assertEquals(PageRequest.of(0, 20), pageable.getValue());
    }

    @Test
    void searchRejectsInvalidValuesBeforeQueryingAnything() {
        signedInAs(IdentityRole.ADMINISTRATOR);
        Instant from = Instant.parse("2026-11-18T01:00:00Z");
        List<Executable> invalid = List.of(
                () -> service.search(null, "nope", null, null, null, null, null, null),
                () -> service.search(null, "USER_LOCKED", null, null, null, null, null, null),
                () -> service.search(null, null, "group", null, null, null, null, null),
                () -> service.search(null, null, null, null, from, from, null, null),
                () -> service.search(null, null, null, null, from, from.minusSeconds(1), null, null),
                () -> service.search(null, null, null, null, from.plusNanos(1), null, null, null),
                () -> service.search(null, null, null, null, null, from.plusNanos(1_500), null, null),
                () -> service.search(null, null, null, null, null, null, -1, null),
                () -> service.search(null, null, null, null, null, null, null, 0),
                () -> service.search(null, null, null, null, null, null, null, 101));

        for (Executable call : invalid) {
            BusinessRuleException thrown = assertThrows(BusinessRuleException.class, call);
            assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatus());
        }
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @EnumSource(value = IdentityRole.class, names = {"LEARNER", "INSTRUCTOR"})
    void searchRejectsEveryNonAdministrator(IdentityRole role) {
        signedInAs(role);

        assertThrows(AccessDeniedException.class, () -> service.search(null, null, null, null, null, null, null, null));

        verifyNoInteractions(repository);
    }
}
