package com.codemastery.integration.repository;

import com.codemastery.integration.AbstractIntegrationTest;
import com.codemastery.modules.interaction.application.service.NotificationService;
import com.codemastery.modules.interaction.application.service.NotificationService.NewNotification;
import com.codemastery.modules.interaction.domain.model.Notification;
import com.codemastery.modules.interaction.domain.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IN07 real-PostgreSQL suite for the notification repository and service (V006; ADR012 §4).
 * Fixtures are rolled back or explicitly cleaned up for committed concurrent transactions.
 */
@DirtiesContext
class NotificationRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final String JWT_SECRET = generateEphemeralJwtSecret();
    private static final Instant T0 = Instant.parse("2026-11-25T01:00:00.123456Z");

    private static String generateEphemeralJwtSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @DynamicPropertySource
    static void registerTestProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private NotificationService service;

    @Test
    void insertIsIdempotentAndStartsUnread() {
        inRolledBackTransaction(() -> {
            UUID user = user();
            Notification row = row(UUID.randomUUID(), user, "Hello", false, T0);

            assertEquals(1, notifications.insertNew(List.of(row)));
            assertEquals(0, notifications.insertNew(List.of(row(row.getId(), user, "Changed", false, T0.plusSeconds(9)))));

            assertEquals("Hello", jdbc.queryForObject(
                    "SELECT message FROM public.notifications WHERE id = ?", String.class, row.getId()));
            assertEquals(Boolean.FALSE, jdbc.queryForObject(
                    "SELECT is_read FROM public.notifications WHERE id = ?", Boolean.class, row.getId()));
        });
    }

    @Test
    void serviceCreateWritesDerivedIdsOnceAndRefusesOutsideATransaction() {
        // Refused before any SQL, so no real user (and nothing committed) is needed.
        assertThrows(IllegalStateException.class,
                () -> service.create(List.of(new NewNotification(UUID.randomUUID(), UUID.randomUUID(), "No tx"))));

        inRolledBackTransaction(() -> {
            UUID recipient = user();
            UUID id = NotificationService.derivedId("test", recipient.toString());

            assertEquals(1, service.create(List.of(new NewNotification(id, recipient, "Once"),
                    new NewNotification(id, recipient, "Once"))));
            assertEquals(0, service.create(List.of(new NewNotification(id, recipient, "Once"))));
            assertEquals(1L, jdbc.queryForObject(
                    "SELECT count(*) FROM public.notifications WHERE user_id = ?", Long.class, recipient));
        });
    }

    @Test
    void aMissingRecipientRollsBackAnEarlierSuccessfulInsert() {
        UUID recipient = user();
        UUID nonexistentRecipient = UUID.randomUUID();
        UUID id1 = NotificationService.derivedId("test", recipient.toString(), "1");
        UUID id2 = NotificationService.derivedId("test", recipient.toString(), "2");
        List<UUID> ordered = java.util.stream.Stream.of(id1, id2)
                .sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        try {
            var tx = new TransactionTemplate(transactionManager);
            assertThrows(DataIntegrityViolationException.class, () -> tx.executeWithoutResult(status ->
                    service.create(List.of(new NewNotification(ordered.getFirst(), recipient, "Valid recipient"),
                            new NewNotification(ordered.getLast(), nonexistentRecipient, "Missing recipient")))));
            assertEquals(0L, jdbc.queryForObject(
                    "SELECT count(*) FROM public.notifications WHERE id IN (?, ?)", Long.class, id1, id2));
        } finally {
            jdbc.update("DELETE FROM public.notifications WHERE id IN (?, ?)", id1, id2);
            jdbc.update("DELETE FROM public.users WHERE id = ?", recipient);
        }
    }

    @Test
    void concurrentCallsWithOppositeInputOrderCommitEachOverlappingIdOnce() throws Exception {
        UUID recipient = user();
        UUID first = NotificationService.derivedId("test", recipient.toString(), "first");
        UUID second = NotificationService.derivedId("test", recipient.toString(), "second");
        var a = new NewNotification(first, recipient, "First notice");
        var b = new NewNotification(second, recipient, "Second notice");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var forward = executor.submit(() -> concurrentCreate(List.of(a, b), ready, start));
            var reverse = executor.submit(() -> concurrentCreate(List.of(b, a), ready, start));
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS), "both transactions started");
                start.countDown();
                int firstCount = forward.get(15, TimeUnit.SECONDS);
                int secondCount = reverse.get(15, TimeUnit.SECONDS);
                assertEquals(List.of(0, 2), java.util.stream.Stream.of(firstCount, secondCount).sorted().toList());
                assertEquals(2L, jdbc.queryForObject(
                        "SELECT count(*) FROM public.notifications WHERE id IN (?, ?)", Long.class, first, second));
            } finally {
                start.countDown();
            }
        } finally {
            jdbc.update("DELETE FROM public.notifications WHERE id IN (?, ?)", first, second);
            jdbc.update("DELETE FROM public.users WHERE id = ?", recipient);
        }
    }

    private int concurrentCreate(List<NewNotification> rows, CountDownLatch ready, CountDownLatch start) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.execute("SET LOCAL statement_timeout = '5s'");
            ready.countDown();
            try {
                assertTrue(start.await(10, TimeUnit.SECONDS), "concurrent creators released");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for concurrent creators", ex);
            }
            return service.create(rows);
        });
    }

    @Test
    void createsAndUpdatesUnderExactlyTheR13NotificationPrivileges() {
        inRolledBackTransaction(() -> {
            UUID recipient = user();
            UUID locked = user();
            jdbc.update("UPDATE public.users SET account_status = 'locked' WHERE id = ?", locked);
            String role = "in07_ceiling_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.execute("CREATE ROLE " + role + " NOLOGIN");
            jdbc.execute("GRANT USAGE ON SCHEMA public TO " + role);
            jdbc.execute("GRANT SELECT, INSERT, UPDATE (is_read) ON public.notifications TO " + role);
            jdbc.execute("SET LOCAL ROLE " + role);

            UUID id = NotificationService.derivedId("test", recipient.toString());
            UUID lockedId = NotificationService.derivedId("test", locked.toString());
            var batch = List.of(new NewNotification(id, recipient, "Notice"),
                    new NewNotification(lockedId, locked, "Locked recipient notice"));
            assertEquals(2, service.create(batch));
            assertEquals(0, service.create(batch));
            assertEquals(List.of(id), notifications.findOwned(recipient, true, PageRequest.of(0, 1))
                    .getContent().stream().map(Notification::getId).toList());
            assertEquals(1, notifications.countUnread(recipient));
            assertTrue(service.markRead(recipient, id).isRead());
            assertTrue(service.markRead(recipient, id).isRead());
            assertEquals(0, service.markAllRead(recipient));
            assertEquals(1, notifications.countUnread(locked));
            assertEquals(1, service.markAllRead(locked));
        });
    }

    @Test
    void listsOnlyOwnRowsNewestFirstWithIdTieBreakAndUnreadFilter() {
        inRolledBackTransaction(() -> {
            UUID me = user();
            UUID other = user();
            UUID older = new UUID(0x3000000000000000L, 1L);
            UUID tieLow = new UUID(0x1000000000000000L, 1L);
            UUID tieHigh = new UUID(0x2000000000000000L, 1L);
            notifications.insertNew(List.of(
                    row(older, me, "older", false, T0),
                    row(tieLow, me, "tie low", false, T0.plusSeconds(10)),
                    row(tieHigh, me, "tie high", false, T0.plusSeconds(10)),
                    row(UUID.randomUUID(), other, "not mine", false, T0.plusSeconds(99))));
            notifications.markRead(older, me);

            Page<Notification> all = notifications.findOwned(me, false, PageRequest.of(0, 20));
            Page<Notification> unread = notifications.findOwned(me, true, PageRequest.of(0, 20));

            assertEquals(List.of(tieHigh, tieLow, older), all.getContent().stream().map(Notification::getId).toList());
            assertEquals(3, all.getTotalElements());
            assertEquals(List.of(tieHigh, tieLow), unread.getContent().stream().map(Notification::getId).toList());
            assertTrue(all.getContent().stream().allMatch(n -> n.getUserId().equals(me)));
            assertEquals(2L, notifications.countUnread(me));
            assertEquals(1L, notifications.countUnread(other));
        });
    }

    @Test
    void markReadPersistsIsIdempotentAndNeverTouchesAForeignRow() {
        inRolledBackTransaction(() -> {
            UUID me = user();
            UUID other = user();
            UUID mine = UUID.randomUUID();
            UUID theirs = UUID.randomUUID();
            notifications.insertNew(List.of(row(mine, me, "mine", false, T0), row(theirs, other, "theirs", false, T0)));

            Notification first = notifications.markRead(mine, me).orElseThrow();
            Notification again = notifications.markRead(mine, me).orElseThrow();

            assertTrue(first.isRead());
            assertTrue(again.isRead());
            assertEquals(Boolean.TRUE, jdbc.queryForObject(
                    "SELECT is_read FROM public.notifications WHERE id = ?", Boolean.class, mine));
            assertTrue(notifications.markRead(theirs, me).isEmpty(), "foreign row looks missing");
            assertTrue(notifications.markRead(UUID.randomUUID(), me).isEmpty());
            assertEquals(Boolean.FALSE, jdbc.queryForObject(
                    "SELECT is_read FROM public.notifications WHERE id = ?", Boolean.class, theirs));
        });
    }

    @Test
    void markAllReadChangesOnlyOwnUnreadRows() {
        inRolledBackTransaction(() -> {
            UUID me = user();
            UUID other = user();
            UUID alreadyRead = UUID.randomUUID();
            notifications.insertNew(List.of(
                    row(UUID.randomUUID(), me, "a", false, T0),
                    row(UUID.randomUUID(), me, "b", false, T0.plusSeconds(1)),
                    row(alreadyRead, me, "c", false, T0.plusSeconds(2)),
                    row(UUID.randomUUID(), other, "d", false, T0)));
            notifications.markRead(alreadyRead, me);

            assertEquals(2, notifications.markAllRead(me));
            assertEquals(0L, notifications.countUnread(me));
            assertEquals(1L, notifications.countUnread(other));
            assertEquals(0, notifications.markAllRead(me));
        });
    }

    // ------------------------------------------------------------------------------- helpers

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.users (id, email, password_hash, display_name, role, account_status) "
                + "VALUES (?, ?, 'not-a-real-hash', 'IN07 user', 'learner', 'active')",
                id, "in07-" + id + "@example.test");
        return id;
    }

    private static Notification row(UUID id, UUID userId, String message, boolean read, Instant createdAt) {
        return new Notification(id, userId, message, read, createdAt);
    }

    private void inRolledBackTransaction(Runnable body) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            body.run();
        });
    }
}
