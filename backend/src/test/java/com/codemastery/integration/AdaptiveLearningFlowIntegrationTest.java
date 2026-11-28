package com.codemastery.integration;

import com.codemastery.modules.adaptive.application.mastery.PlacementDiagnosticService;
import com.codemastery.modules.adaptive.application.mastery.MasteryQueryService;
import com.codemastery.modules.course.application.service.LessonProgressService;
import com.codemastery.modules.course.application.service.LessonProgressService.LearnerCourseState;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.DecisionContext;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.LessonInput;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionService;
import com.codemastery.modules.adaptive.application.event.RecommendationCreatedEvent;
import com.codemastery.modules.adaptive.application.event.RecommendationCreatedEvent.CreatedRecommendation;
import com.codemastery.modules.adaptive.application.recommendation.RecommendationReasonBuilder;
import com.codemastery.modules.adaptive.application.recommendation.RecommendationService;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision.Recommendation;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService.GraphSnapshot;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService.MappingReference;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService.PrerequisiteReference;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService.SkillReference;
import com.codemastery.modules.assessment.application.service.QuizService;
import com.codemastery.modules.assessment.application.service.QuizService.QuizInput;
import com.codemastery.modules.assessment.domain.model.Quiz;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.infrastructure.security.AuthenticatedUser;
import com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider;
import com.codemastery.modules.course.application.service.CourseManagementService;
import com.codemastery.modules.course.application.service.CourseManagementService.AdaptiveConfiguration;
import com.codemastery.modules.course.application.service.CourseManagementService.ConfigurationBusy;
import com.codemastery.modules.assessment.application.service.QuestionBankService;
import com.codemastery.modules.assessment.application.service.QuestionBankService.QuestionInput;
import com.codemastery.modules.assessment.application.service.QuestionBankService.QuestionView;
import com.codemastery.common.api.PageResponse;
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
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
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
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * <p>AD14 (ADR011 §§7, 9, B6-R09; ADR012 §9.4 CT29): recommendation history rows and the
 * recommendation-created event of one run commit or roll back with the caller's unit, the event
 * reaches an after-commit subscriber only after commit, a failing subscriber cannot undo the
 * committed history, and de-duplication and the strictly-later clock hold across committed runs.
 * The quiz listener orchestration (AD13) and the history reads (AD16) are not implemented; these
 * cases read committed history with SQL as those callers would.
 *
 * <p>AD11 practice recommendation (ADR013 §§19.3, 19.4, 19.8 B8-P01–P09): the production
 * {@code GET /api/courses/{courseId}/lessons/{lessonId}/practice-recommendation} through the security
 * chain over real Course admission (CT33 {@code readLearnerState}), Adaptive skills, mappings and
 * stored mastery (AD16 read) and Assessment's practice quiz profiles (AS07), in one read-only
 * REPEATABLE READ snapshot that writes nothing and needs only runtime-matrix SELECT grants.
 *
 * <p>The context is closed after this class so its connection pool does not count against
 * PostgreSQL's connection limit while later suites run.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(AdaptiveLearningFlowIntegrationTest.RecommendationEventProbe.class)
class AdaptiveLearningFlowIntegrationTest extends AbstractIntegrationTest {
    private static final UUID OWNER = id("owner"), OTHER = id("other"), LEARNER = id("learner"), ADMIN = id("admin");
    private static final UUID COURSE = id("course"), SECOND = id("second"), HIDDEN = id("hidden");
    private static final long WAIT_SECONDS = 20;
    private static final String KEY = key();

    /** Runs once on the reading thread immediately before its prerequisite query is prepared. */
    private static final ThreadLocal<Runnable> BEFORE_PREREQUISITES = new ThreadLocal<>();
    /** Runs once on the listing thread immediately before its ordered question page query is prepared. */
    private static final ThreadLocal<Runnable> BEFORE_QUESTION_PAGE = new ThreadLocal<>();
    /** Runs once on the listing thread immediately before its ordered quiz page query is prepared. */
    private static final ThreadLocal<Runnable> BEFORE_QUIZ_PAGE = new ThreadLocal<>();
    /** Runs once on the request thread immediately before the AD16 mastery state query is prepared. */
    private static final ThreadLocal<Runnable> BEFORE_MASTERY_READ = new ThreadLocal<>();
    /** Records Hibernate-prepared SQL on the current thread while set. */
    private static final ThreadLocal<List<String>> RECORDED_SQL = new ThreadLocal<>();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired CourseManagementService service;
    @Autowired QuizService quizService;
    @Autowired QuizRepository quizRepository;
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
    @Autowired RecommendationService recommendationService;
    @Autowired PlacementDiagnosticService placement;
    @Autowired MasteryQueryService masteryQueries;
    @Autowired LessonProgressService learnerStates;
    @Autowired AdaptiveDecisionService adaptiveDecisionService;
    @Autowired RecommendationEventProbe recommendationEvents;

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
            Runnable pageChange = BEFORE_QUESTION_PAGE.get();
            if (pageChange != null && sql.contains("from questions") && sql.contains("order by")) {
                BEFORE_QUESTION_PAGE.remove();
                pageChange.run();
            }
            List<String> recorded = RECORDED_SQL.get();
            if (recorded != null) recorded.add(sql);
            Runnable masteryChange = BEFORE_MASTERY_READ.get();
            if (masteryChange != null && sql.contains("FROM public.learner_skill_masteries")) {
                BEFORE_MASTERY_READ.remove();
                masteryChange.run();
            }
            Runnable quizPageChange = BEFORE_QUIZ_PAGE.get();
            if (quizPageChange != null && sql.contains("FROM public.quizzes") && sql.contains("ORDER BY title COLLATE")) {
                BEFORE_QUIZ_PAGE.remove();
                quizPageChange.run();
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
        BEFORE_QUESTION_PAGE.remove();
        BEFORE_QUIZ_PAGE.remove();
        BEFORE_MASTERY_READ.remove();
        RECORDED_SQL.remove();
        for (UUID course : jdbc.queryForList("SELECT id FROM public.courses WHERE instructor_id IN (?,?)", UUID.class, OWNER, OTHER)) {
            purgeAssessmentAndGraph(course);
        }
        recommendationEvents.reset();
        jdbc.update("DELETE FROM public.path_recommendations WHERE enrollment_id IN (SELECT id FROM public.enrollments "
                + "WHERE user_id IN (?,?,?,?) OR course_id IN (?,?,?))", OWNER, OTHER, LEARNER, ADMIN, COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.lesson_progress WHERE enrollment_id IN (SELECT id FROM public.enrollments WHERE user_id IN (?,?,?,?))",
                OWNER, OTHER, LEARNER, ADMIN);
        jdbc.update("DELETE FROM public.enrollments WHERE user_id IN (?,?,?,?)", OWNER, OTHER, LEARNER, ADMIN);
        for (UUID course : jdbc.queryForList("SELECT id FROM public.courses WHERE instructor_id IN (?,?) AND id NOT IN (?,?,?)",
                UUID.class, OWNER, OTHER, COURSE, SECOND, HIDDEN)) {
            jdbc.update("DELETE FROM public.learning_materials WHERE lesson_id IN (SELECT l.id FROM public.lessons l "
                    + "JOIN public.chapters ch ON ch.id = l.chapter_id WHERE ch.course_id = ?)", course);
            jdbc.update("DELETE FROM public.lessons WHERE chapter_id IN (SELECT id FROM public.chapters WHERE course_id = ?)", course);
            jdbc.update("DELETE FROM public.chapters WHERE course_id = ?", course);
            jdbc.update("DELETE FROM public.courses WHERE id = ?", course);
        }
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

    // CO06 + AD07 + AS05 + AS06 (ADR005 §§3, 5; ADR007 §§4–7): the integrated authoring flow over HTTP.

    @Test void anInstructorAuthorsStructureGraphQuestionsAndAQuizThatAnEnrolledLearnerSees() throws Exception {
        String owner = bearer(OWNER), courses = "/api/instructor/courses";
        UUID course = idOf(json(call(new Operation(HttpMethod.POST, courses,
                "{\"title\":\"Integrated\",\"description\":\"Cross-PR flow\",\"prerequisiteDescription\":null}"), owner), 201));
        UUID chapter = idOf(json(call(new Operation(HttpMethod.POST, courses + "/" + course + "/chapters",
                "{\"title\":\"Basics\"}"), owner), 201));
        UUID lesson = idOf(json(call(new Operation(HttpMethod.POST, courses + "/" + course + "/chapters/" + chapter + "/lessons",
                "{\"title\":\"Variables\",\"contentMarkdown\":\"# Variables\"}"), owner), 201));
        json(call(new Operation(HttpMethod.PUT, courses + "/" + course + "/lessons/" + lesson + "/publication",
                "{\"publicationStatus\":\"published\"}"), owner), 200);

        String base = "/api/courses/" + course;
        UUID skill = idOf(json(call(new Operation(HttpMethod.POST, base + "/skills", "{\"name\":\"Variables\"}"), owner), 201));
        noContent(call(new Operation(HttpMethod.PUT, base + "/lessons/" + lesson + "/skills/" + skill, null), owner));
        JsonNode graphRead = json(call(new Operation(HttpMethod.GET, base + "/skill-graph", null), owner), 200);
        assertEquals(lesson.toString(), graphRead.get("mappings").get(0).get("lessonId").asString(),
                "the CO06 Lesson passes the CT40 structural check of the graph read");

        String question = "{\"skillId\":\"" + skill + "\",\"prompt\":\"x = 1; x += 1; x?\",\"kind\":\"single_choice\","
                + "\"difficulty\":\"easy\",\"options\":[\"1\",\"2\"],\"correctOptions\":[false,true]}";
        UUID questionId = idOf(json(call(new Operation(HttpMethod.POST, base + "/questions", question), owner), 201));
        String quiz = "{\"title\":\"Check\",\"kind\":\"practice\",\"timeLimitSeconds\":300,\"questionIds\":[\"" + questionId + "\"]}";
        JsonNode composed = json(call(new Operation(HttpMethod.POST, base + "/quizzes", quiz), owner), 201);
        UUID quizId = idOf(composed);
        assertEquals(questionId.toString(), composed.get("questions").get(0).get("question").get("id").asString());

        // Foreign references, unauthorized actors and referenced deletes are refused without effects.
        UUID foreignSkill = skillRow(SECOND, "Foreign"), foreignQuestion = questionRow(foreignSkill);
        assertProblem(call(new Operation(HttpMethod.POST, base + "/questions",
                question.replace(skill.toString(), foreignSkill.toString())), owner), 404, null);
        assertProblem(call(new Operation(HttpMethod.POST, base + "/quizzes",
                quiz.replace(questionId.toString(), foreignQuestion.toString())), owner), 404, null);
        assertProblem(call(new Operation(HttpMethod.PUT, base + "/lessons/" + lesson + "/skills/" + foreignSkill, null), owner), 404, null);
        String other = bearer(OTHER);
        assertEquals(404, call(new Operation(HttpMethod.POST, base + "/questions", question), other).getResponse().getStatus(),
                "the hidden Course is concealed from a non-owner");
        assertEquals(403, call(new Operation(HttpMethod.POST, base + "/quizzes", quiz), bearer(LEARNER)).getResponse().getStatus());
        assertProblem(call(new Operation(HttpMethod.DELETE, base + "/skills/" + skill, null), owner), 409, null);
        assertProblem(call(new Operation(HttpMethod.DELETE, base + "/questions/" + questionId, null), owner), 409, null);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.questions q JOIN public.skills s ON s.id = q.skill_id "
                + "WHERE s.course_id = ?", Integer.class, course));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.quizzes WHERE course_id = ?", Integer.class, course));

