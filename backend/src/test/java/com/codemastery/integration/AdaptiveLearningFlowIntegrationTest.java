package com.codemastery.integration;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService.GraphSnapshot;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService.MappingReference;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService.PrerequisiteReference;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService.SkillReference;
import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.infrastructure.security.AuthenticatedUser;
import com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider;
import com.codemastery.modules.course.application.service.CourseManagementService;
import com.codemastery.modules.course.application.service.CourseManagementService.AdaptiveConfiguration;
import com.codemastery.modules.course.application.service.CourseManagementService.ConfigurationBusy;
import com.codemastery.modules.assessment.application.service.QuestionBankService;
import com.codemastery.modules.assessment.application.service.QuestionBankService.QuestionInput;
import com.codemastery.modules.course.application.service.CourseStructureService;
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
import org.hibernate.resource.jdbc.spi.StatementInspector;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
import java.util.concurrent.atomic.AtomicReference;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * CO14 B1-O14/B1-O15 guard and storage evidence on real Spring transactions and PostgreSQL
 * (ADR005 §§5, 7.1, 13.1–13.3; CT14-B1-COVERAGE r2). Concurrency uses latches and observed
 * pg_stat_activity lock waits, never sleeps. Adaptive's AD08 orchestration, ETags and the
 * readiness/history providers are not implemented and are not exercised here.
 *
 * <p>AD07 B1-O01/O03/O04/O11 (ADR005 §§3.2, 4, 5, 8): the skill graph through the production
 * security chain, real Course ownership/ancestry, the Course-root guard, PostgreSQL foreign keys
 * and committed state observed on separate connections. Every assembled graph proves its persisted
 * Lesson mappings against CO06's CT40 projection ({@code CourseStructureService.readStructure},
 * ADR005 §§3.1, 4.1) in the same snapshot or under the held Course root; CO06 Lesson creation and
 * mapping serialize on that root. Question writers and enablement are other Issues' participants.
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

    /** Runs once on the reading thread immediately before its prerequisite query is prepared. */
    private static final ThreadLocal<Runnable> BEFORE_PREREQUISITES = new ThreadLocal<>();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired CourseManagementService service;
    @Autowired SkillGraphService graph;
    @Autowired CourseStructureService structure;
    @Autowired QuestionBankService questionBank;
    @Autowired JwtTokenProvider tokens;
    @Autowired WebApplicationContext context;
    @Autowired EnrollmentService enrollment;
    @Autowired EnrollmentRepository enrollments;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseContentRepository courseContentRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @PersistenceContext EntityManager entityManager;

    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private MockMvc mvc;
    private final List<Connection> held = new ArrayList<>();

    private static UUID id(String name) { return UUID.nameUUIDFromBytes(("co14/" + name).getBytes(StandardCharsets.UTF_8)); }
    private static String key() { byte[] key = new byte[32]; new SecureRandom().nextBytes(key); return HexFormat.of().formatHex(key); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> KEY);
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector",
                () -> SkillGraphStatementInspector.class.getName());
    }

    /** Test hook only: lets a separate connection commit between two statements of one graph read. */
    public static class SkillGraphStatementInspector implements StatementInspector {
        @Override
        public String inspect(String sql) {
            Runnable concurrentChange = BEFORE_PREREQUISITES.get();
            if (concurrentChange != null && sql.contains("from skill_prerequisites")) {
                BEFORE_PREREQUISITES.remove();
                concurrentChange.run();
            }
            return sql;
        }
    }

    @BeforeEach void fixtures() {
        for (UUID user : List.of(OWNER, OTHER, LEARNER, ADMIN)) {
            String role = user.equals(LEARNER) ? "learner" : user.equals(ADMIN) ? "admin" : "instructor";
            jdbc.update("INSERT INTO public.users (id,email,password_hash,display_name,role,account_status) "
                    + "VALUES (?,?,'not-a-login-fixture','CO14 user',?,'active')", user, user + "@example.test", role);
        }
        jdbc.update("INSERT INTO public.courses (id,instructor_id,title,description,publication_status,adaptive_enabled) "
                + "VALUES (?,?,'Alpha','Guarded course','published',false),(?,?,'Beta','Independent course','published',false),"
                + "(?,?,'Hidden','Draft course','hidden',false)", COURSE, OWNER, SECOND, OWNER, HIDDEN, OWNER);
        mvc = webAppContextSetup(context).addFilters(new DelegatingFilterProxy("springSecurityFilterChain", context)).build();
    }

    @AfterEach void cleanup() throws Exception {
        for (Connection connection : held) {
            connection.rollback();
            connection.close();
        }
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS));
        BEFORE_PREREQUISITES.remove();
        String courseQuizzes = "(SELECT id FROM public.quizzes WHERE course_id IN (?,?,?))";
        jdbc.update("DELETE FROM public.attempt_answers WHERE attempt_id IN (SELECT id FROM public.attempts WHERE quiz_id IN "
                + courseQuizzes + ")", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.attempts WHERE quiz_id IN " + courseQuizzes, COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.quiz_questions WHERE quiz_id IN " + courseQuizzes, COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.quizzes WHERE course_id IN (?,?,?)", COURSE, SECOND, HIDDEN);
        String courseSkills = "(SELECT id FROM public.skills WHERE course_id IN (?,?,?))";
        jdbc.update("DELETE FROM public.lesson_skills WHERE skill_id IN " + courseSkills, COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.skill_prerequisites WHERE course_id IN (?,?,?)", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.questions WHERE skill_id IN " + courseSkills, COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.learner_skill_masteries WHERE skill_id IN " + courseSkills, COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.skills WHERE course_id IN (?,?,?)", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.refresh_sessions WHERE user_id IN (?,?,?,?)", OWNER, OTHER, LEARNER, ADMIN);
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

    /**
     * The commit is refused and rolled back. Spring runs beforeCommit before it inspects the
     * rollback-only flag, so the guard's pre-commit check (IllegalStateException) may report the
     * refusal instead of UnexpectedRollbackException; callers assert the database outcome too.
     */
    // AD07 B1-O01/O11: the skill graph through the production security chain (ADR005 §8.1).

    @Test void everyGraphOperationAdmitsOnlyTheOwningInstructorBeforeAnyDescendantLookupOrWrite() throws Exception {
        UUID skill = UUID.randomUUID(), other = UUID.randomUUID(), lesson = UUID.randomUUID();
        for (UUID course : List.of(COURSE, HIDDEN)) {
            for (Operation operation : operations(course, skill, other, lesson)) {
                assertEquals(401, call(operation, null).getResponse().getStatus(), operation::toString);
                assertEquals(403, call(operation, bearer(LEARNER)).getResponse().getStatus(), operation::toString);
                assertEquals(403, call(operation, bearer(ADMIN)).getResponse().getStatus(), operation::toString);
                MvcResult nonOwner = call(operation, bearer(OTHER));
                // Published non-owner 403; hidden Course concealed as 404 (ADR001 §12.4 stages 3-4).
                assertEquals(course.equals(HIDDEN) ? 404 : 403, nonOwner.getResponse().getStatus(), operation::toString);
                assertProblem(nonOwner);
            }
        }
        assertEquals(0, graphRows());
    }

    @Test void theOwnerAuthorsTheGraphOverHttpWithIdempotentPairsAndContractStatuses() throws Exception {
        UUID lesson = lesson(chapter(COURSE, 1), 1), foreignLesson = lesson(chapter(SECOND, 1), 1);
        UUID foreignSkill = skillRow(SECOND, "Foreign");
        String owner = bearer(OWNER), base = "/api/courses/" + COURSE;

        JsonNode empty = json(call(new Operation(HttpMethod.GET, base + "/skill-graph", null), owner), 200);
        assertEquals(COURSE.toString(), empty.get("courseId").asString());
        assertEquals(0, empty.get("skills").size() + empty.get("prerequisites").size() + empty.get("mappings").size());

        JsonNode created = json(call(new Operation(HttpMethod.POST, base + "/skills", "{\"name\":\"Variables\"}"), owner), 201);
        UUID a = UUID.fromString(created.get("id").asString());
        assertEquals("Variables", created.get("name").asString());
        assertTrue(created.has("description") && created.get("description").isNull(), "description is present and null");
        UUID b = UUID.fromString(json(call(new Operation(HttpMethod.POST, base + "/skills",
                "{\"name\":\"Loops\",\"description\":\"for and while\"}"), owner), 201).get("id").asString());

        JsonNode replaced = json(call(new Operation(HttpMethod.PUT, base + "/skills/" + b, "{\"name\":\"Iteration\"}"), owner), 200);
        assertEquals(b.toString(), replaced.get("id").asString());
        assertTrue(replaced.get("description").isNull(), "omitted description is replaced with null");
        assertEquals("Iteration", jdbc.queryForObject("SELECT name FROM public.skills WHERE id = ? AND course_id = ?",
                String.class, b, COURSE));

        String ab = base + "/skills/" + a + "/prerequisites/" + b, ba = base + "/skills/" + b + "/prerequisites/" + a;
        for (int repeat = 0; repeat < 2; repeat++) {
            noContent(call(new Operation(HttpMethod.PUT, ab, null), owner));
        }
        assertEquals(List.of(a + "->" + b), edgesElsewhere(COURSE));
        assertProblem(call(new Operation(HttpMethod.PUT, ba, null), owner), 409, "The request conflicts with an existing resource.");
        assertProblem(call(new Operation(HttpMethod.PUT, base + "/skills/" + a + "/prerequisites/" + a, null), owner), 400, null);
        assertProblem(call(new Operation(HttpMethod.PUT, base + "/skills/" + a + "/prerequisites/" + foreignSkill, null), owner), 404, null);
        assertProblem(call(new Operation(HttpMethod.PUT, base + "/skills/" + UUID.randomUUID() + "/prerequisites/" + a, null), owner), 404, null);
        assertEquals(List.of(a + "->" + b), edgesElsewhere(COURSE));

        String map = base + "/lessons/" + lesson + "/skills/" + a;
        for (int repeat = 0; repeat < 2; repeat++) {
            noContent(call(new Operation(HttpMethod.PUT, map, null), owner));
        }
        assertEquals(List.of(lesson + "->" + a), mappingsElsewhere());
        assertProblem(call(new Operation(HttpMethod.PUT, base + "/lessons/" + foreignLesson + "/skills/" + a, null), owner), 404, null);
        assertProblem(call(new Operation(HttpMethod.PUT, base + "/lessons/" + lesson + "/skills/" + foreignSkill, null), owner), 404, null);
        assertProblem(call(new Operation(HttpMethod.DELETE, base + "/lessons/" + foreignLesson + "/skills/" + a, null), owner), 404, null);
        assertEquals(List.of(lesson + "->" + a), mappingsElsewhere());

        JsonNode read = json(call(new Operation(HttpMethod.GET, base + "/skill-graph", null), owner), 200);
        List<String> expectedSkills = sortedText(a, b);
        assertEquals(expectedSkills, List.of(read.get("skills").get(0).get("id").asString(), read.get("skills").get(1).get("id").asString()));
        assertEquals(a.toString(), read.get("prerequisites").get(0).get("skillId").asString());
        assertEquals(b.toString(), read.get("prerequisites").get(0).get("prerequisiteSkillId").asString());
        assertEquals(lesson.toString(), read.get("mappings").get(0).get("lessonId").asString());

        assertProblem(call(new Operation(HttpMethod.DELETE, base + "/skills/" + a, null), owner), 409, null);
        assertProblem(call(new Operation(HttpMethod.DELETE, base + "/skills/" + UUID.randomUUID(), null), owner), 404, null);
        assertProblem(call(new Operation(HttpMethod.DELETE, base + "/skills/" + foreignSkill, null), owner), 404, null);
        for (int repeat = 0; repeat < 2; repeat++) {
            noContent(call(new Operation(HttpMethod.DELETE, map, null), owner));
            noContent(call(new Operation(HttpMethod.DELETE, ab, null), owner));
        }
        assertEquals(List.of(), mappingsElsewhere());
        assertEquals(List.of(), edgesElsewhere(COURSE));
        noContent(call(new Operation(HttpMethod.DELETE, base + "/skills/" + a, null), owner));
        assertEquals(List.of(b.toString()), jdbc.queryForList("SELECT id::text FROM public.skills WHERE course_id = ?", String.class, COURSE));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.skills WHERE id = ?", Integer.class, foreignSkill));
    }

    @Test void malformedRequestsAreBadRequestsWithoutEffectsAndTheOwnerMayAuthorAHiddenCourse() throws Exception {
        String owner = bearer(OWNER), skills = "/api/courses/" + COURSE + "/skills";
        assertProblem(call(new Operation(HttpMethod.GET, "/api/courses/not-a-uuid/skill-graph", null), owner), 400, null);
        assertProblem(call(new Operation(HttpMethod.GET, "/api/courses/" + COURSE + "/skill-graph?page=1", null), owner), 400, null);
        assertProblem(call(new Operation(HttpMethod.PUT, skills + "/" + UUID.randomUUID() + "/prerequisites/x", null), owner), 400, null);
        for (String body : List.of("{}", "{\"name\":null}", "{\"name\":\"  \"}", "{\"name\":\"\\u2003\"}", "{\"name\":\"\\u0000\"}",
                "{\"name\":5}", "{\"name\":\"A\",\"description\":\"\"}", "{\"name\":\"A\",\"description\":\" \\t\"}", "{\"name\":\"A\",\"description\":\"\\u2003\"}",
                "{\"name\":\"A\",\"description\":7}", "{\"name\":\"A\",\"courseId\":\"" + SECOND + "\"}",
                "{\"name\":\"A\",\"id\":\"" + UUID.randomUUID() + "\"}", "[]", "null", "\"A\"", "{\"name\":\"A\"", "",
                "{\"name\":\"A\"} {\"name\":\"B\"}", "{\"name\":\"A\"}x", "{\"name\":[\"A\"]}", "{\"name\":{\"text\":\"A\"}}")) {
            assertProblem(call(new Operation(HttpMethod.POST, skills, body), owner), 400, null);
        }
        MvcResult text = mvc.perform(request(HttpMethod.POST, skills).header("Authorization", owner)
                .contentType(MediaType.TEXT_PLAIN).content("{\"name\":\"A\"}")).andReturn();
        assertEquals(415, text.getResponse().getStatus());
        assertEquals(0, graphRows());

        JsonNode hidden = json(call(new Operation(HttpMethod.POST, "/api/courses/" + HIDDEN + "/skills", "{\"name\":\"Draft skill\"}"), owner), 201);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.skills WHERE id = ? AND course_id = ?", Integer.class,
                UUID.fromString(hidden.get("id").asString()), HIDDEN));
        assertProblem(call(new Operation(HttpMethod.GET, "/api/courses/" + UUID.randomUUID() + "/skill-graph", null), owner), 404, null);
    }

    @Test void aRepeatedBodyMemberIsAMalformedClosedObjectAndWritesNothing() throws Exception {
        String owner = bearer(OWNER), skills = "/api/courses/" + COURSE + "/skills";
        UUID existing = skillRow(COURSE, "Existing");
        for (String body : List.of("{\"name\":\"First\",\"name\":\"Second\"}",
                "{\"name\":\"A\",\"description\":null,\"description\":\"B\"}", "{\"name\":\"A\",\"name\":null}")) {
            assertProblem(call(new Operation(HttpMethod.POST, skills, body), owner), 400, null);
            assertProblem(call(new Operation(HttpMethod.PUT, skills + "/" + existing, body), owner), 400, null);
        }
        assertEquals("Existing", jdbc.queryForObject("SELECT name FROM public.skills WHERE id = ?", String.class, existing));
        assertEquals(1, graphRows());
    }

    @Test void unpairedSurrogatesAreBadRequestsWithoutEffectsAndSupplementaryTextRoundTripsUnchanged() throws Exception {
        String owner = bearer(OWNER), skills = "/api/courses/" + COURSE + "/skills";
        UUID existing = skillRow(COURSE, "Existing");
        jdbc.update("UPDATE public.skills SET description = 'Kept' WHERE id = ?", existing);
        String before = graphState();
        // JSON escapes reach Java as unpaired UTF-16 surrogates; the driver would have stored them as '?'.
        for (String unpaired : List.of("\\ud800", "\\udc00", "Loops \\ud83d", "\\ude00 Loops", "\\ud83d\\ude00\\ud800")) {
            for (String body : List.of("{\"name\":\"" + unpaired + "\"}",
                    "{\"name\":\"Loops\",\"description\":\"" + unpaired + "\"}")) {
                assertProblem(call(new Operation(HttpMethod.POST, skills, body), owner), 400, null);
                assertProblem(call(new Operation(HttpMethod.PUT, skills + "/" + existing, body), owner), 400, null);
            }
        }
        assertEquals(before, graphState(), "no INSERT or UPDATE reached PostgreSQL");
        assertFalse(rootLockedElsewhere(COURSE));

        // Valid surrogate pairs (supplementary characters) and a non-space Mongolian vowel separator persist exactly.
        String name = "Graphs 𝔸 😀", description = "᠎ pairs 􏿿";
        JsonNode created = json(call(new Operation(HttpMethod.POST, skills,
                "{\"name\":\"Graphs \\ud835\\udd38 \\ud83d\\ude00\",\"description\":\"\\u180e pairs \\udbff\\udfff\"}"), owner), 201);
        UUID createdId = UUID.fromString(created.get("id").asString());
        assertEquals(name, created.get("name").asString());
        assertEquals(description, created.get("description").asString());
        JsonNode replaced = json(call(new Operation(HttpMethod.PUT, skills + "/" + existing,
                "{\"name\":\"" + name + "\",\"description\":\"" + description + "\"}"), owner), 200);
        assertEquals(name, replaced.get("name").asString());
        assertEquals(description, replaced.get("description").asString());
        String utf8 = HexFormat.of().formatHex((name + "|" + description).getBytes(StandardCharsets.UTF_8));
        for (UUID skill : List.of(createdId, existing)) {
            assertEquals(utf8, jdbc.queryForObject("SELECT encode(convert_to(name || '|' || description, 'UTF8'), 'hex') "
                    + "FROM public.skills WHERE id = ?", String.class, skill), "persisted bytes equal the accepted text");
        }
        JsonNode graphRead = json(call(new Operation(HttpMethod.GET, "/api/courses/" + COURSE + "/skill-graph", null), owner), 200);
        assertEquals(2, graphRead.get("skills").size());
        for (JsonNode skill : graphRead.get("skills")) {
            assertEquals(name, skill.get("name").asString());
            assertEquals(description, skill.get("description").asString());
        }
    }

    @Test void aCorruptPersistedGraphFailsClosedWithASafeServerError() throws Exception {
        UUID a = skillRow(COURSE, "A"), b = skillRow(COURSE, "B");
        edgeRow(COURSE, a, b);
        edgeRow(COURSE, b, a);
        MvcResult read = call(new Operation(HttpMethod.GET, "/api/courses/" + COURSE + "/skill-graph", null), bearer(OWNER));
        assertProblem(read, 500, "An unexpected error occurred.");
        String body = read.getResponse().getContentAsString();
        assertFalse(body.contains(a.toString()) || body.contains("cycl") || body.contains("SQL"), body);
        assertProblem(call(new Operation(HttpMethod.PUT, "/api/courses/" + COURSE + "/skills/" + a + "/prerequisites/"
                + skillRow(COURSE, "C"), null), bearer(OWNER)), 500, null);
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM public.skill_prerequisites WHERE course_id = ?", Integer.class, COURSE));
    }

    // AD07 B1-O11: referenced skills and foreign keys.

    @Test void referencedSkillsConflictWithoutCascadeAndAnUnreferencedSkillIsDeleted() {
        UUID dependent = skillRow(COURSE, "Dependent"), prerequisite = skillRow(COURSE, "Prerequisite");
        UUID mapped = skillRow(COURSE, "Mapped"), questioned = skillRow(COURSE, "Questioned");
        UUID mastered = skillRow(COURSE, "Mastered"), free = skillRow(COURSE, "Free");
        UUID lesson = lesson(chapter(COURSE, 1), 1);
        edgeRow(COURSE, dependent, prerequisite);
        jdbc.update("INSERT INTO public.lesson_skills (id,lesson_id,skill_id) VALUES (?,?,?)", UUID.randomUUID(), lesson, mapped);
        jdbc.update("INSERT INTO public.questions (id,skill_id,prompt,kind,difficulty,options,correct_options) "
                + "VALUES (?,?,'Pick one','true_false','easy',ARRAY['True','False'],ARRAY[true,false])", UUID.randomUUID(), questioned);
        jdbc.update("INSERT INTO public.learner_skill_masteries (id,user_id,skill_id,state) VALUES (?,?,?,'learning')",
                UUID.randomUUID(), LEARNER, mastered);
        String before = graphState();

        for (UUID referenced : List.of(dependent, prerequisite, mapped, questioned, mastered)) {
            BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                    () -> as(OWNER, Role.INSTRUCTOR, () -> { graph.deleteSkill(COURSE, referenced); return null; }));
            assertEquals(409, refused.getStatus().value());
            assertFalse(rootLockedElsewhere(COURSE), "the refused unit rolled back and released the root");
        }
        assertEquals(before, graphState());
        as(OWNER, Role.INSTRUCTOR, () -> { graph.deleteSkill(COURSE, free); return null; });
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.skills WHERE id = ?", Integer.class, free));
        assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM public.skills WHERE course_id = ?", Integer.class, COURSE));
    }

    @Test void crossCourseReferencesAreConcealedAndWriteNothing() {
        UUID a = skillRow(COURSE, "A"), foreign = skillRow(SECOND, "Foreign");
        UUID lesson = lesson(chapter(COURSE, 1), 1), foreignLesson = lesson(chapter(SECOND, 1), 1);
        List<org.junit.jupiter.api.function.Executable> attempts = List.of(
                () -> graph.addPrerequisite(COURSE, a, foreign),
                () -> graph.addPrerequisite(COURSE, foreign, a),
                () -> graph.removePrerequisite(COURSE, a, foreign),
                () -> graph.mapLesson(COURSE, foreignLesson, a),
                () -> graph.mapLesson(COURSE, lesson, foreign),
                () -> graph.unmapLesson(COURSE, lesson, foreign),
                () -> graph.updateSkill(COURSE, foreign, "Renamed", null),
                () -> graph.deleteSkill(COURSE, foreign),
                () -> graph.requireSkillInCourse(COURSE, foreign));
        for (var attempt : attempts) {
            as(OWNER, Role.INSTRUCTOR, () -> assertThrows(ResourceNotFoundException.class, attempt));
        }
        assertEquals(List.of(), edgesElsewhere(COURSE));
        assertEquals(List.of(), mappingsElsewhere());
        assertEquals("Foreign", jdbc.queryForObject("SELECT name FROM public.skills WHERE id = ?", String.class, foreign));
    }

    // AD07 CT40.skill-tag producers (ADR005 §3.2) against real Course ownership.

    @Test void skillLookupBoundariesAdmitTheOwnerAndWorkInsideTheConsumersGuard() {
        UUID a = skillRow(COURSE, "A"), b = skillRow(COURSE, "B");
        skillRow(SECOND, "Elsewhere");
        List<UUID> expected = sortedText(a, b).stream().map(UUID::fromString).toList();
        assertEquals(expected, as(OWNER, Role.INSTRUCTOR, () -> graph.readSkillIds(COURSE)));
        assertEquals(List.of(), as(OWNER, Role.INSTRUCTOR, () -> graph.readSkillIds(HIDDEN)));
        as(OWNER, Role.INSTRUCTOR, () -> { graph.requireSkillInCourse(COURSE, a); return null; });
        as(OWNER, Role.INSTRUCTOR, () -> assertThrows(ResourceNotFoundException.class, () -> graph.requireSkillInCourse(COURSE, UUID.randomUUID())));
        as(OWNER, Role.INSTRUCTOR, () -> assertThrows(ResourceNotFoundException.class, () -> graph.readSkillIds(UUID.randomUUID())));
        as(OTHER, Role.INSTRUCTOR, () -> assertThrows(AccessDeniedException.class, () -> graph.readSkillIds(COURSE)));
        as(OTHER, Role.INSTRUCTOR, () -> assertThrows(ResourceNotFoundException.class, () -> graph.requireSkillInCourse(HIDDEN, a)));
        as(LEARNER, Role.LEARNER, () -> assertThrows(AccessDeniedException.class, () -> graph.requireSkillInCourse(COURSE, a)));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> graph.readSkillIds(COURSE));
        assertThrows(IllegalArgumentException.class, () -> graph.requireSkillInCourse(COURSE, null));

        // Assessment validates tags under its own guard: the lookups join that unit and read committed state.
        commitElsewhere("INSERT INTO public.skills (id,course_id,name) VALUES ('" + UUID.randomUUID() + "', ?, 'Late')", COURSE);
        List<UUID> inGuard = as(OWNER, Role.INSTRUCTOR, () -> service.withCourseConfigurationWrite(COURSE, () -> {
            graph.requireSkillInCourse(COURSE, b);
            return graph.readSkillIds(COURSE);
        }));
        assertEquals(3, inGuard.size());
        assertEquals(inGuard.stream().sorted(java.util.Comparator.comparing(UUID::toString)).toList(), inGuard);
    }

    // AD07 B1-O03/O04: Course-root serialization, independence and rollback on PostgreSQL.

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void reverseEdgesRaceOnTheCourseRootAndTheWaiterRecomputesAgainstTheCommittedWinner(boolean winnerCommits) throws Exception {
        UUID a = skillRow(COURSE, "A"), b = skillRow(COURSE, "B");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<Object> winner = workers.submit(() -> as(OWNER, Role.INSTRUCTOR,
                () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false).execute(status -> {
                    graph.addPrerequisite(COURSE, a, b);
                    entityManager.flush();
                    entered.countDown();
                    await(release);
                    if (!winnerCommits) throw new IllegalStateException("winner rolls back");
                    return null;
                })));
        await(entered);
        assertEquals(List.of(), edgesElsewhere(COURSE), "the winner's edge is not yet committed");
        Future<Object> waiter = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> {
            graph.addPrerequisite(COURSE, b, a);
            return null;
        }));
        awaitLockWaiters(1);
        assertFalse(waiter.isDone(), "the reverse edge waits for the Course root");
        release.countDown();
        if (winnerCommits) {
            winner.get(WAIT_SECONDS, TimeUnit.SECONDS);
            BusinessRuleException cycle = assertInstanceOf(BusinessRuleException.class, failureOf(waiter));
            assertEquals(409, cycle.getStatus().value());
            assertEquals(List.of(a + "->" + b), edgesElsewhere(COURSE));
        } else {
            failureOf(winner);
            waiter.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals(List.of(b + "->" + a), edgesElsewhere(COURSE));
        }
        GraphSnapshot finalGraph = as(OWNER, Role.INSTRUCTOR, () -> graph.readGraph(COURSE));
        assertEquals(1, finalGraph.prerequisites().size(), "the committed graph stays acyclic");
        assertFalse(rootLockedElsewhere(COURSE));
    }

    @Test void graphWritesOnIndependentCoursesDoNotShareTheGuard() throws Exception {
        UUID a = skillRow(COURSE, "A"), b = skillRow(COURSE, "B"), c = skillRow(SECOND, "C"), d = skillRow(SECOND, "D");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<Object> holder = workers.submit(() -> as(OWNER, Role.INSTRUCTOR,
                () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false).execute(status -> {
                    graph.addPrerequisite(COURSE, a, b);
                    entered.countDown();
                    await(release);
                    return null;
                })));
        await(entered);
        Future<Object> other = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> {
            graph.addPrerequisite(SECOND, c, d);
            graph.mapLesson(SECOND, lesson(chapter(SECOND, 1), 1), c);
            return null;
        }));
        other.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertFalse(holder.isDone(), "the first Course is still held while the second committed");
        assertEquals(List.of(c + "->" + d), edgesElsewhere(SECOND));
        assertTrue(rootLockedElsewhere(COURSE));
        release.countDown();
        holder.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertEquals(List.of(a + "->" + b), edgesElsewhere(COURSE));
    }

    @Test void aFailureInsideTheUnitRollsBackEveryGraphEffectAndPersistsNothing() {
        UUID a = skillRow(COURSE, "A"), foreignLesson = lesson(chapter(SECOND, 1), 1), lesson = lesson(chapter(COURSE, 1), 1);
        String before = graphState();
        assertThrows(ResourceNotFoundException.class, () -> as(OWNER, Role.INSTRUCTOR,
                () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false).execute(status -> {
                    SkillReference created = graph.createSkill(COURSE, "Temporary", "Rolled back");
                    graph.updateSkill(COURSE, a, "Renamed", null);
                    graph.addPrerequisite(COURSE, created.id(), a);
                    graph.mapLesson(COURSE, lesson, a);
                    graph.mapLesson(COURSE, foreignLesson, a); // Course ancestry refuses: the whole unit rolls back
                    return null;
                })));
        assertEquals(before, graphState());
        assertFalse(rootLockedElsewhere(COURSE));

        SkillReference kept = as(OWNER, Role.INSTRUCTOR, () -> graph.createSkill(COURSE, "Kept", null));
        as(OWNER, Role.INSTRUCTOR, () -> { graph.addPrerequisite(COURSE, kept.id(), a); graph.mapLesson(COURSE, lesson, a); return null; });
        GraphSnapshot committed = as(OWNER, Role.INSTRUCTOR, () -> graph.readGraph(COURSE));
        assertEquals(List.of(new PrerequisiteReference(kept.id(), a)), committed.prerequisites());
        assertEquals(List.of(new MappingReference(lesson, a)), committed.mappings());
    }

    @Test void theOwnerReadIsOneSnapshotEvenWhenAWriterCommitsBetweenItsStatements() {
        UUID a = skillRow(COURSE, "A"), late = UUID.randomUUID();
        BEFORE_PREREQUISITES.set(() -> {
            commitSqlElsewhere("INSERT INTO public.skills (id,course_id,name) VALUES (?,?,'Late')", late, COURSE);
            commitSqlElsewhere("INSERT INTO public.skill_prerequisites (id,course_id,skill_id,prerequisite_skill_id) VALUES (?,?,?,?)",
                    UUID.randomUUID(), COURSE, late, a);
        });
        GraphSnapshot during = as(OWNER, Role.INSTRUCTOR, () -> graph.readGraph(COURSE));
        assertNull(BEFORE_PREREQUISITES.get(), "the concurrent commit ran between the read's statements");
        assertEquals(List.of(a), during.skills().stream().map(SkillReference::id).toList());
        assertEquals(List.of(), during.prerequisites());
        GraphSnapshot after = as(OWNER, Role.INSTRUCTOR, () -> graph.readGraph(COURSE));
        assertEquals(List.of(new PrerequisiteReference(late, a)), after.prerequisites());
    }

    // AD07 x CO06 CT40 projection (ADR005 §§3.1, 4.1, 4.3, 5): mapping ancestry on PostgreSQL.

    @Test void mappingsOfPublishedAndHiddenLessonsOfTheCourseAreReadAndAForeignLessonMappingFailsClosed() throws Exception {
        UUID chapter = chapter(COURSE, 1), published = lesson(chapter, 1), hidden = hiddenLesson(chapter, 2);
        UUID a = skillRow(COURSE, "A"), b = skillRow(COURSE, "B"), free = skillRow(COURSE, "Free");
        as(OWNER, Role.INSTRUCTOR, () -> { graph.mapLesson(COURSE, published, a); graph.mapLesson(COURSE, hidden, b); return null; });
        String owner = bearer(OWNER), base = "/api/courses/" + COURSE;
        JsonNode read = json(call(new Operation(HttpMethod.GET, base + "/skill-graph", null), owner), 200);
        assertEquals(2, read.get("mappings").size(), "the owner's graph includes the hidden Lesson's mapping");

        // Raw SQL is not a qualified writer: a mapping whose Lesson belongs to another Course is corrupt state.
        UUID foreignLesson = lesson(chapter(SECOND, 1), 1);
        jdbc.update("INSERT INTO public.lesson_skills (id,lesson_id,skill_id) VALUES (?,?,?)", UUID.randomUUID(), foreignLesson, a);
        String before = graphState();
        MvcResult corrupt = call(new Operation(HttpMethod.GET, base + "/skill-graph", null), owner);
        assertProblem(corrupt, 500, "An unexpected error occurred.");
        String body = corrupt.getResponse().getContentAsString();
        assertFalse(body.contains(foreignLesson.toString()) || body.contains(SECOND.toString()) || body.contains("Lesson"), body);
        assertProblem(call(new Operation(HttpMethod.PUT, base + "/skills/" + a + "/prerequisites/" + b, null), owner), 500, null);
        assertProblem(call(new Operation(HttpMethod.DELETE, base + "/skills/" + b + "/prerequisites/" + a, null), owner), 500, null);
        assertProblem(call(new Operation(HttpMethod.DELETE, base + "/skills/" + free, null), owner), 500, null);
        assertEquals(before, graphState(), "nothing was repaired, removed or written");
        assertFalse(rootLockedElsewhere(COURSE));
    }

    @Test void aLessonMovedOutsideTheCourseBehindTheApplicationMakesItsMappingCorrupt() throws Exception {
        UUID lesson = lesson(chapter(COURSE, 1), 1), a = skillRow(COURSE, "A"), foreignChapter = chapter(SECOND, 1);
        as(OWNER, Role.INSTRUCTOR, () -> { graph.mapLesson(COURSE, lesson, a); return null; });
        jdbc.update("UPDATE public.lessons SET chapter_id = ? WHERE id = ?", foreignChapter, lesson);
        assertThrows(IllegalStateException.class, () -> as(OWNER, Role.INSTRUCTOR, () -> graph.readGraph(COURSE)));
        assertProblem(call(new Operation(HttpMethod.GET, "/api/courses/" + COURSE + "/skill-graph", null), bearer(OWNER)), 500, null);
        assertEquals(List.of(lesson + "->" + a), mappingsElsewhere());
    }

    @Test void theOwnerReadProvesMappingsAgainstTheStructureOfTheSameSnapshot() {
        UUID chapter = chapter(COURSE, 1), lesson = lesson(chapter, 1), a = skillRow(COURSE, "A");
        UUID foreignChapter = chapter(SECOND, 1), late = UUID.randomUUID();
        as(OWNER, Role.INSTRUCTOR, () -> { graph.mapLesson(COURSE, lesson, a); return null; });
        BEFORE_PREREQUISITES.set(() -> {
            // Between the read's statements another connection appends a Lesson with a mapping and
            // moves the mapped Lesson out of the Course. The snapshot sees neither change.
            commitSqlElsewhere("INSERT INTO public.lessons (id,chapter_id,title,position,publication_status) "
                    + "VALUES (?,?,'Late',2,'hidden')", late, chapter);
            commitSqlElsewhere("INSERT INTO public.lesson_skills (id,lesson_id,skill_id) VALUES (?,?,?)", UUID.randomUUID(), late, a);
            commitSqlElsewhere("UPDATE public.lessons SET chapter_id = ? WHERE id = ?", foreignChapter, lesson);
        });
        GraphSnapshot during = as(OWNER, Role.INSTRUCTOR, () -> graph.readGraph(COURSE));
        assertNull(BEFORE_PREREQUISITES.get(), "the concurrent commits ran between the read's statements");
        assertEquals(List.of(new MappingReference(lesson, a)), during.mappings());
        assertThrows(IllegalStateException.class, () -> as(OWNER, Role.INSTRUCTOR, () -> graph.readGraph(COURSE)),
                "the committed state now holds a foreign-Course mapping and fails closed");
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void mappingALessonWaitsForTheCourseWriterCreatingItAndRereadsCommittedStructure(boolean creatorCommits) throws Exception {
        UUID chapter = chapter(COURSE, 1), a = skillRow(COURSE, "A");
        AtomicReference<UUID> created = new AtomicReference<>();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<Object> creator = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> service.withCourseConfigurationWrite(COURSE, () -> {
            created.set(structure.createCourseLesson(COURSE, chapter, "Concurrent", null).id());
            entered.countDown();
            await(release);
            if (!creatorCommits) throw new IllegalStateException("creator rolls back");
            return null;
        })));
        await(entered);
        Future<Object> mapper = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> {
            graph.mapLesson(COURSE, created.get(), a);
            return null;
        }));
        awaitLockWaiters(1);
        assertFalse(mapper.isDone(), "the mapping waits on the Course root held by the structural writer");
        release.countDown();
        if (creatorCommits) {
            creator.get(WAIT_SECONDS, TimeUnit.SECONDS);
            mapper.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals(List.of(created.get() + "->" + a), mappingsElsewhere());
            GraphSnapshot read = as(OWNER, Role.INSTRUCTOR, () -> graph.readGraph(COURSE));
            assertEquals(List.of(new MappingReference(created.get(), a)), read.mappings());
        } else {
            failureOf(creator);
            assertTrue(failureOf(mapper) instanceof ResourceNotFoundException, "a rolled-back Lesson is not in the Course");
            assertEquals(List.of(), mappingsElsewhere());
        }
    }

    // AS05 CT23.questions (ADR005 §§3.2, 5; ADR007 §4): the real CT40 producer inside CO14's guard.

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void taggingAQuestionWithASkillBeingDeletedWaitsAndRereadsTheCommittedGraph(boolean deleteCommits) throws Exception {
        UUID skill = skillRow(COURSE, "Doomed");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<Object> deleter = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> service.withCourseConfigurationWrite(COURSE, () -> {
            graph.deleteSkill(COURSE, skill);
            entered.countDown();
            await(release);
            if (!deleteCommits) throw new IllegalStateException("deleter rolls back");
            return null;
        })));
        await(entered);
        Future<Object> tagger = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> questionBank.create(COURSE, trueFalse(skill))));
        awaitLockWaiters(1);
        assertFalse(tagger.isDone(), "the tag check waits on the Course root held by the graph writer");
        release.countDown();
        if (deleteCommits) {
            deleter.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertTrue(failureOf(tagger) instanceof ResourceNotFoundException, "the deleted skill is no longer in the Course");
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.questions WHERE skill_id = ?", Integer.class, skill));
        } else {
            failureOf(deleter);
            tagger.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.questions WHERE skill_id = ?", Integer.class, skill));
        }
    }

    /** AS08 capture is not implemented; a separate connection inserts the AttemptAnswer reference. */
    @ParameterizedTest
    @CsvSource({"delete,true", "delete,false", "update,true", "update,false"})
    void anAttemptCaptureReferencingAQuestionFreezesItBeforeAWaitingReplaceOrDelete(String operation, boolean captureCommits)
            throws Exception {
        UUID skill = skillRow(COURSE, "Tagged"), question = questionRow(skill), attempt = attemptRow(quizRow(COURSE));
        Connection capture = dataSource.getConnection();
        held.add(capture);
        capture.setAutoCommit(false);
        try (PreparedStatement insert = capture.prepareStatement("INSERT INTO public.attempt_answers "
                + "(id,attempt_id,question_id,position,selected_options) VALUES (?,?,?,1,ARRAY[false,false])")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, attempt);
            insert.setObject(3, question);
            insert.executeUpdate();
        }
        Future<Object> writer = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> operation.equals("delete")
                ? questionBank.delete(COURSE, question)
                : questionBank.update(COURSE, question,
                        new QuestionInput(skill, "Changed", "true_false", "hard", List.of("True", "False"), List.of(false, true)))));
        awaitLockWaiters(1);
        assertFalse(writer.isDone(), "the question row lock waits for the uncommitted reference");
        if (captureCommits) capture.commit(); else capture.rollback();
        if (captureCommits) {
            BusinessRuleException frozen = assertInstanceOf(BusinessRuleException.class, failureOf(writer));
            assertEquals(409, frozen.getStatus().value());
            assertEquals("Pick one|easy", questionElsewhere(question));
        } else {
            writer.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals(operation.equals("delete") ? "none" : "Changed|hard", questionElsewhere(question));
        }
    }

    @Test void questionScopesOfIndependentCoursesStayApart() {
        UUID mine = skillRow(COURSE, "Mine"), theirs = skillRow(SECOND, "Theirs"), foreignQuestion = questionRow(theirs);
        assertThrows(ResourceNotFoundException.class, () -> as(OWNER, Role.INSTRUCTOR, () -> questionBank.create(COURSE, trueFalse(theirs))));
        assertThrows(ResourceNotFoundException.class, () -> as(OWNER, Role.INSTRUCTOR, () -> questionBank.read(COURSE, foreignQuestion)));
        assertThrows(ResourceNotFoundException.class, () -> as(OWNER, Role.INSTRUCTOR, () -> questionBank.delete(COURSE, foreignQuestion)));
        assertEquals(mine, as(OWNER, Role.INSTRUCTOR, () -> questionBank.create(COURSE, trueFalse(mine))).skillId());
        assertEquals(1L, as(OWNER, Role.INSTRUCTOR, () -> questionBank.list(COURSE, 0, 20)).totalElements());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.questions WHERE id = ?", Integer.class, foreignQuestion));
        assertFalse(rootLockedElsewhere(COURSE));
    }

    private static QuestionInput trueFalse(UUID skill) {
        return new QuestionInput(skill, "Pick one", "true_false", "easy", List.of("True", "False"), List.of(true, false));
    }

    private UUID questionRow(UUID skill) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.questions (id,skill_id,prompt,kind,difficulty,options,correct_options) "
                + "VALUES (?,?,'Pick one','true_false','easy',ARRAY['True','False'],ARRAY[true,false])", id, skill);
        return id;
    }

    private UUID quizRow(UUID course) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.quizzes (id,course_id,title,kind,time_limit_seconds,publication_status) "
                + "VALUES (?,?,'Draft','practice',60,'hidden')", id, course);
        return id;
    }

    private UUID attemptRow(UUID quiz) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.attempts (id,user_id,quiz_id,attempt_number,started_at,expires_at) "
                + "VALUES (?,?,?,1,now(),now() + interval '5 minutes')", id, LEARNER, quiz);
        return id;
    }

    /** Committed prompt and difficulty of a question, or "none", on a separate connection. */
    private String questionElsewhere(UUID question) {
        List<String> rows = queryElsewhere("SELECT prompt || '|' || difficulty FROM public.questions WHERE id = ?", question);
        return rows.isEmpty() ? "none" : rows.getFirst();
    }

    private static void assertCommitRefused(org.junit.jupiter.api.function.Executable unit) {
        RuntimeException refused = assertThrows(RuntimeException.class, unit);
        assertTrue(refused instanceof UnexpectedRollbackException || refused instanceof IllegalStateException,
                () -> "unexpected refusal " + refused);
    }

    private record Operation(HttpMethod method, String path, String body) { }

    private static List<Operation> operations(UUID course, UUID skill, UUID other, UUID lesson) {
        String base = "/api/courses/" + course;
        String body = "{\"name\":\"Probe\"}";
        return List.of(
                new Operation(HttpMethod.GET, base + "/skill-graph", null),
                new Operation(HttpMethod.POST, base + "/skills", body),
                new Operation(HttpMethod.PUT, base + "/skills/" + skill, body),
                new Operation(HttpMethod.DELETE, base + "/skills/" + skill, null),
                new Operation(HttpMethod.PUT, base + "/skills/" + skill + "/prerequisites/" + other, null),
                new Operation(HttpMethod.DELETE, base + "/skills/" + skill + "/prerequisites/" + other, null),
                new Operation(HttpMethod.PUT, base + "/lessons/" + lesson + "/skills/" + skill, null),
                new Operation(HttpMethod.DELETE, base + "/lessons/" + lesson + "/skills/" + skill, null));
    }

    private MvcResult call(Operation operation, String authorization) throws Exception {
        MockHttpServletRequestBuilder builder = request(operation.method(), java.net.URI.create(operation.path()));
        if (authorization != null) builder.header("Authorization", authorization);
        if (operation.body() != null) builder.contentType(MediaType.APPLICATION_JSON).content(operation.body());
        return mvc.perform(builder).andReturn();
    }

    /** ADR014: an access JWT is bound to an unrevoked refresh session of the same account. */
    private String bearer(UUID user) {
        UUID session = UUID.randomUUID();
        jdbc.update("INSERT INTO public.refresh_sessions (id,user_id,created_at,expires_at) VALUES (?,?,now(),now() + interval '1 hour')",
                session, user);
        return "Bearer " + tokens.createToken(user, session);
    }

    private static JsonNode json(MvcResult result, int status) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertEquals(status, result.getResponse().getStatus(), body);
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(result.getResponse().getContentType())));
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static void noContent(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertEquals(204, result.getResponse().getStatus(), body);
        assertEquals("", body);
    }

    private static void assertProblem(MvcResult result) throws Exception {
        assertEquals("application/problem+json", MediaType.parseMediaType(result.getResponse().getContentType()).getType() + "/"
                + MediaType.parseMediaType(result.getResponse().getContentType()).getSubtype());
        JsonNode problem = JSON.readTree(result.getResponse().getContentAsString());
        assertEquals(result.getResponse().getStatus(), problem.get("status").asInt());
    }

    private static void assertProblem(MvcResult result, int status, String detail) throws Exception {
        assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertProblem(result);
        if (detail != null) assertEquals(detail, JSON.readTree(result.getResponse().getContentAsString()).get("detail").asString());
    }

    private UUID skillRow(UUID course, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.skills (id,course_id,name) VALUES (?,?,?)", id, course, name);
        return id;
    }

    private void edgeRow(UUID course, UUID skill, UUID prerequisite) {
        jdbc.update("INSERT INTO public.skill_prerequisites (id,course_id,skill_id,prerequisite_skill_id) VALUES (?,?,?,?)",
                UUID.randomUUID(), course, skill, prerequisite);
    }

    private static List<String> sortedText(UUID... ids) {
        return Arrays.stream(ids).map(UUID::toString).sorted().toList();
    }

    /** Committed edges of one Course, read on a separate connection. */
    private List<String> edgesElsewhere(UUID course) {
        return queryElsewhere("SELECT skill_id || '->' || prerequisite_skill_id FROM public.skill_prerequisites "
                + "WHERE course_id = ? ORDER BY 1", course);
    }

    private List<String> mappingsElsewhere() {
        return queryElsewhere("SELECT m.lesson_id || '->' || m.skill_id FROM public.lesson_skills m JOIN public.skills s "
                + "ON s.id = m.skill_id WHERE s.course_id IN (?, '" + SECOND + "', '" + HIDDEN + "') ORDER BY 1", COURSE);
    }

    private List<String> queryElsewhere(String sql, UUID parameter) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement read = connection.prepareStatement(sql)) {
            read.setObject(1, parameter);
            List<String> rows = new ArrayList<>();
            try (ResultSet result = read.executeQuery()) {
                while (result.next()) rows.add(result.getString(1));
            }
            return rows;
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private int graphRows() {
        return jdbc.queryForObject("SELECT (SELECT count(*) FROM public.skills WHERE course_id IN (?,?,?)) "
                + "+ (SELECT count(*) FROM public.skill_prerequisites WHERE course_id IN (?,?,?)) "
                + "+ (SELECT count(*) FROM public.lesson_skills)", Integer.class, COURSE, SECOND, HIDDEN, COURSE, SECOND, HIDDEN);
    }

    /** Every skill-graph value of the guarded Course plus its foreign-key referrers, on a fresh connection. */
    private String graphState() {
        return queryElsewhere("SELECT concat_ws('|', "
                + "(SELECT string_agg(concat_ws(',', id, name, description), ';' ORDER BY id) FROM public.skills WHERE course_id = ?), "
                + "(SELECT string_agg(concat_ws(',', skill_id, prerequisite_skill_id), ';' ORDER BY 1) FROM public.skill_prerequisites "
                + "WHERE course_id = '" + COURSE + "'), "
                + "(SELECT string_agg(concat_ws(',', lesson_id, skill_id), ';' ORDER BY 1) FROM public.lesson_skills), "
                + "(SELECT count(*) FROM public.questions), (SELECT count(*) FROM public.learner_skill_masteries))", COURSE).getFirst();
    }

    private void commitSqlElsewhere(String sql, Object... parameters) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement update = connection.prepareStatement(sql)) {
            connection.setAutoCommit(true);
            for (int index = 0; index < parameters.length; index++) update.setObject(index + 1, parameters[index]);
            assertEquals(1, update.executeUpdate());
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
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

    private UUID hiddenLesson(UUID chapter, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.lessons (id,chapter_id,title,position,publication_status) VALUES (?,?,?,?,'hidden')",
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
