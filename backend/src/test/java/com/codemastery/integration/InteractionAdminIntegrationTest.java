package com.codemastery.integration;

import com.codemastery.modules.admin.application.service.AuditLogService;
import com.codemastery.modules.admin.domain.model.AuditAction;
import com.codemastery.modules.admin.domain.model.AuditLog;
import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.infrastructure.persistence.entity.UserEntity;
import com.codemastery.modules.auth.infrastructure.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-PostgreSQL Interaction/Admin suite approved by ADR012 section 19.3
 * ({@code CT14-INTEGRATION-PLACEMENT-2026-11-15-r1}).
 *
 * <p>IN10 (#184) contributes the CT31.consistency guarantees that {@link AuditLogService#record} owns
 * (B7-O07, recording side): the audit row commits only with its caller's unit, an audit-insert failure
 * rolls the caller's change back, a later failure removes the audit row, the caller's pending JPA-managed
 * changes are written before the audit insert, and recording never runs or commits on its own. The caller's
 * "change" here is a test-only fixture write of an unrelated {@code users.display_name} (by JDBC, or through
 * JPA and still pending), not a CT12 or CT32 mutation: B7-O07's per-action catalogue rows and no-op
 * cases belong to IN11, IN20 and IN21, which add their audited actions to this suite. Every outcome is
 * checked through an independent committed read on a separate connection, and committed fixtures are
 * removed in FK-safe order after each test (ADR008 section 5).
 */
@DirtiesContext
class InteractionAdminIntegrationTest extends AbstractIntegrationTest {

    private static final String JWT_SECRET = ephemeralJwtSecret();
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String READ_ONLY_TRANSACTION = "25006";
    private static final String CHECK_VIOLATION = "23514";

    private static String ephemeralJwtSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @DynamicPropertySource
    static void provideEphemeralJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AuditLogService audit;

    @PersistenceContext
    private EntityManager entityManager;

    private final List<UUID> users = new ArrayList<>();
    private UUID admin;
    private UUID fixtureTarget;

    @BeforeEach
    void fixtures() {
        admin = user("admin", "IN10 Atomicity Admin");
        fixtureTarget = user("learner", "Original name");
    }

    @AfterEach
    void removeFixtures() {
        SecurityContextHolder.clearContext();
        for (UUID id : users) {
            jdbc.update("DELETE FROM public.audit_logs WHERE actor_id = ?", id);
        }
        for (UUID id : users) {
            jdbc.update("DELETE FROM public.users WHERE id = ?", id);
        }
        users.clear();
    }

    @Test
    void theAuditRowCommitsTogetherWithTheCallersChangeAndIsInvisibleBeforeTheCommit() {
        signIn(admin, Role.ADMIN);

        AuditLog recorded = writable().execute(status -> {
            changeFixture("Changed name");
            AuditLog row = audit.record(AuditAction.USER_LOCKED, fixtureTarget);
            // Recording never commits on its own (no REQUIRES_NEW): another connection sees nothing yet.
            assertEquals(0, committedAuditRows(admin));
            assertEquals("Original name", committedFixtureName());
            return row;
        });

        assertEquals(1, committedAuditRows(admin));
        assertEquals("Changed name", committedFixtureName());
        assertEquals(List.of(admin + "|user_locked|user|" + fixtureTarget), committedAuditRowText(recorded.getId()));
    }

    @Test
    void anInjectedAuditInsertFailureRollsBackTheCallersChange() {
        // The injected fault: an authenticated Administrator principal without a users row makes the
        // audit INSERT fail its actor foreign key, inside record() and after the caller's change.
        UUID ghost = UUID.randomUUID();
        signIn(ghost, Role.ADMIN);
        AtomicBoolean continuedAfterRecord = new AtomicBoolean();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> writable().executeWithoutResult(status -> {
            changeFixture("Changed name");
            audit.record(AuditAction.USER_LOCKED, fixtureTarget);
            continuedAfterRecord.set(true);
        }));

        assertEquals(FOREIGN_KEY_VIOLATION, sqlState(thrown));
        assertFalse(continuedAfterRecord.get(), "the insert failure surfaces from record(), not at commit");
        assertEquals("Original name", committedFixtureName());
        assertEquals(0, committedAuditRows(ghost));
    }

    @Test
    void aFailureAfterRecordingRollsBackTheAuditRowAndTheChange() {
        signIn(admin, Role.ADMIN);

        assertThrows(IllegalStateException.class, () -> writable().executeWithoutResult(status -> {
            changeFixture("Changed name");
            audit.record(AuditAction.COURSE_HIDDEN, UUID.randomUUID());
            throw new IllegalStateException("synthetic failure after the audit insert");
        }));

        assertEquals(0, committedAuditRows(admin));
        assertEquals("Original name", committedFixtureName());
    }

    /**
     * CT31.consistency lock order (ADR012 section 7.2): the caller's own writes precede the audit insert.
     * Here the caller's change is a JPA-managed update still pending in the persistence context. Within one
     * flush Hibernate executes queued inserts before queued updates, so only the flush that {@code record}
     * performs before persisting keeps the caller's UPDATE ahead of the audit INSERT. PostgreSQL's
     * {@code cmin} (the command identifier within the writing transaction) shows which row was written first.
     */
    @Test
    void aPendingJpaManagedChangeIsWrittenBeforeTheAuditInsert() {
        signIn(admin, Role.ADMIN);

        long[] commandIds = writable().execute(status -> {
            changeFixtureThroughJpa("Managed name");
            // Still pending: a plain JDBC read on the transaction's connection does not flush Hibernate.
            assertEquals("Original name", jdbc.queryForObject(
                    "SELECT display_name FROM public.users WHERE id = ?", String.class, fixtureTarget));

            AuditLog row = audit.record(AuditAction.USER_LOCKED, fixtureTarget);

            return new long[] {
                    commandId("SELECT cmin::text::bigint FROM public.users WHERE id = ?", fixtureTarget),
                    commandId("SELECT cmin::text::bigint FROM public.audit_logs WHERE id = ?", row.getId())};
        });

        assertTrue(commandIds[0] < commandIds[1], () -> "the caller's UPDATE (command " + commandIds[0]
                + ") must be written before the audit INSERT (command " + commandIds[1] + ")");
        assertEquals("Managed name", committedFixtureName());
        assertEquals(1, committedAuditRows(admin));
    }

    /**
     * A failure of the caller's pending JPA-managed change surfaces from {@code record} before the audit
     * INSERT runs. The pending UPDATE breaks {@code ck_users_text} and the audit INSERT would break its actor
     * foreign key, so the reported SQLSTATE shows which statement executed first. Nothing commits.
     */
    @Test
    void aFailingPendingJpaManagedChangeSurfacesBeforeTheAuditInsert() {
        UUID ghost = UUID.randomUUID();
        signIn(ghost, Role.ADMIN);
        AtomicBoolean continuedAfterRecord = new AtomicBoolean();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> writable().executeWithoutResult(status -> {
            changeFixtureThroughJpa("   ");
            audit.record(AuditAction.USER_LOCKED, fixtureTarget);
            continuedAfterRecord.set(true);
        }));

        assertEquals(CHECK_VIOLATION, sqlState(thrown), "the caller's UPDATE must execute before the audit INSERT");
        assertFalse(continuedAfterRecord.get(), "the caller's flush failure surfaces from record()");
        assertEquals("Original name", committedFixtureName());
        assertEquals(0, committedAuditRows(ghost));
    }

    @Test
    void recordingRefusesToRunWithoutAWritableCallerTransaction() {
        signIn(admin, Role.ADMIN);

        assertThrows(IllegalTransactionStateException.class,
                () -> audit.record(AuditAction.USER_UNLOCKED, fixtureTarget));

        TransactionTemplate readOnly = writable();
        readOnly.setReadOnly(true);
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> readOnly.executeWithoutResult(status -> audit.record(AuditAction.USER_UNLOCKED, fixtureTarget)));

        assertEquals(READ_ONLY_TRANSACTION, sqlState(thrown));
        assertEquals(0, committedAuditRows(admin));
    }

    @Test
    void eachCallRecordsOneRowWithItsOwnIdentifierInTheSameUnit() {
        signIn(admin, Role.ADMIN);
        List<UUID> reports = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        List<UUID> ids = writable().execute(status -> reports.stream()
                .map(report -> audit.record(AuditAction.REPORT_POST_HIDDEN, report).getId()).toList());

        assertEquals(3, ids.stream().distinct().count());
        assertEquals(3, committedAuditRows(admin));
        for (int i = 0; i < reports.size(); i++) {
            assertEquals(List.of(admin + "|report_post_hidden|content_report|" + reports.get(i)),
                    committedAuditRowText(ids.get(i)));
        }
    }

    // ---------------------------------------------------------------- helpers

    private TransactionTemplate writable() {
        return new TransactionTemplate(transactionManager);
    }

    private UUID user(String role, String displayName) {
        UUID id = UUID.randomUUID();
        users.add(id);
        jdbc.update("INSERT INTO public.users (id, email, password_hash, display_name, role, account_status) "
                + "VALUES (?, ?, 'not-a-login-fixture', ?, ?, 'active')", id, "in10-" + id + "@example.test",
                displayName, role);
        return id;
    }

    /** The test-only stand-in for an audited caller's own change, in the caller's transaction. */
    private void changeFixture(String displayName) {
        assertEquals(1, jdbc.update("UPDATE public.users SET display_name = ? WHERE id = ?", displayName, fixtureTarget));
    }

    /** A caller's change made through JPA: merged onto the managed account and written only when flushed. */
    private void changeFixtureThroughJpa(String displayName) {
        UserEntity managed = entityManager.find(UserEntity.class, fixtureTarget);
        entityManager.merge(new UserEntity(managed.getId(), managed.getEmail(), managed.getPasswordHash(),
                displayName, managed.getRole(), managed.getAccountStatus()));
    }

    /** The command identifier of a row version, read on the caller's own transaction connection. */
    private long commandId(String sql, UUID id) {
        return jdbc.queryForObject(sql, Long.class, id);
    }

    private static void signIn(UUID userId, Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private int committedAuditRows(UUID actor) {
        return Integer.parseInt(independentRead("SELECT count(*) FROM public.audit_logs WHERE actor_id = ?", actor).get(0));
    }

    private String committedFixtureName() {
        return independentRead("SELECT display_name FROM public.users WHERE id = ?", fixtureTarget).get(0);
    }

    private List<String> committedAuditRowText(UUID id) {
        return independentRead("SELECT actor_id || '|' || action || '|' || target_type || '|' || target_reference "
                + "FROM public.audit_logs WHERE id = ?", id);
    }

    /** A separate pooled connection outside any Spring-managed transaction: committed state only. */
    private List<String> independentRead(String sql, UUID parameter) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            assertTrue(connection.getAutoCommit(), "independent reads run outside the caller's transaction");
            statement.setObject(1, parameter);
            List<String> values = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(result.getString(1));
                }
            }
            return values;
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String sqlState(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return "none: " + thrown;
    }
}
