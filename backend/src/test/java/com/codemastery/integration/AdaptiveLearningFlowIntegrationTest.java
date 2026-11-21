package com.codemastery.integration;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.infrastructure.security.AuthenticatedUser;
import com.codemastery.modules.course.application.service.CourseManagementService;
import com.codemastery.modules.course.application.service.CourseManagementService.AdaptiveConfiguration;
import com.codemastery.modules.course.application.service.CourseManagementService.ConfigurationBusy;
import com.codemastery.modules.course.application.service.EnrollmentService;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository;
import com.codemastery.modules.course.infrastructure.persistence.entity.ChapterEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.CourseEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CO14 B1-O14/B1-O15 guard and storage evidence on real Spring transactions and PostgreSQL
 * (ADR005 §§5, 7.1, 13.1–13.3; CT14-B1-COVERAGE r2). Concurrency uses latches and observed
 * pg_stat_activity lock waits, never sleeps. Adaptive's AD08 orchestration, ETags and the
 * readiness/history providers are not implemented and are not exercised here.
 *
 * <p>The context is closed after this class so its connection pool does not count against
 * PostgreSQL's connection limit while later suites run.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdaptiveLearningFlowIntegrationTest extends AbstractIntegrationTest {
    private static final UUID OWNER = id("owner"), OTHER = id("other"), LEARNER = id("learner"), ADMIN = id("admin");
    private static final UUID COURSE = id("course"), SECOND = id("second"), HIDDEN = id("hidden");
    private static final long WAIT_SECONDS = 20;
    private static final String KEY = key();

    @Autowired CourseManagementService service;
    @Autowired EnrollmentService enrollment;
    @Autowired EnrollmentRepository enrollments;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @PersistenceContext EntityManager entityManager;

    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final List<Connection> held = new ArrayList<>();

    private static UUID id(String name) { return UUID.nameUUIDFromBytes(("co14/" + name).getBytes(StandardCharsets.UTF_8)); }
    private static String key() { byte[] key = new byte[32]; new SecureRandom().nextBytes(key); return HexFormat.of().formatHex(key); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("JWT_SECRET", () -> KEY); }

    @BeforeEach void fixtures() {
        for (UUID user : List.of(OWNER, OTHER, LEARNER, ADMIN)) {
            String role = user.equals(LEARNER) ? "learner" : user.equals(ADMIN) ? "admin" : "instructor";
            jdbc.update("INSERT INTO public.users (id,email,password_hash,display_name,role,account_status) "
                    + "VALUES (?,?,'not-a-login-fixture','CO14 user',?,'active')", user, user + "@example.test", role);
        }
        jdbc.update("INSERT INTO public.courses (id,instructor_id,title,description,publication_status,adaptive_enabled) "
                + "VALUES (?,?,'Alpha','Guarded course','published',false),(?,?,'Beta','Independent course','published',false),"
                + "(?,?,'Hidden','Draft course','hidden',false)", COURSE, OWNER, SECOND, OWNER, HIDDEN, OWNER);
    }

    @AfterEach void cleanup() throws Exception {
        for (Connection connection : held) {
            connection.rollback();
            connection.close();
        }
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS));
        jdbc.update("DELETE FROM public.chapters WHERE course_id IN (?,?,?)", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.enrollments WHERE course_id IN (?,?,?)", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.courses WHERE id IN (?,?,?)", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.users WHERE id IN (?,?,?,?)", OWNER, OTHER, LEARNER, ADMIN);
    }

    // B1-O14: transaction reality.

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void eitherEntryStartsAReadCommittedUnitHoldsTheRootUntilCommitAndReturnsTheValue(boolean failFast) {
        String result = as(OWNER, Role.INSTRUCTOR, () -> guard(failFast, COURSE, () -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("read committed", jdbc.queryForObject("SELECT current_setting('transaction_isolation')", String.class));
            assertEquals("off", jdbc.queryForObject("SELECT current_setting('transaction_read_only')", String.class));
            assertTrue(rootLockedElsewhere(COURSE));
            assertFalse(rootLockedElsewhere(SECOND));
            jdbc.update("UPDATE public.courses SET title = 'Guarded' WHERE id = ?", COURSE);
            assertEquals("Alpha", titleElsewhere(COURSE));
            return "callback value";
        }));
        assertEquals("callback value", result);
        assertEquals("Guarded", titleElsewhere(COURSE));
        assertFalse(rootLockedElsewhere(COURSE));
    }

    @Test void selfInvocationOnTheUnproxiedBeanUsesTheSameProgrammaticTransaction() {
        CourseManagementService target = AopTestUtils.getUltimateTargetObject(service);
        assertFalse(AopUtils.isAopProxy(target));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        Long[] ids = new Long[2];
        as(OWNER, Role.INSTRUCTOR, () -> target.withCourseConfigurationWrite(COURSE, () -> {
            ids[0] = txid();
            ids[1] = target.tryWithCourseConfigurationWrite(COURSE, this::txid);
            jdbc.update("UPDATE public.courses SET title = 'Nested' WHERE id = ?", COURSE);
            return null;
        }));
        assertEquals(ids[0], ids[1], "a nested same-Course call joins the same physical transaction");
        assertEquals("Nested", titleElsewhere(COURSE));
    }

    @Test void nestingAnotherCourseIsRejectedAndDoomsTheWholeUnit() {
        AtomicInteger inner = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> as(OWNER, Role.INSTRUCTOR,
                () -> service.withCourseConfigurationWrite(COURSE, () -> {
                    jdbc.update("UPDATE public.courses SET title = 'Lost' WHERE id = ?", COURSE);
                    return service.withCourseConfigurationWrite(SECOND, inner::incrementAndGet);
                })));
        assertEquals(0, inner.get());
        assertEquals("Alpha", titleElsewhere(COURSE));
        assertFalse(rootLockedElsewhere(COURSE));
        assertFalse(rootLockedElsewhere(SECOND));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void deniedActorsFailBeforeLockingAndNeverRunTheCallback(boolean failFast) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        holdRootElsewhere(COURSE);
        // Admission precedes acquisition: denials return although another transaction holds the root.
        List<Callable<Object>> denied = List.of(
                () -> as(OTHER, Role.INSTRUCTOR, () -> guard(failFast, COURSE, calls::incrementAndGet)),
                () -> as(LEARNER, Role.LEARNER, () -> guard(failFast, COURSE, calls::incrementAndGet)),
                () -> as(ADMIN, Role.ADMIN, () -> guard(failFast, COURSE, calls::incrementAndGet)));
        for (Callable<Object> call : denied) {
            assertInstanceOf(AccessDeniedException.class, failureOf(workers.submit(call)));
        }
        assertInstanceOf(ResourceNotFoundException.class, failureOf(workers.submit(
                () -> as(OTHER, Role.INSTRUCTOR, () -> guard(failFast, HIDDEN, calls::incrementAndGet)))));
        assertInstanceOf(ResourceNotFoundException.class, failureOf(workers.submit(
                () -> as(OWNER, Role.INSTRUCTOR, () -> guard(failFast, UUID.randomUUID(), calls::incrementAndGet)))));
        assertInstanceOf(AuthenticationCredentialsNotFoundException.class, failureOf(workers.submit(
                () -> guard(failFast, COURSE, calls::incrementAndGet))));
        assertThrows(IllegalArgumentException.class, () -> as(OWNER, Role.INSTRUCTOR, () -> guard(failFast, null, calls::incrementAndGet)));
        assertThrows(IllegalArgumentException.class, () -> as(OWNER, Role.INSTRUCTOR, () -> guard(failFast, COURSE, null)));
        assertEquals(0, calls.get());
        assertEquals("Alpha", titleElsewhere(COURSE));
    }

    @Test void ownershipIsRecheckedAfterWaitingForTheRoot() throws Exception {
        Connection holder = holdRootElsewhere(COURSE);
        AtomicInteger calls = new AtomicInteger();
        Future<Object> waiter = workers.submit(() -> as(OWNER, Role.INSTRUCTOR,
                () -> service.withCourseConfigurationWrite(COURSE, calls::incrementAndGet)));
        awaitLockWaiters(1);
        try (PreparedStatement update = holder.prepareStatement("UPDATE public.courses SET instructor_id = ? WHERE id = ?")) {
            update.setObject(1, OTHER);
            update.setObject(2, COURSE);
            update.executeUpdate();
        }
        holder.commit();
        assertInstanceOf(AccessDeniedException.class, failureOf(waiter));
        assertEquals(0, calls.get());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void callbackFailureRollsBackEveryEffectAndReleasesTheRoot(boolean failFast) {
        RuntimeException failure = new RuntimeException("callback failure");
        assertSame(failure, assertThrows(RuntimeException.class, () -> as(OWNER, Role.INSTRUCTOR,
                () -> guard(failFast, COURSE, () -> {
                    jdbc.update("UPDATE public.courses SET title = 'Partial' WHERE id = ?", COURSE);
                    throw failure;
                }))));
        assertEquals("Alpha", titleElsewhere(COURSE));
        assertFalse(rootLockedElsewhere(COURSE));
    }

    @Test void joiningACompatibleOuterTransactionKeepsTheRootUntilOuterRollback() {
        IllegalStateException outerFailure = new IllegalStateException("outer failure after the callback returned");
        assertSame(outerFailure, assertThrows(IllegalStateException.class, () -> as(OWNER, Role.INSTRUCTOR,
                () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false).execute(status -> {
                    Long inner = service.withCourseConfigurationWrite(COURSE, () -> {
                        jdbc.update("UPDATE public.courses SET title = 'Joined' WHERE id = ?", COURSE);
                        return txid();
                    });
                    assertEquals(inner, txid(), "the guard shares the outer physical transaction");
                    assertTrue(rootLockedElsewhere(COURSE), "the root stays locked until outer completion");
                    throw outerFailure;
                }))));
        assertEquals("Alpha", titleElsewhere(COURSE));
        assertFalse(rootLockedElsewhere(COURSE));
    }

    @Test void joiningACompatibleOuterTransactionCommitsOnlyWithTheOuterUnit() {
        as(OWNER, Role.INSTRUCTOR, () -> outer(TransactionDefinition.ISOLATION_DEFAULT, false).execute(status -> {
            service.tryWithCourseConfigurationWrite(COURSE, () ->
                    jdbc.update("UPDATE public.courses SET title = 'Committed with outer' WHERE id = ?", COURSE));
            assertEquals("Alpha", titleElsewhere(COURSE));
            return null;
        }));
        assertEquals("Committed with outer", titleElsewhere(COURSE));
        assertFalse(rootLockedElsewhere(COURSE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"read-only", "repeatable-read", "serializable", "prior-write", "prior-row-lock", "queued-persist"})
    void incompatibleOuterContextsAreRejectedBeforeAnyCallbackEffect(String context) {
        AtomicInteger calls = new AtomicInteger();
        UUID chapter = UUID.randomUUID();
        TransactionTemplate outer = switch (context) {
            case "read-only" -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, true);
            case "repeatable-read" -> outer(TransactionDefinition.ISOLATION_REPEATABLE_READ, false);
            case "serializable" -> outer(TransactionDefinition.ISOLATION_SERIALIZABLE, false);
            default -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false);
        };
        assertThrows(IllegalStateException.class, () -> as(OWNER, Role.INSTRUCTOR, () -> outer.execute(status -> {
            switch (context) {
                case "prior-write" -> jdbc.update("UPDATE public.courses SET title = 'Earlier write' WHERE id = ?", SECOND);
                case "prior-row-lock" -> jdbc.queryForList("SELECT id FROM public.courses WHERE id = ? FOR SHARE", SECOND);
                case "queued-persist" -> entityManager.persist(new ChapterEntity(chapter, COURSE, "Queued chapter", 1));
                default -> { }
            }
            return service.withCourseConfigurationWrite(COURSE, calls::incrementAndGet);
        })));
        assertEquals(0, calls.get());
        assertEquals("Beta", titleElsewhere(SECOND));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.chapters WHERE id = ?", Integer.class, chapter));
        assertFalse(rootLockedElsewhere(COURSE));
    }

    @Test void rollbackOnlyCompletionNeverReturnsTheCallbackValue() {
        TransactionTemplate participant = new TransactionTemplate(transactions);
        assertThrows(UnexpectedRollbackException.class, () -> as(OWNER, Role.INSTRUCTOR,
                () -> service.withCourseConfigurationWrite(COURSE, () -> {
                    jdbc.update("UPDATE public.courses SET title = 'Doomed' WHERE id = ?", COURSE);
                    try {
                        participant.executeWithoutResult(status -> { throw new IllegalStateException("participant failure"); });
                    } catch (IllegalStateException swallowed) {
                        // The participant already marked the shared transaction rollback-only.
                    }
                    return "must not be returned";
                })));
        assertEquals("Alpha", titleElsewhere(COURSE));
        assertFalse(rootLockedElsewhere(COURSE));
    }

    @Test void entitiesLoadedBeforeTheRootAreDiscardedSoReadsObserveTheCommittedWinner() {
        as(OWNER, Role.INSTRUCTOR, () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false).execute(status -> {
            CourseEntity preloaded = entityManager.find(CourseEntity.class, COURSE);
            assertEquals("Alpha", preloaded.getTitle());
            commitElsewhere("UPDATE public.courses SET title = 'Committed winner' WHERE id = ?", COURSE);
            return service.withCourseConfigurationWrite(COURSE, () -> {
                assertFalse(entityManager.contains(preloaded), "pre-lock managed state is not trusted");
                assertEquals("Committed winner", entityManager.find(CourseEntity.class, COURSE).getTitle());
                return null;
            });
        }));
    }

    // B1-O15: contention, independence and duration.

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void sameCourseWritersSerializeAndTheWaiterRereadsCommittedState(boolean holderCommits) throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<Object> holder = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> service.withCourseConfigurationWrite(COURSE, () -> {
            jdbc.update("UPDATE public.courses SET title = 'Winner' WHERE id = ?", COURSE);
            entered.countDown();
            await(release);
            if (!holderCommits) throw new IllegalStateException("holder rolls back");
            return null;
        })));
        await(entered);
        Future<String> waiter = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> service.withCourseConfigurationWrite(COURSE,
                () -> jdbc.queryForObject("SELECT title FROM public.courses WHERE id = ?", String.class, COURSE))));
        awaitLockWaiters(1);
        assertFalse(waiter.isDone());
        release.countDown();
        if (holderCommits) holder.get(WAIT_SECONDS, TimeUnit.SECONDS); else failureOf(holder);
        assertEquals(holderCommits ? "Winner" : "Alpha", waiter.get(WAIT_SECONDS, TimeUnit.SECONDS));
    }

    @Test void independentCoursesDoNotSerialize() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<Object> holder = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> service.withCourseConfigurationWrite(COURSE, () -> {
            entered.countDown();
            await(release);
            return null;
        })));
        await(entered);
        Future<Integer> other = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> service.tryWithCourseConfigurationWrite(SECOND,
                () -> jdbc.update("UPDATE public.courses SET title = 'Independent' WHERE id = ?", SECOND))));
        assertEquals(1, other.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertFalse(holder.isDone(), "the first Course is still held while the second committed");
        release.countDown();
        holder.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertEquals("Independent", titleElsewhere(SECOND));
    }

    @Test void settingsNowaitIsBusyWhileEnrollmentHoldsTheRootAndPreservesBothParticipants() throws Exception {
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<Object> joining = workers.submit(() -> new TransactionTemplate(transactions).execute(status -> {
            assertTrue(enrollments.lockPublishedCourse(COURSE), "FE07 membership lock on the same Course root");
            jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status) VALUES (?,?,?,'active')",
                    UUID.randomUUID(), LEARNER, COURSE);
            locked.countDown();
            await(release);
            return null;
        }));
        await(locked);
        AtomicInteger calls = new AtomicInteger();
        assertThrows(ConfigurationBusy.class, () -> as(OWNER, Role.INSTRUCTOR,
                () -> service.tryWithCourseConfigurationWrite(COURSE, calls::incrementAndGet)));
        assertThrows(ConfigurationBusy.class, () -> as(OWNER, Role.INSTRUCTOR,
                () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false).execute(
                        status -> service.tryWithCourseConfigurationWrite(COURSE, calls::incrementAndGet))));
        assertEquals(0, calls.get());
        release.countDown();
        joining.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.enrollments WHERE course_id = ?", Integer.class, COURSE));
        assertEquals(1, as(OWNER, Role.INSTRUCTOR, () -> service.tryWithCourseConfigurationWrite(COURSE, calls::incrementAndGet)));
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void enrollmentWaitsBehindSettingsAndProceedsAfterCommitOrRollback(boolean settingsCommit) throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<Object> settings = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> service.tryWithCourseConfigurationWrite(COURSE, () -> {
            jdbc.update("UPDATE public.courses SET title = 'Settings write' WHERE id = ?", COURSE);
            entered.countDown();
            await(release);
            if (!settingsCommit) throw new IllegalStateException("settings roll back");
            return null;
        })));
        await(entered);
        Future<EnrollmentService.Membership> joining = workers.submit(() -> as(LEARNER, Role.LEARNER, () -> enrollment.enroll(COURSE)));
        awaitLockWaiters(1);
        assertFalse(joining.isDone());
        release.countDown();
        if (settingsCommit) settings.get(WAIT_SECONDS, TimeUnit.SECONDS); else failureOf(settings);
        assertEquals("active", joining.get(WAIT_SECONDS, TimeUnit.SECONDS).status());
        assertEquals(settingsCommit ? "Settings write" : "Alpha", titleElsewhere(COURSE));
    }

    @Test void onlyTheGuardsOwnNowaitFailureIsReportedAsBusy() throws Exception {
        holdRootElsewhere(SECOND);
        RuntimeException calleeLock = assertThrows(RuntimeException.class, () -> as(OWNER, Role.INSTRUCTOR,
                () -> service.tryWithCourseConfigurationWrite(COURSE, () -> {
                    jdbc.update("UPDATE public.courses SET title = 'Before callee lock' WHERE id = ?", COURSE);
                    return jdbc.queryForList("SELECT id FROM public.courses WHERE id = ? FOR UPDATE NOWAIT", UUID.class, SECOND);
                })));
        assertFalse(calleeLock instanceof ConfigurationBusy);
        assertEquals("55P03", sqlState(calleeLock));
        assertEquals("Alpha", titleElsewhere(COURSE));

        holdRootElsewhere(COURSE);
        RuntimeException timeout = assertThrows(RuntimeException.class, () -> as(OWNER, Role.INSTRUCTOR,
                () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false).execute(status -> {
                    jdbc.execute("SET LOCAL lock_timeout = '100ms'");
                    return service.withCourseConfigurationWrite(COURSE, () -> fail("the waiting guard must not run"));
                })));
        assertFalse(timeout instanceof ConfigurationBusy, "the default guard never maps a lock timeout to 409");
        assertEquals("55P03", sqlState(timeout));
    }

    @Test void aCallerDeadlineCancelsAWaitingGuardWithoutPartialEffectsOrLeftoverLocks() throws Exception {
        Connection holder = holdRootElsewhere(COURSE);
        TransactionTemplate bounded = outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false);
        bounded.setTimeout(1); // explicit test budget, not a selected production deadline
        long started = System.nanoTime();
        RuntimeException cancelled = assertThrows(RuntimeException.class, () -> as(OWNER, Role.INSTRUCTOR,
                () -> bounded.execute(status -> service.withCourseConfigurationWrite(COURSE, () -> fail("must not run")))));
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) < WAIT_SECONDS);
        assertFalse(cancelled instanceof ConfigurationBusy);
        assertInstanceOf(DataAccessException.class, cancelled);
        assertEquals("57014", sqlState(cancelled), "PostgreSQL cancelled the waiting statement");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() "
                + "AND wait_event_type = 'Lock'", Integer.class));
        holder.commit();
        assertEquals("ok", as(OWNER, Role.INSTRUCTOR, () -> service.withCourseConfigurationWrite(COURSE, () -> "ok")));
    }

    // ADR005 §7.1 / §13.1: Course-owned storage handoff.

    @Test void ownersReadAnImmutableSnapshotIncludingTheLegacyDisabledNull() throws Exception {
        assertEquals(new AdaptiveConfiguration(COURSE, false, null),
                as(OWNER, Role.INSTRUCTOR, () -> service.readAdaptiveConfiguration(COURSE)));
        jdbc.update("UPDATE public.courses SET adaptive_enabled = true, adaptive_settings = CAST(? AS jsonb) WHERE id = ?",
                "{ \"b\" : [1, {\"c\": 2.50}],  \"a\": null }", HIDDEN);
        AdaptiveConfiguration stored = as(OWNER, Role.INSTRUCTOR, () -> service.readAdaptiveConfiguration(HIDDEN));
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("c", new BigDecimal("2.50"));
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("a", null);
        expected.put("b", List.of(1, nested));
        assertEquals(new AdaptiveConfiguration(HIDDEN, true, expected), stored);
        assertThrows(UnsupportedOperationException.class, () -> stored.adaptiveSettings().put("x", 1));
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) stored.adaptiveSettings().get("b")).clear());
        @SuppressWarnings("unchecked") Map<String, Object> inner = (Map<String, Object>) ((List<?>) stored.adaptiveSettings().get("b")).get(1);
        assertThrows(UnsupportedOperationException.class, () -> inner.put("c", 3));

        assertInstanceOf(AccessDeniedException.class, failureOf(workers.submit(
                () -> as(OTHER, Role.INSTRUCTOR, () -> service.readAdaptiveConfiguration(COURSE)))));
        assertInstanceOf(ResourceNotFoundException.class, failureOf(workers.submit(
                () -> as(OTHER, Role.INSTRUCTOR, () -> service.readAdaptiveConfiguration(HIDDEN)))));
        assertInstanceOf(AccessDeniedException.class, failureOf(workers.submit(
                () -> as(LEARNER, Role.LEARNER, () -> service.readAdaptiveConfiguration(COURSE)))));
        assertInstanceOf(AccessDeniedException.class, failureOf(workers.submit(
                () -> as(ADMIN, Role.ADMIN, () -> service.readAdaptiveConfiguration(COURSE)))));
        assertInstanceOf(ResourceNotFoundException.class, failureOf(workers.submit(
                () -> as(OWNER, Role.INSTRUCTOR, () -> service.readAdaptiveConfiguration(UUID.randomUUID())))));
    }

    @Test void guardedWriteComparesTheFreshStateStructurallyAndPersistsBothFields() {
        jdbc.update("UPDATE public.courses SET adaptive_settings = CAST(? AS jsonb) WHERE id = ?",
                "{\"z\": 1,   \"a\": {\"y\": [1, 2]}}", COURSE);
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("a", Map.of("y", List.of(1, 2)));
        reordered.put("z", 1);
        as(OWNER, Role.INSTRUCTOR, () -> service.tryWithCourseConfigurationWrite(COURSE, () -> {
            service.writeAdaptiveConfiguration(COURSE, new AdaptiveConfiguration(COURSE, false, reordered),
                    true, Map.of("policy", Map.of("n", 3)));
            return null;
        }));
        assertTrue(jdbc.queryForObject("SELECT adaptive_enabled AND adaptive_settings = CAST(? AS jsonb) FROM public.courses WHERE id = ?",
                Boolean.class, "{\"policy\": {\"n\": 3}}", COURSE));
        assertEquals(new AdaptiveConfiguration(COURSE, true, Map.of("policy", Map.of("n", 3))),
                as(OWNER, Role.INSTRUCTOR, () -> service.readAdaptiveConfiguration(COURSE)));

        // The AD08 shape: read the fresh snapshot inside the held guard, then write against it.
        as(OWNER, Role.INSTRUCTOR, () -> service.tryWithCourseConfigurationWrite(COURSE, () -> {
            AdaptiveConfiguration current = service.readAdaptiveConfiguration(COURSE);
            assertEquals("read committed", jdbc.queryForObject("SELECT current_setting('transaction_isolation')", String.class));
            service.writeAdaptiveConfiguration(COURSE, current, false, current.adaptiveSettings());
            return null;
        }));
        assertEquals(new AdaptiveConfiguration(COURSE, false, Map.of("policy", Map.of("n", 3))),
                as(OWNER, Role.INSTRUCTOR, () -> service.readAdaptiveConfiguration(COURSE)));
    }

    @Test void staleExpectedStateRollsBackTheWholeUnitAndPreservesTheNewerWriter() {
        AdaptiveConfiguration readBeforeTheGuard = as(OWNER, Role.INSTRUCTOR, () -> service.readAdaptiveConfiguration(COURSE));
        commitElsewhere("UPDATE public.courses SET adaptive_settings = '{\"newer\": true}'::jsonb WHERE id = ?", COURSE);
        assertThrows(IllegalStateException.class, () -> as(OWNER, Role.INSTRUCTOR, () -> service.tryWithCourseConfigurationWrite(COURSE, () -> {
            jdbc.update("UPDATE public.courses SET title = 'Same unit' WHERE id = ?", COURSE);
            service.writeAdaptiveConfiguration(COURSE, readBeforeTheGuard, false, Map.of("stale", true));
            return null;
        })));
        assertEquals("Alpha", titleElsewhere(COURSE));
        assertTrue(jdbc.queryForObject("SELECT adaptive_settings = '{\"newer\": true}'::jsonb FROM public.courses WHERE id = ?",
                Boolean.class, COURSE));
        assertFalse(rootLockedElsewhere(COURSE));
    }

    @Test void writesRequireTheHeldGuardOfTheSameCourseAndRejectInvalidStorageShapes() {
        AdaptiveConfiguration current = new AdaptiveConfiguration(COURSE, false, null);
        assertThrows(IllegalStateException.class, () -> as(OWNER, Role.INSTRUCTOR,
                () -> { service.writeAdaptiveConfiguration(COURSE, current, false, Map.of("a", 1)); return null; }));
        assertThrows(IllegalStateException.class, () -> as(OWNER, Role.INSTRUCTOR, () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false)
                .execute(status -> { service.writeAdaptiveConfiguration(COURSE, current, false, Map.of("a", 1)); return null; })));
        assertThrows(IllegalStateException.class, () -> as(OWNER, Role.INSTRUCTOR, () -> service.withCourseConfigurationWrite(SECOND, () -> {
            service.writeAdaptiveConfiguration(COURSE, current, false, Map.of("a", 1));
            return null;
        })));
        assertThrows(IllegalArgumentException.class, () -> as(OWNER, Role.INSTRUCTOR, () -> service.withCourseConfigurationWrite(COURSE, () -> {
            service.writeAdaptiveConfiguration(COURSE, current, true, null);
            return null;
        })));
        assertThrows(IllegalArgumentException.class, () -> as(OWNER, Role.INSTRUCTOR, () -> service.withCourseConfigurationWrite(COURSE, () -> {
            service.writeAdaptiveConfiguration(COURSE, new AdaptiveConfiguration(SECOND, false, null), false, Map.of("a", 1));
            return null;
        })));
        assertThrows(IllegalArgumentException.class, () -> as(OWNER, Role.INSTRUCTOR, () -> service.withCourseConfigurationWrite(COURSE, () -> {
            service.writeAdaptiveConfiguration(COURSE, current, false, Map.of("a", new Object()));
            return null;
        })));
        assertNull(jdbc.queryForObject("SELECT adaptive_settings::text FROM public.courses WHERE id = ?", String.class, COURSE));
        assertNotEquals(Boolean.TRUE, jdbc.queryForObject("SELECT adaptive_enabled FROM public.courses WHERE id = ?", Boolean.class, COURSE));
    }

    // Helpers.

    private <T> T guard(boolean failFast, UUID course, Supplier<T> work) {
        return failFast ? service.tryWithCourseConfigurationWrite(course, work) : service.withCourseConfigurationWrite(course, work);
    }

    private TransactionTemplate outer(int isolation, boolean readOnly) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setIsolationLevel(isolation);
        template.setReadOnly(readOnly);
        return template;
    }

    private static <T> T as(UUID user, Role role, Supplier<T> action) {
        AuthenticatedUser principal = new AuthenticatedUser(user, role);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Long txid() {
        return jdbc.queryForObject("SELECT txid_current()", Long.class);
    }

    /** A separate connection and transaction holding the Course row, as another writer would. */
    private Connection holdRootElsewhere(UUID course) throws SQLException {
        Connection connection = dataSource.getConnection();
        held.add(connection);
        connection.setAutoCommit(false);
        try (PreparedStatement lock = connection.prepareStatement("SELECT id FROM public.courses WHERE id = ? FOR UPDATE")) {
            lock.setObject(1, course);
            lock.executeQuery().close();
        }
        return connection;
    }

    private boolean rootLockedElsewhere(UUID course) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement lock = connection.prepareStatement("SELECT id FROM public.courses WHERE id = ? FOR UPDATE NOWAIT")) {
                lock.setObject(1, course);
                lock.executeQuery().close();
                return false;
            } catch (SQLException failure) {
                if ("55P03".equals(failure.getSQLState())) return true;
                throw failure;
            } finally {
                connection.rollback();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private String titleElsewhere(UUID course) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement read = connection.prepareStatement("SELECT title FROM public.courses WHERE id = ?")) {
            read.setObject(1, course);
            try (ResultSet row = read.executeQuery()) {
                assertTrue(row.next());
                return row.getString(1);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void commitElsewhere(String sql, UUID course) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement update = connection.prepareStatement(sql)) {
            connection.setAutoCommit(true);
            update.setObject(1, course);
            assertEquals(1, update.executeUpdate());
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Barrier on observable PostgreSQL state: the expected number of backends waiting for a lock. */
    private void awaitLockWaiters(int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() "
                + "AND wait_event_type = 'Lock'", Integer.class) < expected) {
            if (System.nanoTime() > deadline) fail("Expected " + expected + " lock waiter(s)");
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) throw new IllegalStateException("latch timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static Throwable failureOf(Future<?> future) throws Exception {
        try {
            future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException failure) {
            return failure.getCause();
        }
        throw new AssertionError("Expected a failure");
    }

    private static String sqlState(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) return sql.getSQLState();
        }
        return "none: " + Arrays.toString(failure.getStackTrace());
    }
}
