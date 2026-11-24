package com.codemastery.integration.repository;

import com.codemastery.integration.AbstractIntegrationTest;
import com.codemastery.modules.adaptive.application.mastery.MasteryQueryService;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;
import com.codemastery.modules.adaptive.domain.repository.LearnerSkillMasteryRepository;
import com.codemastery.modules.adaptive.domain.repository.PathRecommendationRepository;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.PathRecommendationEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * AD06.database-verification: real-PostgreSQL regression suite for the Adaptive mastery and
 * recommendation-history write adapters (V005; {@code AD06-PERSISTENCE-2026-11-23-r1};
 * {@code CT17-P6-PRIVILEGES-2026-11-23-r8}).
 *
 * <p>The adapters are reached only through their domain interfaces, so the real Spring Data
 * repository beans, their native statements and the Hibernate configuration are exercised. Most
 * tests run in one transaction that is always rolled back. The transaction, rollback and
 * concurrency tests need committed state: they create a committed fixture, read committed state
 * from an independent connection, synchronize on observable PostgreSQL lock waits (never sleeps)
 * and delete their own rows in FK-safe committed cleanup (ADR008 §5).
 *
 * <p>Privilege tests switch the transaction to a {@code NOLOGIN} role that holds exactly the r8
 * runtime rows for {@code learner_skill_masteries} and {@code path_recommendations}; the rollback
 * removes the role and its grants. This proves the adapter SQL needs nothing more. It is not the
 * deployed runtime role: the VM matrix tests cover that.
 *
 * <p>AD16 state read (ADR011 §13, ADR013 §19.4): {@code findStates} and {@code MasteryQueryService.readStates}
 * read one learner's rows for a skill set in one column projection, within the r8 column grant, and
 * map absent rows to {@code Unknown} only in the query service.
 *
 * <p>The context is closed after this class so its connection pool does not stay open next to the
 * other cached integration contexts that share the one per-JVM PostgreSQL container.
 */
@DirtiesContext
class AdaptivePersistenceIntegrationTest extends AbstractIntegrationTest {

    private static final String JWT_SECRET = generateEphemeralJwtSecret();
    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String NOT_NULL_VIOLATION = "23502";
    private static final String CHECK_VIOLATION = "23514";
    private static final long WAIT_SECONDS = 30;
    private static final Instant T0 = Instant.parse("2026-11-23T01:00:00.123456Z");

    /** SQL that Hibernate prepares on the recording thread; {@code null} when not recording. */
    private static final ThreadLocal<List<String>> STATEMENTS = new ThreadLocal<>();

