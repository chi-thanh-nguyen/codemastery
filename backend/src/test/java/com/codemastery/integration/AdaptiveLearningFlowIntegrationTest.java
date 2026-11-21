package com.codemastery.integration;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.assessment.application.service.QuizService;
import com.codemastery.modules.assessment.application.service.QuizService.QuizInput;
import com.codemastery.modules.assessment.domain.model.Quiz;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.infrastructure.security.AuthenticatedUser;
import com.codemastery.modules.course.application.service.CourseManagementService;
import com.codemastery.modules.course.application.service.CourseManagementService.AdaptiveConfiguration;
import com.codemastery.modules.course.application.service.CourseManagementService.ConfigurationBusy;
import com.codemastery.modules.course.application.service.EnrollmentService;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.MaterialType;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseContentRepository;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository;
import com.codemastery.modules.course.infrastructure.persistence.entity.LearningMaterialEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.ChapterEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.CourseEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
    @Autowired QuizService quizService;
    @Autowired QuizRepository quizRepository;
    @Autowired EnrollmentService enrollment;
    @Autowired EnrollmentRepository enrollments;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseContentRepository courseContentRepository;
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
        String quizzes = "(SELECT id FROM public.quizzes WHERE course_id IN (?,?,?))";
        jdbc.update("DELETE FROM public.attempts WHERE quiz_id IN " + quizzes, COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.quiz_questions WHERE quiz_id IN " + quizzes, COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.quizzes WHERE course_id IN (?,?,?)", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.learning_materials WHERE lesson_id IN (SELECT l.id FROM public.lessons l "
                + "JOIN public.chapters ch ON ch.id = l.chapter_id WHERE ch.course_id IN (?,?,?))", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.lessons WHERE chapter_id IN (SELECT id FROM public.chapters WHERE course_id IN (?,?,?))",
                COURSE, SECOND, HIDDEN);
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

    @Test void aSavepointRollbackThatReleasesTheRootNeverLeavesAUsableHeldMarker() {
        AtomicInteger reentered = new AtomicInteger();
        assertCommitRefused(() -> as(OWNER, Role.INSTRUCTOR,
                () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false).execute(status -> {
                    // A caller-managed savepoint opened before the guard; Spring NESTED is not enabled.
                    jdbc.execute("SAVEPOINT before_guard");
                    service.withCourseConfigurationWrite(COURSE, () -> null);
                    jdbc.execute("ROLLBACK TO SAVEPOINT before_guard");
                    assertFalse(rootLockedElsewhere(COURSE), "PostgreSQL released the root with the savepoint");
                    jdbc.update("UPDATE public.courses SET title = 'Written without the root' WHERE id = ?", SECOND);
                    assertThrows(IllegalStateException.class,
                            () -> service.withCourseConfigurationWrite(COURSE, reentered::incrementAndGet));
                    assertThrows(IllegalStateException.class, () -> service.writeAdaptiveConfiguration(COURSE,
                            new AdaptiveConfiguration(COURSE, false, null), false, Map.of("a", 1)));
                    return null;
                })));
        assertEquals(0, reentered.get());
        assertEquals("Beta", titleElsewhere(SECOND), "the doomed outer unit committed nothing");
        assertNull(jdbc.queryForObject("SELECT adaptive_settings::text FROM public.courses WHERE id = ?", String.class, COURSE));
        assertFalse(rootLockedElsewhere(COURSE));
    }

    @Test void aRequiresNewGuardBehindASuspendedHolderOnTheSameThreadFailsInsteadOfWaitingForItself() throws Exception {
        TransactionTemplate independent = new TransactionTemplate(transactions);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        AtomicInteger inner = new AtomicInteger();
        Future<List<Object>> outcome = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> {
            Object first;
            try {
                service.withCourseConfigurationWrite(COURSE, () -> independent.execute(
                        status -> service.withCourseConfigurationWrite(COURSE, inner::incrementAndGet)));
                first = "no failure";
            } catch (RuntimeException failure) {
                first = failure;
            }
            // The thread's root record is released with the transaction, so the same thread can retry.
            return List.of(first, service.withCourseConfigurationWrite(COURSE, () -> "retried"));
        }));
        List<Object> results = outcome.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertInstanceOf(IllegalStateException.class, results.get(0));
        assertEquals("retried", results.get(1));
        assertEquals(0, inner.get());
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

    // Independent-review P2-01: the held root must survive until the unit completes.

    @Test void aSavepointRollbackInsideTheCallbackCannotCommitAStaleWriteOverTheWinner() {
        IllegalStateException[] reported = new IllegalStateException[1];
        assertCommitRefused(() -> as(OWNER, Role.INSTRUCTOR,
                () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false).execute(status -> {
                    jdbc.execute("SAVEPOINT before_guard");
                    try {
                        service.withCourseConfigurationWrite(COURSE, () -> {
                            String stale = jdbc.queryForObject("SELECT title FROM public.courses WHERE id = ?", String.class, COURSE);
                            jdbc.execute("ROLLBACK TO SAVEPOINT before_guard");
                            commitElsewhere("UPDATE public.courses SET title = 'Intervening winner' WHERE id = ?", COURSE);
                            jdbc.update("UPDATE public.courses SET title = ? WHERE id = ?", stale + " (stale edit)", COURSE);
                            return null;
                        });
                    } catch (IllegalStateException released) {
                        reported[0] = released; // a caller that swallows the failure still cannot commit
                    }
                    return null;
                })));
        assertNotNull(reported[0], "the guard reports the released root before returning");
        assertEquals("Intervening winner", titleElsewhere(COURSE));
        assertFalse(rootLockedElsewhere(COURSE));
    }

    @Test void aSavepointRollbackAfterTheCallbackReturnedFailsTheOuterCommit() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> as(OWNER, Role.INSTRUCTOR,
                () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false).execute(status -> {
                    jdbc.execute("SAVEPOINT before_guard");
                    String stale = service.withCourseConfigurationWrite(COURSE,
                            () -> jdbc.queryForObject("SELECT title FROM public.courses WHERE id = ?", String.class, COURSE));
                    jdbc.execute("ROLLBACK TO SAVEPOINT before_guard");
                    commitElsewhere("UPDATE public.courses SET title = 'Intervening winner' WHERE id = ?", COURSE);
                    courseRepository.updateMetadata(COURSE, stale + " (stale edit)", "Guarded course", null);
                    return null;
                })));
        assertInstanceOf(IllegalStateException.class, failure);
        assertEquals("Intervening winner", titleElsewhere(COURSE));
        assertFalse(rootLockedElsewhere(COURSE));
    }

    // Independent-review P2-02: a direct write never discards pending JPA changes.

    @Test void pendingCourseJsonChangesAreNeverDiscardedByADirectMetadataWrite() {
        jdbc.update("UPDATE public.courses SET adaptive_settings = '{\"pending\": 1}'::jsonb WHERE id = ?", COURSE);
        assertThrows(UnexpectedRollbackException.class, () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false)
                .execute(status -> {
                    entityManager.find(CourseEntity.class, COURSE).getAdaptiveSettings().put("pending", 2);
                    assertThrows(InvalidDataAccessApiUsageException.class,
                            () -> courseRepository.updateMetadata(COURSE, "Direct title", "Guarded course", null));
                    return null; // the caller caught the rejection; the unit must still not commit
                }));
        assertEquals("Alpha", titleElsewhere(COURSE));
        assertTrue(jdbc.queryForObject("SELECT adaptive_settings = '{\"pending\": 1}'::jsonb FROM public.courses WHERE id = ?",
                Boolean.class, COURSE));
    }

    @Test void aPendingChapterMergeIsNeverDiscardedByADirectReorder() {
        UUID first = chapter(COURSE, 1), second = chapter(COURSE, 2);
        String before = contentSnapshot();
        assertThrows(UnexpectedRollbackException.class, () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false)
                .execute(status -> {
                    entityManager.merge(new ChapterEntity(first, COURSE, "Merged title", 1));
                    assertThrows(InvalidDataAccessApiUsageException.class,
                            () -> courseContentRepository.reorderChapters(COURSE, List.of(second, first)));
                    return null;
                }));
        assertEquals(before, contentSnapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = {"course-publication", "chapter-title", "chapter-append", "lesson", "lesson-publication",
            "lesson-reorder", "material", "material-publication", "material-insert", "adaptive-settings"})
    void everyDirectWriteRejectsPendingChangesOfItsRowsAndDoomsTheUnit(String path) {
        UUID chapter = chapter(COURSE, 1);
        UUID lesson = lesson(chapter, 1), otherLesson = lesson(chapter, 2);
        UUID material = material(lesson);
        String before = contentSnapshot();
        assertThrows(UnexpectedRollbackException.class, () -> as(OWNER, Role.INSTRUCTOR,
                () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false).execute(status -> {
                    if (path.startsWith("course") || path.startsWith("adaptive")) {
                        entityManager.find(CourseEntity.class, COURSE).getAdaptiveSettings();
                        entityManager.merge(new ChapterEntity(chapter, COURSE, "Pending chapter", 1));
                    } else if (path.startsWith("chapter")) {
                        entityManager.merge(new ChapterEntity(chapter, COURSE, "Pending chapter", 1));
                    } else if (path.startsWith("lesson")) {
                        entityManager.merge(new LessonEntity(lesson, chapter, "Pending lesson", null, 1, "published"));
                    } else {
                        entityManager.merge(new LearningMaterialEntity(material, lesson, "Pending material", "text",
                                "hidden", "Pending text", null, null, null));
                    }
                    Runnable write = switch (path) {
                        case "course-publication" -> () -> courseRepository.updatePublicationStatus(COURSE, PublishStatus.HIDDEN);
                        case "adaptive-settings" -> () -> courseRepository.replaceAdaptiveConfiguration(COURSE, false, null, false, Map.of("a", 1));
                        case "chapter-title" -> () -> courseContentRepository.updateChapterTitle(COURSE, chapter, "Direct chapter");
                        case "chapter-append" -> () -> courseContentRepository.appendChapter(COURSE, UUID.randomUUID(), "Appended");
                        case "lesson" -> () -> courseContentRepository.updateLesson(chapter, lesson, "Direct lesson", null);
                        case "lesson-publication" -> () -> courseContentRepository.updateLessonPublicationStatus(chapter, lesson, PublishStatus.HIDDEN);
                        case "lesson-reorder" -> () -> courseContentRepository.reorderLessons(chapter, List.of(otherLesson, lesson));
                        case "material" -> () -> courseContentRepository.replaceMaterialContent(new LearningMaterial(material, lesson,
                                "Direct material", MaterialType.TEXT, PublishStatus.HIDDEN, "Direct text", null, null, null));
                        case "material-publication" -> () -> courseContentRepository.updateMaterialPublicationStatus(lesson, material, PublishStatus.PUBLISHED);
                        case "material-insert" -> () -> courseContentRepository.insertMaterial(new LearningMaterial(UUID.randomUUID(), lesson,
                                "Inserted", MaterialType.TEXT, PublishStatus.HIDDEN, "Inserted text", null, null, null));
                        default -> throw new IllegalArgumentException(path);
                    };
                    assertThrows(InvalidDataAccessApiUsageException.class, write::run);
                    return null;
                })));
        assertEquals(before, contentSnapshot());
    }

    @Test void cleanManagedEntitiesAreInvalidatedSoTheCommittedDirectWriteIsReadBackFresh() {
        UUID first = chapter(COURSE, 1), second = chapter(COURSE, 2);
        outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false).execute(status -> {
            ChapterEntity managed = entityManager.find(ChapterEntity.class, first);
            CourseEntity course = entityManager.find(CourseEntity.class, COURSE);
            assertEquals(1, managed.getPosition());
            courseContentRepository.reorderChapters(COURSE, List.of(second, first));
            courseRepository.updateMetadata(COURSE, "Directly renamed", "Guarded course", null);
            ChapterEntity reread = entityManager.find(ChapterEntity.class, first);
            assertNotSame(managed, reread);
            assertEquals(2, reread.getPosition());
            assertNotSame(course, entityManager.find(CourseEntity.class, COURSE));
            assertEquals("Directly renamed", entityManager.find(CourseEntity.class, COURSE).getTitle());
            return null;
        });
        assertEquals("Directly renamed", titleElsewhere(COURSE));
        assertEquals(List.of(second, first), jdbc.queryForList(
                "SELECT id FROM public.chapters WHERE course_id = ? ORDER BY position", UUID.class, COURSE));
    }

    // Helpers.

    // AS06 CT23.composition (ADR007 §§5, 8): quiz writes, attempt start and the Course root. Attempt
    // start (AS08) is not implemented; a separate connection plays it in the contract's lock order.

    @Test void aStoredQuizKeepsItsCourseAndAReplacementWritesOnlyItsDefinition() {
        UUID quiz = quizRow(COURSE);
        TransactionTemplate unit = outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false);
        // An integrity failure (the repository's translated IllegalStateException), never a client error.
        RuntimeException refused = assertThrows(RuntimeException.class, () -> unit.execute(status ->
                quizRepository.save(new Quiz(quiz, SECOND, "Moved", "test", 90, "published"))));
        assertFalse(refused instanceof BusinessRuleException || refused instanceof ResourceNotFoundException, refused::toString);
        assertEquals("Draft|practice|60|hidden|" + COURSE, quizStateElsewhere(quiz));
        unit.execute(status -> quizRepository.save(new Quiz(quiz, COURSE, "Renamed", "test", 90, "published")));
        assertEquals("Renamed|test|90|published|" + COURSE, quizStateElsewhere(quiz));
    }

    @ParameterizedTest
    @CsvSource({"delete,true", "delete,false", "update,true", "update,false"})
    void anAttemptStartHoldingTheRootFreezesTheQuizBeforeAWaitingEditOrDelete(String operation, boolean starterCommits)
            throws Exception {
        UUID quiz = quizRow(COURSE);
        Connection starter = holdRootElsewhere(COURSE);
        insertAttempt(starter, quiz);
        Future<Object> writer = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> operation.equals("delete")
                ? quizService.delete(COURSE, quiz)
                : quizService.update(COURSE, quiz, new QuizInput("Edited", "test", 90, List.of()))));
        awaitLockWaiters(1);
        assertFalse(writer.isDone(), "the quiz writer waits on the Course root held by the attempt start");
        if (starterCommits) starter.commit(); else starter.rollback();
        if (starterCommits) {
            BusinessRuleException frozen = assertInstanceOf(BusinessRuleException.class, failureOf(writer));
            assertEquals(409, frozen.getStatus().value());
            assertEquals("Draft|practice|60|hidden|" + COURSE, quizStateElsewhere(quiz));
        } else {
            writer.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals(operation.equals("delete") ? "none" : "Edited|test|90|hidden|" + COURSE, quizStateElsewhere(quiz));
        }
    }

    @ParameterizedTest
    @CsvSource({"delete,true", "delete,false", "update,true", "update,false"})
    void aQuizWriterHoldingTheRootAndQuizRowMakesAnAttemptStartWaitForItsOutcome(String operation, boolean starterTakesRoot)
            throws Exception {
        UUID quiz = quizRow(COURSE);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<Object> writer = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> service.withCourseConfigurationWrite(COURSE, () -> {
            Object result = operation.equals("delete") ? quizService.delete(COURSE, quiz)
                    : quizService.update(COURSE, quiz, new QuizInput("Edited", "test", 90, List.of()));
            entered.countDown();
            await(release);
            return result;
        })));
        await(entered);
        // Without the root, the attempt's foreign-key check still waits on the quiz row lock.
        Future<String> starter = workers.submit(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                if (starterTakesRoot) {
                    try (PreparedStatement lock = connection.prepareStatement("SELECT id FROM public.courses WHERE id = ? FOR UPDATE")) {
                        lock.setObject(1, COURSE);
                        lock.executeQuery().close();
                    }
                }
                try {
                    insertAttempt(connection, quiz);
                    connection.commit();
                    return "started";
                } catch (SQLException refused) {
                    connection.rollback();
                    return refused.getSQLState();
                }
            }
        });
        awaitLockWaiters(1);
        assertFalse(starter.isDone(), "the attempt start waits for the quiz writer's unit");
        release.countDown();
        writer.get(WAIT_SECONDS, TimeUnit.SECONDS);
        if (operation.equals("delete")) {
            assertEquals("23503", starter.get(WAIT_SECONDS, TimeUnit.SECONDS), "the deleted quiz cannot be started");
            assertEquals("none", quizStateElsewhere(quiz));
        } else {
            assertEquals("started", starter.get(WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals("Edited|test|90|hidden|" + COURSE, quizStateElsewhere(quiz), "the attempt freezes the committed definition");
            BusinessRuleException frozen = assertThrows(BusinessRuleException.class, () -> as(OWNER, Role.INSTRUCTOR,
                    () -> quizService.update(COURSE, quiz, new QuizInput("Again", "test", 90, List.of()))));
            assertEquals(409, frozen.getStatus().value());
        }
    }

    private UUID quizRow(UUID course) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.quizzes (id,course_id,title,kind,time_limit_seconds,publication_status) "
                + "VALUES (?,?,'Draft','practice',60,'hidden')", id, course);
        return id;
    }

    private static void insertAttempt(Connection connection, UUID quiz) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO public.attempts "
                + "(id,user_id,quiz_id,attempt_number,started_at,expires_at) VALUES (?,?,?,1,now(),now() + interval '5 minutes')")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, LEARNER);
            insert.setObject(3, quiz);
            insert.executeUpdate();
        }
    }

    /** Committed quiz row on a separate connection, or "none". */
    private String quizStateElsewhere(UUID quiz) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement read = connection.prepareStatement(
                "SELECT concat_ws('|', title, kind, time_limit_seconds, publication_status, course_id) FROM public.quizzes WHERE id = ?")) {
            read.setObject(1, quiz);
            try (ResultSet row = read.executeQuery()) {
                return row.next() ? row.getString(1) : "none";
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /**
     * The commit is refused and rolled back. Spring runs beforeCommit before it inspects the
     * rollback-only flag, so the guard's pre-commit check (IllegalStateException) may report the
     * refusal instead of UnexpectedRollbackException; callers assert the database outcome too.
     */
    private static void assertCommitRefused(org.junit.jupiter.api.function.Executable unit) {
        RuntimeException refused = assertThrows(RuntimeException.class, unit);
        assertTrue(refused instanceof UnexpectedRollbackException || refused instanceof IllegalStateException,
                () -> "unexpected refusal " + refused);
    }

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

    private UUID chapter(UUID course, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.chapters (id,course_id,title,position) VALUES (?,?,?,?)", id, course, "Chapter " + position, position);
        return id;
    }

    private UUID lesson(UUID chapter, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.lessons (id,chapter_id,title,position,publication_status) VALUES (?,?,?,?,'published')",
                id, chapter, "Lesson " + position, position);
        return id;
    }

    private UUID material(UUID lesson) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.learning_materials (id,lesson_id,title,material_type,publication_status,text_content) "
                + "VALUES (?,?,'Notes','text','hidden','Original text')", id, lesson);
        return id;
    }

    /** Every Course-owned value the direct-write regressions could change, read on a fresh connection. */
    private String contentSnapshot() {
        return jdbc.queryForObject("SELECT concat_ws('|', (SELECT concat_ws(',', title, publication_status, adaptive_enabled, "
                + "adaptive_settings::text) FROM public.courses WHERE id = ?), "
                + "(SELECT string_agg(concat_ws(',', id, title, position), ';' ORDER BY id) FROM public.chapters WHERE course_id = ?), "
                + "(SELECT string_agg(concat_ws(',', l.id, l.title, l.position, l.publication_status), ';' ORDER BY l.id) "
                + "FROM public.lessons l JOIN public.chapters ch ON ch.id = l.chapter_id WHERE ch.course_id = ?), "
                + "(SELECT string_agg(concat_ws(',', m.id, m.title, m.publication_status, m.text_content), ';' ORDER BY m.id) "
                + "FROM public.learning_materials m JOIN public.lessons l ON l.id = m.lesson_id "
                + "JOIN public.chapters ch ON ch.id = l.chapter_id WHERE ch.course_id = ?))",
                String.class, COURSE, COURSE, COURSE, COURSE);
    }

}
