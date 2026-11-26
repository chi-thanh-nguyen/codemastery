package com.codemastery.integration;

import com.codemastery.modules.adaptive.application.recommendation.RecommendationService;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision.Recommendation;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;
import com.codemastery.modules.adaptive.domain.repository.LearnerSkillMasteryRepository;
import com.codemastery.modules.course.application.service.LessonProgressService;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QA04: mastery and the related LessonProgress commit and roll back together on real PostgreSQL
 * (ADR011 §§3–5, §16 B6-V01/B6-V02; CT08.execution/failure, CT22.atomic).
 *
 * <p><b>What runs for real.</b> The three approved participants of one submission unit, through
 * their production beans and SQL: Adaptive's mastery write ({@code LearnerSkillMasteryRepository},
 * ADR011 §4 step 5), Course's monotonic skip participant ({@code LessonProgressService.applyMasterySkips},
 * CO15, step 6) and Adaptive's recommendation history append ({@code RecommendationService.appendSelected},
 * step 7). Every outcome is checked by an independent committed read on another connection.
 * Faults are real participant failures (foreign-key violations, the Course participant's own
 * rejection), not mocks.
 *
 * <p><b>What is a stand-in.</b> The orchestration is not implemented on {@code main}: Assessment's
 * submission and {@code QuizSubmittedEvent} (AS08/AS12) and Adaptive's {@code QuizSubmittedEventListener}
 * / {@code MasteryUpdateService} (AD13) are empty, and the learner-submission guard entry
 * {@code withLearnerAssessmentWrite} (ADR007 §3.1) does not exist. {@link #inSubmissionUnit} therefore
 * opens the READ COMMITTED unit and takes the Course root with the same repository calls the guard
 * makes, as the CO15 suites do. These cases are participant-composition evidence only. They do not
 * establish QA04.commit/QA04.rollback acceptance, which still needs a real manual and scheduler
 * submission driving AD13 (B6-V01–V05, V12) once those producers land.
 */
@DirtiesContext
class MasteryProgressTransactionIntegrationTest extends AbstractIntegrationTest {

    private static final String JWT_SECRET = generateSecret();
    private static final UUID OWNER = id("owner"), LEARNER = id("learner"), COURSE = id("course");
    private static final Instant RUN_AT = Instant.parse("2026-11-26T08:00:00.123456Z");

    @Autowired private LearnerSkillMasteryRepository masteries;
    @Autowired private LessonProgressService progress;
    @Autowired private RecommendationService recommendations;
    @Autowired private CourseRepository courses;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    /** L1→S1 absent progress, L2→S2 incomplete row, L3 already completed, H hidden. */
    private UUID enrollment, l1, l2, l3, hidden, s1, s2;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
    }

    @BeforeEach
    void fixture() {
        jdbc.update("INSERT INTO public.users (id,email,password_hash,display_name,role,account_status) VALUES "
                        + "(?,?,'not-a-login-fixture','QA04 owner','instructor','active'),"
                        + "(?,?,'not-a-login-fixture','QA04 learner','learner','active')",
                OWNER, OWNER + "@example.test", LEARNER, LEARNER + "@example.test");
        jdbc.update("INSERT INTO public.courses (id,instructor_id,title,description,publication_status,adaptive_enabled) "
                + "VALUES (?,?,'QA04','Atomic course','published',false)", COURSE, OWNER);
        UUID chapter = UUID.randomUUID();
        jdbc.update("INSERT INTO public.chapters (id,course_id,title,position) VALUES (?,?,'Chapter',1)", chapter, COURSE);
        l1 = lesson(chapter, 1, "published");
        l2 = lesson(chapter, 2, "published");
        l3 = lesson(chapter, 3, "published");
        hidden = lesson(chapter, 4, "hidden");
        s1 = skill("Loops");
        s2 = skill("Functions");
        enrollment = UUID.randomUUID();
        jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status) VALUES (?,?,?,'active')",
                enrollment, LEARNER, COURSE);
        progressRow(l2, false);
        progressRow(l3, true);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM public.path_recommendations WHERE enrollment_id = ?", enrollment);
        jdbc.update("DELETE FROM public.lesson_progress WHERE enrollment_id = ?", enrollment);
        jdbc.update("DELETE FROM public.enrollments WHERE course_id = ?", COURSE);
        jdbc.update("DELETE FROM public.learner_skill_masteries WHERE user_id = ?", LEARNER);
        jdbc.update("DELETE FROM public.skills WHERE course_id = ?", COURSE);
        jdbc.update("DELETE FROM public.lessons WHERE chapter_id IN (SELECT id FROM public.chapters WHERE course_id = ?)", COURSE);
        jdbc.update("DELETE FROM public.chapters WHERE course_id = ?", COURSE);
        jdbc.update("DELETE FROM public.courses WHERE id = ?", COURSE);
        jdbc.update("DELETE FROM public.users WHERE id IN (?,?)", OWNER, LEARNER);
    }

    // QA04.commit (B6-V01 participant shape): one unit commits mastery, skip progress, completion and history together.

    @Test
    void aSuccessfulUnitCommitsMasteryProgressCompletionAndHistoryTogether() {
        String before = committedState();

        inSubmissionUnit(() -> {
            writeAllSteps();
            // Nothing is visible outside the unit until it commits.
            assertEquals(before, committedState());
        });

        assertEquals("mastered,mastered", committedMastery());
        // L1 inserted, L2's incomplete row upgraded (r11 UPDATE (mastery_skipped)), L3 untouched.
        assertEquals(List.of("true:true", "true:true", "true:false"), committedProgress(l1, l2, l3));
        assertEquals("completed", committedEnrollmentStatus());
        assertEquals(2, committedHistoryCount());
    }

    // QA04.rollback (B6-V02 fault points): a failure on either side leaves neither change.

    @Test
    void anAdaptiveMasteryFailureAfterAnEarlierMasteryWriteLeavesNothing() {
        String before = committedState();

        assertThrows(DataIntegrityViolationException.class, () -> inSubmissionUnit(() -> {
            masteries.storeState(LEARNER, s1, MasteryState.MASTERED);
            masteries.storeState(LEARNER, UUID.randomUUID(), MasteryState.MASTERED); // missing skill: FK violation
            progress.applyMasterySkips(COURSE, LEARNER, Set.of(l1));
        }));

        assertEquals(before, committedState());
    }

    @Test
    void aCourseParticipantFailureAfterTheMasteryWritesLeavesNothing() {
        String before = committedState();

        assertThrows(IllegalArgumentException.class, () -> inSubmissionUnit(() -> {
            masteries.storeState(LEARNER, s1, MasteryState.MASTERED);
            masteries.storeState(LEARNER, s2, MasteryState.MASTERED);
            progress.applyMasterySkips(COURSE, LEARNER, Set.of(l1, hidden)); // not a published Lesson
        }));

        assertEquals(before, committedState());
    }

    @Test
    void aRecommendationFailureAfterBothSidesWroteRollsBackMasteryAndProgress() {
        String before = committedState();

        assertThrows(DataIntegrityViolationException.class, () -> inSubmissionUnit(() -> {
            masteries.storeState(LEARNER, s1, MasteryState.MASTERED);
            masteries.storeState(LEARNER, s2, MasteryState.MASTERED);
            progress.applyMasterySkips(COURSE, LEARNER, Set.of(l1, l2));
            recommendations.appendSelected(enrollment, RUN_AT,
                    List.of(skip(l1), skip(UUID.randomUUID()))); // missing Lesson: FK violation
        }));

        assertEquals(before, committedState());
    }

    @Test
    void aFailureRaisedAfterEveryParticipantReturnedStillRollsBackTheWholeUnit() {
        String before = committedState();

        assertThrows(IllegalStateException.class, () -> inSubmissionUnit(() -> {
            writeAllSteps();
            throw new IllegalStateException("result persistence failed after the adaptive steps");
        }));

        assertEquals(before, committedState());
    }

    @Test
    void theCourseParticipantRefusesAUnitThatDoesNotHoldTheCourseRoot() {
        String before = committedState();
        TransactionTemplate unit = readCommittedUnit();

        assertThrows(IllegalStateException.class, () -> unit.executeWithoutResult(status -> {
            masteries.storeState(LEARNER, s1, MasteryState.MASTERED);
            progress.applyMasterySkips(COURSE, LEARNER, Set.of(l1));
        }));

        assertEquals(before, committedState());
    }

    // ---------------------------------------------------------------------------------------------

    /** ADR011 §4 steps 5–7 with the fixture's two Mastered skills. */
    private void writeAllSteps() {
        masteries.storeState(LEARNER, s1, MasteryState.MASTERED);
        masteries.storeState(LEARNER, s2, MasteryState.MASTERED);
        progress.applyMasterySkips(COURSE, LEARNER, Set.of(l1, l2));
        recommendations.appendSelected(enrollment, RUN_AT, List.of(skip(l1), skip(l2)));
    }

    /**
     * Stand-in for the absent submission guard entry: a READ COMMITTED read-write unit that locks
     * the Course root first and records it, exactly as {@code CourseManagementService}'s guard does.
     */
    private void inSubmissionUnit(Runnable work) {
        readCommittedUnit().executeWithoutResult(status -> {
            assertTrue(courses.lockCourseRoot(COURSE));
            courses.markCourseRootHeld(COURSE);
            work.run();
        });
    }

    private TransactionTemplate readCommittedUnit() {
        TransactionTemplate unit = new TransactionTemplate(transactionManager);
        unit.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return unit;
    }

    private static Recommendation skip(UUID lesson) {
        return new Recommendation(RecommendationType.SKIP, lesson,
                "Tested out: you have mastered every skill in this lesson.");
    }

    /** Every row the unit could change, read on a fresh autocommit connection. */
    private String committedState() {
        return committed("SELECT concat_ws(';', "
                + "(SELECT coalesce(string_agg(skill_id || ':' || state, ',' ORDER BY skill_id), '') "
                + "FROM public.learner_skill_masteries WHERE user_id = ?), "
                + "(SELECT coalesce(string_agg(lesson_id || ':' || completed || ':' || mastery_skipped, ',' ORDER BY lesson_id), '') "
                + "FROM public.lesson_progress WHERE enrollment_id = ?), "
                + "(SELECT status FROM public.enrollments WHERE id = ?), "
                + "(SELECT count(*)::text FROM public.path_recommendations WHERE enrollment_id = ?))",
                LEARNER, enrollment, enrollment, enrollment);
    }

    private String committedMastery() {
        return committed("SELECT string_agg(state, ',' ORDER BY state) FROM public.learner_skill_masteries "
                + "WHERE user_id = ? AND skill_id IN (?,?)", LEARNER, s1, s2);
    }

    private List<String> committedProgress(UUID... lessons) {
        return Arrays.stream(lessons).map(lesson -> committed(
                "SELECT completed || ':' || mastery_skipped FROM public.lesson_progress WHERE enrollment_id = ? AND lesson_id = ?",
                enrollment, lesson)).toList();
    }

    private String committedEnrollmentStatus() {
        return committed("SELECT status FROM public.enrollments WHERE id = ?", enrollment);
    }

    private int committedHistoryCount() {
        return Integer.parseInt(committed("SELECT count(*)::text FROM public.path_recommendations WHERE enrollment_id = ?",
                enrollment));
    }

    /** Independent of any Spring-bound transaction on this thread. */
    private String committed(String sql, Object... args) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement read = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) read.setObject(i + 1, args[i]);
            try (ResultSet row = read.executeQuery()) {
                assertTrue(row.next());
                String value = row.getString(1);
                assertFalse(row.next());
                return value;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private UUID lesson(UUID chapter, int position, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.lessons (id,chapter_id,title,position,publication_status) VALUES (?,?,?,?,?)",
                id, chapter, "Lesson " + position, position, status);
        return id;
    }

    private UUID skill(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.skills (id,course_id,name) VALUES (?,?,?)", id, COURSE, name);
        return id;
    }

    private void progressRow(UUID lesson, boolean completed) {
        jdbc.update("INSERT INTO public.lesson_progress (id,enrollment_id,lesson_id,completed,mastery_skipped) VALUES (?,?,?,?,false)",
                UUID.randomUUID(), enrollment, lesson, completed);
    }

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("qa04/" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