    private static String generateEphemeralJwtSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @DynamicPropertySource
    static void registerTestProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector",
                () -> RecordingStatementInspector.class.getName());
    }

    /** Records every statement Hibernate prepares on a thread that is currently recording. */
    public static class RecordingStatementInspector implements StatementInspector {
        @Override
        public String inspect(String sql) {
            List<String> sink = STATEMENTS.get();
            if (sink != null) {
                sink.add(sql);
            }
            return sql;
        }
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private LearnerSkillMasteryRepository masteries;

    @Autowired
    private PathRecommendationRepository recommendations;

    @Autowired
    private MasteryQueryService masteryQuery;

    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final List<CommittedFixture> committed = new ArrayList<>();

    @AfterEach
    void releaseWorkersAndCommittedRows() {
        workers.shutdownNow();
        for (CommittedFixture f : committed.reversed()) {
            inCommittedTransaction(() -> {
                jdbc.update("DELETE FROM public.path_recommendations WHERE enrollment_id = ?", f.enrollmentId);
                jdbc.update("DELETE FROM public.learner_skill_masteries WHERE user_id IN (?, ?)", f.learnerId, f.otherLearnerId);
                jdbc.update("DELETE FROM public.enrollments WHERE id = ?", f.enrollmentId);
                jdbc.update("DELETE FROM public.skills WHERE course_id = ?", f.courseId);
                jdbc.update("DELETE FROM public.lessons WHERE chapter_id = ?", f.chapterId);
                jdbc.update("DELETE FROM public.chapters WHERE id = ?", f.chapterId);
                jdbc.update("DELETE FROM public.courses WHERE id = ?", f.courseId);
                jdbc.update("DELETE FROM public.users WHERE id IN (?, ?, ?)", f.learnerId, f.otherLearnerId, f.instructorId);
            });
        }
    }

    // ------------------------------------------------------------------- mastery writes

    @Test
    void firstWriteInsertsOneCategoricalRowForTheAssessedSkillOnly() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();

            masteries.storeState(f.learnerId, f.skillA, MasteryState.LEARNING);

            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, user_id, skill_id, state FROM public.learner_skill_masteries WHERE user_id IN (?, ?)",
                    f.learnerId, f.otherLearnerId);
            assertEquals(1, rows.size(), "no default row is written for the unassessed skill or learner");
            Map<String, Object> row = rows.getFirst();
            assertAll(
                    () -> assertTrue(row.get("id") instanceof UUID),
                    () -> assertEquals(f.learnerId, row.get("user_id")),
                    () -> assertEquals(f.skillA, row.get("skill_id")),
                    () -> assertEquals("learning", row.get("state")));
        });
    }

    @Test
    void laterWritesUpdateOnlyTheStateAndKeepTheRowIdentity() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            masteries.storeState(f.learnerId, f.skillA, MasteryState.LEARNING);
            UUID id = masteryId(f.learnerId, f.skillA);

            for (MasteryState state : List.of(MasteryState.MASTERED, MasteryState.WEAK, MasteryState.UNKNOWN,
                    MasteryState.LEARNING, MasteryState.LEARNING)) {
                masteries.storeState(f.learnerId, f.skillA, state);

                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT id, user_id, skill_id, state FROM public.learner_skill_masteries WHERE user_id = ?",
                        f.learnerId);
                assertEquals(1, rows.size(), "one row per (user_id, skill_id)");
                assertEquals(Map.of("id", id, "user_id", f.learnerId, "skill_id", f.skillA, "state", state.getCode()),
                        rows.getFirst());
            }
        });
    }

    @Test
    void everyCategoricalStateIsStoredByItsExactV005Code() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            UUID[] skills = {f.skillA, f.skillB, skill(f.courseId, "C"), skill(f.courseId, "D")};
            MasteryState[] states = MasteryState.values();
            for (int i = 0; i < states.length; i++) {
                masteries.storeState(f.learnerId, skills[i], states[i]);
            }

            assertEquals(List.of("learning", "mastered", "unknown", "weak"), jdbc.queryForList(
                    "SELECT state FROM public.learner_skill_masteries WHERE user_id = ? ORDER BY state", String.class,
                    f.learnerId));
        });
    }

    @Test
    void eachLearnerAndSkillPairIsIndependent() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            masteries.storeState(f.learnerId, f.skillA, MasteryState.MASTERED);
            masteries.storeState(f.learnerId, f.skillB, MasteryState.WEAK);
            masteries.storeState(f.otherLearnerId, f.skillA, MasteryState.LEARNING);
            masteries.storeState(f.otherLearnerId, f.skillB, MasteryState.UNKNOWN);

            masteries.storeState(f.learnerId, f.skillA, MasteryState.WEAK);

            assertEquals(List.of(
                            Map.of("user_id", f.learnerId, "skill_id", f.skillA, "state", "weak"),
                            Map.of("user_id", f.learnerId, "skill_id", f.skillB, "state", "weak"),
                            Map.of("user_id", f.otherLearnerId, "skill_id", f.skillA, "state", "learning"),
                            Map.of("user_id", f.otherLearnerId, "skill_id", f.skillB, "state", "unknown")),
                    jdbc.queryForList("SELECT user_id, skill_id, state FROM public.learner_skill_masteries "
                                    + "WHERE user_id IN (?, ?) ORDER BY user_id = ? DESC, skill_id = ? DESC",
                            f.learnerId, f.otherLearnerId, f.learnerId, f.skillA));
        });
    }

    @Test
    void masteryWriteIssuesOneUpsertAndNoReadOrFullRowUpdateOnEitherBranch() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();

            List<String> insertBranch = recording(() -> masteries.storeState(f.learnerId, f.skillA, MasteryState.LEARNING));
            List<String> updateBranch = recording(() -> masteries.storeState(f.learnerId, f.skillA, MasteryState.MASTERED));

            for (List<String> statements : List.of(insertBranch, updateBranch)) {
                assertEquals(1, statements.size(), () -> "exactly one statement, got " + statements);
                String sql = normalized(statements.getFirst());
                assertTrue(sql.startsWith("insert into public.learner_skill_masteries (id, user_id, skill_id, state) values"), sql);
                assertTrue(sql.endsWith("on conflict (user_id, skill_id) do update set state = excluded.state"), sql);
            }
            assertEquals("mastered", jdbc.queryForObject(
                    "SELECT state FROM public.learner_skill_masteries WHERE user_id = ? AND skill_id = ?", String.class,
                    f.learnerId, f.skillA));
        });
    }

    @Test
    void masteryForeignKeysAndTheV005StateCheckAreEnforcedByPostgreSql() {
        assertSqlState(FOREIGN_KEY_VIOLATION, "fk_learner_skill_masteries_user_id",
                f -> masteries.storeState(UUID.randomUUID(), f.skillA, MasteryState.LEARNING));
        assertSqlState(FOREIGN_KEY_VIOLATION, "fk_learner_skill_masteries_skill_id",
                f -> masteries.storeState(f.learnerId, UUID.randomUUID(), MasteryState.LEARNING));
        assertSqlState(CHECK_VIOLATION, "ck_learner_skill_masteries_state",
                f -> jdbc.update("INSERT INTO public.learner_skill_masteries (id, user_id, skill_id, state) "
                        + "VALUES (?, ?, ?, 'Mastered')", UUID.randomUUID(), f.learnerId, f.skillA));
        assertSqlState(UNIQUE_VIOLATION, "uq_learner_skill_masteries_user_skill",
                f -> jdbc.update("INSERT INTO public.learner_skill_masteries (id, user_id, skill_id, state) "
                        + "VALUES (?, ?, ?, 'weak'), (?, ?, ?, 'learning')",
                        UUID.randomUUID(), f.learnerId, f.skillA, UUID.randomUUID(), f.learnerId, f.skillA));
    }

    @Test
    void nullMasteryArgumentsAreRejectedBeforeAnySql() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            List<String> statements = recording(() -> {
                assertThrows(NullPointerException.class, () -> masteries.storeState(null, f.skillA, MasteryState.WEAK));
                assertThrows(NullPointerException.class, () -> masteries.storeState(f.learnerId, null, MasteryState.WEAK));
                assertThrows(NullPointerException.class, () -> masteries.storeState(f.learnerId, f.skillA, null));
            });
            assertEquals(List.of(), statements);
            assertEquals(0, masteryCount(f.learnerId));
        });
    }

    @Test
    void concurrentFirstWritesOfOnePairConvergeOnOneRowWithoutAUniqueViolation() throws Exception {
        CommittedFixture f = committedFixture();
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        Future<?> first = workers.submit(() -> inCommittedTransaction(() -> {
            masteries.storeState(f.learnerId, f.skillA, MasteryState.LEARNING);
            firstWritten.countDown();
            await(releaseFirst);
        }));
        await(firstWritten);
        Future<?> second;
        try {
            second = workers.submit(() -> inCommittedTransaction(
                    () -> masteries.storeState(f.learnerId, f.skillA, MasteryState.WEAK)));
            // The second writer's speculative insert waits on the first writer's uncommitted key.
            awaitLockWaiters(1);
            assertEquals(0, committedMasteryCount(f.learnerId), "the first writer has not committed yet");
        } finally {
            releaseFirst.countDown();
        }
        first.get(WAIT_SECONDS, TimeUnit.SECONDS);
        second.get(WAIT_SECONDS, TimeUnit.SECONDS);

        List<Map<String, Object>> rows = committedRows(f.learnerId);
        assertEquals(1, rows.size(), () -> "one row, got " + rows);
        assertEquals("weak", rows.getFirst().get("state"), "the later writer's state wins after the first commits");
    }

    @Test
    void writesOutsideACallerTransactionAreRefusedAndLeaveNoRow() {
        CommittedFixture f = committedFixture();
        Recommendation r = recommendation(f.enrollmentId, f.lessonId, RecommendationType.CONTINUE, T0);

        assertThrows(IllegalTransactionStateException.class,
                () -> masteries.storeState(f.learnerId, f.skillA, MasteryState.WEAK));
        assertThrows(IllegalTransactionStateException.class, () -> recommendations.append(r.domain));

        assertEquals(0, committedMasteryCount(f.learnerId));
        assertEquals(0, committedRecommendationCount(f.enrollmentId));
    }

    // ------------------------------------------------------------------- AD16 state read

    @Test
    void stateReadReturnsOnlyTheLearnersRequestedRowsInOneColumnProjection() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            UUID skillC = skill(f.courseId, "C");
            masteries.storeState(f.learnerId, f.skillA, MasteryState.MASTERED);
            masteries.storeState(f.learnerId, skillC, MasteryState.WEAK);
            masteries.storeState(f.otherLearnerId, f.skillB, MasteryState.LEARNING);

            List<String> statements = recording(() -> assertEquals(Map.of(f.skillA, MasteryState.MASTERED),
                    masteries.findStates(f.learnerId, List.of(f.skillA, f.skillB, f.skillA))));

            assertEquals(1, statements.size(), () -> "exactly one statement, got " + statements);
            String sql = normalized(statements.getFirst());
            assertTrue(sql.startsWith("select skill_id, state from public.learner_skill_masteries "
                    + "where user_id = ? and skill_id in ("), sql);
            assertEquals(Map.of(f.skillA, MasteryState.MASTERED, skillC, MasteryState.WEAK),
                    masteries.findStates(f.learnerId, Set.of(f.skillA, f.skillB, skillC)));
            assertEquals(Map.of(f.skillB, MasteryState.LEARNING),
                    masteries.findStates(f.otherLearnerId, Set.of(f.skillA, f.skillB)), "another learner's rows only");
            assertEquals(List.of(), recording(() -> assertEquals(Map.of(), masteries.findStates(f.learnerId, Set.of()))));
            assertThrows(UnsupportedOperationException.class, () -> masteries.findStates(f.learnerId, Set.of(f.skillA)).clear());
            assertThrows(NullPointerException.class, () -> masteries.findStates(null, Set.of(f.skillA)));
            assertThrows(NullPointerException.class, () -> masteries.findStates(f.learnerId, null));
            // @Repository exception translation reports the rejected argument as a data-access usage error.
            assertThrows(InvalidDataAccessApiUsageException.class,
                    () -> masteries.findStates(f.learnerId, Arrays.asList(f.skillA, null)));
        });
    }

    @Test
    void stateReadNeedsOnlyTheR8ColumnGrantAndACallerTransaction() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            masteries.storeState(f.learnerId, f.skillA, MasteryState.LEARNING);
            switchToR8Role();

            assertEquals(Map.of(f.skillA, MasteryState.LEARNING), masteries.findStates(f.learnerId, Set.of(f.skillA, f.skillB)));
            assertFalse(Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class)),
                    "the read above ran as the restricted role");
        });
        assertThrows(IllegalTransactionStateException.class,
                () -> masteries.findStates(UUID.randomUUID(), Set.of(UUID.randomUUID())));
    }

    @Test
    void queryServiceReturnsEveryRequestedSkillWithAbsentRowsAsUnknownInTheCallersReadOnlySnapshot() {
        CommittedFixture f = committedFixture();
        UUID[] skillB = new UUID[1];
        inCommittedTransaction(() -> {
            skillB[0] = skill(f.courseId, "B");
            masteries.storeState(f.learnerId, f.skillA, MasteryState.WEAK);
            masteries.storeState(f.otherLearnerId, skillB[0], MasteryState.MASTERED);
        });

        Map<UUID, MasteryState> states = snapshot(TransactionDefinition.ISOLATION_REPEATABLE_READ, true, () -> {
            Map<UUID, MasteryState> first = masteryQuery.readStates(f.learnerId, List.of(f.skillA, skillB[0]));
            // A concurrent commit is invisible inside the caller's snapshot.
            onWorker(() -> {
                inCommittedTransaction(() -> masteries.storeState(f.learnerId, skillB[0], MasteryState.LEARNING));
                return null;
            });
            assertEquals(first, masteryQuery.readStates(f.learnerId, Set.of(f.skillA, skillB[0])));
            return first;
        });

        assertEquals(Map.of(f.skillA, MasteryState.WEAK, skillB[0], MasteryState.UNKNOWN), states);
        assertThrows(UnsupportedOperationException.class, states::clear);
        assertEquals(Map.of(f.skillA, MasteryState.WEAK, skillB[0], MasteryState.LEARNING),
                snapshot(TransactionDefinition.ISOLATION_REPEATABLE_READ, true,
                        () -> masteryQuery.readStates(f.learnerId, Set.of(f.skillA, skillB[0]))), "the next snapshot sees it");
        assertEquals(Map.of(), snapshot(TransactionDefinition.ISOLATION_REPEATABLE_READ, true,
                () -> masteryQuery.readStates(f.learnerId, Set.of())));
    }

    @Test
    void queryServiceRefusesAnyCallerContextOtherThanAReadOnlyRepeatableReadTransaction() {
        UUID learner = UUID.randomUUID();
        Set<UUID> skills = Set.of(UUID.randomUUID());

        assertThrows(IllegalTransactionStateException.class, () -> masteryQuery.readStates(learner, skills));
        assertThrows(IllegalStateException.class, () -> snapshot(TransactionDefinition.ISOLATION_REPEATABLE_READ, false,
                () -> masteryQuery.readStates(learner, skills)));
        assertThrows(IllegalStateException.class, () -> snapshot(TransactionDefinition.ISOLATION_READ_COMMITTED, true,
                () -> masteryQuery.readStates(learner, skills)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(TransactionDefinition.ISOLATION_REPEATABLE_READ, true,
                () -> masteryQuery.readStates(learner, Arrays.asList(UUID.randomUUID(), null))));
    }

    // ------------------------------------------------------------------- recommendation history

    @Test
    void appendInsertsEveryCallerSuppliedFieldUnchanged() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            List<Recommendation> appended = List.of(
                    recommendation(f.enrollmentId, f.lessonId, RecommendationType.CONTINUE, T0),
                    recommendation(f.enrollmentId, f.otherLessonId, RecommendationType.SKIP, T0.plusSeconds(1)),
                    recommendation(f.enrollmentId, f.lessonId, RecommendationType.REMEDIAL, T0.plusSeconds(2)));

            appended.forEach(r -> recommendations.append(r.domain));

            for (Recommendation r : appended) {
                Map<String, Object> row = jdbc.queryForMap("SELECT * FROM public.path_recommendations WHERE id = ?", r.domain.id());
                assertAll(
                        () -> assertEquals(r.domain.enrollmentId(), row.get("enrollment_id")),
                        () -> assertEquals(r.domain.lessonId(), row.get("lesson_id")),
                        () -> assertEquals(r.domain.recommendationType().getCode(), row.get("recommendation_type")),
                        () -> assertEquals(r.domain.reason(), row.get("reason")),
                        () -> assertEquals(r.domain.recommendedAt(), instant(row.get("recommended_at"))));
            }
            assertEquals(List.of("continue", "remedial", "skip"), jdbc.queryForList(
                    "SELECT recommendation_type FROM public.path_recommendations WHERE enrollment_id = ? "
                            + "ORDER BY recommendation_type", String.class, f.enrollmentId));
        });
    }

    @Test
    void identicalRecommendationsWithDistinctIdsStaySeparateHistoryRows() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            // One processing run shares its instant (ADR011 §9); the adapter neither merges nor de-duplicates.
            Recommendation a = recommendation(f.enrollmentId, f.lessonId, RecommendationType.SKIP, T0);
            Recommendation b = recommendation(f.enrollmentId, f.lessonId, RecommendationType.SKIP, T0);
            Recommendation c = recommendation(f.enrollmentId, f.lessonId, RecommendationType.SKIP, T0.plusSeconds(5));

            List.of(a, b, c).forEach(r -> recommendations.append(r.domain));

            assertEquals(3, recommendationCount(f.enrollmentId));
            assertEquals(java.util.Set.of(a.domain.id(), b.domain.id(), c.domain.id()), java.util.Set.copyOf(
                    jdbc.queryForList("SELECT id FROM public.path_recommendations WHERE enrollment_id = ?",
                            UUID.class, f.enrollmentId)));
        });
    }

    @Test
    void appendingAnExistingIdFailsAndNeverRewritesTheCommittedRow() {
        CommittedFixture f = committedFixture();
        Recommendation original = recommendation(f.enrollmentId, f.lessonId, RecommendationType.CONTINUE, T0);
        inCommittedTransaction(() -> recommendations.append(original.domain));
        PathRecommendation rewrite = new PathRecommendation(original.domain.id(), f.enrollmentId, f.otherLessonId,
                RecommendationType.REMEDIAL, "A different reason.", T0.plusSeconds(60));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> inCommittedTransaction(() -> recommendations.append(rewrite)));

        assertTrue(thrown instanceof DataIntegrityViolationException, () -> "unexpected " + causeMessages(thrown));
        assertSqlStateIn(thrown, UNIQUE_VIOLATION, "pk_path_recommendations");
        List<Map<String, Object>> rows = jdbcRowsOnWorker(
                "SELECT lesson_id, recommendation_type, reason, recommended_at FROM public.path_recommendations "
                        + "WHERE enrollment_id = ?", f.enrollmentId);
        assertEquals(1, rows.size());
        assertAll(
                () -> assertEquals(f.lessonId, rows.getFirst().get("lesson_id")),
                () -> assertEquals("continue", rows.getFirst().get("recommendation_type")),
                () -> assertEquals(original.domain.reason(), rows.getFirst().get("reason")),
                () -> assertEquals(T0, instant(rows.getFirst().get("recommended_at"))));
    }

    @Test
    void appendingTheSameIdTwiceInOneTransactionFailsAtTheSecondCall() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            Recommendation r = recommendation(f.enrollmentId, f.lessonId, RecommendationType.CONTINUE, T0);
            recommendations.append(r.domain);

            DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                    () -> recommendations.append(r.domain));
            assertSqlStateIn(thrown, UNIQUE_VIOLATION, "pk_path_recommendations");
        });
    }

    @Test
    void historyWriteIssuesExactlyOneInsertAndNoReadUpdateOrDelete() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            Recommendation r = recommendation(f.enrollmentId, f.lessonId, RecommendationType.REMEDIAL, T0);

            List<String> statements = recording(() -> recommendations.append(r.domain));

            assertEquals(1, statements.size(), () -> "exactly one statement, got " + statements);
            String sql = normalized(statements.getFirst());
            assertTrue(sql.startsWith("insert into public.path_recommendations "
                    + "(id, enrollment_id, lesson_id, recommendation_type, reason, recommended_at) values"), sql);
            assertFalse(sql.contains("on conflict"), sql);
        });
    }

    @Test
    void historyForeignKeysNotNullAndTextChecksAreEnforcedByPostgreSql() {
        assertSqlState(FOREIGN_KEY_VIOLATION, "fk_path_recommendations_enrollment_id", f ->
                recommendations.append(recommendation(UUID.randomUUID(), f.lessonId, RecommendationType.CONTINUE, T0).domain));
        assertSqlState(FOREIGN_KEY_VIOLATION, "fk_path_recommendations_lesson_id", f ->
                recommendations.append(recommendation(f.enrollmentId, UUID.randomUUID(), RecommendationType.CONTINUE, T0).domain));
        assertSqlState(CHECK_VIOLATION, "ck_path_recommendations_text", f ->
                recommendations.append(new PathRecommendation(UUID.randomUUID(), f.enrollmentId, f.lessonId,
                        RecommendationType.CONTINUE, " \n\t", T0)));
        assertSqlState(NOT_NULL_VIOLATION, "recommendation_type", f ->
                jdbc.update("INSERT INTO public.path_recommendations VALUES (?, ?, ?, NULL, 'r', now())",
                        UUID.randomUUID(), f.enrollmentId, f.lessonId));
        assertSqlState(CHECK_VIOLATION, "ck_path_recommendations_type", f ->
                jdbc.update("INSERT INTO public.path_recommendations VALUES (?, ?, ?, 'Continue', 'r', now())",
                        UUID.randomUUID(), f.enrollmentId, f.lessonId));
    }

    @Test
    void invalidRecommendationsAreRejectedBeforeAnySql() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            List<String> statements = recording(() -> {
                assertThrows(NullPointerException.class, () -> recommendations.append(null));
                for (PathRecommendation incomplete : List.of(
                        new PathRecommendation(null, f.enrollmentId, f.lessonId, RecommendationType.CONTINUE, "Continue.", T0),
                        new PathRecommendation(UUID.randomUUID(), null, f.lessonId, RecommendationType.CONTINUE, "Continue.", T0),
                        new PathRecommendation(UUID.randomUUID(), f.enrollmentId, null, RecommendationType.CONTINUE, "Continue.", T0),
                        new PathRecommendation(UUID.randomUUID(), f.enrollmentId, f.lessonId, null, "Continue.", T0),
                        new PathRecommendation(UUID.randomUUID(), f.enrollmentId, f.lessonId, RecommendationType.CONTINUE, null, T0),
                        new PathRecommendation(UUID.randomUUID(), f.enrollmentId, f.lessonId, RecommendationType.CONTINUE, "Continue.", null))) {
                    assertThrows(NullPointerException.class, () -> recommendations.append(incomplete));
                }
                // PostgreSQL would round a sub-microsecond instant; the adapter never alters a supplied time.
                // Spring's @Repository translation wraps the IllegalArgumentException.
                InvalidDataAccessApiUsageException subMicro = assertThrows(InvalidDataAccessApiUsageException.class,
                        () -> recommendations.append(new PathRecommendation(UUID.randomUUID(), f.enrollmentId,
                                f.lessonId, RecommendationType.CONTINUE, "Continue.", T0.plusNanos(1))));
                assertTrue(subMicro.getCause() instanceof IllegalArgumentException, () -> causeMessages(subMicro));
                assertTrue(subMicro.getCause().getMessage().contains("microsecond"), subMicro.getCause().getMessage());
            });
            assertEquals(List.of(), statements);
            assertEquals(0, recommendationCount(f.enrollmentId));
        });
    }

    // ------------------------------------------------------------------- caller-owned transaction

    @Test
    void writesJoinTheCallerTransactionAndRollBackWithIt() throws Exception {
        CommittedFixture f = committedFixture();
        Recommendation r = recommendation(f.enrollmentId, f.lessonId, RecommendationType.CONTINUE, T0);
        IllegalStateException induced = new IllegalStateException("induced failure after the writes");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> inCommittedTransaction(() -> {
            masteries.storeState(f.learnerId, f.skillA, MasteryState.MASTERED);
            recommendations.append(r.domain);
            assertEquals(1, masteryCount(f.learnerId), "visible inside the caller's transaction");
            assertEquals(1, recommendationCount(f.enrollmentId), "visible inside the caller's transaction");
            assertEquals(0, committedMasteryCount(f.learnerId), "not committed by the adapter");
            assertEquals(0, committedRecommendationCount(f.enrollmentId), "not committed by the adapter");
            throw induced;
        }));

        assertEquals(induced, thrown);
        assertEquals(0, committedMasteryCount(f.learnerId), "rolled back with the caller");
        assertEquals(0, committedRecommendationCount(f.enrollmentId), "rolled back with the caller");

        inCommittedTransaction(() -> {
            masteries.storeState(f.learnerId, f.skillA, MasteryState.MASTERED);
            recommendations.append(r.domain);
        });
        assertEquals(1, committedMasteryCount(f.learnerId), "committed with the caller");
        assertEquals(1, committedRecommendationCount(f.enrollmentId), "committed with the caller");
    }

    // ------------------------------------------------------------------- CT17-P6 r8 privileges

    @Test
    void adapterSqlRunsUnderExactlyTheR8RuntimePrivileges() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            switchToR8Role();

            masteries.storeState(f.learnerId, f.skillA, MasteryState.LEARNING);
            masteries.storeState(f.learnerId, f.skillA, MasteryState.MASTERED);
            masteries.storeState(f.learnerId, f.skillB, MasteryState.UNKNOWN);
            recommendations.append(recommendation(f.enrollmentId, f.lessonId, RecommendationType.SKIP, T0).domain);
            recommendations.append(recommendation(f.enrollmentId, f.otherLessonId, RecommendationType.CONTINUE, T0).domain);
            assertFalse(Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class)),
                    "statements above ran as the restricted role");

            jdbc.execute("RESET ROLE");
            assertEquals(List.of("mastered", "unknown"), jdbc.queryForList(
                    "SELECT state FROM public.learner_skill_masteries WHERE user_id = ? ORDER BY state", String.class,
                    f.learnerId));
            assertEquals(2, recommendationCount(f.enrollmentId));
        });
    }

    @Test
    void r8PrivilegesDenyIdentityRewritesDeletesReadsAndHistoryMutations() {
        List<String> denied = List.of(
                "UPDATE public.learner_skill_masteries SET user_id = user_id",
                "UPDATE public.learner_skill_masteries SET skill_id = skill_id",
                "UPDATE public.learner_skill_masteries SET id = id",
                "DELETE FROM public.learner_skill_masteries",
                "TRUNCATE public.learner_skill_masteries",
                "SELECT id FROM public.learner_skill_masteries",
                "UPDATE public.path_recommendations SET reason = reason",
                "UPDATE public.path_recommendations SET recommended_at = recommended_at, recommendation_type = recommendation_type",
                "DELETE FROM public.path_recommendations",
                "TRUNCATE public.path_recommendations",
                "SELECT id FROM public.path_recommendations");
        for (String sql : denied) {
            assertInsufficientPrivilege(sql, f -> {
                masteries.storeState(f.learnerId, f.skillA, MasteryState.WEAK);
                recommendations.append(recommendation(f.enrollmentId, f.lessonId, RecommendationType.CONTINUE, T0).domain);
                switchToR8Role();
                jdbc.execute(sql);
            });
        }
    }

    @Test
    void r8PrivilegesRejectTheFullRowUpdateThatAMergeBasedAppendWouldIssue() {
        assertInsufficientPrivilege("merge of an existing history id", f -> {
            Recommendation r = recommendation(f.enrollmentId, f.lessonId, RecommendationType.CONTINUE, T0);
            recommendations.append(r.domain);
            switchToR8Role();
            entityManager.merge(new PathRecommendationEntity(r.domain.id(), f.enrollmentId, f.otherLessonId,
                    "remedial", "Rewritten.", T0.plusSeconds(1)));
            entityManager.flush();
        });
    }

    // ------------------------------------------------------------------- helpers

    private record Fixture(UUID learnerId, UUID otherLearnerId, UUID courseId, UUID lessonId, UUID otherLessonId,
                           UUID skillA, UUID skillB, UUID enrollmentId) {
    }

    private record CommittedFixture(UUID learnerId, UUID otherLearnerId, UUID instructorId, UUID courseId,
                                    UUID chapterId, UUID lessonId, UUID otherLessonId, UUID skillA, UUID enrollmentId) {
    }

    private record Recommendation(PathRecommendation domain) {
    }

    private static Recommendation recommendation(UUID enrollmentId, UUID lessonId, RecommendationType type, Instant at) {
        String reason = switch (type) {
            case CONTINUE -> "Continue with the next lesson in the course order.";
            case SKIP -> "Tested out: you have mastered every skill in this lesson (\"Loops\").";
            case REMEDIAL -> "Review this lesson first: your skill \"Loops\" is Weak and its prerequisite "
                    + "\"Variables\" is not yet Mastered.";
        };
        return new Recommendation(new PathRecommendation(UUID.randomUUID(), enrollmentId, lessonId, type, reason, at));
    }

    private Fixture fixture() {
        UUID learner = user("learner");
        UUID other = user("learner");
        UUID instructor = user("instructor");
        UUID course = course(instructor);
        UUID chapter = chapter(course);
        UUID lesson = lesson(chapter, 1);
        UUID otherLesson = lesson(chapter, 2);
        return new Fixture(learner, other, course, lesson, otherLesson, skill(course, "A"), skill(course, "B"),
                enrollment(learner, course));
    }

    private CommittedFixture committedFixture() {
        CommittedFixture[] holder = new CommittedFixture[1];
        inCommittedTransaction(() -> {
            UUID learner = user("learner");
            UUID other = user("learner");
            UUID instructor = user("instructor");
            UUID course = course(instructor);
            UUID chapter = chapter(course);
            holder[0] = new CommittedFixture(learner, other, instructor, course, chapter, lesson(chapter, 1),
                    lesson(chapter, 2), skill(course, "A"), enrollment(learner, course));
        });
        committed.add(holder[0]);
        return holder[0];
    }

    private UUID user(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.users (id, email, password_hash, display_name, role, account_status) "
                + "VALUES (?, ?, 'not-a-real-hash', 'AD06 user', ?, 'active')", id, "ad06-" + id + "@example.test", role);
        return id;
    }

    private UUID course(UUID instructor) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.courses (id, instructor_id, title, description, publication_status, adaptive_enabled) "
                + "VALUES (?, ?, 'AD06 course', 'AD06 repository fixture', 'published', false)", id, instructor);
        return id;
    }

    private UUID chapter(UUID course) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.chapters (id, course_id, title, position) VALUES (?, ?, 'Chapter', 1)", id, course);
        return id;
    }

    private UUID lesson(UUID chapter, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.lessons (id, chapter_id, title, position, publication_status) "
                + "VALUES (?, ?, 'Lesson', ?, 'published')", id, chapter, position);
        return id;
    }

    private UUID skill(UUID course, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.skills (id, course_id, name) VALUES (?, ?, ?)", id, course, name);
        return id;
    }

    private UUID enrollment(UUID learner, UUID course) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.enrollments (id, user_id, course_id, status) VALUES (?, ?, ?, 'active')",
                id, learner, course);
        return id;
    }

    /**
     * Switches the current transaction to a new {@code NOLOGIN} role holding exactly the
     * {@code CT17-P6-PRIVILEGES-2026-11-23-r8} rows. Role, grants and the switch end with the rollback.
     */
    private void switchToR8Role() {
        entityManager.flush();
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname = current_user",
                Boolean.class), "the disposable test container user creates the test-scoped role");
        String role = "ad06_r8_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE ROLE " + role + " NOLOGIN");
        jdbc.execute("GRANT USAGE ON SCHEMA public TO " + role);
        jdbc.execute("GRANT INSERT, SELECT (user_id, skill_id, state), UPDATE (state) ON public.learner_skill_masteries TO " + role);
        jdbc.execute("GRANT INSERT ON public.path_recommendations TO " + role);
        jdbc.execute("SET LOCAL ROLE " + role);
    }

    private void inRolledBackTransaction(Runnable body) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            body.run();
        });
    }

    private void inCommittedTransaction(Runnable body) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> body.run());
    }

    private <T> T snapshot(int isolation, boolean readOnly, java.util.function.Supplier<T> body) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(isolation);
        template.setReadOnly(readOnly);
        return template.execute(status -> body.get());
    }

    private List<String> recording(Runnable body) {
        entityManager.flush();
        List<String> sink = new ArrayList<>();
        STATEMENTS.set(sink);
        try {
            body.run();
        } finally {
            STATEMENTS.remove();
        }
        return List.copyOf(sink);
    }

    private static String normalized(String sql) {
        return sql.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private UUID masteryId(UUID learnerId, UUID skillId) {
        return jdbc.queryForObject("SELECT id FROM public.learner_skill_masteries WHERE user_id = ? AND skill_id = ?",
                UUID.class, learnerId, skillId);
    }

    private int masteryCount(UUID learnerId) {
        return jdbc.queryForObject("SELECT count(*) FROM public.learner_skill_masteries WHERE user_id = ?",
                Integer.class, learnerId);
    }

    private int recommendationCount(UUID enrollmentId) {
        return jdbc.queryForObject("SELECT count(*) FROM public.path_recommendations WHERE enrollment_id = ?",
                Integer.class, enrollmentId);
    }

    /** Committed state, read on a worker thread that has no transaction and therefore its own connection. */
    private int committedMasteryCount(UUID learnerId) {
        return onWorker(() -> masteryCount(learnerId));
    }

    private int committedRecommendationCount(UUID enrollmentId) {
        return onWorker(() -> recommendationCount(enrollmentId));
    }

    private List<Map<String, Object>> committedRows(UUID learnerId) {
        return jdbcRowsOnWorker("SELECT id, skill_id, state FROM public.learner_skill_masteries WHERE user_id = ?", learnerId);
    }

    private List<Map<String, Object>> jdbcRowsOnWorker(String sql, Object... args) {
        return onWorker(() -> jdbc.queryForList(sql, args));
    }

    private <T> T onWorker(java.util.concurrent.Callable<T> read) {
        try {
            return workers.submit(read).get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (ExecutionException | java.util.concurrent.TimeoutException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Barrier on observable PostgreSQL state: the expected number of backends waiting for a lock. */
    private void awaitLockWaiters(int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (onWorker(() -> jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                + "WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class)) < expected) {
            if (System.nanoTime() > deadline) {
                fail("Expected " + expected + " lock waiter(s)");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private void assertSqlState(String sqlState, String constraint, Consumer<Fixture> body) {
        Throwable thrown = assertThrows(RuntimeException.class, () -> inRolledBackTransaction(() -> {
            body.accept(fixture());
            entityManager.flush();
        }));
        assertSqlStateIn(thrown, sqlState, constraint);
    }

    private static void assertSqlStateIn(Throwable thrown, String sqlState, String messageFragment) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
                assertTrue(String.valueOf(sql.getMessage()).contains(messageFragment),
                        () -> "expected " + messageFragment + " in " + sql.getMessage());
                return;
            }
        }
        fail("expected SQLSTATE " + sqlState + " but got " + causeMessages(thrown));
    }

    private void assertInsufficientPrivilege(String label, Consumer<Fixture> body) {
        Throwable thrown = assertThrows(RuntimeException.class,
                () -> inRolledBackTransaction(() -> body.accept(fixture())), label);
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && INSUFFICIENT_PRIVILEGE.equals(sql.getSQLState())) {
                return;
            }
        }
        fail(label + ": expected SQLSTATE 42501 but got " + causeMessages(thrown));
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