        json(call(new Operation(HttpMethod.PUT, base + "/quizzes/" + quizId + "/publication",
                "{\"publicationStatus\":\"published\"}"), owner), 200);
        json(call(new Operation(HttpMethod.PUT, courses + "/" + course + "/publication",
                "{\"publicationStatus\":\"published\"}"), owner), 200);
        String learner = bearer(LEARNER);
        assertEquals(200, call(new Operation(HttpMethod.PUT, base + "/membership", null), learner).getResponse().getStatus());
        MvcResult listed = call(new Operation(HttpMethod.GET, base + "/quizzes", null), learner);
        JsonNode page = json(listed, 200);
        assertEquals(1, page.get("content").size());
        assertEquals(quizId.toString(), page.get("content").get(0).get("id").asString());
        assertEquals(1, page.get("content").get(0).get("questionCount").asInt());
        String body = listed.getResponse().getContentAsString();
        assertFalse(body.contains("correctOptions") || body.contains("x += 1"), body);

        // Published and then attempted quizzes stay frozen even after hiding.
        assertProblem(call(new Operation(HttpMethod.PUT, base + "/quizzes/" + quizId, quiz), owner), 409, null);
        json(call(new Operation(HttpMethod.PUT, base + "/quizzes/" + quizId + "/publication",
                "{\"publicationStatus\":\"hidden\"}"), owner), 200);
        jdbc.update("INSERT INTO public.attempts (id,user_id,quiz_id,attempt_number,started_at,expires_at) "
                + "VALUES (?,?,?,1,now(),now() + interval '5 minutes')", UUID.randomUUID(), LEARNER, quizId);
        assertProblem(call(new Operation(HttpMethod.PUT, base + "/quizzes/" + quizId, quiz), owner), 409, null);
        assertProblem(call(new Operation(HttpMethod.DELETE, base + "/quizzes/" + quizId, null), owner), 409, null);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void composingAQuizWithAQuestionBeingDeletedWaitsAndRereadsTheCommittedBank(boolean deleteCommits) throws Exception {
        UUID question = questionRow(skillRow(COURSE, "Tagged"));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<Object> deleter = workers.submit(() -> as(OWNER, Role.INSTRUCTOR, () -> service.withCourseConfigurationWrite(COURSE, () -> {
            questionBank.delete(COURSE, question);
            entered.countDown();
            await(release);
            if (!deleteCommits) throw new IllegalStateException("deleter rolls back");
            return null;
        })));
        await(entered);
        Future<Object> composer = workers.submit(() -> as(OWNER, Role.INSTRUCTOR,
                () -> quizService.create(COURSE, new QuizInput("Race", "practice", 60, List.of(question)))));
        awaitLockWaiters(1);
        assertFalse(composer.isDone(), "composition waits on the Course root held by the question writer");
        release.countDown();
        if (deleteCommits) {
            deleter.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertTrue(failureOf(composer) instanceof ResourceNotFoundException, "the deleted question is no longer selectable");
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.quizzes WHERE course_id = ?", Integer.class, COURSE));
        } else {
            failureOf(deleter);
            composer.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.quiz_questions WHERE question_id = ?", Integer.class, question));
        }
    }

    @Test void aQuizCannotComposeAQuestionOfAnotherCourse() {
        UUID theirs = skillRow(SECOND, "Theirs"), foreignQuestion = questionRow(theirs);
        assertThrows(ResourceNotFoundException.class, () -> as(OWNER, Role.INSTRUCTOR,
                () -> quizService.create(COURSE, new QuizInput("Mixed", "practice", 60, List.of(foreignQuestion)))));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.quizzes WHERE course_id IN (?,?)", Integer.class, COURSE, SECOND));
        assertFalse(rootLockedElsewhere(COURSE));
    }

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

