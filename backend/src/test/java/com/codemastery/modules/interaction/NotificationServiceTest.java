package com.codemastery.modules.interaction;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.interaction.application.service.NotificationService;
import com.codemastery.modules.interaction.application.service.NotificationService.NewNotification;
import com.codemastery.modules.interaction.domain.model.Notification;
import com.codemastery.modules.interaction.domain.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** IN07 unit tests of {@link NotificationService} (CT11, ADR012 §4); the repository is a double. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T03:04:05.123456789Z");
    private static final Instant NOW_MICROS = Instant.parse("2026-11-25T03:04:05.123456Z");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Mock
    private NotificationRepository repository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    private static void inReadWriteTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    // ------------------------------------------------------------------------------- derivedId

    @Test
    void derivedIdIsTheNameBasedUuidOfTheDocumentedString() {
        UUID expected = UUID.nameUUIDFromBytes(
                "codemastery/notification/deadline/course-1/2026-11-25T03:04:05.123456Z"
                        .getBytes(StandardCharsets.UTF_8));

        assertEquals(expected, NotificationService.derivedId("deadline", "course-1", "2026-11-25T03:04:05.123456Z"));
        assertEquals(NotificationService.derivedId("p", "a"), NotificationService.derivedId("p", "a"));
        assertNotEquals(NotificationService.derivedId("p", "a"), NotificationService.derivedId("p", "b"));
        assertNotEquals(NotificationService.derivedId("p", "a"), NotificationService.derivedId("q", "a"));
    }

    // ------------------------------------------------------------------------------- create

    @Test
    void createRefusesWithoutATransaction() {
        assertThrows(IllegalStateException.class,
                () -> service.create(List.of(new NewNotification(UUID.randomUUID(), USER, "Hello"))));
        verifyNoInteractions(repository);
    }

    @Test
    void createRefusesInAReadOnlyTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        assertThrows(IllegalStateException.class, () -> service.create(List.of()));
        verifyNoInteractions(repository);
    }

    @Test
    void createWithNothingInsertsNothing() {
        inReadWriteTransaction();

        assertEquals(0, service.create(List.of()));
        verifyNoInteractions(repository);
    }

    @Test
    void createCollapsesDuplicatesStampsOneMicrosecondInstantAndStartsUnread() {
        inReadWriteTransaction();
        UUID id = UUID.fromString("10000000-0000-0000-0000-000000000001");
        when(repository.insertNew(any())).thenReturn(1);

        int created = service.create(List.of(
                new NewNotification(id, USER, "First"),
                new NewNotification(id, USER, "Second wins nothing")));

        assertEquals(1, created);
        ArgumentCaptor<List<Notification>> rows = ArgumentCaptor.forClass(List.class);
        verify(repository).insertNew(rows.capture());
        assertEquals(1, rows.getValue().size());
        Notification row = rows.getValue().get(0);
        assertEquals(id, row.getId());
        assertEquals(USER, row.getUserId());
        assertEquals("First", row.getMessage());
        assertFalse(row.isRead());
        assertEquals(NOW_MICROS, row.getCreatedAt());
    }

    @Test
    void createInsertsInPostgresUnsignedUuidOrderNotJavaSignedOrder() {
        inReadWriteTransaction();
        UUID high = new UUID(0x8000000000000000L, 0L);   // negative as a signed long, so Java sorts it first
        UUID low = new UUID(1L, 0L);                     // PostgreSQL (unsigned bytes) sorts this first
        when(repository.insertNew(any())).thenReturn(2);

        service.create(List.of(new NewNotification(high, USER, "A"), new NewNotification(low, USER, "B")));

        ArgumentCaptor<List<Notification>> rows = ArgumentCaptor.forClass(List.class);
        verify(repository).insertNew(rows.capture());
        assertEquals(List.of(low, high), rows.getValue().stream().map(Notification::getId).toList());
    }

    @Test
    void createReturnsTheNumberOfNewRowsReportedByTheRepository() {
        inReadWriteTransaction();
        when(repository.insertNew(any())).thenReturn(0);

        assertEquals(0, service.create(List.of(new NewNotification(UUID.randomUUID(), USER, "Repeat"))));
    }

    @Test
    void createRejectsInvalidInputBeforeAnyWrite() {
        inReadWriteTransaction();
        UUID id = UUID.randomUUID();

        assertThrows(NullPointerException.class, () -> service.create(null));
        assertThrows(NullPointerException.class, () -> service.create(java.util.Collections.singletonList(null)));
        assertThrows(NullPointerException.class,
                () -> service.create(List.of(new NewNotification(null, USER, "x"))));
        assertThrows(NullPointerException.class,
                () -> service.create(List.of(new NewNotification(id, null, "x"))));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(List.of(new NewNotification(id, USER, null))));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(List.of(new NewNotification(id, USER, " \n\t "))));
        verify(repository, never()).insertNew(any());
    }

    // ------------------------------------------------------------------------------- reads

    @Test
    void listOwnPassesTheCallerAsTheOnlyOwnerFilter() {
        when(repository.findOwned(any(), org.mockito.ArgumentMatchers.eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listOwn(USER, true, 2, 50);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findOwned(org.mockito.ArgumentMatchers.eq(USER),
                org.mockito.ArgumentMatchers.eq(true), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(50, pageable.getValue().getPageSize());
    }

    @Test
    void listOwnRejectsInvalidPageOrSizeWithoutReading() {
        assertThrows(BusinessRuleException.class, () -> service.listOwn(USER, false, -1, 20));
        assertThrows(BusinessRuleException.class, () -> service.listOwn(USER, false, 0, 0));
        assertThrows(BusinessRuleException.class, () -> service.listOwn(USER, false, 0, 101));
        verifyNoInteractions(repository);
    }

    @Test
    void unreadCountIsTheCallersCount() {
        when(repository.countUnread(USER)).thenReturn(7L);

        assertEquals(7L, service.unreadCount(USER));
    }

    // ------------------------------------------------------------------------------- mark read

    @Test
    void markReadReturnsTheOwnRow() {
        UUID id = UUID.randomUUID();
        Notification row = new Notification(id, USER, "Hi", true, NOW_MICROS);
        when(repository.markRead(id, USER)).thenReturn(Optional.of(row));

        assertSame(row, service.markRead(USER, id));
    }

    @Test
    void markReadOfAMissingOrForeignRowIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.markRead(id, USER)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.markRead(USER, id));
    }

    @Test
    void markAllReadReturnsTheNumberOfRowsChanged() {
        when(repository.markAllRead(USER)).thenReturn(3);

        assertEquals(3, service.markAllRead(USER));
    }
}
