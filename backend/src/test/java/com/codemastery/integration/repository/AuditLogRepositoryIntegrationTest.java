package com.codemastery.integration.repository;

import com.codemastery.common.api.PageResponse;
import com.codemastery.integration.AbstractIntegrationTest;
import com.codemastery.modules.admin.application.service.AuditLogService;
import com.codemastery.modules.admin.application.service.AuditLogService.AuditLogView;
import com.codemastery.modules.admin.domain.model.AuditAction;
import com.codemastery.modules.admin.domain.model.AuditLog;
import com.codemastery.modules.admin.domain.repository.AuditLogRepository;
import com.codemastery.modules.admin.domain.repository.AuditLogRepository.AuditLogFilter;
import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.infrastructure.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * IN10 real-PostgreSQL suite for the append-only audit log (V006 audit_logs; ADR012 sections 7, 19.2):
 * insert, filtered/ordered/paged reads, the SELECT+INSERT privilege ceiling and the transaction rule of
 * {@link AuditLogService#record}. Every test runs in one transaction that is always rolled back.
 */
@DirtiesContext
class AuditLogRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final String JWT_SECRET = generateEphemeralJwtSecret();
    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final Instant T0 = Instant.parse("2026-11-18T01:00:00.123456Z");

    private static String generateEphemeralJwtSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @DynamicPropertySource
    static void registerTestProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AuditLogRepository audits;

    @Autowired
    private AuditLogService auditService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- append and read

    @Test
    void appendInsertsOneRowAndSearchReadsItBack() {
        inRolledBackTransaction(() -> {
            UUID admin = user("admin", "Audit Admin");
            AuditLog row = row(admin, AuditAction.USER_LOCKED, T0);

            assertSame(row, audits.append(row));
            flushAndClear();

            Map<String, Object> stored = jdbc.queryForMap("SELECT * FROM public.audit_logs WHERE id = ?", row.getId());
            assertAll(
                    () -> assertEquals(admin, stored.get("actor_id")),
                    () -> assertEquals("user_locked", stored.get("action")),
                    () -> assertEquals("user", stored.get("target_type")),
                    () -> assertEquals(row.getTargetReference(), stored.get("target_reference")),
                    () -> assertEquals(T0, instant(stored.get("occurred_at"))));
            Page<AuditLog> page = audits.search(filter(admin, null, null, null, null, null), PageRequest.of(0, 10));
            assertEquals(1, page.getTotalElements());
            AuditLog read = page.getContent().get(0);
            assertAll(
                    () -> assertEquals(row.getId(), read.getId()),
                    () -> assertEquals(row.getActorId(), read.getActorId()),
                    () -> assertEquals(row.getAction(), read.getAction()),
                    () -> assertEquals(row.getTargetType(), read.getTargetType()),
                    () -> assertEquals(row.getTargetReference(), read.getTargetReference()),
                    () -> assertEquals(row.getOccurredAt(), read.getOccurredAt()));
        });
    }

    @Test
    void searchOrdersNewestFirstThenByIdDescendingAndIgnoresCallerSorting() {
        inRolledBackTransaction(() -> {
            UUID admin = user("admin", "Audit Admin");
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID lowId = a.toString().compareTo(b.toString()) < 0 ? a : b;
            UUID highId = lowId.equals(a) ? b : a;
            AuditLog older = row(admin, AuditAction.USER_LOCKED, T0);
            AuditLog sameInstantLow = new AuditLog(lowId, admin, "course_hidden", "course",
                    UUID.randomUUID().toString(), T0.plusSeconds(5));
            AuditLog sameInstantHigh = new AuditLog(highId, admin, "user_unlocked", "user",
                    UUID.randomUUID().toString(), T0.plusSeconds(5));
            audits.append(sameInstantLow);
            audits.append(older);
            audits.append(sameInstantHigh);
            flushAndClear();

            List<UUID> expected = List.of(sameInstantHigh.getId(), sameInstantLow.getId(), older.getId());
            assertEquals(expected, ids(audits.search(filter(admin, null, null, null, null, null), PageRequest.of(0, 10))));
            assertEquals(expected, ids(audits.search(filter(admin, null, null, null, null, null),
                    PageRequest.of(0, 10, Sort.by(Sort.Order.asc("action"))))));
        });
    }

    // ---------------------------------------------------------------- filters

    @Test
    void filtersCombineWithAndWithInclusiveFromAndExclusiveTo() {
        inRolledBackTransaction(() -> {
            UUID admin = user("admin", "Audit Admin");
            UUID other = user("admin", "Second Admin");
            UUID userTarget = UUID.randomUUID();
            AuditLog r1 = new AuditLog(UUID.randomUUID(), admin, "user_locked", "user", userTarget.toString(), T0);
            AuditLog r2 = new AuditLog(UUID.randomUUID(), admin, "course_hidden", "course",
                    UUID.randomUUID().toString(), T0.plusSeconds(1));
            AuditLog r3 = new AuditLog(UUID.randomUUID(), other, "report_dismissed", "content_report",
                    UUID.randomUUID().toString(), T0.plusSeconds(2));
            AuditLog r4 = new AuditLog(UUID.randomUUID(), admin, "user_unlocked", "user", userTarget.toString(),
                    T0.plusSeconds(3));
            for (AuditLog row : List.of(r1, r2, r3, r4)) {
                audits.append(row);
            }
            flushAndClear();

            assertAll(
                    () -> assertEquals(List.of(r4.getId(), r2.getId(), r1.getId()), found(admin, null, null, null, null, null)),
                    () -> assertEquals(List.of(r3.getId()), found(other, null, null, null, null, null)),
                    () -> assertEquals(List.of(r2.getId()), found(admin, "course_hidden", null, null, null, null)),
                    () -> assertEquals(List.of(r4.getId(), r1.getId()), found(admin, null, "user", null, null, null)),
                    () -> assertEquals(List.of(r4.getId(), r1.getId()),
                            found(admin, null, null, userTarget.toString(), null, null)),
                    () -> assertEquals(List.of(r2.getId()),
                            found(admin, null, null, null, T0.plusSeconds(1), T0.plusSeconds(3))),
                    () -> assertEquals(List.of(r4.getId(), r2.getId()),
                            found(admin, null, null, null, T0.plusSeconds(1), null)),
                    () -> assertEquals(List.of(r1.getId()), found(admin, null, null, null, null, T0.plusSeconds(1))),
                    () -> assertEquals(List.of(r4.getId()), found(admin, null, "user", null, T0.plusSeconds(1), null)),
                    () -> assertEquals(List.of(), found(admin, "report_dismissed", null, null, null, null)));
        });
    }

    @Test
    void pagesReportTheTrueTotalEvenBeyondTheLastPage() {
        inRolledBackTransaction(() -> {
            UUID admin = user("admin", "Audit Admin");
            for (int i = 0; i < 3; i++) {
                audits.append(row(admin, AuditAction.USER_LOCKED, T0.plusSeconds(i)));
            }
            flushAndClear();
            AuditLogFilter mine = filter(admin, null, null, null, null, null);

            Page<AuditLog> first = audits.search(mine, PageRequest.of(0, 2));
            Page<AuditLog> second = audits.search(mine, PageRequest.of(1, 2));
            Page<AuditLog> beyond = audits.search(mine, PageRequest.of(5, 2));
            Page<AuditLog> beyondAnyOffset = audits.search(mine, PageRequest.of(Integer.MAX_VALUE, 100));

            assertAll(
                    () -> assertEquals(2, first.getContent().size()),
                    () -> assertEquals(3, first.getTotalElements()),
                    () -> assertEquals(2, first.getTotalPages()),
                    () -> assertEquals(1, second.getContent().size()),
                    () -> assertEquals(0, beyond.getContent().size()),
                    () -> assertEquals(3, beyond.getTotalElements()),
                    () -> assertEquals(0, beyondAnyOffset.getContent().size()),
                    () -> assertEquals(3, beyondAnyOffset.getTotalElements()));
        });
    }

    // ---------------------------------------------------------------- append-only

    @Test
    void appendNeverOverwritesAnExistingId() {
        Throwable thrown = assertThrows(RuntimeException.class, () -> inRolledBackTransaction(() -> {
            UUID admin = user("admin", "Audit Admin");
            AuditLog first = row(admin, AuditAction.USER_LOCKED, T0);
            audits.append(first);
            flushAndClear();

            audits.append(new AuditLog(first.getId(), admin, "course_hidden", "course",
                    UUID.randomUUID().toString(), T0.plusSeconds(1)));
            entityManager.flush();
        }));

        assertTrue(causeMessages(thrown).contains("pk_audit_logs"), () -> "unexpected failure " + causeMessages(thrown));
    }

    @Test
    void ceilingRoleCanInsertAndSelectAuditRows() {
        inRolledBackTransaction(() -> {
            UUID admin = user("admin", "Audit Admin");
            entityManager.flush();
            switchToCeilingRole();
            AuditLog row = row(admin, AuditAction.REPORT_DISMISSED, T0);

            audits.append(row);
            entityManager.flush();

            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.audit_logs WHERE id = ?", Integer.class, row.getId()));
        });
    }

    @Test
    void ceilingRoleCannotUpdateOrDeleteAuditRows() {
        assertInsufficientPrivilege(admin -> {
            UUID id = insertCommittedLookingRow(admin);
            switchToCeilingRole();
            jdbc.update("UPDATE public.audit_logs SET action = 'user_unlocked' WHERE id = ?", id);
        });
        assertInsufficientPrivilege(admin -> {
            UUID id = insertCommittedLookingRow(admin);
            switchToCeilingRole();
            jdbc.update("DELETE FROM public.audit_logs WHERE id = ?", id);
        });
    }

    // ---------------------------------------------------------------- AuditLogService.record

    @Test
    void recordFailsOutsideACallerTransactionInsteadOfCommittingOnItsOwn() {
        assertThrows(IllegalTransactionStateException.class,
                () -> auditService.record(AuditAction.USER_LOCKED, UUID.randomUUID()));
    }

    @Test
    void recordWritesTheAdministratorsRowInsideTheCallerTransaction() {
        inRolledBackTransaction(() -> {
            UUID admin = user("admin", "Audit Admin");
            signIn(admin, Role.ADMIN);
            UUID course = UUID.randomUUID();
            Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

            AuditLog row = auditService.record(AuditAction.COURSE_HIDDEN, course);
            Instant after = Instant.now();
            flushAndClear();

            Map<String, Object> stored = jdbc.queryForMap("SELECT * FROM public.audit_logs WHERE id = ?", row.getId());
            Instant occurred = instant(stored.get("occurred_at"));
            assertAll(
                    () -> assertEquals(admin, stored.get("actor_id")),
                    () -> assertEquals("course_hidden", stored.get("action")),
                    () -> assertEquals("course", stored.get("target_type")),
                    () -> assertEquals(course.toString(), stored.get("target_reference")),
                    () -> assertTrue(!occurred.isBefore(before) && !occurred.isAfter(after),
                            () -> "occurred_at " + occurred + " outside [" + before + ", " + after + "]"));
        });
    }

    @Test
    void recordRejectsNonAdministratorsAndWritesNothing() {
        inRolledBackTransaction(() -> {
            UUID learner = user("learner", "Learner");
            for (Role role : List.of(Role.LEARNER, Role.INSTRUCTOR)) {
                signIn(learner, role);
                assertThrows(AccessDeniedException.class,
                        () -> auditService.record(AuditAction.USER_LOCKED, UUID.randomUUID()));
            }
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.audit_logs WHERE actor_id = ?",
                    Integer.class, learner));
        });
    }

    // ---------------------------------------------------------------- AuditLogService.search

    @Test
    void searchResolvesActorDisplayNamesFromTheAccounts() {
        inRolledBackTransaction(() -> {
            UUID admin = user("admin", "Audit Admin");
            UUID other = user("admin", "Second Admin");
            audits.append(row(admin, AuditAction.USER_LOCKED, T0));
            audits.append(row(other, AuditAction.COURSE_HIDDEN, T0.plusSeconds(1)));
            flushAndClear();
            signIn(admin, Role.ADMIN);

            PageResponse<AuditLogView> mine = auditService.search(admin, null, null, null, null, null, 0, 10);
            PageResponse<AuditLogView> theirs = auditService.search(other, null, null, null, null, null, 0, 10);

            assertAll(
                    () -> assertEquals(1, mine.totalElements()),
                    () -> assertEquals("Audit Admin", mine.content().get(0).actorDisplayName()),
                    () -> assertEquals("user_locked", mine.content().get(0).action()),
                    () -> assertEquals(1, theirs.totalElements()),
                    () -> assertEquals("Second Admin", theirs.content().get(0).actorDisplayName()));
        });
    }

    // ---------------------------------------------------------------- helpers

    private void inRolledBackTransaction(Runnable body) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            status.setRollbackOnly();
            body.run();
        });
    }

    private void assertInsufficientPrivilege(Consumer<UUID> body) {
        Throwable thrown = assertThrows(RuntimeException.class,
                () -> inRolledBackTransaction(() -> body.accept(user("admin", "Audit Admin"))));
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && INSUFFICIENT_PRIVILEGE.equals(sql.getSQLState())) {
                return;
            }
        }
        fail("expected SQLSTATE 42501 but got " + causeMessages(thrown));
    }

    /**
     * Switches the current transaction to a new {@code NOLOGIN} role holding exactly the ADR012 section
     * 19.2 ceiling for {@code audit_logs} (SELECT, INSERT). Role, grants and the switch end with the rollback.
     */
    private void switchToCeilingRole() {
        entityManager.flush();
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class),
                "the disposable test container user creates the test-scoped role");
        String role = "in10_ceiling_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE ROLE " + role + " NOLOGIN");
        jdbc.execute("GRANT USAGE ON SCHEMA public TO " + role);
        jdbc.execute("GRANT SELECT, INSERT ON public.audit_logs TO " + role);
        jdbc.execute("SET LOCAL ROLE " + role);
    }

    /** Inserts and flushes a row so a privilege test can then try to change it. */
    private UUID insertCommittedLookingRow(UUID admin) {
        AuditLog row = row(admin, AuditAction.USER_LOCKED, T0);
        audits.append(row);
        entityManager.flush();
        return row.getId();
    }

    private UUID user(String role, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.users (id, email, password_hash, display_name, role, account_status) "
                + "VALUES (?, ?, 'not-a-real-hash', ?, ?, 'active')", id, "in10-" + id + "@example.test", displayName, role);
        return id;
    }

    private static void signIn(UUID userId, Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static AuditLog row(UUID actor, AuditAction action, Instant at) {
        return new AuditLog(UUID.randomUUID(), actor, action.getCode(), action.getTargetType(),
                UUID.randomUUID().toString(), at.truncatedTo(ChronoUnit.MICROS));
    }

    private static AuditLogFilter filter(UUID actor, String action, String targetType, String targetReference,
                                         Instant from, Instant to) {
        return new AuditLogFilter(actor, action, targetType, targetReference, from, to);
    }

    private List<UUID> found(UUID actor, String action, String targetType, String targetReference,
                             Instant from, Instant to) {
        return ids(audits.search(filter(actor, action, targetType, targetReference, from, to), PageRequest.of(0, 10)));
    }

    private static List<UUID> ids(Page<AuditLog> page) {
        return page.getContent().stream().map(AuditLog::getId).toList();
    }

    private static Instant instant(Object value) {
        return value instanceof OffsetDateTime odt ? odt.toInstant() : ((java.sql.Timestamp) value).toInstant();
    }

    private static String causeMessages(Throwable thrown) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            messages.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(" | ");
        }
        return messages.toString();
    }
}