    /**
     * B3-O04 fault injection on the real schema, over HTTP: a test-only trigger fails each quiz write
     * part-way through. Create and replace fail on the second new member, after the old membership was
     * deleted and the first new row written; delete fails on the quiz row after its membership rows
     * were deleted; publication fails on the quiz row update. Each is a safe 500 that names no
     * internal detail, and the whole unit rolls back: no new quiz, the stored definition, membership
     * and positions exactly as before, and the Course root released.
     */
    @ParameterizedTest @ValueSource(strings = {"create", "update", "delete", "publish"})
    void aFaultPartWayThroughAQuizWriteRestoresTheQuizAndItsMembership(String operation) throws Exception {
        UUID skill = skillRow(COURSE, "Tagged");
        UUID first = questionRow(skill), second = questionRow(skill), third = questionRow(skill);
        UUID quiz = quizRow(COURSE);
        memberRow(quiz, first, 1);
        memberRow(quiz, second, 2);
        String before = quizStateElsewhere(quiz) + " " + membersElsewhere(quiz);
        assertEquals("Draft|practice|60|hidden|" + COURSE + " " + first + "@1," + second + "@2", before);

        String table = operation.equals("create") || operation.equals("update") ? "quiz_questions" : "quizzes";
        String when = switch (operation) {
            case "create", "update" -> "BEFORE INSERT ON public.quiz_questions FOR EACH ROW WHEN (NEW.question_id = '" + first + "'::uuid)";
            case "delete" -> "BEFORE DELETE ON public.quizzes FOR EACH ROW WHEN (OLD.id = '" + quiz + "'::uuid)";
            default -> "BEFORE UPDATE ON public.quizzes FOR EACH ROW WHEN (OLD.id = '" + quiz + "'::uuid)";
        };
        String base = "/api/courses/" + COURSE;
        String replacement = "{\"title\":\"Edited\",\"kind\":\"test\",\"timeLimitSeconds\":90,\"questionIds\":[\""
                + third + "\",\"" + first + "\"]}";
        Operation write = switch (operation) {
            case "create" -> new Operation(HttpMethod.POST, base + "/quizzes", replacement);
            case "update" -> new Operation(HttpMethod.PUT, base + "/quizzes/" + quiz, replacement);
            case "delete" -> new Operation(HttpMethod.DELETE, base + "/quizzes/" + quiz, null);
            default -> new Operation(HttpMethod.PUT, base + "/quizzes/" + quiz + "/publication", "{\"publicationStatus\":\"published\"}");
        };
        String owner = bearer(OWNER);
        jdbc.execute("CREATE FUNCTION public.as06_injected_fault() RETURNS trigger LANGUAGE plpgsql AS "
                + "$$ BEGIN RAISE EXCEPTION 'as06 injected fault'; END $$");
        try {
            jdbc.execute("CREATE TRIGGER as06_injected_fault " + when + " EXECUTE FUNCTION public.as06_injected_fault()");
            MvcResult failed = call(write, owner);
            assertProblem(failed, 500, null);
            assertEquals("no-store", failed.getResponse().getHeader("Cache-Control"));
            assertFalse(failed.getResponse().getContentAsString().contains("injected"), failed.getResponse().getContentAsString());
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS as06_injected_fault ON public." + table);
            jdbc.execute("DROP FUNCTION IF EXISTS public.as06_injected_fault()");
        }

        assertEquals(before, quizStateElsewhere(quiz) + " " + membersElsewhere(quiz), "the stored quiz and membership are restored");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.quizzes WHERE course_id = ?", Integer.class, COURSE));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM public.quiz_questions WHERE question_id IN (?,?,?)",
                Integer.class, first, second, third));
        assertFalse(rootLockedElsewhere(COURSE));
        // Without the fault the same request commits, so the rollback above came from the injected failure.
        json(call(write, owner), operation.equals("create") ? 201 : 200);
    }

    /**
     * ADR007 §7: admission, count, rows and membership counts of a quiz list come from one REPEATABLE
     * READ snapshot. Between the count and the ordered page query another connection commits a new
     * quiz that sorts first, publishes a hidden quiz and adds a member to the listed quiz; the page
     * shows none of it and agrees with its count, for the owner's list and the Learner's list.
     */
    @ParameterizedTest @ValueSource(strings = {"owner", "learner"})
    void aQuizListIsOneSnapshotEvenWhenAWriterCommitsBetweenItsCountAndItsPage(String audience) {
        UUID skill = skillRow(COURSE, "Listed"), first = questionRow(skill), second = questionRow(skill);
        UUID listed = quizRow(COURSE), hidden = quizRow(COURSE), late = UUID.randomUUID();
        jdbc.update("UPDATE public.quizzes SET title = 'B listed', publication_status = 'published' WHERE id = ?", listed);
        jdbc.update("UPDATE public.quizzes SET title = 'C hidden' WHERE id = ?", hidden);
        memberRow(listed, first, 1);
        jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status) VALUES (?,?,?,'active')",
                UUID.randomUUID(), LEARNER, COURSE);
        BEFORE_QUIZ_PAGE.set(() -> {
            commitSqlElsewhere("INSERT INTO public.quizzes (id,course_id,title,kind,time_limit_seconds,publication_status) "
                    + "VALUES (?,?,'A late','practice',60,'published')", late, COURSE);
            commitSqlElsewhere("UPDATE public.quizzes SET publication_status = 'published' WHERE id = ?", hidden);
            commitSqlElsewhere("INSERT INTO public.quiz_questions (id,quiz_id,question_id,position) VALUES (?,?,?,2)",
                    UUID.randomUUID(), listed, second);
        });
        Supplier<List<String>> list = () -> audience.equals("owner")
                ? rows(as(OWNER, Role.INSTRUCTOR, () -> quizService.listOwned(COURSE, 0, 20)),
                        quiz -> quiz.title() + ":" + quiz.questionCount() + ":" + quiz.questions().size())
                : rows(as(LEARNER, Role.LEARNER, () -> quizService.listForLearner(COURSE, 0, 20)),
                        quiz -> quiz.title() + ":" + quiz.questionCount());
        List<String> during = list.get();
        assertNull(BEFORE_QUIZ_PAGE.get(), "the concurrent commits ran between the list's count and page");
        // Owner rows are title:questionCount:questions, Learner rows title:questionCount (no definitions).
        assertEquals(audience.equals("owner") ? List.of("total=2", "B listed:1:1", "C hidden:0:0") : List.of("total=1", "B listed:1"),
                during);
        assertEquals(audience.equals("owner") ? List.of("total=3", "A late:0:0", "B listed:2:2", "C hidden:0:0")
                : List.of("total=3", "A late:0", "B listed:2", "C hidden:0"), list.get());
    }

    /** The page's total, then one formatted row per element in page order. */
    private static <T> List<String> rows(PageResponse<T> page, java.util.function.Function<T, String> row) {
        List<String> rows = new ArrayList<>();
        rows.add("total=" + page.totalElements());
        page.content().forEach(element -> rows.add(row.apply(element)));
        return rows;
    }

    private void memberRow(UUID quiz, UUID question, int position) {
        jdbc.update("INSERT INTO public.quiz_questions (id,quiz_id,question_id,position) VALUES (?,?,?,?)",
                UUID.randomUUID(), quiz, question, position);
    }

    /** Committed membership as {@code question@position} in position order, on a separate connection. */
    private String membersElsewhere(UUID quiz) {
        return queryElsewhere("SELECT string_agg(question_id::text || '@' || position, ',' ORDER BY position) "
                + "FROM public.quiz_questions WHERE quiz_id = ?", quiz).getFirst();
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

    /**
     * AS06 composition and AS08 capture are not implemented; a separate connection inserts the
     * QuizQuestion or AttemptAnswer reference and holds it uncommitted while the writer waits on the
     * question row. An AttemptAnswer freezes both operations; a QuizQuestion refuses only DELETE.
     */
    @ParameterizedTest
    @CsvSource({"attempt,delete,true", "attempt,delete,false", "attempt,update,true", "attempt,update,false",
            "quiz,delete,true", "quiz,delete,false", "quiz,update,true", "quiz,update,false"})
    void aConcurrentQuizOrAttemptReferenceDecidesAWaitingReplaceOrDelete(String reference, String operation,
            boolean captureCommits) throws Exception {
        UUID skill = skillRow(COURSE, "Tagged"), question = questionRow(skill), quiz = quizRow(COURSE);
        UUID attempt = attemptRow(quiz);
        Connection capture = dataSource.getConnection();
        held.add(capture);
        capture.setAutoCommit(false);
        try (PreparedStatement insert = capture.prepareStatement(reference.equals("attempt")
                ? "INSERT INTO public.attempt_answers (id,attempt_id,question_id,position,selected_options) "
                        + "VALUES (?,?,?,1,ARRAY[false,false])"
                : "INSERT INTO public.quiz_questions (id,quiz_id,question_id,position) VALUES (?,?,?,1)")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, reference.equals("attempt") ? attempt : quiz);
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
        boolean refused = captureCommits && (reference.equals("attempt") || operation.equals("delete"));
        if (refused) {
            BusinessRuleException conflict = assertInstanceOf(BusinessRuleException.class, failureOf(writer));
            assertEquals(409, conflict.getStatus().value());
            assertEquals("Pick one|easy", questionElsewhere(question));
        } else {
            writer.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals(operation.equals("delete") ? "none" : "Changed|hard", questionElsewhere(question));
        }
        assertFalse(rootLockedElsewhere(COURSE));
    }

    /**
     * ADR007 §7: scope, count and rows of one list come from one REPEATABLE READ snapshot. Between
     * the count and the ordered page query another connection commits a question of a Course skill
     * and a new skill with its own question; the page shows neither and agrees with its count.
     */
    @Test void aQuestionListIsOneSnapshotEvenWhenAWriterCommitsBetweenItsCountAndItsPage() {
        UUID skill = skillRow(COURSE, "Listed"), first = questionRow(skill);
        UUID late = UUID.randomUUID(), lateSkill = UUID.randomUUID(), lateSkillQuestion = UUID.randomUUID();
        String insertQuestion = "INSERT INTO public.questions (id,skill_id,prompt,kind,difficulty,options,correct_options) "
                + "VALUES (?,?,'Late','true_false','easy',ARRAY['True','False'],ARRAY[true,false])";
        BEFORE_QUESTION_PAGE.set(() -> {
            commitSqlElsewhere(insertQuestion, late, skill);
            commitSqlElsewhere("INSERT INTO public.skills (id,course_id,name) VALUES (?,?,'Late skill')", lateSkill, COURSE);
            commitSqlElsewhere(insertQuestion, lateSkillQuestion, lateSkill);
        });
        PageResponse<QuestionView> during = as(OWNER, Role.INSTRUCTOR, () -> questionBank.list(COURSE, 0, 20));
        assertNull(BEFORE_QUESTION_PAGE.get(), "the concurrent commits ran between the list's count and page");
        assertEquals(1L, during.totalElements());
        assertEquals(List.of(first), during.content().stream().map(QuestionView::id).toList());
        PageResponse<QuestionView> after = as(OWNER, Role.INSTRUCTOR, () -> questionBank.list(COURSE, 0, 20));
        assertEquals(3L, after.totalElements());
        assertEquals(Set.of(first, late, lateSkillQuestion), after.content().stream().map(QuestionView::id).collect(Collectors.toSet()));
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

    private static UUID idOf(JsonNode node) {
        return UUID.fromString(node.get("id").asString());
    }

    /** Removes one Course's Assessment and graph rows, children first, in committed statements. */
    private void purgeAssessmentAndGraph(UUID course) {
        String quizzes = "(SELECT id FROM public.quizzes WHERE course_id = ?)";
        jdbc.update("DELETE FROM public.attempt_answers WHERE attempt_id IN (SELECT id FROM public.attempts WHERE quiz_id IN " + quizzes + ")", course);
        jdbc.update("DELETE FROM public.attempts WHERE quiz_id IN " + quizzes, course);
        jdbc.update("DELETE FROM public.quiz_questions WHERE quiz_id IN " + quizzes, course);
        jdbc.update("DELETE FROM public.quizzes WHERE course_id = ?", course);
        String skills = "(SELECT id FROM public.skills WHERE course_id = ?)";
        jdbc.update("DELETE FROM public.lesson_skills WHERE skill_id IN " + skills, course);
        jdbc.update("DELETE FROM public.skill_prerequisites WHERE course_id = ?", course);
        jdbc.update("DELETE FROM public.questions WHERE skill_id IN " + skills, course);
        jdbc.update("DELETE FROM public.learner_skill_masteries WHERE skill_id IN " + skills, course);
        jdbc.update("DELETE FROM public.skills WHERE course_id = ?", course);
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

    // AD14: recommendation history and the CT29 recommendation-created event (ADR011 §§7, 9; ADR012 §9.4).

    @Test void aRunsHistoryRowsCommitWithTheUnitAndItsSingleEventIsDeliveredOnlyAfterCommit() {
        HistoryFixture fixture = historyFixture();
        MutableClock clock = new MutableClock(Instant.parse("2026-11-24T08:00:00.123456789Z"));
        // B6-R01 shape: S1, S2 Mastered, S3 Unknown, nothing completed -> skip L1, skip L2, continue L3.
        AdaptiveDecision decision = new AdaptiveDecisionEngine().decide(fixture.context(Map.of(
                fixture.s1(), MasteryState.MASTERED, fixture.s2(), MasteryState.MASTERED, fixture.s3(), MasteryState.UNKNOWN)));

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            decisions(clock).recordAndPublish(fixture.context(Map.of()), fixture.enrollment(), decision,
                    currentGuidance(fixture.enrollment()), latestInstant(fixture.enrollment()));
            assertTrue(recommendationEvents.received().isEmpty(), "Delivered before the unit committed");
        });

        Instant runInstant = Instant.parse("2026-11-24T08:00:00.123456Z");
        List<PathRecommendation> history = committedHistory(fixture.enrollment());
        // ADR011 §9 order: one instant, continue before skip, then skips by lowercase target UUID (random here).
        List<UUID> skipOrder = Stream.of(fixture.l1(), fixture.l2()).sorted(Comparator.comparing(UUID::toString)).toList();
        assertEquals(List.of(fixture.l3(), skipOrder.get(0), skipOrder.get(1)),
                history.stream().map(PathRecommendation::lessonId).toList());
        assertEquals(List.of(RecommendationType.CONTINUE, RecommendationType.SKIP, RecommendationType.SKIP),
                history.stream().map(PathRecommendation::recommendationType).toList());
        Map<UUID, String> expectedReasons = Map.of(fixture.l3(), RecommendationReasonBuilder.continueInCourseOrder(),
                fixture.l1(), "Tested out: you have mastered every skill in this lesson (\"Skill one\").",
                fixture.l2(), "Tested out: you have mastered every skill in this lesson (\"Skill two\").");
        history.forEach(row -> assertEquals(expectedReasons.get(row.lessonId()), row.reason()));
        assertTrue(history.stream().allMatch(row -> row.recommendedAt().equals(runInstant)));

        RecommendationCreatedEvent event = onlyRecommendationEvent();
        assertEquals(COURSE, event.courseId());
        assertEquals(LEARNER, event.learnerId());
        assertEquals(fixture.enrollment(), event.enrollmentId());
        assertEquals(runInstant, event.recommendedAt());
        Map<UUID, PathRecommendation> byId = new LinkedHashMap<>();
        history.forEach(row -> byId.put(row.id(), row));
        assertEquals(List.of(fixture.l1(), fixture.l2(), fixture.l3()),
                event.recommendations().stream().map(CreatedRecommendation::targetLessonId).toList());
        for (CreatedRecommendation created : event.recommendations()) {
            PathRecommendation row = byId.get(created.recommendationId());
            assertNotNull(row, "Event names a row that was not committed");
            assertEquals(row.recommendationType().getCode(), created.type());
            assertEquals(row.lessonId(), created.targetLessonId());
            assertEquals(row.reason(), created.reason());
        }
    }

    @Test void aRolledBackUnitLeavesNoHistoryAndNeverDeliversItsEvent() {
        HistoryFixture fixture = historyFixture();

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            decisions(new MutableClock(Instant.parse("2026-11-24T08:00:00Z"))).recordAndPublish(fixture.context(Map.of()),
                    fixture.enrollment(), decisionOf(skipDraft(fixture.l1()), continueDraft(fixture.l2())),
                    Optional.empty(), Optional.empty());
            assertEquals(2, history(fixture.enrollment()).size(), "The unit sees its own inserts");
            status.setRollbackOnly();
        });

        assertTrue(committedHistory(fixture.enrollment()).isEmpty());
        assertTrue(recommendationEvents.received().isEmpty());
    }

    @Test void aFailingAfterCommitSubscriberCannotRollBackTheCommittedHistory() {
        HistoryFixture fixture = historyFixture();
        recommendationEvents.failWith(new IllegalStateException("notification delivery failed"));

        new TransactionTemplate(transactions).executeWithoutResult(status ->
                decisions(new MutableClock(Instant.parse("2026-11-24T08:00:00Z"))).recordAndPublish(fixture.context(Map.of()),
                        fixture.enrollment(), decisionOf(remedialDraft(fixture.l1())), Optional.empty(), Optional.empty()));

        assertEquals(1, committedHistory(fixture.enrollment()).size());
        assertEquals(1, recommendationEvents.received().size(), "The subscriber ran after commit and failed");
    }

    @Test void unchangedGuidanceIsNotReinsertedAndEveryInsertingRunNeedsAStrictlyLaterInstant() {
        HistoryFixture fixture = historyFixture();
        Instant t1 = Instant.parse("2026-11-24T08:00:00.000001Z");
        MutableClock clock = new MutableClock(t1);
        AdaptiveDecision first = decisionOf(continueDraft(fixture.l1()));

        runInUnit(fixture, clock, first);
        UUID firstRow = committedHistory(fixture.enrollment()).getFirst().id();

        // B6-R09: same type, target and reason as the current recommendation -> no row and no event.
        clock.set(t1.plusSeconds(60));
        runInUnit(fixture, clock, first);
        assertEquals(List.of(firstRow), committedHistory(fixture.enrollment()).stream().map(PathRecommendation::id).toList());
        assertEquals(1, recommendationEvents.received().size());

        // A run that would insert with an unadvanced clock fails and rolls back every row of the run.
        clock.set(t1);
        AdaptiveDecision changed = decisionOf(skipDraft(fixture.l2()), continueDraft(fixture.l3()));
        assertThrows(IllegalStateException.class, () -> runInUnit(fixture, clock, changed));
        assertEquals(List.of(firstRow), committedHistory(fixture.enrollment()).stream().map(PathRecommendation::id).toList());
        assertEquals(1, recommendationEvents.received().size());

        // With an advanced clock the change inserts one run; its continue row is now current whatever the UUIDs.
        Instant t2 = t1.plusNanos(1_000);
        clock.set(t2);
        runInUnit(fixture, clock, changed);
        List<PathRecommendation> history = committedHistory(fixture.enrollment());
        assertEquals(List.of(fixture.l3(), fixture.l2(), fixture.l1()), history.stream().map(PathRecommendation::lessonId).toList());
        assertEquals(List.of(t2, t2, t1), history.stream().map(PathRecommendation::recommendedAt).toList());
        PathRecommendation current = new TransactionTemplate(transactions).execute(status ->
                currentGuidance(fixture.enrollment()).orElseThrow());
        assertEquals(history.getFirst().id(), current.id());
        assertEquals(2, recommendationEvents.received().size());
        assertEquals(List.of("skip", "continue"), recommendationEvents.received().getLast().recommendations().stream()
                .map(CreatedRecommendation::type).toList());
    }

    @Test void historyIsWrittenOnlyInsideTheCallersTransaction() {
        HistoryFixture fixture = historyFixture();

        assertThrows(IllegalTransactionStateException.class, () -> adaptiveDecisionService.recordAndPublish(
                fixture.context(Map.of()), fixture.enrollment(), decisionOf(continueDraft(fixture.l1())),
                Optional.empty(), Optional.empty()));
        assertThrows(IllegalTransactionStateException.class, () -> recommendationService.appendSelected(
                fixture.enrollment(), Instant.parse("2026-11-24T08:00:00Z"), List.of(continueDraft(fixture.l1()))));

        assertTrue(committedHistory(fixture.enrollment()).isEmpty());
        assertTrue(recommendationEvents.received().isEmpty());
    }

    @Test void aPersistenceFailureRollsBackTheEarlierRowsOfTheRunAndPublishesNothing() {
        HistoryFixture fixture = historyFixture();
        UUID missingLesson = UUID.randomUUID();

        assertThrows(DataIntegrityViolationException.class, () -> runInUnit(fixture,
                new MutableClock(Instant.parse("2026-11-24T08:00:00Z")),
                decisionOf(skipDraft(fixture.l1()), continueDraft(missingLesson))));

        assertTrue(committedHistory(fixture.enrollment()).isEmpty());
        assertTrue(recommendationEvents.received().isEmpty());
    }

    // AD11: practice recommendation REST (ADR013 §19.4; B8-P01–P09 through the production stack).

    @Test void b8TheRecommendationFollowsStoredMasteryAndTheUniformPublishedPracticeBankOverHttp() throws Exception {
        PracticeFixture f = practiceFixture();
        mastery(f.loops(), "mastered");
        UUID arraysEasy = question(f.arrays(), "easy"), arraysEasy2 = question(f.arrays(), "easy");
        UUID loopsEasy = question(f.loops(), "easy"), arraysHard = question(f.arrays(), "hard");
        UUID stringsEasy = question(f.strings(), "easy"), stringsHard = question(f.strings(), "hard");
        UUID alpha = practiceQuiz(COURSE, "Alpha easy", "practice", "published", loopsEasy, arraysEasy2);
        practiceQuiz(COURSE, "Bravo easy", "practice", "published", arraysEasy);
        // B8-P03 exclusions, each ordered before the winner by title.
        practiceQuiz(COURSE, "Aardvark mixed", "practice", "published", arraysEasy, arraysHard);
        practiceQuiz(COURSE, "Aaa hidden", "practice", "hidden", arraysEasy);
        practiceQuiz(COURSE, "Aab test", "test", "published", arraysEasy);
        practiceQuiz(COURSE, "Aab diagnostic", "diagnostic", "published", arraysEasy);
        practiceQuiz(COURSE, "Aac other skill", "practice", "published", arraysEasy, stringsEasy);
        practiceQuiz(COURSE, "Aad empty", "practice", "published");
        UUID foreignSkill = skillRow(SECOND, "Arrays");
        practiceQuiz(SECOND, "Aae other course", "practice", "published", question(foreignSkill, "easy"));
        String learner = bearer(LEARNER);

        // B8-P01/P02: Loops Mastered (Hard) and Arrays without a row (Unknown, Easy); the easier target governs.
        JsonNode body = practiceJson(practice(COURSE, f.l1(), learner));
        assertEquals(Set.of("courseId", "lessonId", "adaptiveEnabled", "recommendation"), fieldNames(body));
        assertEquals(COURSE.toString(), body.get("courseId").asString());
        assertEquals(f.l1().toString(), body.get("lessonId").asString());
        assertTrue(body.get("adaptiveEnabled").asBoolean());
        assertRecommended(body.get("recommendation"), alpha, "Alpha easy", "easy", "easy", false, f.arrays(), "Arrays",
                "unknown", "Recommended practice: Easy, because your skill \"Arrays\" is not yet assessed.");

        // B8-P04: Learning targets Medium; with only Easy and Hard uniform Quizzes the fallback is Easy.
        mastery(f.strings(), "learning");
        UUID stringsEasyQuiz = practiceQuiz(COURSE, "Strings easy", "practice", "published", stringsEasy);
        practiceQuiz(COURSE, "Strings hard", "practice", "published", stringsHard);
        assertRecommended(practiceJson(practice(COURSE, f.l2(), learner)).get("recommendation"), stringsEasyQuiz,
                "Strings easy", "easy", "medium", true, f.strings(), "Strings", "learning",
                "Recommended practice: Easy, because your skill \"Strings\" is Learning and no Medium practice quiz is "
                        + "available for this lesson.");

        // Recomputed on read from the stored rows: Arrays Mastered, Loops Learning -> Loops (Medium) governs.
        mastery(f.arrays(), "mastered");
        jdbc.update("UPDATE public.learner_skill_masteries SET state = 'learning' WHERE user_id = ? AND skill_id = ?",
                LEARNER, f.loops());
        UUID loopsMedium = practiceQuiz(COURSE, "Loops medium", "practice", "published", question(f.loops(), "medium"));
        assertRecommended(practiceJson(practice(COURSE, f.l1(), learner)).get("recommendation"), loopsMedium,
                "Loops medium", "medium", "medium", false, f.loops(), "Loops", "learning",
                "Recommended practice: Medium, because your skill \"Loops\" is Learning.");

        // B8-P06: a Lesson without mapped skills, then a disabled Course, has no recommendation.
        JsonNode unmapped = practiceJson(practice(COURSE, f.unmapped(), learner));
        assertTrue(unmapped.get("adaptiveEnabled").asBoolean());
        assertTrue(unmapped.has("recommendation") && unmapped.get("recommendation").isNull());
        jdbc.update("UPDATE public.courses SET adaptive_enabled = false WHERE id = ?", COURSE);
        JsonNode disabled = practiceJson(practice(COURSE, f.l1(), learner));
        assertFalse(disabled.get("adaptiveEnabled").asBoolean());
        assertTrue(disabled.has("recommendation") && disabled.get("recommendation").isNull());
    }

    @Test void b8P07AdmissionAndUnavailableLessonsFollowTheContractOrderWithoutReadingMasteryOrAssessment() throws Exception {
        PracticeFixture f = practiceFixture();
        UUID quiz = practiceQuiz(COURSE, "Alpha easy", "practice", "published", question(f.arrays(), "easy"));
        UUID otherCourseLesson = lesson(chapter(SECOND, 1), 1);
        UUID hiddenCourseLesson = lesson(chapter(HIDDEN, 1), 1);
        String learner = bearer(LEARNER), owner = bearer(OWNER), admin = bearer(ADMIN);
        String path = "/api/courses/" + COURSE + "/lessons/" + f.l1() + "/practice-recommendation";

        // Positive control: an admitted request's recorded SQL does include the mastery and profile reads.
        List<String> admittedSql = new ArrayList<>();
        RECORDED_SQL.set(admittedSql);
        try {
            practiceJson(practice(COURSE, f.l1(), learner));
        } finally {
            RECORDED_SQL.remove();
        }
        assertTrue(admittedSql.stream().anyMatch(sql -> sql.contains("learner_skill_masteries")), admittedSql::toString);
        assertTrue(admittedSql.stream().anyMatch(sql -> sql.contains("quiz_questions")), admittedSql::toString);

        List<Object[]> cases = List.of(
                new Object[] {null, path, 401},
                new Object[] {"Bearer not-a-token", path, 401},
                new Object[] {learner, "/api/courses/not-a-uuid/lessons/" + f.l1() + "/practice-recommendation", 400},
                new Object[] {learner, "/api/courses/" + COURSE + "/lessons/" + f.l1() + "x/practice-recommendation", 400},
                new Object[] {owner, "/api/courses/not-a-uuid/lessons/" + f.l1() + "/practice-recommendation", 400},
                new Object[] {learner, path + "?page=0", 400},
                new Object[] {owner, path, 403},
                new Object[] {admin, path, 403},
                new Object[] {learner, "/api/courses/" + SECOND + "/lessons/" + otherCourseLesson + "/practice-recommendation", 403},
                new Object[] {learner, "/api/courses/" + HIDDEN + "/lessons/" + hiddenCourseLesson + "/practice-recommendation", 404},
                new Object[] {learner, "/api/courses/" + UUID.randomUUID() + "/lessons/" + f.l1() + "/practice-recommendation", 404},
                new Object[] {learner, "/api/courses/" + COURSE + "/lessons/" + f.hidden() + "/practice-recommendation", 404},
                new Object[] {learner, "/api/courses/" + COURSE + "/lessons/" + otherCourseLesson + "/practice-recommendation", 404},
                new Object[] {learner, "/api/courses/" + COURSE + "/lessons/" + UUID.randomUUID() + "/practice-recommendation", 404});
        for (Object[] c : cases) {
            List<String> statements = new ArrayList<>();
            RECORDED_SQL.set(statements);
            MvcResult result;
            try {
                result = call(new Operation(HttpMethod.GET, (String) c[1], null), (String) c[0]);
            } finally {
                RECORDED_SQL.remove();
            }
            assertProblem(result, (int) c[2], null);
            assertEquals("no-store", result.getResponse().getHeader("Cache-Control"), (String) c[1]);
            assertFalse(result.getResponse().getContentAsString().contains(quiz.toString()));
            assertTrue(statements.stream().noneMatch(sql -> sql.contains("learner_skill_masteries") || sql.contains("quiz")),
                    () -> c[1] + " read mastery or Assessment data: " + statements);
        }

        // A LEFT membership is CT03 stage 4 (403), exactly as for /adaptive-guidance.
        jdbc.update("UPDATE public.enrollments SET status = 'left' WHERE user_id = ? AND course_id = ?", LEARNER, COURSE);
        assertProblem(practice(COURSE, f.l1(), learner), 403, null);
        jdbc.update("UPDATE public.enrollments SET status = 'completed' WHERE user_id = ? AND course_id = ?", LEARNER, COURSE);
        assertEquals(quiz.toString(), practiceJson(practice(COURSE, f.l1(), learner)).get("recommendation").get("quizId").asString(),
                "a COMPLETED membership is admitted");
    }

    @Test void b8P08TheRecommendationWritesNothingPublishesNothingAndRunsInTheCallersReadOnlySnapshot() throws Exception {
        PracticeFixture f = practiceFixture();
        mastery(f.loops(), "weak");
        practiceQuiz(COURSE, "Alpha easy", "practice", "published", question(f.loops(), "easy"));
        String before = mutableState();

        practiceJson(practice(COURSE, f.l1(), bearer(LEARNER)));
        practiceJson(practice(COURSE, f.unmapped(), bearer(LEARNER)));
        assertEquals(before.replaceAll("refresh_sessions=\\d+", ""), mutableState().replaceAll("refresh_sessions=\\d+", ""));
        assertTrue(recommendationEvents.received().isEmpty(), "no recommendation event");

        List<String> facts = outer(TransactionDefinition.ISOLATION_REPEATABLE_READ, true).execute(status -> as(LEARNER, Role.LEARNER, () -> {
            assertEquals("Alpha easy", recommendationService.readPracticeRecommendation(COURSE, f.l1()).recommendation().quizTitle());
            return List.of(String.valueOf(jdbc.queryForObject("SELECT pg_current_xact_id_if_assigned()", Object.class)),
                    jdbc.queryForObject("SHOW transaction_isolation", String.class),
                    jdbc.queryForObject("SHOW transaction_read_only", String.class));
        }));
        assertEquals(List.of("null", "repeatable read", "on"), facts, "no transaction id was assigned: nothing was written");

        // Its own unit is the same kind of snapshot; a read-write or READ COMMITTED caller is refused (500 path).
        assertThrows(IllegalStateException.class, () -> outer(TransactionDefinition.ISOLATION_READ_COMMITTED, false)
                .execute(status -> as(LEARNER, Role.LEARNER, () -> recommendationService.readPracticeRecommendation(COURSE, f.l1()))));
    }

    @Test void b8TheRecommendationIsOneSnapshotEvenWhenAWriterCommitsBetweenItsReads() throws Exception {
        PracticeFixture f = practiceFixture();
        mastery(f.loops(), "mastered");
        UUID arraysEasy = question(f.arrays(), "easy");
        UUID alpha = practiceQuiz(COURSE, "Alpha easy", "practice", "published", arraysEasy);
        UUID late = practiceQuiz(COURSE, "Aaa late", "practice", "hidden", arraysEasy);
        String learner = bearer(LEARNER);
        AtomicInteger hooks = new AtomicInteger();
        BEFORE_MASTERY_READ.set(() -> {
            hooks.incrementAndGet();
            commitSqlElsewhere("INSERT INTO public.learner_skill_masteries (id,user_id,skill_id,state) VALUES (?,?,?,'mastered')",
                    UUID.randomUUID(), LEARNER, f.arrays());
            commitSqlElsewhere("UPDATE public.quizzes SET publication_status = 'published' WHERE id = ?", late);
        });

        assertRecommended(practiceJson(practice(COURSE, f.l1(), learner)).get("recommendation"), alpha, "Alpha easy", "easy",
                "easy", false, f.arrays(), "Arrays", "unknown",
                "Recommended practice: Easy, because your skill \"Arrays\" is not yet assessed.");
        assertEquals(1, hooks.get(), "the writer committed between the admission and the mastery read");

        assertRecommended(practiceJson(practice(COURSE, f.l1(), learner)).get("recommendation"), late, "Aaa late", "easy",
                "hard", true, f.arrays(), "Arrays", "mastered",
                "Recommended practice: Easy, because your skill \"Arrays\" is Mastered and no Hard practice quiz is "
                        + "available for this lesson.");
    }

    @Test void b8AProviderFailureIsASafeNoStoreServerError() throws Exception {
        PracticeFixture f = practiceFixture();
        BEFORE_MASTERY_READ.set(() -> { throw new IllegalStateException("leaked-diagnostic-" + f.loops()); });

        MvcResult result = practice(COURSE, f.l1(), bearer(LEARNER));

        assertProblem(result, 500, null);
        assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
        assertFalse(result.getResponse().getContentAsString().contains("leaked-diagnostic"));
        assertFalse(result.getResponse().getContentAsString().contains(f.loops().toString()));
    }

    @Test void b8TheWholeCompositionNeedsOnlyTheRuntimeMatrixSelectGrants() throws Exception {
        PracticeFixture f = practiceFixture();
        mastery(f.arrays(), "learning");
        UUID medium = practiceQuiz(COURSE, "Arrays medium", "practice", "published", question(f.arrays(), "medium"));
        String role = "ad11_select_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE ROLE " + role + " NOLOGIN");
        try {
            jdbc.execute("GRANT USAGE ON SCHEMA public TO " + role);
            // CT17-P6 matrix SELECT rows of the relations this read touches (CT03's snapshot included); masteries
            // (r8) and learning_materials are column-limited exactly as in the matrix.
            jdbc.execute("GRANT SELECT ON public.users, public.courses, public.chapters, public.lessons, public.enrollments, "
                    + "public.lesson_progress, public.skills, public.lesson_skills, public.quizzes, public.quiz_questions, "
                    + "public.questions TO " + role);
            jdbc.execute("GRANT SELECT (user_id, skill_id, state) ON public.learner_skill_masteries TO " + role);
            jdbc.execute("GRANT SELECT (id, lesson_id, publication_status) ON public.learning_materials TO " + role);

            String choice = outer(TransactionDefinition.ISOLATION_REPEATABLE_READ, true).execute(status -> {
                status.setRollbackOnly(); // the denied probe below aborts the transaction
                jdbc.execute("SET LOCAL ROLE " + role);
                String quizId = as(LEARNER, Role.LEARNER,
                        () -> recommendationService.readPracticeRecommendation(COURSE, f.l1()).recommendation().quizId().toString());
                assertEquals(role, jdbc.queryForObject("SELECT current_user", String.class));
                assertThrows(DataAccessException.class, () -> jdbc.queryForList("SELECT id FROM public.learner_skill_masteries"));
                return quizId;
            });
            assertEquals(medium.toString(), choice);
        } finally {
            jdbc.execute("DROP OWNED BY " + role);
            jdbc.execute("DROP ROLE " + role);
        }
    }

    /** COURSE adaptive with L1 (Loops, Arrays), L2 (Strings), an unmapped L3 and a hidden mapped Lesson; LEARNER active. */

    // AD10/CT25 and the bounded AD16 course-guidance composition, real proxies and PostgreSQL.
    @Test void ad10GuidanceHasTheCompleteWireShapeAndSkipWritesNothing() throws Exception {
        var f = guidanceFixture();
        UUID diagnostic = practiceQuiz(COURSE, "Placement", "diagnostic", "published", question(f.loops(), "easy"));
        String token = bearer(LEARNER);
        String before = mutableState();
        var first = guidanceJson(COURSE, token);
        assertEquals(Set.of("courseId", "adaptiveEnabled", "placementOffered", "placementQuizId",
                "currentRecommendation", "lessons"), fieldNames(first));
        assertTrue(first.get("adaptiveEnabled").asBoolean());
        assertTrue(first.get("placementOffered").asBoolean());
        assertEquals(diagnostic.toString(), first.get("placementQuizId").asString());
        assertTrue(first.get("currentRecommendation").isNull(), "no fabricated recommendation");
        assertEquals(List.of(f.l1().toString(), f.l2().toString(), f.unmapped().toString()),
                first.get("lessons").valueStream().map(l -> l.get("lessonId").asString()).toList());
        for (var lesson : first.get("lessons")) {
            assertEquals(Set.of("lessonId", "unmasteredPrerequisiteSkillIds"), fieldNames(lesson));
        }
        // Skip is navigation to Course's canonical path, followed by a later revisit.
        assertEquals(f.l1(), as(LEARNER, Role.LEARNER, () -> learnerStates.progress(COURSE).nextLessonId()));
        assertEquals(first, guidanceJson(COURSE, token), "offer reappears; no dismissal marker");
        assertEquals(before.replaceAll("refresh_sessions=\\d+", ""), mutableState().replaceAll("refresh_sessions=\\d+", ""));
        assertTrue(recommendationEvents.received().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"unknown", "weak", "learning", "mastered"})
    void ad10EveryStoredStateSuppressesTheOfferIncludingExplicitUnknown(String state) throws Exception {
        var f = guidanceFixture();
        practiceQuiz(COURSE, "Placement", "diagnostic", "published", question(f.loops(), "easy"));
        mastery(f.arrays(), state);
        var body = guidanceJson(COURSE, bearer(LEARNER));
        assertFalse(body.get("placementOffered").asBoolean());
        assertTrue(body.get("placementQuizId").isNull());
    }

    @Test void ad10OtherLearnersAndOtherCoursesDoNotSuppressTheOffer() throws Exception {
        var f = guidanceFixture();
        UUID diagnostic = practiceQuiz(COURSE, "Placement", "diagnostic", "published", question(f.loops(), "easy"));
        UUID foreignSkill = skillRow(SECOND, "Foreign");
        mastery(foreignSkill, "mastered");
        jdbc.update("INSERT INTO public.learner_skill_masteries (id,user_id,skill_id,state) VALUES (?,?,?,'unknown')",
                UUID.randomUUID(), OWNER, f.loops());
        assertEquals(diagnostic.toString(), guidanceJson(COURSE, bearer(LEARNER)).get("placementQuizId").asString());
    }

    @Test void ad10OfferRefusalsAndCompletedMembershipUseStoredConfiguration() throws Exception {
        var f = guidanceFixture();
        String token = bearer(LEARNER);
        assertFalse(guidanceJson(COURSE, token).get("placementOffered").asBoolean(), "no diagnostic");
        practiceQuiz(COURSE, "Hidden", "diagnostic", "hidden", question(f.loops(), "easy"));
        assertFalse(guidanceJson(COURSE, token).get("placementOffered").asBoolean(), "hidden diagnostic");
        UUID diagnostic = practiceQuiz(COURSE, "Placement", "diagnostic", "published", question(f.arrays(), "easy"));
        jdbc.update("UPDATE public.enrollments SET status = 'completed' WHERE user_id = ? AND course_id = ?", LEARNER, COURSE);
        assertEquals(diagnostic.toString(), guidanceJson(COURSE, token).get("placementQuizId").asString());
        jdbc.update("UPDATE public.courses SET adaptive_enabled = false, adaptive_settings = null WHERE id = ?", COURSE);
        var disabled = guidanceJson(COURSE, token);
        assertFalse(disabled.get("adaptiveEnabled").asBoolean());
        assertFalse(disabled.get("placementOffered").asBoolean());
        assertTrue(disabled.get("currentRecommendation").isNull());
        for (var lesson : disabled.get("lessons")) assertEquals(0, lesson.get("unmasteredPrerequisiteSkillIds").size());
    }

    @Test void ad10ServerSelectionResumesD2AndRefusesOverdueHiddenAndSubmittedAttempts() throws Exception {
        var f = guidanceFixture();
        UUID q = question(f.loops(), "easy");
        UUID d2 = practiceQuiz(COURSE, "Z diagnostic", "diagnostic", "published", q);
        String token = bearer(LEARNER);
        String start = "/api/courses/" + COURSE + "/quizzes/" + d2 + "/attempts";
        var started = json(call(new Operation(HttpMethod.POST, start, null), token), 201);
        UUID attempt = UUID.fromString(started.get("attemptId").asString());
        UUID d1 = practiceQuiz(COURSE, "A diagnostic", "diagnostic", "published", q);
        assertProblem(call(new Operation(HttpMethod.POST, "/api/courses/" + COURSE + "/quizzes/" + d1 + "/attempts", null), token), 409, null);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.attempts WHERE user_id = ?", Integer.class, LEARNER));
        assertEquals(d2.toString(), guidanceJson(COURSE, token).get("placementQuizId").asString());
        assertEquals(attempt.toString(), json(call(new Operation(HttpMethod.POST, start, null), token), 200).get("attemptId").asString());
        jdbc.update("UPDATE public.quizzes SET publication_status = 'hidden' WHERE id = ?", d2);
        assertFalse(guidanceJson(COURSE, token).get("placementOffered").asBoolean());
        jdbc.update("UPDATE public.quizzes SET publication_status = 'published' WHERE id = ?", d2);
        jdbc.update("UPDATE public.attempts SET started_at = now() - interval '2 hours', expires_at = now() - interval '1 hour' WHERE id = ?", attempt);
        assertFalse(guidanceJson(COURSE, token).get("placementOffered").asBoolean());
        jdbc.update("UPDATE public.attempts SET submitted_at = expires_at, score_percentage = 0 WHERE id = ?", attempt);
        assertFalse(guidanceJson(COURSE, token).get("placementOffered").asBoolean());
    }

    @Test void ad10ServerSelectsFirstPublishedDiagnosticByAssessmentOrder() throws Exception {
        var f = guidanceFixture();
        UUID q = question(f.loops(), "easy");
        practiceQuiz(COURSE, "A hidden", "diagnostic", "hidden", q);
        practiceQuiz(COURSE, "A practice", "practice", "published", q);
        practiceQuiz(COURSE, "Z diagnostic", "diagnostic", "published", q);
        UUID first = practiceQuiz(COURSE, "B diagnostic", "diagnostic", "published", q);
        assertEquals(first.toString(), guidanceJson(COURSE, bearer(LEARNER)).get("placementQuizId").asString());
    }

    @Test void ad10GuidanceAdmissionAndErrorsAreSafeAndNoStore() throws Exception {
        guidanceFixture();
        String base = "/api/courses/" + COURSE + "/adaptive-guidance";
        for (var c : List.of(new Object[]{null, base, 401}, new Object[]{bearer(OWNER), base, 403},
                new Object[]{bearer(ADMIN), base, 403}, new Object[]{bearer(LEARNER), "/api/courses/" + SECOND + "/adaptive-guidance", 403},
                new Object[]{bearer(LEARNER), "/api/courses/" + HIDDEN + "/adaptive-guidance", 404},
                new Object[]{bearer(LEARNER), "/api/courses/1-1-1-1-1/adaptive-guidance", 400},
                new Object[]{bearer(LEARNER), base + "?learnerId=" + OWNER, 400})) {
            List<String> sql = new ArrayList<>(); RECORDED_SQL.set(sql);
            MvcResult result;
            try { result = call(new Operation(HttpMethod.GET, (String)c[1], null), (String)c[0]); }
            finally { RECORDED_SQL.remove(); }
            assertProblem(result, (int)c[2], null);
            assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
            assertTrue(sql.stream().noneMatch(q -> q.contains("learner_skill_masteries") || q.contains("path_recommendations")), sql.toString());
        }
        jdbc.update("UPDATE public.enrollments SET status = 'left' WHERE user_id = ? AND course_id = ?", LEARNER, COURSE);
        assertProblem(call(new Operation(HttpMethod.GET, base, null), bearer(LEARNER)), 403, null);
    }

    @Test void ad10BothPlacementEntriesEnforceRealTransactionAdmissionAndRejectUntrustedStatesBeforeMastery() {
        guidanceFixture();
        assertTrue(AopUtils.isAopProxy(placement));
        assertThrows(IllegalTransactionStateException.class, () -> placement.readPlacementOffer((LearnerCourseState)null));
        for (boolean stateEntry : List.of(false, true)) {
            for (int isolation : List.of(TransactionDefinition.ISOLATION_READ_COMMITTED, TransactionDefinition.ISOLATION_REPEATABLE_READ)) {
                for (boolean readOnly : List.of(false, true)) {
                    if (readOnly && isolation == TransactionDefinition.ISOLATION_REPEATABLE_READ) continue;
                    assertThrows(IllegalStateException.class, () -> outer(isolation, readOnly).execute(status ->
                            as(LEARNER, Role.LEARNER, () -> stateEntry ? placement.readPlacementOffer(
                                    new LearnerCourseState(COURSE, LEARNER, UUID.randomUUID(), "active", true, Map.of(), List.of()))
                                    : placement.readPlacementOffer(COURSE))));
                }
            }
        }
        var admitted = outer(TransactionDefinition.ISOLATION_REPEATABLE_READ, true).execute(status ->
                as(LEARNER, Role.LEARNER, () -> learnerStates.readLearnerState(COURSE)));
        var foreign = new LearnerCourseState(COURSE, OWNER, admitted.enrollmentId(), "active", true,
                admitted.adaptiveSettings(), admitted.lessons());
        List<String> sql = new ArrayList<>(); RECORDED_SQL.set(sql);
        try {
            assertThrows(IllegalStateException.class, () -> outer(TransactionDefinition.ISOLATION_REPEATABLE_READ, true)
                    .execute(status -> as(LEARNER, Role.LEARNER, () -> placement.readPlacementOffer(foreign))));
            jdbc.update("UPDATE public.courses SET adaptive_enabled = false WHERE id = ?", COURSE);
            assertThrows(IllegalStateException.class, () -> outer(TransactionDefinition.ISOLATION_REPEATABLE_READ, true)
                    .execute(status -> as(LEARNER, Role.LEARNER, () -> placement.readPlacementOffer(admitted))));
        } finally { RECORDED_SQL.remove(); }
        assertTrue(sql.stream().noneMatch(q -> q.contains("learner_skill_masteries")), sql.toString());
    }

    @Test void ad10PlacementStateAndGuidanceJoinTheActualPostgresSnapshotWithoutWrites() throws Exception {
        var f = guidanceFixture();
        UUID diagnostic = practiceQuiz(COURSE, "Placement", "diagnostic", "published", question(f.loops(), "easy"));
        var offer = outer(TransactionDefinition.ISOLATION_REPEATABLE_READ, true).execute(status -> as(LEARNER, Role.LEARNER, () -> {
            var state = learnerStates.readLearnerState(COURSE);
            commitSqlElsewhere("UPDATE public.courses SET adaptive_enabled = false WHERE id = ?", COURSE);
            commitSqlElsewhere("INSERT INTO public.learner_skill_masteries (id,user_id,skill_id,state) VALUES (?,?,?,'unknown')",
                    UUID.randomUUID(), LEARNER, f.loops());
            assertEquals("repeatable read", jdbc.queryForObject("SHOW transaction_isolation", String.class));
            assertEquals("on", jdbc.queryForObject("SHOW transaction_read_only", String.class));
            assertNull(jdbc.queryForObject("SELECT pg_current_xact_id_if_assigned()", Object.class));
            assertTrue(masteryQueries.readAdaptiveGuidance(COURSE).placementOffered());
            return placement.readPlacementOffer(state);
        }));
        assertEquals(diagnostic, offer.placementQuizId());
        assertFalse(guidanceJson(COURSE, bearer(LEARNER)).get("placementOffered").asBoolean());
    }

    @Test void ad10GuidanceVisibilityUsesTheRealEngineLatestRowAndExactReason() throws Exception {
        var f = guidanceFixture();
        UUID enrollmentId = jdbc.queryForObject("SELECT id FROM public.enrollments WHERE user_id = ? AND course_id = ?",
                UUID.class, LEARNER, COURSE);
        String reason = "Continue with the next lesson in the course order.";
        guidanceHistory(enrollmentId, f.l1(), "continue", reason, 0);
        String token = bearer(LEARNER);
        var current = guidanceJson(COURSE, token).get("currentRecommendation");
        assertEquals(Set.of("type", "targetLessonId", "reason", "recommendedAt", "targetCompleted"), fieldNames(current));
        assertEquals(reason, current.get("reason").asString()); assertFalse(current.get("targetCompleted").asBoolean());
        guidanceHistory(enrollmentId, f.l2(), "skip", "Skip history must not determine current guidance.", 1);
        assertEquals(current, guidanceJson(COURSE, token).get("currentRecommendation"));
        guidanceHistory(enrollmentId, f.l2(), "continue", reason, 2);
        assertTrue(guidanceJson(COURSE, token).get("currentRecommendation").isNull(), "never fall back to valid older row");
        jdbc.update("DELETE FROM public.path_recommendations WHERE enrollment_id = ? AND lesson_id = ?", enrollmentId, f.l2());
        jdbc.update("UPDATE public.skills SET name = 'Unrelated renamed' WHERE id = ?", f.strings());
        assertEquals(current, guidanceJson(COURSE, token).get("currentRecommendation"), "unrelated rename preserves draft");
        jdbc.update("UPDATE public.lessons SET publication_status = 'hidden' WHERE id = ?", f.l1());
        assertTrue(guidanceJson(COURSE, token).get("currentRecommendation").isNull());
    }

    @Test void ad10CompletedRemedialReviewRemainsVisibleAndSelectedRenameInvalidatesIt() throws Exception {
        var f = guidanceFixture();
        jdbc.update("INSERT INTO public.skill_prerequisites (id,course_id,skill_id,prerequisite_skill_id) VALUES (?,?,?,?)",
                UUID.randomUUID(), COURSE, f.strings(), f.loops());
        mastery(f.strings(), "weak"); mastery(f.loops(), "learning");
        UUID enrollmentId = jdbc.queryForObject("SELECT id FROM public.enrollments WHERE user_id = ? AND course_id = ?",
                UUID.class, LEARNER, COURSE);
        for (UUID lesson : List.of(f.l1(), f.l2(), f.unmapped())) jdbc.update(
                "INSERT INTO public.lesson_progress (id,enrollment_id,lesson_id,completed,mastery_skipped) VALUES (?,?,?,true,false)",
                UUID.randomUUID(), enrollmentId, lesson);
        jdbc.update("UPDATE public.enrollments SET status = 'completed' WHERE id = ?", enrollmentId);
        guidanceHistory(enrollmentId, f.l1(), "remedial",
                com.codemastery.modules.adaptive.application.recommendation.RecommendationReasonBuilder.remedial("Strings", "Loops"), 0);
        String token = bearer(LEARNER);
        var body = guidanceJson(COURSE, token);
        assertEquals("remedial", body.get("currentRecommendation").get("type").asString());
        assertTrue(body.get("currentRecommendation").get("targetCompleted").asBoolean());
        assertEquals(List.of(f.loops().toString()), body.get("lessons").get(1).get("unmasteredPrerequisiteSkillIds")
                .valueStream().map(JsonNode::asString).toList());
        jdbc.update("UPDATE public.skills SET name = 'Selected renamed' WHERE id = ?", f.loops());
        assertTrue(guidanceJson(COURSE, token).get("currentRecommendation").isNull());
    }

    @Test void ad10DuplicateLatestGuidanceInstantFailsSafelyRatherThanChoosingRandomUuid() throws Exception {
        var f = guidanceFixture();
        UUID enrollmentId = jdbc.queryForObject("SELECT id FROM public.enrollments WHERE user_id = ? AND course_id = ?", UUID.class, LEARNER, COURSE);
        guidanceHistory(enrollmentId, f.l1(), "continue", "Continue with the next lesson in the course order.", 0);
        guidanceHistory(enrollmentId, f.l2(), "remedial", "Different guidance at the same instant", 0);
        MvcResult result = call(new Operation(HttpMethod.GET, "/api/courses/" + COURSE + "/adaptive-guidance", null), bearer(LEARNER));
        assertProblem(result, 500, null); assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
        assertFalse(result.getResponse().getContentAsString().contains(enrollmentId.toString()));
    }

    @Test void ad10GuidanceSnapshotRemainsCoherentAcrossACommittedConcurrentWriter() throws Exception {
        var f = guidanceFixture();
        UUID diagnostic = practiceQuiz(COURSE, "Placement", "diagnostic", "published", question(f.loops(), "easy"));
        AtomicInteger hooks = new AtomicInteger();
        BEFORE_MASTERY_READ.set(() -> {
            hooks.incrementAndGet();
            commitSqlElsewhere("UPDATE public.quizzes SET publication_status = 'hidden' WHERE id = ?", diagnostic);
            commitSqlElsewhere("INSERT INTO public.learner_skill_masteries (id,user_id,skill_id,state) VALUES (?,?,?,'unknown')",
                    UUID.randomUUID(), LEARNER, f.loops());
        });
        String token = bearer(LEARNER);
        assertEquals(diagnostic.toString(), guidanceJson(COURSE, token).get("placementQuizId").asString());
        assertEquals(1, hooks.get());
        assertFalse(guidanceJson(COURSE, token).get("placementOffered").asBoolean());
    }

    @Test void ad10GuidanceUsesOnlyTheRuntimeReadGrantsAndActuallyNeedsHistorySelect() throws Exception {
        var f = guidanceFixture();
        UUID enrollmentId = jdbc.queryForObject("SELECT id FROM public.enrollments WHERE user_id = ? AND course_id = ?", UUID.class, LEARNER, COURSE);
        guidanceHistory(enrollmentId, f.l1(), "continue", "Continue with the next lesson in the course order.", 0);
        String role = "ad10_read_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE ROLE " + role + " NOLOGIN");
        try {
            jdbc.execute("GRANT USAGE ON SCHEMA public TO " + role);
            jdbc.execute("GRANT SELECT ON public.users, public.courses, public.chapters, public.lessons, public.enrollments, "
                    + "public.lesson_progress, public.skills, public.skill_prerequisites, public.lesson_skills, public.quizzes, "
                    + "public.attempts, public.path_recommendations TO " + role);
            jdbc.execute("GRANT SELECT (user_id,skill_id,state) ON public.learner_skill_masteries TO " + role);
            jdbc.execute("GRANT SELECT (id,lesson_id,publication_status) ON public.learning_materials TO " + role);
            outer(TransactionDefinition.ISOLATION_REPEATABLE_READ, true).execute(status -> {
                jdbc.execute("SET LOCAL ROLE " + role);
                var result = as(LEARNER, Role.LEARNER, () -> masteryQueries.readAdaptiveGuidance(COURSE));
                assertEquals(f.l1(), result.currentRecommendation().targetLessonId());
                assertNull(jdbc.queryForObject("SELECT pg_current_xact_id_if_assigned()", Object.class));
                return null;
            });
            jdbc.execute("REVOKE SELECT ON public.path_recommendations FROM " + role);
            assertThrows(DataAccessException.class, () -> outer(TransactionDefinition.ISOLATION_REPEATABLE_READ, true).execute(status -> {
                jdbc.execute("SET LOCAL ROLE " + role);
                return as(LEARNER, Role.LEARNER, () -> masteryQueries.readAdaptiveGuidance(COURSE));
            }));
        } finally {
            jdbc.execute("DROP OWNED BY " + role);
            jdbc.execute("DROP ROLE " + role);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"published", "hidden"})
    void ad10Ir01ForeignMappedLessonFailsClosedBeforePublicationFiltering(String publication) throws Exception {
        var f = guidanceFixture();
        UUID foreign = "published".equals(publication) ? lesson(chapter(SECOND, 1), 1)
                : hiddenLesson(chapter(SECOND, 1), 1);
        jdbc.update("INSERT INTO public.lesson_skills (id,lesson_id,skill_id) VALUES (?,?,?)",
                UUID.randomUUID(), foreign, f.loops());
        String token = bearer(LEARNER), before = mutableState();
        var result = call(new Operation(HttpMethod.GET, "/api/courses/" + COURSE + "/adaptive-guidance", null), token);
        assertProblem(result, 500, null);
        assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains(foreign.toString()));
        assertFalse(body.contains(SECOND.toString()));
        assertFalse(body.contains("Persisted Lesson mapping"));
        assertEquals(before, mutableState());
    }

    @Test void ad10Ir01ValidHiddenSameCourseMappingIsAcceptedWithoutBecomingGuidance() throws Exception {
        var f = guidanceFixture(); // Includes a real hidden Lesson mapped to Loops.
        UUID enrollmentId = jdbc.queryForObject("SELECT id FROM public.enrollments WHERE user_id=? AND course_id=?",
                UUID.class, LEARNER, COURSE);
        guidanceHistory(enrollmentId, f.l1(), "continue", "Continue with the next lesson in the course order.", 0);
        String token = bearer(LEARNER), before = mutableState();
        var withHidden = guidanceJson(COURSE, token);
        assertEquals(List.of(f.l1().toString(), f.l2().toString(), f.unmapped().toString()),
                withHidden.get("lessons").valueStream().map(l -> l.get("lessonId").asString()).toList());
        assertEquals(f.l1().toString(), withHidden.get("currentRecommendation").get("targetLessonId").asString());
        assertFalse(withHidden.toString().contains(f.hidden().toString()));
        assertEquals(before, mutableState());
        jdbc.update("DELETE FROM public.lesson_skills WHERE lesson_id=?", f.hidden());
        assertEquals(withHidden, guidanceJson(COURSE, token), "hidden mappings are valid but never routing inputs");
    }

    @Test void ad10Ir01CourseAssertionRequiresSnapshotAndLearnerAdmissionEvenForEmptyTargets() {
        var f = guidanceFixture();
        assertTrue(AopUtils.isAopProxy(structure));
        assertThrows(IllegalTransactionStateException.class,
                () -> structure.requireLearnerMappedLessonsInCourse(COURSE, Set.of(f.hidden())));
        for (int isolation : List.of(TransactionDefinition.ISOLATION_READ_COMMITTED, TransactionDefinition.ISOLATION_REPEATABLE_READ)) {
            for (boolean readOnly : List.of(false, true)) {
                if (readOnly && isolation == TransactionDefinition.ISOLATION_REPEATABLE_READ) continue;
                assertThrows(IllegalStateException.class, () -> outer(isolation, readOnly).execute(status ->
                        as(LEARNER, Role.LEARNER, () -> {
                            // SECOND would be 403 if admission ran before the transaction guard.
                            structure.requireLearnerMappedLessonsInCourse(SECOND, Set.of());
                            return null;
                        })));
            }
        }
        for (var actor : List.of(new Object[]{OWNER, Role.INSTRUCTOR}, new Object[]{ADMIN, Role.ADMIN})) {
            assertThrows(AccessDeniedException.class, () -> outer(TransactionDefinition.ISOLATION_REPEATABLE_READ, true)
                    .execute(status -> as((UUID) actor[0], (Role) actor[1], () -> {
                        structure.requireLearnerMappedLessonsInCourse(COURSE, Set.of(f.hidden())); return null;
                    })));
        }
        assertThrows(AccessDeniedException.class, () -> outer(TransactionDefinition.ISOLATION_REPEATABLE_READ, true)
                .execute(status -> as(LEARNER, Role.LEARNER, () -> {
                    structure.requireLearnerMappedLessonsInCourse(SECOND, Set.of()); return null;
                })));
        assertThrows(IllegalStateException.class, () -> outer(TransactionDefinition.ISOLATION_REPEATABLE_READ, true)
                .execute(status -> as(LEARNER, Role.LEARNER, () -> {
                    structure.requireLearnerMappedLessonsInCourse(COURSE, Set.of(UUID.randomUUID())); return null;
                })));
        outer(TransactionDefinition.ISOLATION_REPEATABLE_READ, true).execute(status -> as(LEARNER, Role.LEARNER, () -> {
            structure.requireLearnerMappedLessonsInCourse(COURSE, Set.of(f.l1(), f.hidden()));
            assertEquals("repeatable read", jdbc.queryForObject("SHOW transaction_isolation", String.class));
            assertEquals("on", jdbc.queryForObject("SHOW transaction_read_only", String.class));
            assertNull(jdbc.queryForObject("SELECT pg_current_xact_id_if_assigned()", Object.class));
            return null;
        }));
    }

    @Test void ad10Ir01MappingAncestryUsesTheEstablishedSnapshotAcrossCommittedLessonMove() throws Exception {
        var f = guidanceFixture();
        UUID foreignChapter = chapter(SECOND, 1);
        outer(TransactionDefinition.ISOLATION_REPEATABLE_READ, true).execute(status -> as(LEARNER, Role.LEARNER, () -> {
            learnerStates.readLearnerState(COURSE); // Establish the PostgreSQL snapshot before the writer commits.
            try (Connection writer = dataSource.getConnection();
                    PreparedStatement update = writer.prepareStatement("UPDATE public.lessons SET chapter_id=? WHERE id=?")) {
                writer.setAutoCommit(true);
                update.setQueryTimeout(Math.toIntExact(WAIT_SECONDS));
                update.setObject(1, foreignChapter); update.setObject(2, f.hidden());
                assertEquals(1, update.executeUpdate());
            } catch (SQLException failure) {
                throw new AssertionError("Disposable corrupt-writer fixture did not commit", failure);
            }
            var during = masteryQueries.readAdaptiveGuidance(COURSE);
            assertEquals(List.of(f.l1(), f.l2(), f.unmapped()), during.lessons().stream().map(MasteryQueryService.LessonGuidance::lessonId).toList());
            assertNull(jdbc.queryForObject("SELECT pg_current_xact_id_if_assigned()", Object.class));
            return null;
        }));
        assertEquals(foreignChapter, jdbc.queryForObject("SELECT chapter_id FROM public.lessons WHERE id=?", UUID.class, f.hidden()));
        var fresh = call(new Operation(HttpMethod.GET, "/api/courses/" + COURSE + "/adaptive-guidance", null), bearer(LEARNER));
        assertProblem(fresh, 500, null);
        assertEquals("no-store", fresh.getResponse().getHeader("Cache-Control"));
    }

    private PracticeFixture guidanceFixture() {
        var f = practiceFixture();
        jdbc.update("UPDATE public.courses SET adaptive_settings = ?::jsonb WHERE id = ?",
                "{\"weak\":{\"numerator\":1,\"denominator\":2},\"mastered\":{\"numerator\":4,\"denominator\":5},\"minimumQuestionsPerSkill\":6}", COURSE);
        return f;
    }
    private JsonNode guidanceJson(UUID course, String token) throws Exception {
        var result = call(new Operation(HttpMethod.GET, "/api/courses/" + course + "/adaptive-guidance", null), token);
        assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
        return json(result, 200);
    }
    private void guidanceHistory(UUID enrollmentId, UUID lesson, String type, String reason, int second) {
        jdbc.update("INSERT INTO public.path_recommendations (id,enrollment_id,lesson_id,recommendation_type,reason,recommended_at) "
                + "VALUES (?,?,?,?,?,?)", UUID.randomUUID(), enrollmentId, lesson, type, reason,
                OffsetDateTime.parse("2026-11-28T00:00:00Z").plusSeconds(second));
    }

    private record PracticeFixture(UUID l1, UUID l2, UUID unmapped, UUID hidden, UUID loops, UUID arrays, UUID strings) { }

    private PracticeFixture practiceFixture() {
        jdbc.update("UPDATE public.courses SET adaptive_enabled = true, adaptive_settings = '{}'::jsonb WHERE id = ?", COURSE);
        UUID chapter = chapter(COURSE, 1);
        UUID l1 = lesson(chapter, 1), l2 = lesson(chapter, 2), unmapped = lesson(chapter, 3), hidden = hiddenLesson(chapter, 4);
        UUID loops = skillRow(COURSE, "Loops"), arrays = skillRow(COURSE, "Arrays"), strings = skillRow(COURSE, "Strings");
        for (UUID[] pair : List.of(new UUID[] {l1, loops}, new UUID[] {l1, arrays}, new UUID[] {l2, strings},
                new UUID[] {hidden, loops})) {
            jdbc.update("INSERT INTO public.lesson_skills (id,lesson_id,skill_id) VALUES (?,?,?)", UUID.randomUUID(), pair[0], pair[1]);
        }
        jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status) VALUES (?,?,?,'active')",
                UUID.randomUUID(), LEARNER, COURSE);
        return new PracticeFixture(l1, l2, unmapped, hidden, loops, arrays, strings);
    }

    private void mastery(UUID skill, String state) {
        jdbc.update("INSERT INTO public.learner_skill_masteries (id,user_id,skill_id,state) VALUES (?,?,?,?)",
                UUID.randomUUID(), LEARNER, skill, state);
    }

    private UUID question(UUID skill, String difficulty) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.questions (id,skill_id,prompt,kind,difficulty,options,correct_options) "
                + "VALUES (?,?,'Pick one','true_false',?,ARRAY['True','False'],ARRAY[true,false])", id, skill, difficulty);
        return id;
    }

    private UUID practiceQuiz(UUID course, String title, String kind, String status, UUID... questions) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.quizzes (id,course_id,title,kind,time_limit_seconds,publication_status) "
                + "VALUES (?,?,?,?,60,?)", id, course, title, kind, status);
        for (int i = 0; i < questions.length; i++) {
            jdbc.update("INSERT INTO public.quiz_questions (id,quiz_id,question_id,position) VALUES (?,?,?,?)",
                    UUID.randomUUID(), id, questions[i], i + 1);
        }
        return id;
    }

    private MvcResult practice(UUID course, UUID lesson, String authorization) throws Exception {
        return call(new Operation(HttpMethod.GET, "/api/courses/" + course + "/lessons/" + lesson + "/practice-recommendation",
                null), authorization);
    }

    private static JsonNode practiceJson(MvcResult result) throws Exception {
        JsonNode body = json(result, 200);
        assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
        return body;
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.TreeSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    private static void assertRecommended(JsonNode node, UUID quiz, String title, String difficulty, String target,
            boolean fallback, UUID skill, String skillName, String state, String reason) {
        assertEquals(Set.of("quizId", "quizTitle", "difficulty", "targetDifficulty", "fallback", "skillId", "skillName",
                "skillState", "reason"), fieldNames(node));
        assertEquals(quiz.toString(), node.get("quizId").asString());
        assertEquals(title, node.get("quizTitle").asString());
        assertEquals(difficulty, node.get("difficulty").asString());
        assertEquals(target, node.get("targetDifficulty").asString());
        assertEquals(fallback, node.get("fallback").asBoolean());
        assertEquals(skill.toString(), node.get("skillId").asString());
        assertEquals(skillName, node.get("skillName").asString());
        assertEquals(state, node.get("skillState").asString());
        assertEquals(reason, node.get("reason").asString());
    }

    /** Every row a write by this read could touch, as one comparable text. */
    private String mutableState() {
        return jdbc.queryForObject("SELECT concat_ws(';', "
                + "(SELECT 'masteries=' || coalesce(string_agg(user_id || ':' || skill_id || ':' || state, ',' ORDER BY skill_id), '') "
                + "FROM public.learner_skill_masteries), "
                + "(SELECT 'history=' || count(*) FROM public.path_recommendations), "
                + "(SELECT 'progress=' || coalesce(string_agg(lesson_id || ':' || completed || ':' || mastery_skipped, ',' ORDER BY lesson_id), '') "
                + "FROM public.lesson_progress), "
                + "(SELECT 'enrollments=' || coalesce(string_agg(id || ':' || status || ':' || coalesce(resume_lesson_id::text, '-') "
                + "|| ':' || coalesce(last_visited_at::text, '-'), ',' ORDER BY id), '') FROM public.enrollments), "
                + "(SELECT 'attempts=' || count(*) FROM public.attempts), "
                + "(SELECT 'quizzes=' || coalesce(string_agg(id || ':' || publication_status, ',' ORDER BY id), '') FROM public.quizzes), "
                + "(SELECT 'members=' || count(*) FROM public.quiz_questions), "
                + "(SELECT 'refresh_sessions=' || count(*) FROM public.refresh_sessions))", String.class);
    }

    /** One published Course with three published Lessons, L1-L3 mapped (in the context only) to S1-S3. */
    private record HistoryFixture(UUID enrollment, UUID l1, UUID l2, UUID l3, UUID s1, UUID s2, UUID s3) {
        DecisionContext context(Map<UUID, MasteryState> states) {
            Map<UUID, MasteryState> mastery = new LinkedHashMap<>(Map.of(
                    s1, MasteryState.UNKNOWN, s2, MasteryState.UNKNOWN, s3, MasteryState.UNKNOWN));
            mastery.putAll(states);
            return new DecisionContext(COURSE, LEARNER,
                    List.of(new LessonInput(l1, false, Set.of(s1)), new LessonInput(l2, false, Set.of(s2)),
                            new LessonInput(l3, false, Set.of(s3))),
                    mastery, List.of(), Map.of(s1, "Skill one", s2, "Skill two", s3, "Skill three"));
        }
    }

    private HistoryFixture historyFixture() {
        UUID chapter = chapter(COURSE, 1);
        UUID l1 = lesson(chapter, 1), l2 = lesson(chapter, 2), l3 = lesson(chapter, 3);
        UUID enrollmentId = UUID.randomUUID();
        jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status) VALUES (?,?,?,'active')",
                enrollmentId, LEARNER, COURSE);
        return new HistoryFixture(enrollmentId, l1, l2, l3, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    /** One submission-like unit: history inputs read under the caller's transaction, then recorded. */
    private void runInUnit(HistoryFixture fixture, Clock clock, AdaptiveDecision decision) {
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                decisions(clock).recordAndPublish(fixture.context(Map.of()), fixture.enrollment(), decision,
                        currentGuidance(fixture.enrollment()), latestInstant(fixture.enrollment())));
    }

    /** The production service wired to the real history service and publisher, with a test clock (ADR011 §9). */
    private AdaptiveDecisionService decisions(Clock clock) {
        try {
            return BeanUtils.instantiateClass(AdaptiveDecisionService.class.getDeclaredConstructor(
                    RecommendationService.class, ApplicationEventPublisher.class, Clock.class),
                    recommendationService, context, clock);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError(missing);
        }
    }

    private static AdaptiveDecision decisionOf(Recommendation... drafts) {
        return new AdaptiveDecision(Arrays.stream(drafts).filter(draft -> draft.type() == RecommendationType.SKIP)
                .map(Recommendation::targetLessonId).toList(), List.of(drafts));
    }

    private static Recommendation skipDraft(UUID lesson) {
        return new Recommendation(RecommendationType.SKIP, lesson,
                "Tested out: you have mastered every skill in this lesson (\"Skill one\").");
    }

    private static Recommendation continueDraft(UUID lesson) {
        return new Recommendation(RecommendationType.CONTINUE, lesson, RecommendationReasonBuilder.continueInCourseOrder());
    }

    private static Recommendation remedialDraft(UUID lesson) {
        return new Recommendation(RecommendationType.REMEDIAL, lesson, RecommendationReasonBuilder.remedial("Skill two", "Skill one"));
    }

    /** ADR011 §7 current row, read as AD13/AD16 would; two rows sharing the greatest instant are an integrity failure. */
    private Optional<PathRecommendation> currentGuidance(UUID enrollmentId) {
        List<PathRecommendation> rows = history(enrollmentId).stream()
                .filter(row -> row.recommendationType() != RecommendationType.SKIP).limit(2).toList();
        if (rows.size() == 2 && rows.get(0).recommendedAt().equals(rows.get(1).recommendedAt())) {
            throw new IllegalStateException("Two current recommendations share one instant");
        }
        return rows.stream().findFirst();
    }

    private Optional<Instant> latestInstant(UUID enrollmentId) {
        return history(enrollmentId).stream().map(PathRecommendation::recommendedAt).findFirst();
    }

    /** History in ADR011 §9 order, on the current (possibly transactional) connection. */
    private List<PathRecommendation> history(UUID enrollmentId) {
        return jdbc.query("SELECT id, enrollment_id, lesson_id, recommendation_type, reason, recommended_at "
                        + "FROM public.path_recommendations WHERE enrollment_id = ? ORDER BY recommended_at DESC, "
                        + "CASE WHEN recommendation_type = 'skip' THEN 1 ELSE 0 END, lesson_id::text",
                (row, n) -> new PathRecommendation(row.getObject("id", UUID.class), row.getObject("enrollment_id", UUID.class),
                        row.getObject("lesson_id", UUID.class),
                        RecommendationType.valueOf(row.getString("recommendation_type").toUpperCase(java.util.Locale.ROOT)),
                        row.getString("reason"), row.getObject("recommended_at", OffsetDateTime.class).toInstant()),
                enrollmentId);
    }

    /** Independent committed read on a fresh connection. */
    private List<PathRecommendation> committedHistory(UUID enrollmentId) {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        return history(enrollmentId);
    }

    private RecommendationCreatedEvent onlyRecommendationEvent() {
        assertEquals(1, recommendationEvents.received().size());
        return recommendationEvents.received().getFirst();
    }

    /**
     * Test-scoped after-commit subscriber with the phase IN15 uses (ADR012 §8); it only observes
     * delivery and can be told to fail.
     */
    static class RecommendationEventProbe {
        private final List<RecommendationCreatedEvent> received = new CopyOnWriteArrayList<>();
        private volatile RuntimeException failure;

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void onRecommendationCreated(RecommendationCreatedEvent event) {
            received.add(event);
            RuntimeException configured = failure;
            if (configured != null) throw configured;
        }

        List<RecommendationCreatedEvent> received() { return received; }
        void failWith(RuntimeException exception) { failure = exception; }
        void reset() { received.clear(); failure = null; }
    }

    /** Settable UTC test clock, local to one test (ADR008 §6). */
    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;
        MutableClock(Instant start) { now = new AtomicReference<>(start); }
        void set(Instant instant) { now.set(instant); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
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
