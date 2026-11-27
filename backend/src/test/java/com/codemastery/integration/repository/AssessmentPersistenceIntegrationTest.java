package com.codemastery.integration.repository;

import com.codemastery.integration.AbstractIntegrationTest;
import com.codemastery.modules.assessment.application.service.QuizService;
import com.codemastery.modules.assessment.application.service.QuizService.PracticeQuizProfile;
import com.codemastery.modules.assessment.domain.model.Assignment;
import com.codemastery.modules.assessment.domain.model.Attempt;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.model.Quiz;
import com.codemastery.modules.assessment.domain.repository.AssignmentRepository;
import com.codemastery.modules.assessment.domain.repository.AssignmentRepository.SubmissionCounts;
import com.codemastery.modules.assessment.domain.repository.AttemptRepository;
import com.codemastery.modules.assessment.domain.repository.QuestionRepository;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.AssignmentEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.AttemptAnswerEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.AttemptEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuestionEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuizEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuizQuestionEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.SubmissionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class AssessmentPersistenceIntegrationTest extends AbstractIntegrationTest {

    private static final String JWT_SECRET = ephemeralJwtSecret();
    private static final Instant START = Instant.parse("2026-11-11T03:00:00.123456Z");
    private static final ThreadLocal<List<String>> STATEMENTS = new ThreadLocal<>();

    @DynamicPropertySource
    static void registerTestProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector",
                () -> RecordingStatementInspector.class.getName());
    }

    public static class RecordingStatementInspector implements StatementInspector {
        @Override
        public String inspect(String sql) {
            List<String> recorded = STATEMENTS.get();
            if (recorded != null) {
                recorded.add(sql);
            }
            return sql;
        }
    }

    @AfterEach
    void clearRecordedStatements() {
        STATEMENTS.remove();
    }

    private static String ephemeralJwtSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private AttemptRepository attemptRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private QuizService quizService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @ParameterizedTest
    @EnumSource(DifficultyLevel.class)
    void questionArraysAndCodesRoundTripForEveryApprovedKindAndDifficulty(DifficultyLevel difficulty) {
        UUID courseId = course();
        UUID skillId = UUID.randomUUID();
        jdbc.update("INSERT INTO skills (id, course_id, name, description) VALUES (?, ?, ?, NULL)",
                skillId, courseId, "Fixture skill");

        for (String kind : List.of("single_choice", "multiple_choice", "true_false")) {
            String[] options = kind.equals("true_false") ? new String[]{"True", "False"}
                    : new String[]{"comma, quote \" and slash \\", "Unicode: λ", "x".repeat(300)};
            Boolean[] key = kind.equals("true_false") ? new Boolean[]{false, true}
                    : new Boolean[]{true, kind.equals("multiple_choice"), false};
            UUID id = UUID.randomUUID();
            entityManager.persist(new QuestionEntity(id, skillId, "Predict the output: " + "p".repeat(300),
                    kind, difficulty.getCode(), options, key));
            entityManager.flush();
            entityManager.clear();

            QuestionEntity reloaded = entityManager.find(QuestionEntity.class, id);
            assertAll(
                    () -> assertEquals(skillId, reloaded.getSkillId()),
                    () -> assertEquals(kind, reloaded.getKind()),
                    () -> assertEquals(difficulty.getCode(), reloaded.getDifficulty()),
                    () -> assertEquals("Predict the output: " + "p".repeat(300), reloaded.getPrompt()),
                    () -> assertArrayEquals(options, reloaded.getOptions()),
                    () -> assertArrayEquals(key, reloaded.getCorrectOptions()),
                    () -> assertEquals("text[]/boolean[]", jdbc.queryForObject(
                            "SELECT pg_typeof(options)::text || '/' || pg_typeof(correct_options)::text "
                                    + "FROM questions WHERE id = ?", String.class, id)),
                    () -> assertEquals(1, jdbc.queryForObject(
                            "SELECT array_lower(options, 1) FROM questions WHERE id = ?", Integer.class, id)));
        }
    }

    @Test
    void quizAssociationAttemptAndAnswerMappingsPreserveReferencesOrderMasksAndTerminalFields() {
        UUID courseId = course();
        UUID learnerId = user("learner");
        UUID skillId = UUID.randomUUID();
        jdbc.update("INSERT INTO skills (id, course_id, name) VALUES (?, ?, ?)", skillId, courseId, "Fixture skill");
        UUID questionId = UUID.randomUUID();
        entityManager.persist(new QuestionEntity(questionId, skillId, "Choose one", "single_choice", "easy",
                new String[]{"A", "B"}, new Boolean[]{true, false}));
        UUID quizId = UUID.randomUUID();
        QuizEntity quiz = new QuizEntity(quizId, UUID.randomUUID(), "Fixture quiz", "practice", 60, "hidden");
        quiz.setCourseId(courseId);
        entityManager.persist(quiz);
        UUID membershipId = UUID.randomUUID();
        entityManager.persist(new QuizQuestionEntity(membershipId, quizId, questionId, 2));
        UUID attemptId = UUID.randomUUID();
        entityManager.persist(new AttemptEntity(attemptId, quizId, learnerId, 1, START, START.plusSeconds(60), null, null));
        UUID answerId = UUID.randomUUID();
        entityManager.persist(new AttemptAnswerEntity(answerId, attemptId, questionId, 2, new Boolean[]{false, false}, null));
        entityManager.flush();
        entityManager.clear();

        QuizEntity storedQuiz = entityManager.find(QuizEntity.class, quizId);
        QuizQuestionEntity membership = entityManager.find(QuizQuestionEntity.class, membershipId);
        AttemptEntity attempt = entityManager.find(AttemptEntity.class, attemptId);
        AttemptAnswerEntity answer = entityManager.find(AttemptAnswerEntity.class, answerId);
        assertAll(
                () -> assertEquals(courseId, storedQuiz.getCourseId()),
                () -> assertEquals("practice", storedQuiz.getKind()),
                () -> assertEquals(60, storedQuiz.getTimeLimitSeconds()),
                () -> assertEquals("hidden", storedQuiz.getPublicationStatus()),
                () -> assertEquals(quizId, membership.getQuizId()),
                () -> assertEquals(questionId, membership.getQuestionId()),
                () -> assertEquals(2, membership.getPosition()),
                () -> assertEquals(learnerId, attempt.getUserId()),
                () -> assertEquals(quizId, attempt.getQuizId()),
                () -> assertEquals(1, attempt.getAttemptNumber()),
                () -> assertEquals(START, attempt.getStartedAt()),
                () -> assertEquals(START.plusSeconds(60), attempt.getExpiresAt()),
                () -> assertNull(attempt.getSubmittedAt()),
                () -> assertNull(attempt.getScorePercentage()),
                () -> assertEquals(attemptId, answer.getAttemptId()),
                () -> assertEquals(questionId, answer.getQuestionId()),
                () -> assertEquals(2, answer.getPosition()),
                () -> assertArrayEquals(new Boolean[]{false, false}, answer.getSelectedOptions()),
                () -> assertNull(answer.getIsCorrect()));

        // Persistence fixtures only: no scoring, timing or lifecycle service is implemented here.
        answer.setSelectedOptions(new Boolean[]{true, false});
        answer.setIsCorrect(true);
        attempt.setSubmittedAt(START.plusSeconds(30));
        attempt.setScorePercentage(new BigDecimal("100.00"));
        entityManager.flush();
        entityManager.clear();
        assertAll(
                () -> assertArrayEquals(new Boolean[]{true, false}, entityManager.find(AttemptAnswerEntity.class, answerId).getSelectedOptions()),
                () -> assertEquals(Boolean.TRUE, entityManager.find(AttemptAnswerEntity.class, answerId).getIsCorrect()),
                () -> assertEquals(new BigDecimal("100.00"), entityManager.find(AttemptEntity.class, attemptId).getScorePercentage()),
                () -> assertEquals(START.plusSeconds(30), entityManager.find(AttemptEntity.class, attemptId).getSubmittedAt()),
                () -> assertEquals("boolean[]", jdbc.queryForObject(
                        "SELECT pg_typeof(selected_options)::text FROM attempt_answers WHERE id = ?", String.class, answerId)));
    }

    @Test
    void courseOnlyAssignmentAndSubmissionRoundTripNullableAndGradedRepresentations() {
        UUID courseId = course();
        UUID learnerId = user("learner");
        UUID assignmentId = UUID.randomUUID();
        entityManager.persist(new AssignmentEntity(assignmentId, courseId, null, "Fixture assignment",
                "Instructions " + "i".repeat(300), START.plusSeconds(3600), "published"));
        UUID submissionId = UUID.randomUUID();
        entityManager.persist(new SubmissionEntity(submissionId, assignmentId, learnerId, "fixture/submission",
                "answer.txt", START, null, null, null));
        entityManager.flush();
        entityManager.clear();

        AssignmentEntity assignment = entityManager.find(AssignmentEntity.class, assignmentId);
        SubmissionEntity submission = entityManager.find(SubmissionEntity.class, submissionId);
        assertAll(
                () -> assertEquals(courseId, assignment.getCourseId()),
                () -> assertNull(assignment.getLessonId()),
                () -> assertEquals("Fixture assignment", assignment.getTitle()),
                () -> assertEquals("Instructions " + "i".repeat(300), assignment.getInstructions()),
                () -> assertEquals(START.plusSeconds(3600), assignment.getDeadlineAt()),
                () -> assertEquals("published", assignment.getPublicationStatus()),
                () -> assertEquals(assignmentId, submission.getAssignmentId()),
                () -> assertEquals(learnerId, submission.getUserId()),
                () -> assertEquals("fixture/submission", submission.getObjectKey()),
                () -> assertEquals("answer.txt", submission.getOriginalFilename()),
                () -> assertEquals(START, submission.getSubmittedAt()),
                () -> assertNull(submission.getGradePercentage()),
                () -> assertNull(submission.getFeedback()),
                () -> assertNull(submission.getGradedAt()));

        submission.setGradePercentage(new BigDecimal("87.65"));
        submission.setFeedback("Feedback " + "f".repeat(300));
        submission.setGradedAt(START.plusSeconds(120));
        entityManager.flush();
        entityManager.clear();
        SubmissionEntity graded = entityManager.find(SubmissionEntity.class, submissionId);
        assertAll(
                () -> assertEquals(new BigDecimal("87.65"), graded.getGradePercentage()),
                () -> assertEquals("Feedback " + "f".repeat(300), graded.getFeedback()),
                () -> assertEquals(START.plusSeconds(120), graded.getGradedAt()));
    }

    // ---- AS05.repo adapters (ADR007 §7): scoped pages, references, writes and lifecycle row locks ----

    @Test
    void questionAdapterScopesPagesInPostgresUuidOrderAndCountsBeforePaging() {
        UUID courseId = course();
        UUID skillA = skill(courseId);
        UUID skillB = skill(courseId);
        UUID foreignSkill = skill(course());
        // Java's signed UUID order puts 80…/ff… before 00…/7f…; PostgreSQL orders like canonical text.
        UUID q00 = prefixed("00"), q7f = prefixed("7f"), q80 = prefixed("80"), qff = prefixed("ff");
        questionRepository.save(question(q80, skillA));
        questionRepository.save(question(qff, skillB));
        questionRepository.save(question(q00, skillB));
        questionRepository.save(question(q7f, skillA));
        UUID foreign = UUID.randomUUID();
        questionRepository.save(question(foreign, foreignSkill));
        entityManager.flush();
        entityManager.clear();

        List<UUID> scope = List.of(skillA, skillB);
        assertAll(
                () -> assertEquals(4, questionRepository.countInSkills(scope)),
                () -> assertEquals(List.of(q00, q7f, q80), ids(questionRepository.findPageInSkills(scope, 0, 3))),
                () -> assertEquals(List.of(qff), ids(questionRepository.findPageInSkills(scope, 1, 3))),
                () -> assertEquals(List.of(), ids(questionRepository.findPageInSkills(scope, 2, 3))),
                () -> assertEquals(List.of(q80), ids(questionRepository.findPageInSkills(List.of(skillA), 1, 1))),
                () -> assertTrue(questionRepository.findInSkills(q7f, scope).isPresent()),
                () -> assertTrue(questionRepository.findInSkills(foreign, scope).isEmpty()),
                () -> assertTrue(questionRepository.findInSkillsForUpdate(foreign, scope).isEmpty()),
                () -> assertEquals(0, questionRepository.countInSkills(List.of())),
                () -> assertEquals(List.of(), questionRepository.findPageInSkills(List.of(), 0, 20)),
                () -> assertTrue(questionRepository.findInSkills(q7f, List.of()).isEmpty()),
                () -> assertTrue(questionRepository.findInSkillsForUpdate(q7f, List.of()).isEmpty()));
    }

    @Test
    void questionAdapterReplacesDeletesAndReportsQuizAndAttemptReferencesSeparately() {
        UUID courseId = course();
        UUID skillA = skill(courseId);
        UUID skillB = skill(courseId);
        UUID unused = UUID.randomUUID(), quizLinked = UUID.randomUUID(), answered = UUID.randomUUID();
        for (UUID id : List.of(unused, quizLinked, answered)) {
            questionRepository.save(question(id, skillA));
        }
        entityManager.flush();
        UUID quizId = quiz(courseId);
        jdbc.update("INSERT INTO quiz_questions (id, quiz_id, question_id, position) VALUES (?, ?, ?, 1)",
                UUID.randomUUID(), quizId, quizLinked);
        UUID attemptId = attempt(quizId, user("learner"));
        jdbc.update("INSERT INTO attempt_answers (id, attempt_id, question_id, position, selected_options) "
                + "VALUES (?, ?, ?, 1, ARRAY[false, false])", UUID.randomUUID(), attemptId, answered);
        entityManager.flush();
        entityManager.clear();

        assertAll(
                () -> assertFalse(questionRepository.isReferencedByQuizQuestion(unused)),
                () -> assertFalse(questionRepository.isReferencedByAttemptAnswer(unused)),
                () -> assertTrue(questionRepository.isReferencedByQuizQuestion(quizLinked)),
                () -> assertFalse(questionRepository.isReferencedByAttemptAnswer(quizLinked)),
                () -> assertFalse(questionRepository.isReferencedByQuizQuestion(answered)),
                () -> assertTrue(questionRepository.isReferencedByAttemptAnswer(answered)));

        Question locked = questionRepository.findInSkillsForUpdate(quizLinked, List.of(skillA, skillB)).orElseThrow();
        questionRepository.save(new Question(locked.id(), skillB, "Replaced prompt", "true_false", "hard",
                new String[]{"True", "False"}, new Boolean[]{false, true}));
        entityManager.flush();
        entityManager.clear();
        Question replaced = questionRepository.findInSkills(quizLinked, List.of(skillB)).orElseThrow();
        assertAll(
                () -> assertEquals(skillB, replaced.skill_id()),
                () -> assertEquals("Replaced prompt", replaced.prompt()),
                () -> assertEquals("true_false", replaced.kind()),
                () -> assertEquals("hard", replaced.difficulty()),
                () -> assertArrayEquals(new String[]{"True", "False"}, replaced.options()),
                () -> assertArrayEquals(new Boolean[]{false, true}, replaced.correct_options()),
                () -> assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM quiz_questions WHERE question_id = ?",
                        Integer.class, quizLinked)));

        questionRepository.delete(unused);
        entityManager.flush();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM questions WHERE id = ?", Integer.class, unused));
        assertTrue(questionRepository.findInSkills(unused, List.of(skillA)).isEmpty());
    }

    @Test
    void quizAndAttemptAdaptersRoundTripV004RowsWithoutSwappingReferences() {
        UUID courseId = course();
        UUID learnerId = user("learner");
        UUID quizId = UUID.randomUUID();
        quizRepository.save(new Quiz(quizId, courseId, "Adapter quiz", "test", 900, "hidden"));
        UUID attemptId = UUID.randomUUID();
        attemptRepository.save(new Attempt(attemptId, learnerId, quizId, 1, START, START.plusSeconds(900), null, null));
        entityManager.flush();
        entityManager.clear();

        Quiz quiz = quizRepository.findById(quizId).orElseThrow();
        Attempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        assertAll(
                () -> assertEquals(new Quiz(quizId, courseId, "Adapter quiz", "test", 900, "hidden"), quiz),
                () -> assertEquals(learnerId, attempt.user_id()),
                () -> assertEquals(quizId, attempt.quiz_id()),
                () -> assertEquals(learnerId, jdbc.queryForObject("SELECT user_id FROM attempts WHERE id = ?",
                        UUID.class, attemptId)),
                () -> assertEquals(1, attempt.attempt_number()),
                () -> assertEquals(START.plusSeconds(900), attempt.expires_at()),
                () -> assertNull(attempt.submitted_at()),
                () -> assertTrue(quizRepository.findById(UUID.randomUUID()).isEmpty()),
                () -> assertTrue(attemptRepository.findById(UUID.randomUUID()).isEmpty()));
    }

    /** Committed rows and two connections: the lifecycle lock must conflict with new FK references. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lifecycleRowLockBlocksNewQuizAndAttemptReferencesUntilItsTransactionEnds() throws Exception {
        UUID instructor = user("instructor");
        UUID learner = user("learner");
        UUID courseId = UUID.randomUUID();
        jdbc.update("INSERT INTO courses (id, instructor_id, title, description, publication_status, adaptive_enabled) "
                + "VALUES (?, ?, 'Lock fixture', 'AS05 lock fixture', 'published', false)", courseId, instructor);
        UUID skillId = skill(courseId);
        UUID questionId = UUID.randomUUID();
        jdbc.update("INSERT INTO questions (id, skill_id, prompt, kind, difficulty, options, correct_options) "
                + "VALUES (?, ?, 'Lock me', 'single_choice', 'easy', ARRAY['A','B'], ARRAY[true,false])", questionId, skillId);
        UUID quizId = quiz(courseId);
        UUID attemptId = attempt(quizId, learner);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            tx.executeWithoutResult(status -> {
                assertTrue(questionRepository.findInSkillsForUpdate(questionId, List.of(skillId)).isPresent());
                assertEquals("55P03", referenceFailure(other, tx, "INSERT INTO attempt_answers (id, attempt_id, question_id, "
                        + "position, selected_options) VALUES (?, '" + attemptId + "', '" + questionId + "', 1, ARRAY[false,false])"));
                assertEquals("55P03", referenceFailure(other, tx, "INSERT INTO quiz_questions (id, quiz_id, question_id, position) "
                        + "VALUES (?, '" + quizId + "', '" + questionId + "', 1)"));
            });
            // After the lock holder commits, the same references are accepted.
            assertNull(referenceFailure(other, tx, "INSERT INTO attempt_answers (id, attempt_id, question_id, "
                    + "position, selected_options) VALUES (?, '" + attemptId + "', '" + questionId + "', 1, ARRAY[false,false])"));
            assertNull(referenceFailure(other, tx, "INSERT INTO quiz_questions (id, quiz_id, question_id, position) "
                    + "VALUES (?, '" + quizId + "', '" + questionId + "', 1)"));

            // Why the adapter uses native FOR UPDATE: FOR NO KEY UPDATE (Hibernate's PESSIMISTIC_WRITE on
            // PostgreSQL) does not conflict with the key-share lock of a new reference.
            UUID second = UUID.randomUUID();
            jdbc.update("INSERT INTO questions (id, skill_id, prompt, kind, difficulty, options, correct_options) "
                    + "VALUES (?, ?, 'Weak lock', 'single_choice', 'easy', ARRAY['A','B'], ARRAY[true,false])", second, skillId);
            tx.executeWithoutResult(status -> {
                jdbc.queryForList("SELECT id FROM questions WHERE id = ? FOR NO KEY UPDATE", UUID.class, second);
                assertNull(referenceFailure(other, tx, "INSERT INTO quiz_questions (id, quiz_id, question_id, position) "
                        + "VALUES (?, '" + quizId + "', '" + second + "', 2)"));
            });
        } finally {
            other.shutdownNow();
            jdbc.update("DELETE FROM attempt_answers WHERE attempt_id = ?", attemptId);
            jdbc.update("DELETE FROM attempts WHERE id = ?", attemptId);
            jdbc.update("DELETE FROM quiz_questions WHERE quiz_id = ?", quizId);
            jdbc.update("DELETE FROM quizzes WHERE id = ?", quizId);
            jdbc.update("DELETE FROM questions WHERE skill_id = ?", skillId);
            jdbc.update("DELETE FROM skills WHERE id = ?", skillId);
            jdbc.update("DELETE FROM courses WHERE id = ?", courseId);
            jdbc.update("DELETE FROM users WHERE id IN (?, ?)", instructor, learner);
        }
    }

    /** Runs one reference insert on another connection with a short lock timeout; returns its SQLSTATE. */
    private String referenceFailure(ExecutorService other, TransactionTemplate tx, String insert) {
        try {
            return other.submit(() -> {
                try {
                    tx.executeWithoutResult(status -> {
                        jdbc.execute("SET LOCAL lock_timeout = '300ms'");
                        jdbc.update(insert, UUID.randomUUID());
                    });
                    return (String) null;
                } catch (RuntimeException failure) {
                    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                        if (cause instanceof SQLException sql) {
                            return sql.getSQLState();
                        }
                    }
                    throw failure;
                }
            }).get(10, TimeUnit.SECONDS);
        } catch (Exception unexpected) {
            throw new IllegalStateException(unexpected);
        }
    }

    // ---- AS07 CT23-PRACTICE-PROFILES-2026-11-15-r1 (ADR013 §19.3, B8-P03/B8-P08/B8-P09) ----

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void practiceQuizProfilesFilterWholeMembershipKindPublicationAndCourseInOneMinimalQuery() {
        UUID instructor = user("instructor");
        UUID courseId = insertCourse(instructor, "AS07 Course");
        UUID foreignCourseId = insertCourse(instructor, "AS07 Foreign Course");

        UUID skillA = skill(courseId);
        UUID skillB = skill(courseId);
        UUID extraUnusedSkill = skill(courseId);
        UUID disallowedSkill = skill(courseId);
        UUID foreignSkill = skill(foreignCourseId);

        UUID qEasyA1 = insertQuestion(skillA, "easy");
        UUID qEasyA2 = insertQuestion(skillA, "easy");
        UUID qMediumA = insertQuestion(skillA, "medium");
        UUID qHardA = insertQuestion(skillA, "hard");
        UUID qMediumB = insertQuestion(skillB, "medium");
        UUID qHardB = insertQuestion(skillB, "hard");
        UUID qDisallowedHard = insertQuestion(disallowedSkill, "hard");
        UUID qForeignEasy = insertQuestion(foreignSkill, "easy");

        // Qualifying quizzes: uniform, mixed, subset of M(L), case-sensitive UTF-16 title order and UUID tie-breaks.
        UUID tie00 = insertQuiz(prefixed("00"), courseId, "Loops Tie", "practice", "published");
        UUID tie7f = insertQuiz(prefixed("7f"), courseId, "Loops Tie", "practice", "published");
        UUID tie80 = insertQuiz(prefixed("80"), courseId, "Loops Tie", "practice", "published");
        UUID tieff = insertQuiz(prefixed("ff"), courseId, "Loops Tie", "practice", "published");
        UUID zebraQuiz = insertQuiz(UUID.randomUUID(), courseId, "Zebra Practice", "practice", "published");
        UUID alphaQuiz = insertQuiz(UUID.randomUUID(), courseId, "alpha Practice", "practice", "published");

        insertMembership(tie00, List.of(qEasyA1, qEasyA2));
        insertMembership(tie7f, List.of(qMediumB));
        insertMembership(tie80, List.of(qHardA));
        insertMembership(tieff, List.of(qEasyA1, qHardB));
        insertMembership(zebraQuiz, List.of(qEasyA1, qMediumA, qHardB));
        insertMembership(alphaQuiz, List.of(qMediumA));

        // Excluded quizzes: hidden, test, diagnostic, empty (vacuous-truth guard), mixed allowed+disallowed skill, wrong Course.
        UUID hiddenPractice = insertQuiz(UUID.randomUUID(), courseId, "Hidden Practice", "practice", "hidden");
        UUID publishedTest = insertQuiz(UUID.randomUUID(), courseId, "Published Test", "test", "published");
        UUID publishedDiagnostic = insertQuiz(UUID.randomUUID(), courseId, "Published Diagnostic", "diagnostic", "published");
        UUID emptyPublishedPractice = insertQuiz(UUID.randomUUID(), courseId, "Empty Practice", "practice", "published");
        UUID partialSkillPractice = insertQuiz(UUID.randomUUID(), courseId, "Partial Skill Practice", "practice", "published");
        UUID foreignCoursePractice = insertQuiz(UUID.randomUUID(), foreignCourseId, "Foreign Practice", "practice", "published");

        insertMembership(hiddenPractice, List.of(qEasyA1));
        insertMembership(publishedTest, List.of(qEasyA1));
        insertMembership(publishedDiagnostic, List.of(qEasyA1));
        insertMembership(partialSkillPractice, List.of(qEasyA1, qDisallowedHard));
        insertMembership(foreignCoursePractice, List.of(qForeignEasy));

        List<UUID> allQuizzes = List.of(
                tie00, tie7f, tie80, tieff, zebraQuiz, alphaQuiz,
                hiddenPractice, publishedTest, publishedDiagnostic,
                emptyPublishedPractice, partialSkillPractice, foreignCoursePractice);
        List<UUID> allQuestions = List.of(
                qEasyA1, qEasyA2, qMediumA, qHardA, qMediumB, qHardB, qDisallowedHard, qForeignEasy);
        List<UUID> allSkills = List.of(skillA, skillB, extraUnusedSkill, disallowedSkill, foreignSkill);

        try {
            TransactionTemplate readTx = repeatableReadOnlyTx();

            // Even when foreignSkill is passed in skillIds, foreignCoursePractice is excluded by course_id.
            Set<UUID> fullAllowedSet = Set.of(skillA, skillB, extraUnusedSkill, foreignSkill);
            List<String> recordedSql = new ArrayList<>();
            List<PracticeQuizProfile> profiles = readTx.execute(status -> {
                assertEquals("repeatable read/on", jdbc.queryForObject(
                        "SELECT current_setting('transaction_isolation') || '/' || current_setting('transaction_read_only')",
                        String.class));
                STATEMENTS.set(recordedSql);
                try {
                    List<PracticeQuizProfile> result = quizService.readPracticeQuizProfiles(courseId, fullAllowedSet);
                    assertNull(jdbc.queryForObject("SELECT pg_current_xact_id_if_assigned()::text", String.class),
                            "readPracticeQuizProfiles must not write or assign a transaction xid");
                    return result;
                } finally {
                    STATEMENTS.remove();
                }
            });

            assertEquals(1, recordedSql.size(), () -> "Expected exactly 1 SQL query, got: " + recordedSql);
            String sql = recordedSql.getFirst().toLowerCase(Locale.ROOT);
            assertAll(
                    () -> assertFalse(sql.contains("prompt"), "SQL must not select question prompts"),
                    () -> assertFalse(sql.contains("correct_options"), "SQL must not select answer keys"),
                    () -> assertFalse(sql.contains("options"), "SQL must not select question options"),
                    () -> assertFalse(sql.contains("attempts"), "SQL must not touch attempts"),
                    () -> assertFalse(sql.contains("attempt_answers"), "SQL must not touch attempt_answers"));

            assertEquals(List.of(
                    new PracticeQuizProfile(tie00, "Loops Tie", Set.of(DifficultyLevel.EASY)),
                    new PracticeQuizProfile(tie7f, "Loops Tie", Set.of(DifficultyLevel.MEDIUM)),
                    new PracticeQuizProfile(tie80, "Loops Tie", Set.of(DifficultyLevel.HARD)),
                    new PracticeQuizProfile(tieff, "Loops Tie", Set.of(DifficultyLevel.EASY, DifficultyLevel.HARD)),
                    new PracticeQuizProfile(zebraQuiz, "Zebra Practice",
                            Set.of(DifficultyLevel.EASY, DifficultyLevel.MEDIUM, DifficultyLevel.HARD)),
                    new PracticeQuizProfile(alphaQuiz, "alpha Practice", Set.of(DifficultyLevel.MEDIUM))
            ), profiles);

            // Narrowing skillIds to only skillA excludes quizzes that also contain skillB questions.
            List<PracticeQuizProfile> skillAOnly = readTx.execute(
                    status -> quizService.readPracticeQuizProfiles(courseId, Set.of(skillA)));
            assertEquals(List.of(
                    new PracticeQuizProfile(tie00, "Loops Tie", Set.of(DifficultyLevel.EASY)),
                    new PracticeQuizProfile(tie80, "Loops Tie", Set.of(DifficultyLevel.HARD)),
                    new PracticeQuizProfile(alphaQuiz, "alpha Practice", Set.of(DifficultyLevel.MEDIUM))
            ), skillAOnly);

            // Empty skillIds executes zero SQL queries and returns an empty list.
            List<String> emptyCallSql = new ArrayList<>();
            List<PracticeQuizProfile> emptyProfiles = readTx.execute(status -> {
                STATEMENTS.set(emptyCallSql);
                try {
                    return quizService.readPracticeQuizProfiles(courseId, Set.of());
                } finally {
                    STATEMENTS.remove();
                }
            });
            assertTrue(emptyProfiles.isEmpty());
            assertEquals(List.of(), emptyCallSql);
        } finally {
            cleanupPracticeFixtures(allQuizzes, allQuestions, allSkills, List.of(courseId, foreignCourseId), instructor);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void practiceQuizProfilesExecuteUnderColumnMinimalPrivilegesWithoutAnswerOrAttemptAccess() {
        UUID instructor = user("instructor");
        UUID courseId = insertCourse(instructor, "AS07 Minimal Privilege Course");
        UUID skillA = skill(courseId);
        UUID qEasy = insertQuestion(skillA, "easy");
        UUID qHard = insertQuestion(skillA, "hard");
        UUID quizId = insertQuiz(UUID.randomUUID(), courseId, "Minimal Privilege Practice", "practice", "published");
        insertMembership(quizId, List.of(qEasy, qHard));

        String role = "as07_min_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE ROLE " + role + " NOLOGIN");
        jdbc.execute("GRANT USAGE ON SCHEMA public TO " + role);
        jdbc.execute("GRANT SELECT (id, course_id, title, kind, publication_status) ON public.quizzes TO " + role);
        jdbc.execute("GRANT SELECT (quiz_id, question_id) ON public.quiz_questions TO " + role);
        jdbc.execute("GRANT SELECT (id, skill_id, difficulty) ON public.questions TO " + role);

        try {
            // Confirm the restricted role is genuinely denied prompt, options, answer-key and attempt reads (42501).
            TransactionTemplate verifyDenialTx = new TransactionTemplate(transactionManager);
            for (String deniedSql : List.of(
                    "SELECT prompt FROM public.questions",
                    "SELECT options FROM public.questions",
                    "SELECT correct_options FROM public.questions",
                    "SELECT id FROM public.attempts",
                    "SELECT id FROM public.attempt_answers")) {
                assertEquals("42501", sqlStateOf(verifyDenialTx, role, deniedSql), deniedSql);
            }

            // Under the exact same column-minimal role, readPracticeQuizProfiles succeeds cleanly.
            List<PracticeQuizProfile> profiles = repeatableReadOnlyTx().execute(status -> {
                jdbc.execute("SET LOCAL ROLE " + role);
                assertEquals(role, jdbc.queryForObject("SELECT current_user", String.class));
                assertEquals("on", jdbc.queryForObject("SHOW transaction_read_only", String.class));
                return quizService.readPracticeQuizProfiles(courseId, Set.of(skillA));
            });

            assertEquals(List.of(new PracticeQuizProfile(
                    quizId, "Minimal Privilege Practice", Set.of(DifficultyLevel.EASY, DifficultyLevel.HARD))),
                    profiles);
        } finally {
            jdbc.execute("DROP OWNED BY " + role);
            jdbc.execute("DROP ROLE " + role);
            cleanupPracticeFixtures(
                    List.of(quizId), List.of(qEasy, qHard), List.of(skillA), List.of(courseId), instructor);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void practiceQuizProfilesRequireAndJoinCallersReadOnlyRepeatableReadSnapshot() throws Exception {
        UUID instructor = user("instructor");
        UUID courseId = insertCourse(instructor, "AS07 Snapshot Course");
        UUID skillA = skill(courseId);
        UUID disallowedSkill = skill(courseId);
        UUID qEasy = insertQuestion(skillA, "easy");
        UUID qMedium = insertQuestion(skillA, "medium");
        UUID qDisallowed = insertQuestion(disallowedSkill, "hard");

        UUID quizToHide = insertQuiz(UUID.randomUUID(), courseId, "A Will Hide", "practice", "published");
        UUID quizToContaminate = insertQuiz(UUID.randomUUID(), courseId, "B Will Gain Foreign Skill", "practice", "published");
        UUID quizToPublish = insertQuiz(UUID.randomUUID(), courseId, "C Will Publish", "practice", "hidden");
        insertMembership(quizToHide, List.of(qEasy));
        insertMembership(quizToContaminate, List.of(qMedium));
        insertMembership(quizToPublish, List.of(qEasy));

        ExecutorService writer = Executors.newSingleThreadExecutor();
        try {
            // Mandatory caller-owned read-only REPEATABLE READ transaction enforcement through Spring proxy.
            assertThrows(IllegalTransactionStateException.class,
                    () -> quizService.readPracticeQuizProfiles(courseId, Set.of(skillA)));
            assertThrows(IllegalTransactionStateException.class,
                    () -> quizService.readPracticeQuizProfiles(courseId, Set.of()));

            TransactionTemplate readCommittedReadOnly = new TransactionTemplate(transactionManager);
            readCommittedReadOnly.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            readCommittedReadOnly.setReadOnly(true);
            assertThrows(IllegalStateException.class,
                    () -> readCommittedReadOnly.execute(
                            s -> quizService.readPracticeQuizProfiles(courseId, Set.of(skillA))));

            TransactionTemplate repeatableReadWrite = new TransactionTemplate(transactionManager);
            repeatableReadWrite.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            repeatableReadWrite.setReadOnly(false);
            assertThrows(IllegalStateException.class,
                    () -> repeatableReadWrite.execute(
                            s -> quizService.readPracticeQuizProfiles(courseId, Set.of(skillA))));

            // Repository adapter direct defensive checks (@Repository translates IllegalArgumentException).
            assertThrows(NullPointerException.class,
                    () -> quizRepository.findPublishedPracticeQuizDifficultyRows(null, Set.of(skillA)));
            assertThrows(NullPointerException.class,
                    () -> quizRepository.findPublishedPracticeQuizDifficultyRows(courseId, null));
            var badArg = assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,
                    () -> quizRepository.findPublishedPracticeQuizDifficultyRows(
                            courseId, Collections.singletonList(null)));
            assertEquals(IllegalArgumentException.class, badArg.getCause().getClass());
            assertEquals(List.of(), quizRepository.findPublishedPracticeQuizDifficultyRows(courseId, Set.of()));

            // Snapshot isolation: concurrent commits after the caller's snapshot started remain invisible.
            TransactionTemplate callerSnapshot = repeatableReadOnlyTx();
            callerSnapshot.executeWithoutResult(status -> {
                List<PracticeQuizProfile> before = quizService.readPracticeQuizProfiles(courseId, Set.of(skillA));
                assertEquals(List.of(
                        new PracticeQuizProfile(quizToHide, "A Will Hide", Set.of(DifficultyLevel.EASY)),
                        new PracticeQuizProfile(quizToContaminate, "B Will Gain Foreign Skill",
                                Set.of(DifficultyLevel.MEDIUM))
                ), before);

                try {
                    writer.submit(() -> {
                        jdbc.update("UPDATE quizzes SET publication_status = 'hidden' WHERE id = ?", quizToHide);
                        jdbc.update("UPDATE quizzes SET publication_status = 'published' WHERE id = ?", quizToPublish);
                        jdbc.update("INSERT INTO quiz_questions (id, quiz_id, question_id, position) VALUES (?, ?, ?, 2)",
                                UUID.randomUUID(), quizToContaminate, qDisallowed);
                    }).get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }

                List<PracticeQuizProfile> sameSnapshot = quizService.readPracticeQuizProfiles(courseId, Set.of(skillA));
                assertEquals(before, sameSnapshot,
                        "readPracticeQuizProfiles must join the caller's REPEATABLE READ snapshot");
            });

            // A subsequent snapshot observes all three committed mutations.
            List<PracticeQuizProfile> nextSnapshot = callerSnapshot.execute(
                    status -> quizService.readPracticeQuizProfiles(courseId, Set.of(skillA)));
            assertEquals(List.of(
                    new PracticeQuizProfile(quizToPublish, "C Will Publish", Set.of(DifficultyLevel.EASY))
            ), nextSnapshot);
        } finally {
            writer.shutdownNow();
            cleanupPracticeFixtures(
                    List.of(quizToHide, quizToContaminate, quizToPublish),
                    List.of(qEasy, qMedium, qDisallowed),
                    List.of(skillA, disallowedSkill),
                    List.of(courseId),
                    instructor);
        }
    }

    /** D2: compare SQL instants independently, not just symmetric application conversions. */
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "0000-01-01T00:00:00Z", "0000-02-29T12:34:56.123456Z", "0001-01-01T00:00:00Z",
            "1500-02-28T23:59:59.999999Z", "1500-03-01T00:00:00Z", "1500-03-10T00:00:00Z",
            "1500-03-11T00:00:00.000001Z", "1582-10-04T23:59:59.999999Z",
            "1582-10-05T00:00:00Z", "1582-10-14T23:59:59.999999Z", "1582-10-15T00:00:00.000001Z",
            "1600-02-29T12:00:00Z", "1700-03-01T00:00:00Z", "2026-12-01T10:00:00.123456Z",
            "9999-12-31T23:59:59.999999Z"
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void assignmentDeadlineBoundariesRoundTripThroughPostgresqlAtMicrosecondPrecision(String input) {
        UUID instructor = user("instructor");
        UUID courseId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Instant deadline = Instant.parse(input);
        jdbc.update("INSERT INTO courses (id, instructor_id, title, description, publication_status, adaptive_enabled) "
                + "VALUES (?, ?, 'D2 course', 'D2 fixture', 'hidden', false)", courseId, instructor);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.executeWithoutResult(status -> assignmentRepository.insert(
                    new Assignment(id, courseId, null, "Inserted", "I", deadline, "hidden")));
            assertAssignmentSqlEpoch(id, deadline);
            tx.executeWithoutResult(status -> assertEquals(deadline,
                    assignmentRepository.findInCourse(id, courseId).orElseThrow().deadline_at()));

            // A targeted JPQL UPDATE must use the field's JDBC type too, including the historical leap-day probe.
            tx.executeWithoutResult(status -> assignmentRepository.updateDefinition(
                    id, courseId, null, "Replaced", "I2", deadline));
            assertAssignmentSqlEpoch(id, deadline);
            tx.executeWithoutResult(status -> {
                Assignment read = assignmentRepository.findInCourse(id, courseId).orElseThrow();
                assertEquals("Replaced", read.title());
                assertEquals(deadline, read.deadline_at());
            });

            // Store via a PostgreSQL text cast, independent of Hibernate and java.sql.Timestamp.
            // PostgreSQL has no year zero: ISO 0000 is year 0001 BC.
            String literal = input.replace('T', ' ').replace("Z", "+00");
            if (literal.startsWith("0000-")) literal = "0001-" + literal.substring(5) + " BC";
            jdbc.update("UPDATE assignments SET deadline_at = CAST(? AS timestamptz) WHERE id = ?", literal, id);
            assertAssignmentSqlEpoch(id, deadline);
            for (String zone : List.of("UTC", "America/New_York", "Asia/Ho_Chi_Minh")) {
                tx.executeWithoutResult(status -> {
                    jdbc.execute("SET LOCAL TIME ZONE '" + zone + "'");
                    assertEquals(deadline, assignmentRepository.findInCourse(id, courseId).orElseThrow().deadline_at(), zone);
                    assertEquals(deadline, assignmentRepository.findPageInCourse(courseId, 0, 20).getFirst().deadline_at(), zone);
                    assertEquals(deadline, assignmentRepository.findInCourseForUpdate(id, courseId).orElseThrow().deadline_at(), zone);
                });
            }
        } finally {
            jdbc.update("DELETE FROM assignments WHERE course_id = ?", courseId);
            jdbc.update("DELETE FROM courses WHERE id = ?", courseId);
            jdbc.update("DELETE FROM users WHERE id = ?", instructor);
        }
    }

    private void assertAssignmentSqlEpoch(UUID id, Instant expected) {
        BigDecimal epoch = BigDecimal.valueOf(expected.getEpochSecond()).add(BigDecimal.valueOf(expected.getNano(), 9));
        BigDecimal stored = jdbc.queryForObject("SELECT extract(epoch FROM deadline_at) FROM assignments WHERE id = ?",
                BigDecimal.class, id);
        assertEquals(0, epoch.compareTo(stored), () -> expected + ": expected SQL epoch " + epoch + ", actual " + stored);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void assignmentDeadlineSqlOrderingAndComparisonsUseUtcInstants() {
        UUID instructor = user("instructor"), course = UUID.randomUUID();
        List<Instant> deadlines = List.of(Instant.parse("0000-01-01T00:00:00Z"),
                Instant.parse("1500-03-09T23:59:59.999999Z"), Instant.parse("1500-03-10T00:00:00Z"),
                Instant.parse("1582-10-14T23:59:59.999999Z"), Instant.parse("1582-10-15T00:00:00Z"),
                Instant.parse("2026-12-01T00:00:00Z"), Instant.parse("9999-12-31T23:59:59.999999Z"));
        List<UUID> ids = deadlines.stream().map(value -> UUID.randomUUID()).toList();
        jdbc.update("INSERT INTO courses (id,instructor_id,title,description,publication_status,adaptive_enabled) "
                + "VALUES (?,?,'D2 ordering','D2 fixture','hidden',false)", course, instructor);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                for (int index = deadlines.size() - 1; index >= 0; index--) {
                    assignmentRepository.insert(new Assignment(ids.get(index), course, null, "D2", "I", deadlines.get(index), "hidden"));
                }
            });
            assertEquals(ids, jdbc.queryForList("SELECT id FROM assignments WHERE course_id = ? ORDER BY deadline_at", UUID.class, course));
            assertEquals(ids.subList(0, 2), jdbc.queryForList("SELECT id FROM assignments WHERE course_id = ? "
                    + "AND deadline_at < '1500-03-10T00:00:00Z'::timestamptz ORDER BY deadline_at", UUID.class, course));
        } finally {
            jdbc.update("DELETE FROM assignments WHERE course_id = ?", course);
            jdbc.update("DELETE FROM courses WHERE id = ?", course);
            jdbc.update("DELETE FROM users WHERE id = ?", instructor);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void assignmentAdapterOrdersByCollateCCountsSubmissionsLocksForUpdateAndRespectsColumnPrivileges() throws Exception {
        UUID instructor = user("instructor");
        UUID learnerA = user("learner");
        UUID learnerB = user("learner");
        UUID courseId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffffe");
        UUID third = UUID.randomUUID();
        String restrictedRole = "as09_runtime_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO courses (id, instructor_id, title, description, publication_status, adaptive_enabled) "
                + "VALUES (?, ?, 'AS09 course', 'AS09 fixture', 'published', false)", courseId, instructor);
        jdbc.update("INSERT INTO chapters (id, course_id, title, position) VALUES (?, ?, 'Ch 1', 1)", chapterId, courseId);
        jdbc.update("INSERT INTO lessons (id, chapter_id, title, content_markdown, position, publication_status) "
                + "VALUES (?, ?, 'Lesson 1', 'Body', 1, 'published')", lessonId, chapterId);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            jdbc.execute("CREATE ROLE " + restrictedRole + " NOLOGIN");
            jdbc.execute("GRANT USAGE ON SCHEMA public TO " + restrictedRole);
            // ADR012 §19.2 ceiling: SELECT, INSERT, UPDATE (lesson_id, title, instructions, deadline_at,
            // publication_status) on assignments (never course_id or DELETE), plus FK/SELECT references.
            // CT17-P6 r12 rows: no REFERENCES (PostgreSQL checks foreign keys with the owner's rights)
            // and only the submission columns the owner counts read.
            jdbc.execute("GRANT SELECT ON public.courses, public.lessons TO " + restrictedRole);
            jdbc.execute("GRANT SELECT, INSERT, UPDATE (lesson_id, title, instructions, deadline_at, publication_status) "
                    + "ON public.assignments TO " + restrictedRole);
            jdbc.execute("GRANT SELECT (id, assignment_id, graded_at) ON public.submissions TO " + restrictedRole);

            tx.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL ROLE " + restrictedRole);
                assignmentRepository.insert(new Assignment(high, courseId, null, "Same", "Inst H", START.plusSeconds(60), "hidden"));
                assignmentRepository.insert(new Assignment(low, courseId, lessonId, "Same", "Inst L", START.plusSeconds(120), "hidden"));
                assignmentRepository.insert(new Assignment(third, courseId, null, "apple", "Inst A", START.plusSeconds(180), "hidden"));
                assignmentRepository.updateDefinition(low, courseId, null, "Same", "Updated Inst L", START.plusSeconds(240));
                assignmentRepository.updatePublicationStatus(low, courseId, "published");
            });

            java.time.OffsetDateTime submittedAt = START.atOffset(java.time.ZoneOffset.UTC);
            jdbc.update("INSERT INTO submissions (id, assignment_id, user_id, object_key, original_filename, submitted_at) "
                    + "VALUES (?, ?, ?, 'submissions/00000000-0000-4000-8000-000000000001', 'one.pdf', ?)",
                    UUID.randomUUID(), low, learnerA, submittedAt);
            jdbc.update("INSERT INTO submissions (id, assignment_id, user_id, object_key, original_filename, submitted_at, "
                            + "grade_percentage, feedback, graded_at) "
                            + "VALUES (?, ?, ?, 'submissions/00000000-0000-4000-8000-000000000002', 'two.zip', ?, 95.50, 'Good', ?)",
                    UUID.randomUUID(), low, learnerB, submittedAt, submittedAt.plusSeconds(10));

            tx.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL ROLE " + restrictedRole);
                assertEquals(3, assignmentRepository.countInCourse(courseId));
                List<Assignment> page = assignmentRepository.findPageInCourse(courseId, 0, 10);
                assertEquals(List.of(low, high, third), page.stream().map(Assignment::id).toList());
                assertEquals("Updated Inst L", page.get(0).instructions());
                assertEquals("published", page.get(0).publication_status());
                List<String> countSql = new java.util.ArrayList<>();
                STATEMENTS.set(countSql);
                try {
                    assertEquals(
                            new SubmissionCounts(2, 1),
                            assignmentRepository.countSubmissions(List.of(low, high)).get(low));
                } finally {
                    STATEMENTS.remove();
                }
                assertEquals(1, countSql.size(), countSql::toString);
                assertTrue(countSql.get(0).contains("from submissions"), countSql::toString);
                assertFalse(countSql.get(0).matches("(?s).*\\b(object_key|original_filename|user_id|feedback|grade_percentage|submitted_at)\\b.*"),
                        countSql::toString);

                assertTrue(assignmentRepository.findInCourseForUpdate(high, courseId).isPresent());
                assertEquals("55P03", referenceFailure(other, tx,
                        "INSERT INTO submissions (id, assignment_id, user_id, object_key, original_filename, submitted_at) "
                                + "VALUES (?, '" + high + "', '" + learnerA
                                + "', 'submissions/00000000-0000-4000-8000-000000000003', 'blocked.pdf', now())"));
            });
        } finally {
            other.shutdownNow();
            jdbc.update("DELETE FROM submissions WHERE assignment_id IN (?, ?, ?)", low, high, third);
            jdbc.update("DELETE FROM assignments WHERE course_id = ?", courseId);
            jdbc.update("DELETE FROM lessons WHERE id = ?", lessonId);
            jdbc.update("DELETE FROM chapters WHERE id = ?", chapterId);
            jdbc.update("DELETE FROM courses WHERE id = ?", courseId);
            jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", instructor, learnerA, learnerB);
            jdbc.execute("REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM " + restrictedRole);
            jdbc.execute("REVOKE USAGE ON SCHEMA public FROM " + restrictedRole);
            jdbc.execute("DROP ROLE IF EXISTS " + restrictedRole);
        }
    }

    private TransactionTemplate repeatableReadOnlyTx() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setReadOnly(true);
        return tx;
    }

    private String sqlStateOf(TransactionTemplate tx, String role, String sql) {
        try {
            tx.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL ROLE " + role);
                jdbc.queryForList(sql);
            });
            return null;
        } catch (RuntimeException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sqlEx) {
                    return sqlEx.getSQLState();
                }
            }
            throw failure;
        }
    }

    private UUID insertCourse(UUID instructorId, String title) {
        UUID courseId = UUID.randomUUID();
        jdbc.update("INSERT INTO courses (id, instructor_id, title, description, publication_status, adaptive_enabled) "
                + "VALUES (?, ?, ?, 'AS07 fixture', 'published', false)", courseId, instructorId, title);
        return courseId;
    }

    private UUID insertQuestion(UUID skillId, String difficulty) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questions (id, skill_id, prompt, kind, difficulty, options, correct_options) "
                + "VALUES (?, ?, 'Sensitive prompt', 'single_choice', ?, ARRAY['Secret A','Secret B'], ARRAY[true,false])",
                id, skillId, difficulty);
        return id;
    }

    private UUID insertQuiz(UUID quizId, UUID courseId, String title, String kind, String status) {
        jdbc.update("INSERT INTO quizzes (id, course_id, title, kind, time_limit_seconds, publication_status) "
                + "VALUES (?, ?, ?, ?, 120, ?)", quizId, courseId, title, kind, status);
        return quizId;
    }

    private void insertMembership(UUID quizId, List<UUID> questionIds) {
        for (int i = 0; i < questionIds.size(); i++) {
            jdbc.update("INSERT INTO quiz_questions (id, quiz_id, question_id, position) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(), quizId, questionIds.get(i), i + 1);
        }
    }

    private void cleanupPracticeFixtures(
            List<UUID> quizIds,
            List<UUID> questionIds,
            List<UUID> skillIds,
            List<UUID> courseIds,
            UUID instructorId
    ) {
        for (UUID quizId : quizIds) {
            jdbc.update("DELETE FROM quiz_questions WHERE quiz_id = ?", quizId);
            jdbc.update("DELETE FROM quizzes WHERE id = ?", quizId);
        }
        for (UUID questionId : questionIds) {
            jdbc.update("DELETE FROM questions WHERE id = ?", questionId);
        }
        for (UUID skillId : skillIds) {
            jdbc.update("DELETE FROM skills WHERE id = ?", skillId);
        }
        for (UUID courseId : courseIds) {
            jdbc.update("DELETE FROM courses WHERE id = ?", courseId);
        }
        jdbc.update("DELETE FROM users WHERE id = ?", instructorId);
    }

    private UUID skill(UUID courseId) {
        UUID skillId = UUID.randomUUID();
        jdbc.update("INSERT INTO skills (id, course_id, name) VALUES (?, ?, ?)", skillId, courseId, "AS05 skill " + skillId);
        return skillId;
    }

    private UUID quiz(UUID courseId) {
        UUID quizId = UUID.randomUUID();
        jdbc.update("INSERT INTO quizzes (id, course_id, title, kind, time_limit_seconds, publication_status) "
                + "VALUES (?, ?, 'AS05 quiz', 'practice', 60, 'hidden')", quizId, courseId);
        return quizId;
    }

    private UUID attempt(UUID quizId, UUID learnerId) {
        UUID attemptId = UUID.randomUUID();
        jdbc.update("INSERT INTO attempts (id, user_id, quiz_id, attempt_number, started_at, expires_at) "
                + "VALUES (?, ?, ?, 1, now(), now() + interval '1 hour')", attemptId, learnerId, quizId);
        return attemptId;
    }

    private static Question question(UUID id, UUID skillId) {
        return new Question(id, skillId, "Adapter prompt", "single_choice", "easy",
                new String[]{"A", "B"}, new Boolean[]{true, false});
    }

    private static UUID prefixed(String prefix) {
        String random = UUID.randomUUID().toString();
        return UUID.fromString(prefix + random.substring(prefix.length()));
    }

    private static List<UUID> ids(List<Question> questions) {
        return questions.stream().map(Question::id).toList();
    }

    private UUID course() {
        UUID courseId = UUID.randomUUID();
        jdbc.update("INSERT INTO courses (id, instructor_id, title, description, publication_status, adaptive_enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", courseId, user("instructor"), "Fixture course", "AS01 fixture", "published", false);
        return courseId;
    }

    private UUID user(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, display_name, role, account_status) VALUES (?, ?, ?, ?, ?, ?)",
                id, "as01-" + id + "@example.test", "fixture-not-used-for-authentication", "AS01 fixture", role, "active");
        return id;
    }
    /** AS08 author verification: real Spring guard, repositories, PostgreSQL and security chain. */
    @org.junit.jupiter.api.Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class TimedAttemptRuntime {
        @Autowired com.codemastery.modules.assessment.application.service.AttemptService live;
        @Autowired com.codemastery.modules.assessment.application.service.QuizGradingService grading;
        @Autowired com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService skillGraph;
        @Autowired com.codemastery.modules.course.application.service.CourseAccessService access;
        @Autowired com.codemastery.modules.course.application.service.CourseManagementService guard;
        @Autowired com.codemastery.modules.auth.application.service.UserAccountService identities;
        @Autowired org.springframework.web.context.WebApplicationContext web;
        @Autowired com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider tokens;
        @Autowired javax.sql.DataSource dataSource;
        UUID owner, learner, foreignLearner, admin, courseId, skillId, quizId, q1, q2;
        final Instant now = Instant.parse("2030-01-01T00:00:00.123456Z");

        @org.junit.jupiter.api.BeforeEach
        void createAttemptFixtures() {
            owner = user("instructor"); learner = user("learner"); foreignLearner = user("learner"); admin = user("admin");
            courseId = UUID.randomUUID(); skillId = UUID.randomUUID(); quizId = UUID.randomUUID();
            q1 = UUID.randomUUID(); q2 = UUID.randomUUID();
            jdbc.update("INSERT INTO courses (id,instructor_id,title,description,publication_status,adaptive_enabled) VALUES (?,?,'Timed','Fixture','published',false)", courseId, owner);
            jdbc.update("INSERT INTO enrollments (id,user_id,course_id,status) VALUES (?,?,?,'active')", UUID.randomUUID(), learner, courseId);
            jdbc.update("INSERT INTO enrollments (id,user_id,course_id,status) VALUES (?,?,?,'completed')", UUID.randomUUID(), foreignLearner, courseId);
            jdbc.update("INSERT INTO skills (id,course_id,name) VALUES (?,?,'Timed skill')", skillId, courseId);
            for (UUID question : List.of(q1,q2)) jdbc.update("INSERT INTO questions VALUES (?,?,'Choose','single_choice','easy',ARRAY['A','B'],ARRAY[true,false])", question, skillId);
            addQuiz(quizId, "practice");
        }

        void addQuiz(UUID id, String kind) {
            jdbc.update("INSERT INTO quizzes VALUES (?,?,'Timed quiz',?,60,'published')", id, courseId, kind);
            jdbc.update("INSERT INTO quiz_questions VALUES (?,?,?,1)", UUID.randomUUID(), id, q1);
            jdbc.update("INSERT INTO quiz_questions VALUES (?,?,?,3)", UUID.randomUUID(), id, q2);
        }

        @org.junit.jupiter.api.AfterEach
        void deleteAttemptFixtures() {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            jdbc.update("DELETE FROM attempt_answers WHERE attempt_id IN (SELECT a.id FROM attempts a JOIN quizzes q ON q.id=a.quiz_id WHERE q.course_id=?)", courseId);
            jdbc.update("DELETE FROM attempts WHERE quiz_id IN (SELECT id FROM quizzes WHERE course_id=?)", courseId);
            jdbc.update("DELETE FROM quiz_questions WHERE quiz_id IN (SELECT id FROM quizzes WHERE course_id=?)", courseId);
            jdbc.update("DELETE FROM quizzes WHERE course_id=?", courseId);
            jdbc.update("DELETE FROM questions WHERE skill_id=?", skillId);
            jdbc.update("DELETE FROM skills WHERE id=?", skillId);
            jdbc.update("DELETE FROM enrollments WHERE course_id=?", courseId);
            jdbc.update("DELETE FROM courses WHERE id=?", courseId);
            for (UUID id : List.of(owner,learner,foreignLearner,admin)) {
                jdbc.update("DELETE FROM refresh_sessions WHERE user_id=?", id);
                jdbc.update("DELETE FROM users WHERE id=?", id);
            }
        }

        void as(UUID user, com.codemastery.modules.auth.domain.model.Role role) {
            var principal = new com.codemastery.modules.auth.infrastructure.security.AuthenticatedUser(user,role);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                            principal, null, principal.getAuthorities()));
        }
        void asLearner() { as(learner,com.codemastery.modules.auth.domain.model.Role.LEARNER); }
        void asOwner() { as(owner,com.codemastery.modules.auth.domain.model.Role.INSTRUCTOR); }
        com.codemastery.modules.assessment.application.service.AttemptService at(Instant instant) {
            return new com.codemastery.modules.assessment.application.service.AttemptService(attemptRepository, quizRepository,
                    grading, access, identities, guard, skillGraph, java.time.Clock.fixed(instant,java.time.ZoneOffset.UTC), eventBusContext);
        }
        com.codemastery.modules.assessment.application.service.AttemptService.AnswersInput answers(boolean correct) {
            return new com.codemastery.modules.assessment.application.service.AttemptService.AnswersInput(List.of(
                    new com.codemastery.modules.assessment.application.service.AttemptService.AnswerInput(q2,List.of(false,false)),
                    new com.codemastery.modules.assessment.application.service.AttemptService.AnswerInput(q1,List.of(correct,false))));
        }
        <T> T snapshot(java.util.function.Supplier<T> action) {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setReadOnly(true); tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            return tx.execute(status -> action.get());
        }
        String bearer(UUID user) {
            UUID session=UUID.randomUUID();
            jdbc.update("INSERT INTO refresh_sessions (id,user_id,created_at,expires_at) VALUES (?,?,now(),now()+interval '1 hour')",session,user);
            return tokens.createToken(user,session);
        }

        <T> T awaitWorker(java.util.concurrent.Future<T> task, long timeout, TimeUnit unit)
                throws java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException, InterruptedException {
            try { return task.get(timeout, unit); }
            catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw failure;
            }
        }

        enum CommitAdmission { PENDING, CANCELLED, ADMITTED }

        final class WorkerTransaction {
            volatile CommitAdmission admission = CommitAdmission.PENDING;
            volatile Integer completion;
            Throwable actionFailure;
        }

        static final class WorkerCancelled extends RuntimeException {
            WorkerCancelled() { super("AS08 cleanup won before commit admission"); }
        }

        /** Test-only budgets and ownership. No ExecutorService.close(): it can wait forever. */
        final class AttemptWorkers implements AutoCloseable {
            final ExecutorService executor;
            final List<java.util.concurrent.Future<?>> tasks = new java.util.concurrent.CopyOnWriteArrayList<>();
            final Set<Integer> backendPids = new java.util.concurrent.CopyOnWriteArraySet<>();
            final java.util.concurrent.atomic.AtomicBoolean stopping = new java.util.concurrent.atomic.AtomicBoolean();
            final Object admissionLock = new Object();
            final List<WorkerTransaction> transactions = new java.util.concurrent.CopyOnWriteArrayList<>();
            final Set<Throwable> completionFailures = new java.util.concurrent.CopyOnWriteArraySet<>();
            // Test seam runs after admission, outside the coordination lock.
            Runnable afterCommitAdmission = () -> {};
            java.sql.Connection blocker;
            boolean closed;
            IllegalStateException cleanupFailure;

            AttemptWorkers(int size) { executor = Executors.newFixedThreadPool(size); }

            java.sql.Connection blockCourseRoot() throws java.sql.SQLException {
                blocker = dataSource.getConnection(); // Owned even if any subsequent setup step throws.
                blocker.setAutoCommit(false);
                try (var statement = blocker.createStatement()) {
                    statement.setQueryTimeout(5);
                    statement.execute("SET LOCAL statement_timeout='5000ms'");
                    statement.execute("SET LOCAL lock_timeout='4000ms'");
                }
                try (var statement = blocker.prepareStatement("SELECT id FROM courses WHERE id=? FOR UPDATE")) {
                    statement.setQueryTimeout(5);
                    statement.setObject(1, courseId);
                    statement.executeQuery().close();
                }
                return blocker;
            }

            <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> action) {
                return submitWithoutTransaction(() -> inTransaction(action));
            }

            <T> T inTransaction(java.util.concurrent.Callable<T> action) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                        "AttemptWorkers must own the physical transaction");
                assertEquals(JpaTransactionManager.class, transactionManager.getClass(),
                        "Requalify test coordination if the application transaction manager changes");
                var configured = (JpaTransactionManager) transactionManager;
                var outcome = new WorkerTransaction();
                var manager = new JpaTransactionManager(configured.getEntityManagerFactory()) {
                    @Override protected void doCommit(DefaultTransactionStatus status) {
                        try {
                            // Spring has already run every beforeCommit/beforeCompletion callback.
                            // Flush pending ORM writes before admission; never wait for JDBC under the lock.
                            entityManager.flush();
                            synchronized (admissionLock) {
                                if (stopping.get() || Thread.currentThread().isInterrupted()) {
                                    outcome.admission = CommitAdmission.CANCELLED;
                                    // A plain RuntimeException makes Spring perform real rollback.
                                    throw new WorkerCancelled();
                                }
                                outcome.admission = CommitAdmission.ADMITTED; // Linearization point.
                            }
                            afterCommitAdmission.run();
                            super.doCommit(status);
                        } catch (WorkerCancelled cancelled) {
                            throw cancelled;
                        } catch (RuntimeException | Error failure) {
                            completionFailures.add(failure);
                            throw failure;
                        }
                    }

                    @Override protected void doRollback(DefaultTransactionStatus status) {
                        try { super.doRollback(status); }
                        catch (RuntimeException | Error failure) {
                            completionFailures.add(failure);
                            throw failure;
                        }
                    }

                    @Override protected void doCleanupAfterCompletion(Object transaction) {
                        try { super.doCleanupAfterCompletion(transaction); }
                        catch (RuntimeException | Error failure) {
                            completionFailures.add(failure);
                            throw failure;
                        }
                    }
                };
                manager.setDataSource(configured.getDataSource());
                manager.setJpaDialect(configured.getJpaDialect());
                manager.setTransactionExecutionListeners(configured.getTransactionExecutionListeners());
                TransactionTemplate tx = new TransactionTemplate(manager);
                tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                tx.setTimeout(10);
                try {
                    return tx.execute(status -> {
                        assertTrue(status.isNewTransaction());
                        transactions.add(outcome);
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override public void afterCompletion(int completion) { outcome.completion = completion; }
                        });
                        jdbc.execute("SET LOCAL statement_timeout='8000ms'");
                        jdbc.execute("SET LOCAL lock_timeout='6000ms'");
                        backendPids.add(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        try {
                            T value = action.call();
                            // Early rollback is useful, but commit admission also covers later synchronizations.
                            synchronized (admissionLock) {
                                if (stopping.get() || Thread.currentThread().isInterrupted()) {
                                    outcome.admission = CommitAdmission.CANCELLED;
                                    throw new WorkerCancelled();
                                }
                            }
                            return value;
                        } catch (RuntimeException | Error failure) {
                            outcome.actionFailure = failure;
                            throw failure;
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            outcome.actionFailure = new RuntimeException(failure);
                            throw (RuntimeException) outcome.actionFailure;
                        } catch (Exception failure) {
                            outcome.actionFailure = new RuntimeException(failure);
                            throw (RuntimeException) outcome.actionFailure;
                        }
                    });
                } catch (RuntimeException | Error failure) {
                    // Also retain failures after the callback (including afterCommit), even if a Future is cancelled.
                    if (outcome.actionFailure == null && !(failure instanceof WorkerCancelled)) completionFailures.add(failure);
                    // TransactionTemplate may replace an action failure if rollback also throws.
                    if (outcome.actionFailure != null && outcome.actionFailure != failure) {
                        failure.addSuppressed(outcome.actionFailure);
                    }
                    throw failure;
                }
            }

            <T> java.util.concurrent.Future<T> submitWithoutTransaction(java.util.concurrent.Callable<T> action) {
                var task = executor.submit(() -> {
                    try { return action.call(); }
                    finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
                });
                tasks.add(task);
                return task;
            }

            void awaitRootWait() {
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() ->
                        backendPids.stream().anyMatch(pid -> jdbc.queryForObject(
                                "SELECT count(*) FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock' "
                                        + "AND cardinality(pg_blocking_pids(pid))>0", Integer.class, pid) == 1));
            }

            @Override public synchronized void close() {
                if (closed) {
                    if (cleanupFailure != null) throw cleanupFailure;
                    return;
                }
                boolean interrupted = Thread.interrupted();
                List<Throwable> failures = new ArrayList<>();
                try {
                    synchronized (admissionLock) {
                        stopping.set(true); // Linearizes against admission, never against a database wait.
                        transactions.forEach(outcome -> {
                            if (outcome.admission == CommitAdmission.PENDING) outcome.admission = CommitAdmission.CANCELLED;
                        });
                    }
                    // Always attempt both operations: close must not mask a rollback failure.
                    if (blocker != null) {
                        try { blocker.rollback(); } catch (Throwable failure) { failures.add(failure); }
                        try { blocker.close(); } catch (Throwable failure) { failures.add(failure); }
                    }
                    // Release the physical root before cancelling/waiting for any worker.
                    for (var task : tasks) {
                        try { task.cancel(true); } catch (Throwable failure) { failures.add(failure); }
                    }
                    try { executor.shutdownNow(); } catch (Throwable failure) { failures.add(failure); }
                    interrupted |= Thread.interrupted();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                    while (!executor.isTerminated() && System.nanoTime() < deadline) {
                        try { executor.awaitTermination(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
                        catch (InterruptedException failure) { interrupted = true; }
                        catch (Throwable failure) { failures.add(failure); break; }
                    }
                    if (!executor.isTerminated()) failures.add(new IllegalStateException("AS08 workers exceeded the cleanup deadline"));
                    failures.addAll(completionFailures);
                    for (var outcome : transactions) {
                        if (outcome.completion == null || outcome.completion == TransactionSynchronization.STATUS_UNKNOWN
                                || (outcome.admission == CommitAdmission.CANCELLED
                                    && outcome.completion != TransactionSynchronization.STATUS_ROLLED_BACK)) {
                            failures.add(new IllegalStateException("Unqualified AS08 completion: admission="
                                    + outcome.admission + ", completion=" + outcome.completion));
                        }
                    }
                    if (!failures.isEmpty()) {
                        cleanupFailure = new IllegalStateException("AS08 cleanup failed", failures.getFirst());
                        failures.stream().skip(1).forEach(cleanupFailure::addSuppressed);
                        throw cleanupFailure;
                    }
                } finally {
                    closed = true;
                    if (interrupted) Thread.currentThread().interrupt();
                }
            }
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings={"assertion", "timeout", "setup", "interruption"})
        void failureBeforeBlockerCommitHasBoundedCleanupAndNoPartialAttempt(String path) throws Exception {
            qualifyFailurePath(path, new AttemptWorkers(1), false);
        }

        void requireExpectedFailure(Class<? extends Throwable> expected, Throwable failure) {
            if (!expected.isInstance(failure)) throw new AssertionError("Unexpected AS08 primary failure", failure);
            var seen = Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
            for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
                if (current.getSuppressed().length != 0) {
                    throw new AssertionError("Expected AS08 failure concealed cleanup failure", failure);
                }
            }
        }

        void qualifyFailurePath(String path, AttemptWorkers workers, boolean injectRollbackFailure) throws Exception {
            long started = System.nanoTime();
            try {
                var failure = assertThrows(Throwable.class, () -> {
                    try (workers) {
                        workers.blockCourseRoot();
                        var waiting = workers.submit(() -> { asLearner(); return at(now).start(courseId, quizId); });
                        workers.awaitRootWait();
                        // Leave an uncommitted mutation behind as well as the physical row lock.
                        try (var statement = workers.blocker.prepareStatement("UPDATE enrollments SET status='left' WHERE user_id=?")) {
                            statement.setObject(1, learner); statement.executeUpdate();
                        }
                        if (injectRollbackFailure) {
                            workers.blocker = rollbackThenFail(workers.blocker, false);
                        }
                        switch (path) {
                            case "timeout" -> {
                                awaitWorker(waiting,25,TimeUnit.MILLISECONDS);
                                throw new AssertionError("Worker did not wait for the root");
                            }
                            case "interruption" -> {
                                Thread.currentThread().interrupt();
                                awaitWorker(waiting,1,TimeUnit.SECONDS);
                                throw new AssertionError("Future ignored caller interruption");
                            }
                            case "setup" -> {
                                try (var statement = workers.blocker.createStatement()) {
                                    statement.execute("SELECT as08_injected_missing_column FROM courses");
                                }
                            }
                            default -> org.junit.jupiter.api.Assertions.fail("Injected before blocker commit");
                        }
                    }
                });
                Class<? extends Throwable> expected = switch (path) {
                    case "timeout" -> java.util.concurrent.TimeoutException.class;
                    case "interruption" -> InterruptedException.class;
                    case "setup" -> java.sql.SQLException.class;
                    default -> org.opentest4j.AssertionFailedError.class;
                };
                requireExpectedFailure(expected, failure);
                assertEquals(path.equals("interruption"), Thread.currentThread().isInterrupted());
            } finally {
                // The test runner must not inherit the deliberately injected interruption.
                Thread.interrupted();
            }
            assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(15));
            assertWorkerResourcesReleased(workers);
            assertEquals("active", jdbc.queryForObject("SELECT status FROM enrollments WHERE user_id=? AND course_id=?", String.class, learner, courseId));
            assertNoCommittedAttempt();
            asLearner();
            assertTrue(at(now).start(courseId, quizId).created()); // Reacquires the same root.
        }

        java.sql.Connection rollbackThenFail(java.sql.Connection physical, boolean failClose) {
            return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                    java.sql.Connection.class.getClassLoader(), new Class<?>[]{java.sql.Connection.class},
                    (proxy, method, arguments) -> {
                        try {
                            Object value = method.invoke(physical, arguments);
                            if (method.getName().equals("rollback")) throw new SQLException("Injected after physical rollback", "IR296");
                            if (failClose && method.getName().equals("close")) throw new SQLException("Injected after physical close", "IR296");
                            return value;
                        } catch (java.lang.reflect.InvocationTargetException wrapped) { throw wrapped.getCause(); }
                    });
        }

        void assertWorkerResourcesReleased(AttemptWorkers workers) throws Exception {
            assertTrue(workers.executor.isTerminated());
            if (workers.blocker != null) assertTrue(workers.blocker.isClosed());
            assertFalse(workers.backendPids.isEmpty());
            for (int pid : workers.backendPids) {
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE pid=? "
                        + "AND (xact_start IS NOT NULL OR wait_event_type='Lock')", Integer.class, pid));
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE pid=? "
                        + "AND (relation='courses'::regclass OR locktype='advisory')", Integer.class, pid));
            }
            assertEquals(0, dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class)
                    .getHikariPoolMXBean().getActiveConnections());
        }

        void assertNoCommittedAttempt() {
            // JdbcTemplate obtains independent autocommit reads on the test thread.
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM attempts WHERE quiz_id=?", Integer.class, quizId));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM attempt_answers WHERE question_id IN (?,?)", Integer.class, q1, q2));
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings={"assertion", "timeout", "setup", "interruption"})
        void cleanupFailureCannotQualifyAsAnExpectedPrimaryFailure(String path) throws Exception {
            var workers = new AttemptWorkers(1);
            var rejected = assertThrows(AssertionError.class, () -> qualifyFailurePath(path, workers, true));
            assertEquals("Expected AS08 failure concealed cleanup failure", rejected.getMessage());
            Throwable primary = rejected.getCause();
            assertEquals(1, primary.getSuppressed().length);
            var cleanup = primary.getSuppressed()[0];
            assertEquals("AS08 cleanup failed", cleanup.getMessage());
            assertEquals("Injected after physical rollback", cleanup.getCause().getMessage());
            assertEquals("IR296", ((SQLException) cleanup.getCause()).getSQLState());
            assertWorkerResourcesReleased(workers);
            assertNoCommittedAttempt();
            assertEquals("active", jdbc.queryForObject("SELECT status FROM enrollments WHERE user_id=? AND course_id=?", String.class, learner, courseId));
            asLearner();
            assertTrue(at(now).start(courseId, quizId).created());
        }

        @Test void rollbackAndCloseFailuresBothSurviveCleanupAndExpectedPrimaryQualification() throws Exception {
            var workers = new AttemptWorkers(1);
            var primary = assertThrows(org.opentest4j.AssertionFailedError.class, () -> {
                try (workers) {
                    workers.blockCourseRoot();
                    workers.blocker = rollbackThenFail(workers.blocker, true);
                    workers.submit(() -> { asLearner(); return at(now).start(courseId, quizId); });
                    workers.awaitRootWait();
                    org.junit.jupiter.api.Assertions.fail("Expected primary with two cleanup faults");
                }
            });
            assertEquals(1, primary.getSuppressed().length);
            Throwable cleanup = primary.getSuppressed()[0];
            assertEquals("Injected after physical rollback", cleanup.getCause().getMessage());
            assertEquals(1, cleanup.getSuppressed().length);
            assertEquals("Injected after physical close", cleanup.getSuppressed()[0].getMessage());
            var rejected = assertThrows(AssertionError.class,
                    () -> requireExpectedFailure(org.opentest4j.AssertionFailedError.class, primary));
            assertTrue(rejected.getCause() == primary);
            assertWorkerResourcesReleased(workers);
            assertNoCommittedAttempt();
            asLearner();
            assertTrue(at(now).start(courseId, quizId).created());
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings={"beforeCommit", "beforeCompletion", "commitAdmitted", "commitFailure", "afterCommitFailure"})
        void jdbcCompletionWaitClassifiesCancellationAndCommitAdmission(String phase) throws Exception {
            var workers = new AttemptWorkers(1);
            var reachedCompletion = new java.util.concurrent.CountDownLatch(1);
            var cleanupFailure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            Thread closer = new Thread(() -> {
                try { workers.close(); } catch (Throwable failure) { cleanupFailure.set(failure); }
            }, "as08-completion-cleanup");
            boolean commitWins = phase.equals("commitAdmitted") || phase.equals("afterCommitFailure");
            boolean admissionWins = commitWins || phase.equals("commitFailure");
            boolean completionFails = phase.equals("commitFailure") || phase.equals("afterCommitFailure");
            long started = System.nanoTime();
            // This session lock deliberately blocks real JDBC after the old callback stopping check.
            try (var physicalBlocker = dataSource.getConnection()) {
                try (var statement = physicalBlocker.createStatement()) {
                    statement.setQueryTimeout(5);
                    statement.execute("SELECT pg_advisory_lock(296, 3)");
                }
                Runnable jdbcWait = () -> {
                    // Actual AttemptService.start writes are visible inside the still-pending transaction.
                    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM attempts WHERE quiz_id=?", Integer.class, quizId));
                    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM attempt_answers WHERE question_id IN (?,?)", Integer.class, q1, q2));
                    reachedCompletion.countDown();
                    jdbc.execute("SELECT pg_advisory_xact_lock(296, 3)");
                };
                Throwable primaryFailure = null;
                try {
                    if (admissionWins) workers.afterCommitAdmission = () -> {
                        jdbcWait.run();
                        if (phase.equals("commitFailure")) throw new IllegalStateException("Injected completion failure");
                    };
                    var task = workers.submit(() -> {
                        asLearner();
                        var value = at(now).start(courseId, quizId);
                        assertTrue(value.created());
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override public void beforeCommit(boolean readOnly) {
                                if (phase.equals("beforeCommit")) jdbcWait.run();
                            }
                            @Override public void beforeCompletion() {
                                if (phase.equals("beforeCompletion")) jdbcWait.run();
                            }
                            @Override public void afterCommit() {
                                if (phase.equals("afterCommitFailure")) throw new IllegalStateException("Injected completion failure");
                            }
                        });
                        return value;
                    });
                    assertTrue(reachedCompletion.await(5, TimeUnit.SECONDS));
                    workers.awaitRootWait();
                    assertNoCommittedAttempt();
                    assertEquals(admissionWins ? CommitAdmission.ADMITTED : CommitAdmission.PENDING,
                            workers.transactions.getFirst().admission);
                    closer.start();
                    org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(3))
                            .until(() -> workers.stopping.get() && task.isCancelled());
                    assertEquals(admissionWins ? CommitAdmission.ADMITTED : CommitAdmission.CANCELLED,
                            workers.transactions.getFirst().admission);
                    try (var statement = physicalBlocker.createStatement()) {
                        statement.setQueryTimeout(5);
                        try (var unlocked = statement.executeQuery("SELECT pg_advisory_unlock(296, 3)")) {
                            assertTrue(unlocked.next()); assertTrue(unlocked.getBoolean(1));
                        }
                    }
                    closer.join(12000);
                    assertFalse(closer.isAlive(), "AS08 cleanup did not terminate");
                    if (completionFails) {
                        assertTrue(cleanupFailure.get() instanceof IllegalStateException);
                        assertEquals("AS08 cleanup failed", cleanupFailure.get().getMessage());
                        assertEquals("Injected completion failure", cleanupFailure.get().getCause().getMessage());
                    } else assertNull(cleanupFailure.get(), () -> "Cleanup failed: " + cleanupFailure.get());
                    var outcome = workers.transactions.getFirst();
                    assertEquals(commitWins ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK,
                            outcome.completion);
                    System.out.println("AS08_COMPLETION phase=" + phase + " admission=" + outcome.admission
                            + " completion=" + outcome.completion + " cancelled=" + task.isCancelled()
                            + " cleanupFailed=" + (cleanupFailure.get() != null)
                            + " attempts=" + jdbc.queryForObject("SELECT count(*) FROM attempts WHERE quiz_id=?", Integer.class, quizId));
                } catch (Exception | Error failure) {
                    primaryFailure = failure;
                    throw failure;
                } finally {
                    // Release before waiting, and retain every cleanup error if an assertion already failed.
                    List<Throwable> secondary = new ArrayList<>();
                    try (var statement = physicalBlocker.createStatement()) {
                        statement.setQueryTimeout(5);
                        statement.execute("SELECT pg_advisory_unlock_all()");
                    } catch (Throwable failure) { secondary.add(failure); }
                    try {
                        if (closer.getState() == Thread.State.NEW) workers.close();
                        else { closer.join(12000); assertFalse(closer.isAlive()); }
                    } catch (Throwable failure) {
                        if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                        secondary.add(failure);
                    }
                    if (primaryFailure != null) {
                        if (cleanupFailure.get() != null) secondary.add(cleanupFailure.get());
                        secondary.forEach(primaryFailure::addSuppressed);
                    } else if (!secondary.isEmpty()) {
                        var failure = new IllegalStateException("AS08 race resource cleanup failed", secondary.getFirst());
                        secondary.stream().skip(1).forEach(failure::addSuppressed);
                        throw failure;
                    }
                }
            }
            assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(15));
            assertWorkerResourcesReleased(workers);
            // Also prove that the blocker session returned to the pool without an advisory lock.
            try (var probe = dataSource.getConnection(); var statement = probe.createStatement()) {
                statement.setQueryTimeout(5);
                try (var result = statement.executeQuery("SELECT pg_try_advisory_xact_lock(296, 3)")) {
                    assertTrue(result.next()); assertTrue(result.getBoolean(1));
                }
            }
            if (commitWins) {
                assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM attempts WHERE quiz_id=?", Integer.class, quizId));
                assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM attempt_answers WHERE question_id IN (?,?)", Integer.class, q1, q2));
                asLearner();
                assertFalse(at(now).start(courseId, quizId).created());
            } else {
                assertNoCommittedAttempt();
                asLearner();
                assertTrue(at(now).start(courseId, quizId).created()); // Root is available to a subsequent valid operation.
            }
        }

        @Test void concurrentStartsAreIdempotentAndOnlyCommittedRetakesAdvanceTheNumber() throws Exception {
            try (var workers=new AttemptWorkers(2)) {
                var barrier=new java.util.concurrent.CyclicBarrier(2);
                java.util.concurrent.Callable<com.codemastery.modules.assessment.application.service.AttemptService.StartOutcome> call=()->{
                    asLearner(); try {barrier.await(5,TimeUnit.SECONDS);return at(now).start(courseId,quizId);}
                    finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
                };
                var a=workers.submit(call);var b=workers.submit(call);
                var first=awaitWorker(a,10,TimeUnit.SECONDS);var second=awaitWorker(b,10,TimeUnit.SECONDS);
                assertEquals(first.attempt().attemptId(),second.attempt().attemptId());
                assertTrue(first.created()!=second.created());
                assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM attempts WHERE quiz_id=?",Integer.class,quizId));
                asLearner();
                var result=at(now.plusSeconds(1)).submit(courseId,quizId,first.attempt().attemptId(),answers(true));
                assertEquals(new BigDecimal("50.00"),result.scorePercentage());
                assertEquals(2,at(now.plusSeconds(2)).start(courseId,quizId).attempt().attemptNumber());
                asOwner();
                assertTrue(live.hasSubmittedAttempts(courseId));
                assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                        ()->quizService.update(courseId,quizId,new QuizService.QuizInput("Changed","practice",60,List.of(q1))));
            }
        }

        @Test void exactDeadlineAndLogicalExpiryUseOnlyTheLastCommittedSavedMasks() {
            asLearner();var attempt=at(now).start(courseId,quizId).attempt();
            at(now.plusSeconds(59).plusNanos(999999000)).saveAnswers(courseId,quizId,attempt.attemptId(),answers(true));
            assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                    ()->at(attempt.expiresAt()).saveAnswers(courseId,quizId,attempt.attemptId(),answers(false)));
            assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                    ()->at(attempt.expiresAt()).submit(courseId,quizId,attempt.attemptId(),answers(false)));
            // No identity/publication/membership is needed by internal persisted-overdue cleanup.
            jdbc.update("UPDATE courses SET publication_status='hidden' WHERE id=?",courseId);
            jdbc.update("UPDATE quizzes SET publication_status='hidden' WHERE id=?",quizId);
            jdbc.update("UPDATE enrollments SET status='left' WHERE user_id=?",learner);
            jdbc.update("UPDATE users SET account_status='locked' WHERE id=?",learner);
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            at(now.plusSeconds(3600)).finalizeExpired(attempt.attemptId());
            at(now.plusSeconds(3601)).finalizeExpired(attempt.attemptId());
            asOwner();var result=snapshot(()->live.readOwnedResult(courseId,quizId,attempt.attemptId()));
            assertEquals(attempt.expiresAt(),result.submittedAt());assertEquals(new BigDecimal("50.00"),result.scorePercentage());
            assertArrayEquals(new Boolean[]{true,false},attemptRepository.findAnswersInPositionOrder(attempt.attemptId()).getFirst().selected_options());
        }

        @Test void failureAfterFlushedAnswerWritesRollsBackMasksScoreAndTerminalTime() {
            asLearner();var attempt=at(now).start(courseId,quizId).attempt();
            com.codemastery.modules.assessment.infrastructure.persistence.repository.AttemptRepositoryImpl target =
                    org.springframework.test.util.AopTestUtils.getUltimateTargetObject(attemptRepository);
            var spy=org.mockito.Mockito.spy(target);
            org.mockito.Mockito.doAnswer(call->{ call.callRealMethod();throw new IllegalStateException("Injected after flush");})
                    .when(spy).updateAnswers(org.mockito.ArgumentMatchers.anyList());
            var failing=new com.codemastery.modules.assessment.application.service.AttemptService(spy,quizRepository,grading,access,identities,
                    guard,skillGraph,java.time.Clock.fixed(now.plusSeconds(1),java.time.ZoneOffset.UTC), eventBusContext);
            assertThrows(IllegalStateException.class,()->failing.submit(courseId,quizId,attempt.attemptId(),answers(true)));
            Attempt persisted=attemptRepository.findById(attempt.attemptId()).orElseThrow();
            assertNull(persisted.submitted_at());assertNull(persisted.score_percentage());
            for (var answer:attemptRepository.findAnswersInPositionOrder(attempt.attemptId())) {
                assertArrayEquals(new Boolean[]{false,false},answer.selected_options());assertNull(answer.is_correct());
            }
            assertEquals(new BigDecimal("50.00"),at(now.plusSeconds(2)).submit(courseId,quizId,attempt.attemptId(),answers(true)).scorePercentage());
        }

        @Test void missingCapturedRowsAndIncorrectStoredGradingNeverBecomePartialResults() {
            asLearner();var attempt=at(now).start(courseId,quizId).attempt();
            jdbc.update("DELETE FROM attempt_answers WHERE attempt_id=? AND question_id=?",attempt.attemptId(),q2);
            assertThrows(IllegalStateException.class,()->at(now.plusSeconds(1)).submit(courseId,quizId,attempt.attemptId(),
                    new com.codemastery.modules.assessment.application.service.AttemptService.AnswersInput(List.of(
                    new com.codemastery.modules.assessment.application.service.AttemptService.AnswerInput(q1,List.of(true,false))))));
            assertNull(attemptRepository.findById(attempt.attemptId()).orElseThrow().submitted_at());
            assertThrows(IllegalStateException.class,()->at(now.plusSeconds(60)).finalizeExpired(attempt.attemptId()));
        }

        @Test void keysetPassContinuesPastAPoisonedFirstCandidateAcrossMultiplePages() {
            asLearner();var first=at(now).start(courseId,quizId).attempt();
            UUID secondQuiz=UUID.randomUUID(),thirdQuiz=UUID.randomUUID();addQuiz(secondQuiz,"practice");addQuiz(thirdQuiz,"test");
            var second=at(now.plusSeconds(1)).start(courseId,secondQuiz).attempt();
            var third=at(now.plusSeconds(2)).start(courseId,thirdQuiz).attempt();
            jdbc.update("DELETE FROM attempt_answers WHERE attempt_id=? AND question_id=?",first.attemptId(),q2);
            var scheduler=new com.codemastery.modules.assessment.application.scheduler.AttemptAutoSubmitScheduler(
                    attemptRepository,at(now.plusSeconds(100)),1000,1,java.time.Clock.fixed(now.plusSeconds(100),java.time.ZoneOffset.UTC));
            assertEquals(2,scheduler.runPass());
            assertNull(attemptRepository.findById(first.attemptId()).orElseThrow().submitted_at());
            assertEquals(second.expiresAt(),attemptRepository.findById(second.attemptId()).orElseThrow().submitted_at());
            assertEquals(third.expiresAt(),attemptRepository.findById(third.attemptId()).orElseThrow().submitted_at());
        }

        @Test void learnerAdmissionIsRecheckedAfterWaitingForThePhysicalCourseRoot() throws Exception {
            try (var worker=new AttemptWorkers(1)) {
                var blocker=worker.blockCourseRoot();
                var waiting=worker.submit(()->{asLearner();try {return at(now).start(courseId,quizId);}
                    finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}});
                // Observe the real lock wait rather than timing a sleep.
                worker.awaitRootWait();
                try(var statement=blocker.prepareStatement("UPDATE enrollments SET status='left' WHERE user_id=?")) {statement.setObject(1,learner);statement.executeUpdate();}
                blocker.commit();
                var failure=assertThrows(java.util.concurrent.ExecutionException.class,()->awaitWorker(waiting,10,TimeUnit.SECONDS));
                assertTrue(failure.getCause() instanceof org.springframework.security.access.AccessDeniedException);
                assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM attempts WHERE quiz_id=?",Integer.class,quizId));
            }
        }

        @Test void manualSaveAndExpiryRacesProduceOneImmutableTerminalResult() throws Exception {
            asLearner();var attempt=at(now).start(courseId,quizId).attempt();
            try(var workers=new AttemptWorkers(3)) {
                var barrier=new java.util.concurrent.CyclicBarrier(3);
                var manual=workers.submit(()->{asLearner();try {barrier.await(5,TimeUnit.SECONDS);
                    return at(now.plusSeconds(59)).submit(courseId,quizId,attempt.attemptId(),answers(true));
                    }finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}});
                var save=workers.submitWithoutTransaction(()->{asLearner();try {barrier.await(5,TimeUnit.SECONDS);
                    try {workers.inTransaction(()->{at(now.plusSeconds(59)).saveAnswers(courseId,quizId,attempt.attemptId(),answers(false));return null;});}
                    catch(com.codemastery.common.exception.BusinessRuleException terminal) {assertEquals(org.springframework.http.HttpStatus.CONFLICT,terminal.getStatus());}
                    return null;
                    }finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}});
                var expiry=workers.submit(()->{barrier.await(5,TimeUnit.SECONDS);at(attempt.expiresAt()).finalizeExpired(attempt.attemptId());return null;});
                var manualResult=awaitWorker(manual,10,TimeUnit.SECONDS);awaitWorker(save,10,TimeUnit.SECONDS);awaitWorker(expiry,10,TimeUnit.SECONDS);
                asLearner();var repeated=at(now.plusSeconds(61)).submit(courseId,quizId,attempt.attemptId(),answers(false));
                assertEquals(manualResult,repeated);
                assertTrue(Set.of(now.plusSeconds(59),attempt.expiresAt()).contains(repeated.submittedAt()));
                assertEquals(repeated.scorePercentage(),snapshot(()->live.readResult(courseId,quizId,attempt.attemptId())).scorePercentage());
            }
        }

        @Test void firstAttemptRereadsDefinitionsCommittedByTheRootHolder() throws Exception {
            // AS06's authoring fixture is canonical 1..N; separate AS08 cases retain legacy gaps.
            jdbc.update("UPDATE quiz_questions SET position=2 WHERE quiz_id=? AND question_id=?",quizId,q2);
            try(var worker=new AttemptWorkers(1)) {
                asOwner();
                java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<com.codemastery.modules.assessment.application.service.AttemptService.StartOutcome>> waiting=
                        new java.util.concurrent.atomic.AtomicReference<>();
                guard.withCourseConfigurationWrite(courseId,()->{
                    waiting.set(worker.submit(()->{asLearner();try{return at(now).start(courseId,quizId);}
                        finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}}));
                    worker.awaitRootWait();
                    quizService.publish(courseId,quizId,false);
                    quizService.update(courseId,quizId,new QuizService.QuizInput("Replacement","test",120,List.of(q2)));
                    quizService.publish(courseId,quizId,true);
                    return null;
                });
                var captured=awaitWorker(waiting.get(),10,TimeUnit.SECONDS).attempt();
                assertEquals(List.of(q2),captured.questions().stream().map(com.codemastery.modules.assessment.application.service.AttemptService.AttemptQuestionView::questionId).toList());
                assertEquals(now.plusSeconds(120),captured.expiresAt());
            }
        }

        @Test void explicitStatementDeadlineRollsBackAWaitingAttemptAndReleasesItsConnection() throws Exception {
            try(var worker=new AttemptWorkers(1)) {
                var blocker=worker.blockCourseRoot();
                var waiting=worker.submit(()->{asLearner();try{
                    TransactionTemplate tx=new TransactionTemplate(transactionManager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    return tx.execute(status->{jdbc.execute("SET LOCAL statement_timeout='1000ms'");return at(now).start(courseId,quizId);});
                    }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}});
                var failure=assertThrows(java.util.concurrent.ExecutionException.class,()->awaitWorker(waiting,5,TimeUnit.SECONDS));
                assertTrue(failure.getCause() instanceof org.springframework.dao.DataAccessException);
                assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM attempts WHERE quiz_id=?",Integer.class,quizId));
                blocker.rollback();asLearner();assertTrue(at(now).start(courseId,quizId).created());
            }
        }

        void terminal(UUID quiz,int number,Instant start,Instant submitted,boolean first,boolean second) {
            UUID attempt=UUID.randomUUID();
            jdbc.update("INSERT INTO attempts VALUES (?,?,?,?,?,?,?,?)",attempt,learner,quiz,number,
                    java.time.OffsetDateTime.ofInstant(start,java.time.ZoneOffset.UTC),java.time.OffsetDateTime.ofInstant(start.plusSeconds(60),java.time.ZoneOffset.UTC),
                    java.time.OffsetDateTime.ofInstant(submitted,java.time.ZoneOffset.UTC),BigDecimal.valueOf((first?50:0)+(second?50:0)));
            jdbc.update("INSERT INTO attempt_answers VALUES (?,?,?,1,ARRAY[?,false],?)",UUID.randomUUID(),attempt,q1,first,first);
            jdbc.update("INSERT INTO attempt_answers VALUES (?,?,?,3,ARRAY[?,false],?)",UUID.randomUUID(),attempt,q2,second,second);
        }

        @Test void diagnosticOfferStartResumeExpiryAndOneTimeCompletionShareTheApprovedGate() {
            UUID diagnostic=UUID.randomUUID(),other=UUID.randomUUID();addQuiz(diagnostic,"diagnostic");addQuiz(other,"diagnostic");
            jdbc.update("UPDATE quizzes SET title='A diagnostic' WHERE id=?",diagnostic);
            jdbc.update("UPDATE quizzes SET title='Z diagnostic' WHERE id=?",other);
            asLearner();assertEquals(diagnostic,snapshot(()->live.readMyPlacementQuizId(courseId)));
            var started=at(now).start(courseId,diagnostic).attempt();
            assertFalse(at(now.plusSeconds(1)).start(courseId,diagnostic).created());
            assertEquals(diagnostic,snapshot(()->live.readMyPlacementQuizId(courseId)));
            assertThrows(com.codemastery.common.exception.BusinessRuleException.class,()->at(now.plusSeconds(1)).start(courseId,other));
            var overdue=at(now.plusSeconds(60));
            assertNull(snapshot(()->overdue.readMyPlacementQuizId(courseId)));
            assertThrows(com.codemastery.common.exception.BusinessRuleException.class,()->overdue.start(courseId,diagnostic));
            overdue.finalizeExpired(started.attemptId());
            assertNull(snapshot(()->live.readMyPlacementQuizId(courseId)));
            assertThrows(com.codemastery.common.exception.BusinessRuleException.class,()->at(now.plusSeconds(61)).start(courseId,diagnostic));
        }

        @Test void concurrentDifferentDiagnosticStartsCaptureOnlyOneQuizForTheLearner() throws Exception {
            UUID first=UUID.randomUUID(),second=UUID.randomUUID();addQuiz(first,"diagnostic");addQuiz(second,"diagnostic");
            try(var workers=new AttemptWorkers(2)) {
                var barrier=new java.util.concurrent.CyclicBarrier(2);
                java.util.function.Function<UUID,java.util.concurrent.Callable<Boolean>> call=id->()->{
                    asLearner();try {barrier.await(5,TimeUnit.SECONDS);workers.inTransaction(()->at(now).start(courseId,id));return true;}
                    catch(com.codemastery.common.exception.BusinessRuleException conflict){assertEquals(org.springframework.http.HttpStatus.CONFLICT,conflict.getStatus());return false;}
                    finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}};
                var a=workers.submitWithoutTransaction(call.apply(first));var b=workers.submitWithoutTransaction(call.apply(second));
                assertTrue(awaitWorker(a,10,TimeUnit.SECONDS)!=awaitWorker(b,10,TimeUnit.SECONDS));
                assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM attempts WHERE user_id=?",Integer.class,learner));
            }
        }

        @Test void missingFrozenMembershipCannotSilentlyTurnCapturedEvidenceIntoUnknown() {
            asLearner();var attempt=at(now).start(courseId,quizId).attempt();
            at(now.plusSeconds(1)).submit(courseId,quizId,attempt.attemptId(),answers(true));
            jdbc.update("DELETE FROM quiz_questions WHERE quiz_id=?",quizId);
            assertThrows(IllegalStateException.class,()->snapshot(()->live.readMyLatestSkillEvidence(courseId,Set.of(skillId))));
        }

        @Test void admittedSelfEvidenceNeverAcceptsAnotherRoleOrLeftMembershipAndAncestryIsNotAnOpenRead() {
            assertThrows(IllegalStateException.class,()->skillGraph.requireAssessmentSkillsInCourse(courseId,Set.of(skillId)));
            asOwner();
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    ()->snapshot(()->live.readMyLatestSkillEvidence(courseId,Set.of(skillId))));
            asLearner();
            jdbc.update("UPDATE enrollments SET status='left' WHERE user_id=?",learner);
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    ()->snapshot(()->live.readMyLatestSkillEvidence(courseId,Set.of(skillId))));
            assertThrows(IllegalStateException.class,()->snapshot(()->{skillGraph.requireAssessmentSkillsInCourse(courseId,Set.of(UUID.randomUUID()));return null;}));
        }

        @Test void tupleTieBreakersHiddenQuizzesDiagnosticsAndPairAveragesKeepTheirDistinctSemantics() {
            UUID highQuiz=UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff0"),diagnostic=UUID.randomUUID();
            addQuiz(highQuiz,"test");addQuiz(diagnostic,"diagnostic");
            terminal(quizId,1,now,now.plusSeconds(10),false,false);
            terminal(highQuiz,1,now,now.plusSeconds(10),true,true);
            terminal(highQuiz,2,now,now.plusSeconds(10),true,false);
            asLearner();
            var latest=snapshot(()->live.readMyLatestSkillEvidence(courseId,Set.of(skillId))).bySkill().get(skillId);
            assertEquals(highQuiz,latest.quizId());assertEquals(2,latest.attemptNumber());
            terminal(quizId,2,now.plusSeconds(1),now.plusSeconds(10),false,false);
            assertEquals(quizId,snapshot(()->live.readMyLatestSkillEvidence(courseId,Set.of(skillId))).bySkill().get(skillId).quizId());
            terminal(diagnostic,1,now.plusSeconds(2),now.plusSeconds(11),true,true);
            jdbc.update("UPDATE quizzes SET publication_status='hidden' WHERE course_id=?",courseId);
            assertEquals(diagnostic,snapshot(()->live.readMyLatestSkillEvidence(courseId,Set.of(skillId))).bySkill().get(skillId).quizId());
            var outcome=snapshot(()->live.readQuizOutcome(courseId,Set.of(learner)));
            assertEquals(2,outcome.scoredLearnerQuizPairs());assertEquals(new BigDecimal("25.00"),outcome.averageQuizScore());
            assertEquals(0,snapshot(()->live.readQuizOutcome(courseId,Set.of(foreignLearner))).scoredLearnerQuizPairs());
        }

        @Test void submissionMarkerIsBoundOnlyToThisCallbackThreadCourseAndPhysicalTransaction() {
            asLearner();var attempt=at(now).start(courseId,quizId).attempt();
            var observed=new java.util.concurrent.atomic.AtomicBoolean();
            var inspector=new com.codemastery.modules.assessment.application.service.QuizGradingService(){
                @Override public Grade grade(List<GradingItem> items) {
                    assertTrue(live.readLatestSkillEvidence(courseId,learner,Set.of(skillId)).bySkill().isEmpty());
                    assertThrows(IllegalStateException.class,()->live.readLatestSkillEvidence(UUID.randomUUID(),learner,Set.of(skillId)));
                    TransactionTemplate fresh=new TransactionTemplate(transactionManager);
                    fresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    fresh.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    assertThrows(IllegalStateException.class,()->fresh.execute(status->live.readLatestSkillEvidence(courseId,learner,Set.of(skillId))));
                    try(var worker=new AttemptWorkers(1)) {
                        var failure=assertThrows(java.util.concurrent.ExecutionException.class,()->awaitWorker(worker.submitWithoutTransaction(
                                ()->live.readLatestSkillEvidence(courseId,learner,Set.of(skillId))),5,TimeUnit.SECONDS));
                        assertTrue(failure.getCause() instanceof IllegalStateException);
                    }
                    observed.set(true);return super.grade(items);
                }
            };
            var service=new com.codemastery.modules.assessment.application.service.AttemptService(attemptRepository,quizRepository,inspector,
                    access,identities,guard,skillGraph,java.time.Clock.fixed(now.plusSeconds(1),java.time.ZoneOffset.UTC), eventBusContext);
            service.submit(courseId,quizId,attempt.attemptId(),answers(true));assertTrue(observed.get());
            assertThrows(IllegalStateException.class,()->live.readLatestSkillEvidence(courseId,learner,Set.of(skillId)));
        }

        @Test void realRuntimeMatrixPermitsOnlyTheImplementedAttemptOperations() throws Exception {
            String role="as08_"+UUID.randomUUID().toString().replace("-", "");
            jdbc.execute("CREATE ROLE "+role+" NOLOGIN");
            try {
                jdbc.execute("GRANT USAGE ON SCHEMA public TO "+role);
                String script=java.nio.file.Files.readString(java.nio.file.Path.of("..","infrastructure","vm","deploy.sh"));
                String matrix=script.split("RUNTIME_PRIVILEGE_MATRIX='\n",2)[1].split("\n'",2)[0];
                for(String line:matrix.split("\n")) {
                    String[] fields=line.trim().split("\\s+");
                    if(!fields[1].equals("-")) jdbc.execute("GRANT "+fields[1]+" ON TABLE public."+fields[0]+" TO "+role);
                    if(!fields[2].equals("-")) jdbc.execute("GRANT "+fields[2].replace('+',',')+" ON TABLE public."+fields[0]+" TO "+role);
                }
                asLearner();TransactionTemplate tx=new TransactionTemplate(transactionManager);
                tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                var attempt=tx.execute(status->{jdbc.execute("SET LOCAL ROLE "+role);return at(now).start(courseId,quizId).attempt();});
                var result=tx.execute(status->{jdbc.execute("SET LOCAL ROLE "+role);return at(now.plusSeconds(1)).submit(courseId,quizId,attempt.attemptId(),answers(true));});
                assertEquals(new BigDecimal("50.00"),result.scorePercentage());
                for(String forbidden:List.of("UPDATE public.attempts SET expires_at=expires_at WHERE false",
                        "UPDATE public.attempt_answers SET question_id=question_id WHERE false",
                        "DELETE FROM public.attempts WHERE false", "DELETE FROM public.attempt_answers WHERE false")) {
                    var failure=assertThrows(org.springframework.dao.DataAccessException.class,
                            ()->tx.execute(status->{jdbc.execute("SET LOCAL ROLE "+role);jdbc.execute(forbidden);return null;}));
                    assertTrue(failure.getMostSpecificCause() instanceof java.sql.SQLException sql && "42501".equals(sql.getSQLState()));
                }
            } finally {
                jdbc.execute("DROP OWNED BY "+role);jdbc.execute("DROP ROLE "+role);
            }
        }

        @Test void incompatibleOuterTransactionsAndStartOrSaveDoNotGrantEvidenceExecution() {
            asLearner();TransactionTemplate rr=new TransactionTemplate(transactionManager);rr.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            assertThrows(IllegalStateException.class,()->rr.execute(status->at(now).start(courseId,quizId)));
            assertThrows(IllegalStateException.class,()->live.readLatestSkillEvidence(courseId,learner,Set.of(skillId)));
            guard.withLearnerAssessmentWrite(courseId,()->{
                assertThrows(IllegalStateException.class,()->live.readLatestSkillEvidence(courseId,learner,Set.of(skillId)));return null;});
            assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,()->live.readQuizOutcome(courseId,Set.of(learner)));
            assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,()->live.readMyLatestSkillEvidence(courseId,Set.of(skillId)));
        }

        @Test void latestEvidenceAndReportingUseAuthoritativeTupleCountsAndLatestPairMean() {
            asLearner();var a=at(now).start(courseId,quizId).attempt();at(now.plusSeconds(1)).submit(courseId,quizId,a.attemptId(),answers(true));
            var b=at(now.plusSeconds(2)).start(courseId,quizId).attempt();at(now.plusSeconds(3)).submit(courseId,quizId,b.attemptId(),answers(false));
            var evidence=snapshot(()->live.readMyLatestSkillEvidence(courseId,Set.of(skillId,UUID.randomUUID())));
            assertEquals(Set.of(skillId),evidence.bySkill().keySet());assertEquals(b.attemptId(),evidence.bySkill().get(skillId).attemptId());
            assertEquals(0,evidence.bySkill().get(skillId).correctCount());assertEquals(2,evidence.bySkill().get(skillId).totalCount());
            var outcome=snapshot(()->live.readQuizOutcome(courseId,Set.of(learner)));
            assertEquals(1,outcome.scoredLearnerQuizPairs());assertEquals(new BigDecimal("0.00"),outcome.averageQuizScore());
            assertNull(snapshot(()->live.readQuizOutcome(courseId,Set.of())).averageQuizScore());
            jdbc.update("UPDATE attempt_answers SET is_correct=true WHERE attempt_id=? AND question_id=?",b.attemptId(),q1);
            assertThrows(IllegalStateException.class,()->snapshot(()->live.readMyLatestSkillEvidence(courseId,Set.of(skillId))));
        }

        @Test void httpSecurityClosedBodiesNoStoreAndResultConfidentialityUseTheRealStack() throws Exception {
            var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
            String path="/api/courses/"+courseId+"/quizzes/"+quizId+"/attempts";
            String learnerToken=bearer(learner),ownerToken=bearer(owner),foreignToken=bearer(foreignLearner),adminToken=bearer(admin);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"));
            for(String token:List.of(ownerToken,adminToken)) mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                    .header("Authorization","Bearer "+token)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
            var started=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Authorization","Bearer "+learnerToken))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()).andReturn();
            var json=tools.jackson.databind.json.JsonMapper.builder().build();String id=json.readTree(started.getResponse().getContentAsString()).get("attemptId").asString();
            String valid="{\"answers\":[{\"questionId\":\""+q1+"\",\"selectedOptions\":[true,false]},{\"questionId\":\""+q2+"\",\"selectedOptions\":[false,false]}]}";
            for(String body:List.of(valid+" {}",valid.replace("{\"answers\":","{\"answers\":[],\"answers\":"),
                    valid.replace("true","\"true\""),valid.replace(q1.toString(),"1-1-1-1-1"),valid.replace(q2.toString(),q1.toString()),
                    valid.replace("true","null"))) {
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path+"/"+id+"/answers")
                        .header("Authorization","Bearer "+learnerToken).contentType("application/json").content(body))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"));
            }
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path+"/"+id).header("Authorization","Bearer "+foreignToken))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"/"+id+"/submission")
                    .header("Authorization","Bearer "+learnerToken).contentType("application/json").content(valid))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.scorePercentage").value(50))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.answers[0].correctOptions").doesNotExist());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path+"?size=1&page=0&page=1").header("Authorization","Bearer "+learnerToken))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
            jdbc.update("UPDATE users SET account_status='locked' WHERE id=?",learner);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("Authorization","Bearer "+learnerToken))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        }

        @Test void admittedEvidenceAndPagesExecuteWithOnlyExistingCt17ReadPrivileges() {
            terminal(quizId, 1, now, now.plusSeconds(1), true, false);
            String role = "as16_read_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.execute("CREATE ROLE " + role + " NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT");
            try {
                jdbc.execute("GRANT USAGE ON SCHEMA public TO " + role);
                // A SELECT-only subset of deploy.sh's unchanged CT17 matrix, including the r15 rows.
                jdbc.execute("GRANT SELECT ON public.courses, public.enrollments, public.chapters, public.lessons, "
                        + "public.learning_materials, public.skills, public.quizzes, public.questions, "
                        + "public.quiz_questions, public.attempts, public.attempt_answers TO " + role);
                asOwner();
                snapshot(() -> {
                    jdbc.execute("SET LOCAL ROLE " + role);
                    assertEquals(role, jdbc.queryForObject("SELECT current_user", String.class));
                    assertEquals(1L, live.listLearnerAttemptsForOwner(courseId, learner, 0, 20).totalElements());
                    assertEquals(new BigDecimal("50.00"), live.readOwnedLatestSkillEvidence(courseId, learner, Set.of(skillId))
                            .bySkill().get(skillId).scorePercentage());
                    return null;
                });
                asLearner();
                snapshot(() -> {
                    jdbc.execute("SET LOCAL ROLE " + role);
                    assertEquals(new BigDecimal("50.00"), live.readMyLatestSkillEvidence(courseId, Set.of(skillId))
                            .bySkill().get(skillId).scorePercentage());
                    return null;
                });
            } finally {
                jdbc.execute("DROP OWNED BY " + role);
                jdbc.execute("DROP ROLE " + role);
            }
        }

        @Test void ownerReadsRejectActualIncompatibleTransactionsBeforeCourseAdmission() {
            // Use an absent Course to prove the transaction guard precedes even the CT03 lookup.
            UUID absent = UUID.randomUUID();
            asOwner();
            for (int isolation : List.of(TransactionDefinition.ISOLATION_READ_COMMITTED,
                    TransactionDefinition.ISOLATION_REPEATABLE_READ, TransactionDefinition.ISOLATION_SERIALIZABLE)) {
                for (boolean readOnly : List.of(false, true)) {
                    if (readOnly && isolation == TransactionDefinition.ISOLATION_REPEATABLE_READ) continue;
                    TransactionTemplate tx = new TransactionTemplate(transactionManager);
                    tx.setReadOnly(readOnly);
                    tx.setIsolationLevel(isolation);
                    tx.executeWithoutResult(status -> {
                        status.setRollbackOnly(); // Expected proxied denials mark the caller transaction for rollback.
                        assertThrows(IllegalStateException.class,
                                () -> live.readOwnedLatestSkillEvidence(absent, learner, Set.of(skillId)));
                        assertThrows(IllegalStateException.class,
                                () -> live.listLearnerAttemptsForOwner(absent, learner, 0, 20));
                        assertThrows(IllegalStateException.class,
                                () -> live.readMyLatestSkillEvidence(absent, Set.of(skillId)));
                    });
                }
            }
        }

        @Test void ownerReadsScopeBothLearnerAndCourseAndKeepEmptyPageMetadata() {
            terminal(quizId, 1, now, now.plusSeconds(1), true, false);
            asOwner();
            assertEquals(1L, snapshot(() -> live.listLearnerAttemptsForOwner(courseId, learner, 0, 20)).totalElements());
            var empty = snapshot(() -> live.listLearnerAttemptsForOwner(courseId, foreignLearner, 0, 20));
            assertTrue(empty.content().isEmpty());
            assertEquals(0L, empty.totalElements());
            assertEquals(0L, empty.totalPages());
            assertTrue(snapshot(() -> live.readOwnedLatestSkillEvidence(courseId, foreignLearner, Set.of(skillId)))
                    .bySkill().isEmpty());
            var beyond = snapshot(() -> live.listLearnerAttemptsForOwner(courseId, learner, Integer.MAX_VALUE, 100));
            assertTrue(beyond.content().isEmpty());
            assertEquals(1L, beyond.totalElements());
            assertEquals(1L, beyond.totalPages());
            UUID otherCourse = UUID.randomUUID();
            jdbc.update("INSERT INTO courses (id,instructor_id,title,description,publication_status,adaptive_enabled) VALUES (?,?,'Other AS16','Fixture','hidden',false)",
                    otherCourse, owner);
            try {
                assertEquals(0L, snapshot(() -> live.listLearnerAttemptsForOwner(otherCourse, learner, 0, 20)).totalElements());
                assertTrue(snapshot(() -> live.readOwnedLatestSkillEvidence(otherCourse, learner, Set.of(skillId)))
                        .bySkill().isEmpty());
                jdbc.update("UPDATE courses SET publication_status='hidden' WHERE id=?", courseId);
                assertEquals(1L, snapshot(() -> live.listLearnerAttemptsForOwner(courseId, learner, 0, 20)).totalElements());
                assertEquals(1, snapshot(() -> live.readOwnedLatestSkillEvidence(courseId, learner, Set.of(skillId)))
                        .bySkill().size());
            } finally {
                jdbc.update("DELETE FROM courses WHERE id=?", otherCourse);
            }
        }

        @Test void ownerEvidenceUsesPerSkillCountsHalfUpAndRejectsIncompleteAuthoritativeCapture() {
            UUID secondSkill = UUID.randomUUID(), absentSkill = UUID.randomUUID();
            UUID third = UUID.randomUUID(), fourth = UUID.randomUUID();
            for (UUID id : List.of(secondSkill, absentSkill)) {
                jdbc.update("INSERT INTO skills (id,course_id,name) VALUES (?,?,'AS16 extra')", id, courseId);
            }
            try {
                jdbc.update("INSERT INTO questions VALUES (?,?,'Third','single_choice','hard',ARRAY['A','B'],ARRAY[true,false])", third, skillId);
                jdbc.update("INSERT INTO questions VALUES (?,?,'Fourth','single_choice','easy',ARRAY['A','B'],ARRAY[true,false])", fourth, secondSkill);
                jdbc.update("INSERT INTO quiz_questions VALUES (?,?,?,4)", UUID.randomUUID(), quizId, third);
                jdbc.update("INSERT INTO quiz_questions VALUES (?,?,?,6)", UUID.randomUUID(), quizId, fourth);
                terminal(quizId, 1, now, now.plusSeconds(1), true, true);
                UUID attempt = jdbc.queryForObject("SELECT id FROM attempts WHERE quiz_id=?", UUID.class, quizId);
                jdbc.update("INSERT INTO attempt_answers VALUES (?,?,?,4,ARRAY[false,false],false)", UUID.randomUUID(), attempt, third);
                jdbc.update("INSERT INTO attempt_answers VALUES (?,?,?,6,ARRAY[false,false],false)", UUID.randomUUID(), attempt, fourth);
                jdbc.update("UPDATE attempts SET score_percentage=50.00 WHERE id=?", attempt);
                asOwner();
                var evidence = snapshot(() -> live.readOwnedLatestSkillEvidence(courseId, learner, Set.of(skillId, secondSkill, absentSkill)));
                assertEquals(Set.of(skillId, secondSkill), evidence.bySkill().keySet());
                assertEquals(2, evidence.bySkill().get(skillId).correctCount());
                assertEquals(3, evidence.bySkill().get(skillId).totalCount());
                assertEquals(new BigDecimal("66.67"), evidence.bySkill().get(skillId).scorePercentage());
                assertEquals(1, evidence.bySkill().get(secondSkill).totalCount());
                assertEquals(new BigDecimal("0.00"), evidence.bySkill().get(secondSkill).scorePercentage());
                assertThrows(UnsupportedOperationException.class, () -> evidence.bySkill().clear());
                for (String mutation : List.of(
                        "UPDATE attempt_answers SET is_correct=NULL WHERE attempt_id=? AND position=4",
                        "DELETE FROM attempt_answers WHERE attempt_id=? AND position=4",
                        "DELETE FROM quiz_questions WHERE quiz_id=(SELECT quiz_id FROM attempts WHERE id=?) AND position=4",
                        "UPDATE attempt_answers SET is_correct=true WHERE attempt_id=? AND position=4")) {
                    jdbc.update(mutation, attempt);
                    try {
                        assertThrows(IllegalStateException.class,
                                () -> snapshot(() -> live.readOwnedLatestSkillEvidence(courseId, learner, Set.of(skillId))));
                    } finally {
                        jdbc.update("DELETE FROM attempt_answers WHERE attempt_id=? AND position=4", attempt);
                        jdbc.update("INSERT INTO attempt_answers VALUES (?,?,?,4,ARRAY[false,false],false)", UUID.randomUUID(), attempt, third);
                        jdbc.update("DELETE FROM quiz_questions WHERE quiz_id=? AND position=4", quizId);
                        jdbc.update("INSERT INTO quiz_questions VALUES (?,?,?,4)", UUID.randomUUID(), quizId, third);
                    }
                }
            } finally {
                jdbc.update("DELETE FROM attempt_answers WHERE question_id=?", fourth);
                jdbc.update("DELETE FROM quiz_questions WHERE question_id=?", fourth);
                jdbc.update("DELETE FROM questions WHERE id=?", fourth);
                jdbc.update("DELETE FROM skills WHERE id IN (?,?)", secondSkill, absentSkill);
            }
        }

        @Test void ownedSkillEvidenceAndAttemptListEnforceOwnershipHiddenQuizzesOrderPagingAndSnapshotIsolation()
                throws Exception {
            UUID otherInstructor = user("instructor");
            UUID hiddenCourse = UUID.randomUUID();
            jdbc.update("INSERT INTO courses (id,instructor_id,title,description,publication_status,adaptive_enabled) VALUES (?,?,'Hidden AS16','Fixture','hidden',false)",
                    hiddenCourse, owner);
            try {
                // Missing caller transaction is rejected by Spring @Transactional(MANDATORY).
                asOwner();
                assertThrows(IllegalTransactionStateException.class,
                        () -> live.readOwnedLatestSkillEvidence(courseId, learner, Set.of(skillId)));
                assertThrows(IllegalTransactionStateException.class,
                        () -> live.listLearnerAttemptsForOwner(courseId, learner, 0, 20));

                // CT03 ownership rules: Admin 403, non-owner 403 on published / 404 on hidden, Learner 403.
                as(admin, com.codemastery.modules.auth.domain.model.Role.ADMIN);
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                        () -> snapshot(() -> live.readOwnedLatestSkillEvidence(courseId, learner, Set.of(skillId))));
                as(otherInstructor, com.codemastery.modules.auth.domain.model.Role.INSTRUCTOR);
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                        () -> snapshot(() -> live.listLearnerAttemptsForOwner(courseId, learner, 0, 20)));
                assertThrows(com.codemastery.common.exception.ResourceNotFoundException.class,
                        () -> snapshot(() -> live.readOwnedLatestSkillEvidence(hiddenCourse, learner, Set.of(skillId))));
                asLearner();
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                        () -> snapshot(() -> live.listLearnerAttemptsForOwner(courseId, learner, 0, 20)));

                UUID visibleQuiz = UUID.fromString("10000000-0000-4000-8000-00000000b900");
                addQuiz(visibleQuiz, "practice");
                UUID hiddenQuiz = UUID.fromString("80000000-0000-4000-8000-00000000b901");
                addQuiz(hiddenQuiz, "diagnostic");
                jdbc.update("UPDATE quizzes SET title='Hidden Diag', publication_status='hidden' WHERE id=?", hiddenQuiz);

                UUID lowSubmitted = UUID.fromString("00000000-0000-4000-8000-00000000c901");
                UUID highSubmitted = UUID.fromString("ffffffff-0000-4000-8000-00000000c902");
                UUID olderSubmitted = UUID.randomUUID();
                UUID unsubmitted = UUID.randomUUID();

                Instant t1 = now.plusSeconds(10);
                Instant t2 = now.plusSeconds(20);
                jdbc.update("INSERT INTO attempts VALUES (?,?,?,?,?,?,?,?)", olderSubmitted, learner, visibleQuiz, 1,
                        java.time.OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC),
                        java.time.OffsetDateTime.ofInstant(now.plusSeconds(60), java.time.ZoneOffset.UTC),
                        java.time.OffsetDateTime.ofInstant(t1, java.time.ZoneOffset.UTC), new BigDecimal("50.00"));
                jdbc.update("INSERT INTO attempt_answers VALUES (?,?,?,1,ARRAY[true,false],true)", UUID.randomUUID(), olderSubmitted, q1);
                jdbc.update("INSERT INTO attempt_answers VALUES (?,?,?,3,ARRAY[false,false],false)", UUID.randomUUID(), olderSubmitted, q2);

                jdbc.update("INSERT INTO attempts VALUES (?,?,?,?,?,?,?,?)", highSubmitted, learner, visibleQuiz, 2,
                        java.time.OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC),
                        java.time.OffsetDateTime.ofInstant(now.plusSeconds(60), java.time.ZoneOffset.UTC),
                        java.time.OffsetDateTime.ofInstant(t2, java.time.ZoneOffset.UTC), new BigDecimal("100.00"));
                jdbc.update("INSERT INTO attempt_answers VALUES (?,?,?,1,ARRAY[true,false],true)", UUID.randomUUID(), highSubmitted, q1);
                jdbc.update("INSERT INTO attempt_answers VALUES (?,?,?,3,ARRAY[true,false],true)", UUID.randomUUID(), highSubmitted, q2);

                jdbc.update("INSERT INTO attempts VALUES (?,?,?,?,?,?,?,?)", lowSubmitted, learner, hiddenQuiz, 1,
                        java.time.OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC),
                        java.time.OffsetDateTime.ofInstant(now.plusSeconds(60), java.time.ZoneOffset.UTC),
                        java.time.OffsetDateTime.ofInstant(t2, java.time.ZoneOffset.UTC), new BigDecimal("0.00"));
                jdbc.update("INSERT INTO attempt_answers VALUES (?,?,?,1,ARRAY[false,false],false)", UUID.randomUUID(), lowSubmitted, q1);
                jdbc.update("INSERT INTO attempt_answers VALUES (?,?,?,3,ARRAY[false,false],false)", UUID.randomUUID(), lowSubmitted, q2);

                // Unsubmitted attempt must be ignored by both owner reads.
                jdbc.update("INSERT INTO attempts (id,user_id,quiz_id,attempt_number,started_at,expires_at) VALUES (?,?,?,3,?,?)",
                        unsubmitted, learner, visibleQuiz,
                        java.time.OffsetDateTime.ofInstant(t2, java.time.ZoneOffset.UTC),
                        java.time.OffsetDateTime.ofInstant(t2.plusSeconds(60), java.time.ZoneOffset.UTC));

                asOwner();
                try (var worker = new AttemptWorkers(1)) {
                    TransactionTemplate rr = new TransactionTemplate(transactionManager);
                    rr.setReadOnly(true);
                    rr.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                    rr.executeWithoutResult(status -> {
                        assertEquals("repeatable read", jdbc.queryForObject("SHOW transaction_isolation", String.class));
                        assertEquals("on", jdbc.queryForObject("SHOW transaction_read_only", String.class));
                        var evidence = live.readOwnedLatestSkillEvidence(
                                courseId, learner, Set.of(skillId, UUID.randomUUID()));
                        assertEquals(Set.of(skillId), evidence.bySkill().keySet());
                        // hiddenQuiz (8000...) beats visibleQuiz by CAST(quiz_id AS text) COLLATE "C".
                        assertEquals(lowSubmitted, evidence.bySkill().get(skillId).attemptId());
                        assertEquals(new BigDecimal("0.00"), evidence.bySkill().get(skillId).scorePercentage());

                        var page0 = live.listLearnerAttemptsForOwner(courseId, learner, 0, 2);
                        assertEquals(3L, page0.totalElements());
                        assertEquals(2L, page0.totalPages());
                        assertEquals(List.of(
                                new com.codemastery.modules.assessment.application.service.AttemptService.LearnerAttemptSummary(
                                        lowSubmitted, hiddenQuiz, "Hidden Diag", "diagnostic", 1, t2, new BigDecimal("0.00")),
                                new com.codemastery.modules.assessment.application.service.AttemptService.LearnerAttemptSummary(
                                        highSubmitted, visibleQuiz, "Timed quiz", "practice", 2, t2, new BigDecimal("100.00"))),
                                page0.content());

                        var page1 = live.listLearnerAttemptsForOwner(courseId, learner, 1, 2);
                        assertEquals(List.of(
                                new com.codemastery.modules.assessment.application.service.AttemptService.LearnerAttemptSummary(
                                        olderSubmitted, visibleQuiz, "Timed quiz", "practice", 1, t1, new BigDecimal("50.00"))),
                                page1.content());

                        // Concurrent committed attempt remains invisible inside the active REPEATABLE READ snapshot.
                        try {
                            awaitWorker(worker.submit(() -> {
                                terminal(visibleQuiz, 4, t1, t2.plusSeconds(30), true, true);
                                return null;
                            }), 10, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                        assertEquals(3L, live.listLearnerAttemptsForOwner(courseId, learner, 0, 20).totalElements());
                        assertEquals(page0, live.listLearnerAttemptsForOwner(courseId, learner, 0, 2));
                        assertEquals(evidence, live.readOwnedLatestSkillEvidence(courseId, learner, evidence.bySkill().keySet()));
                    });
                }
                assertEquals(4L, snapshot(() -> live.listLearnerAttemptsForOwner(courseId, learner, 0, 20)).totalElements());
                assertEquals(4, snapshot(() -> live.readOwnedLatestSkillEvidence(courseId, learner, Set.of(skillId)))
                        .bySkill().get(skillId).attemptNumber());
            } finally {
                jdbc.update("DELETE FROM courses WHERE id=?", hiddenCourse);
                jdbc.update("DELETE FROM users WHERE id=?", otherInstructor);
            }
        }

        @Autowired
        private org.springframework.context.ConfigurableApplicationContext eventBusContext;

        /** A scoped producer probe, never a production subscriber or AD13/CT28 acceptance double. */
        final class QuizEventProbe implements
                org.springframework.context.ApplicationListener<org.springframework.context.PayloadApplicationEvent<?>>,
                AutoCloseable {
            final List<com.codemastery.modules.assessment.application.event.QuizSubmittedEvent> events =
                    new java.util.concurrent.CopyOnWriteArrayList<>();
            final List<Integer> completions = new java.util.concurrent.CopyOnWriteArrayList<>();
            volatile java.util.function.Predicate<com.codemastery.modules.assessment.application.event.QuizSubmittedEvent>
                    failWhen = event -> false;
            volatile java.util.function.Consumer<com.codemastery.modules.assessment.application.event.QuizSubmittedEvent>
                    onEvent = event -> {};

            QuizEventProbe() { eventBusContext.addApplicationListener(this); }

            @Override public void onApplicationEvent(org.springframework.context.PayloadApplicationEvent<?> payload) {
                if (!(payload.getPayload() instanceof com.codemastery.modules.assessment.application.event.QuizSubmittedEvent event)
                        || !courseId.equals(event.courseId())) return;
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED,
                        TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
                assertEquals(learner, event.learnerId());
                assertEquals(List.of(q1, q2), event.answers().stream()
                        .map(com.codemastery.modules.assessment.application.event.QuizSubmittedEvent.AnswerEvidence::questionId).toList());
                assertEquals(List.of(1, 3), event.answers().stream()
                        .map(com.codemastery.modules.assessment.application.event.QuizSubmittedEvent.AnswerEvidence::position).toList());
                assertTrue(event.answers().stream().allMatch(answer -> skillId.equals(answer.skillId())));
                // Uses the real execution-only seam while the marker and held Course root are active.
                var latest = live.readLatestSkillEvidence(courseId, learner, Set.of(skillId)).bySkill().get(skillId);
                org.junit.jupiter.api.Assertions.assertNotNull(latest);
                var stored = attemptRepository.findById(event.attemptId()).orElseThrow();
                assertEquals(stored.submitted_at(), event.submittedAt());
                assertEquals(stored.started_at(), event.startedAt());
                assertEquals(stored.expires_at(), event.expiresAt());
                assertEquals(stored.attempt_number(), event.attemptNumber());
                var answers = attemptRepository.findAnswersInPositionOrder(event.attemptId());
                for (int i = 0; i < answers.size(); i++) {
                    assertEquals(answers.get(i).is_correct(), event.answers().get(i).correct());
                }
                // An independent physical connection cannot see the terminal write yet, and NOWAIT
                // proves the Course root is still held at publication, not merely earlier in the call.
                try (var connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        try (var statement = connection.prepareStatement("SELECT submitted_at FROM attempts WHERE id=?")) {
                            statement.setObject(1, event.attemptId());
                            try (var row = statement.executeQuery()) {
                                assertTrue(row.next()); assertNull(row.getTimestamp(1));
                            }
                        }
                        try (var statement = connection.prepareStatement("SELECT id FROM courses WHERE id=? FOR UPDATE NOWAIT")) {
                            statement.setObject(1, courseId);
                            SQLException locked = assertThrows(SQLException.class, () -> statement.executeQuery().close());
                            assertEquals("55P03", locked.getSQLState());
                        }
                    } finally { connection.rollback(); }
                } catch (SQLException failure) { throw new IllegalStateException(failure); }
                events.add(event);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) { completions.add(status); }
                });
                onEvent.accept(event);
                if (failWhen.test(event)) throw new IllegalStateException("test-scoped synchronous listener failure");
            }

            @Override public void close() {
                eventBusContext.removeApplicationListener(this);
                assertFalse(((org.springframework.context.support.AbstractApplicationContext) eventBusContext)
                        .getApplicationListeners().contains(this));
            }
        }

        void assertCommittedMasks(UUID attemptId, boolean firstCorrect, boolean submitted) {
            // Each repository call runs outside the completed producer transaction.
            Attempt stored = attemptRepository.findById(attemptId).orElseThrow();
            assertEquals(submitted, stored.submitted_at() != null);
            assertEquals(submitted ? new BigDecimal(firstCorrect ? "50.00" : "0.00") : null, stored.score_percentage());
            var answers = attemptRepository.findAnswersInPositionOrder(attemptId);
            assertArrayEquals(new Boolean[] {firstCorrect, false}, answers.getFirst().selected_options());
            assertArrayEquals(new Boolean[] {false, false}, answers.getLast().selected_options());
            assertEquals(submitted ? firstCorrect : null, answers.getFirst().is_correct());
            assertEquals(submitted ? false : null, answers.getLast().is_correct());
        }

        @Test
        void manualSubmitAndExpiryPublishValidatedQuizSubmittedEventOnceAfterFlushingAndRollbackOnListenerFailure() {
            try (var probe = new QuizEventProbe()) {
                asLearner();
                var first = at(now).start(courseId, quizId).attempt();
                at(now).saveAnswers(courseId, quizId, first.attemptId(), answers(false));
                snapshot(() -> at(now).read(courseId, quizId, first.attemptId()));
                snapshot(() -> at(now).history(courseId, quizId, 0, 20));
                assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                        () -> snapshot(() -> at(now).readResult(courseId, quizId, first.attemptId())));
                assertTrue(probe.events.isEmpty());
                Thread producerThread = Thread.currentThread();
                probe.onEvent = event -> assertEquals(producerThread, Thread.currentThread());
                var submitted = at(now.plusSeconds(1)).submit(courseId, quizId, first.attemptId(), answers(true));
                assertEquals(1, probe.events.size());
                assertEquals(first.attemptId(), probe.events.getFirst().attemptId());
                assertEquals(submitted.submittedAt(), probe.events.getFirst().submittedAt());
                assertEquals("practice", probe.events.getFirst().quizKind());
                assertEquals(List.of(true, false), probe.events.getFirst().answers().stream()
                        .map(com.codemastery.modules.assessment.application.event.QuizSubmittedEvent.AnswerEvidence::correct).toList());
                assertCommittedMasks(first.attemptId(), true, true);
                at(now.plusSeconds(2)).submit(courseId, quizId, first.attemptId(), answers(false));
                assertEquals(1, probe.events.size());

                var second = at(now.plusSeconds(3)).start(courseId, quizId).attempt();
                at(now.plusSeconds(4)).saveAnswers(courseId, quizId, second.attemptId(), answers(true));
                at(second.expiresAt()).finalizeExpired(second.attemptId());
                assertEquals(2, probe.events.size());
                assertEquals(second.attemptId(), probe.events.get(1).attemptId());
                assertEquals(second.expiresAt(), probe.events.get(1).submittedAt());
                assertCommittedMasks(second.attemptId(), true, true);
                at(second.expiresAt().plusSeconds(1)).finalizeExpired(second.attemptId());
                assertEquals(2, probe.events.size());

                var third = at(now.plusSeconds(70)).start(courseId, quizId).attempt();
                at(now.plusSeconds(71)).saveAnswers(courseId, quizId, third.attemptId(), answers(false));
                probe.failWhen = event -> true;
                assertThrows(IllegalStateException.class,
                        () -> at(now.plusSeconds(72)).submit(courseId, quizId, third.attemptId(), answers(true)));
                assertCommittedMasks(third.attemptId(), false, false);
                assertThrows(IllegalStateException.class, () -> live.readLatestSkillEvidence(courseId, learner, Set.of(skillId)));
                probe.failWhen = event -> false;
                at(now.plusSeconds(73)).submit(courseId, quizId, third.attemptId(), answers(true));
                assertCommittedMasks(third.attemptId(), true, true);
                assertEquals(List.of(TransactionSynchronization.STATUS_COMMITTED, TransactionSynchronization.STATUS_COMMITTED,
                        TransactionSynchronization.STATUS_ROLLED_BACK, TransactionSynchronization.STATUS_COMMITTED), probe.completions);
                assertEquals(4, probe.events.size(), "One failed invocation is not a second committed transition");
            }
        }

        @Test
        void springManagedProducerUsesTheRequiredContextPublisher() {
            try (var probe = new QuizEventProbe()) {
                asLearner();
                var attempt = live.start(courseId, quizId).attempt();
                var result = live.submit(courseId, quizId, attempt.attemptId(), answers(true));
                assertEquals(1, probe.events.size());
                assertEquals(result.submittedAt(), probe.events.getFirst().submittedAt());
                assertEquals(List.of(TransactionSynchronization.STATUS_COMMITTED), probe.completions);
                assertCommittedMasks(attempt.attemptId(), true, true);
            }
        }

        @Test
        void schedulerRetriesRolledBackPublicationAndGradesLatestCommittedOtherTabMasks() {
            try (var probe = new QuizEventProbe()) {
                asLearner();
                var attempt = at(now).start(courseId, quizId).attempt();
                at(now.plusSeconds(1)).saveAnswers(courseId, quizId, attempt.attemptId(), answers(false));
                at(now.plusSeconds(2)).saveAnswers(courseId, quizId, attempt.attemptId(), answers(true)); // another tab's N
                probe.failWhen = event -> attempt.attemptId().equals(event.attemptId());
                assertThrows(IllegalStateException.class,
                        () -> at(now.plusSeconds(3)).submit(courseId, quizId, attempt.attemptId(), answers(false))); // A's M
                assertCommittedMasks(attempt.attemptId(), true, false);
                UUID otherQuiz = UUID.randomUUID(); addQuiz(otherQuiz, "test");
                var other = at(now).start(courseId, otherQuiz).attempt();
                var scheduler = new com.codemastery.modules.assessment.application.scheduler.AttemptAutoSubmitScheduler(
                        attemptRepository, at(now.plusSeconds(100)), 1000, 1,
                        java.time.Clock.fixed(now.plusSeconds(100), java.time.ZoneOffset.UTC));
                assertEquals(1, scheduler.runPass());
                assertCommittedMasks(attempt.attemptId(), true, false);
                assertCommittedMasks(other.attemptId(), false, true);
                assertEquals(0, scheduler.runPass());
                assertCommittedMasks(attempt.attemptId(), true, false);
                probe.failWhen = event -> false;
                assertEquals(1, scheduler.runPass());
                assertCommittedMasks(attempt.attemptId(), true, true);
                assertEquals(attempt.expiresAt(), probe.events.getLast().submittedAt());
                assertEquals(0, scheduler.runPass());
                assertEquals(5, probe.events.size());
                assertEquals(3, Collections.frequency(probe.completions, TransactionSynchronization.STATUS_ROLLED_BACK));
                assertEquals(2, Collections.frequency(probe.completions, TransactionSynchronization.STATUS_COMMITTED));
            }
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"answers", "terminal", "readback"})
        void persistenceOrIncompleteRefreshedGradingFailsClosedWithoutPublication(String fault) {
            try (var probe = new QuizEventProbe()) {
                asLearner();
                var attempt = at(now).start(courseId, quizId).attempt();
                at(now.plusSeconds(1)).saveAnswers(courseId, quizId, attempt.attemptId(), answers(true));
                com.codemastery.modules.assessment.infrastructure.persistence.repository.AttemptRepositoryImpl target =
                        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(attemptRepository);
                var spy = org.mockito.Mockito.spy(target);
                if (fault.equals("terminal")) {
                    org.mockito.Mockito.doAnswer(call -> {
                        call.callRealMethod(); throw new IllegalStateException("test failure after terminal flush");
                    }).when(spy).save(org.mockito.ArgumentMatchers.any());
                } else {
                    org.mockito.Mockito.doAnswer(call -> {
                        call.callRealMethod();
                        if (fault.equals("answers")) throw new IllegalStateException("test failure after answer flush");
                        // Simulate an inconsistent persisted row with a stale managed entity present.
                        jdbc.update("UPDATE attempt_answers SET is_correct=NULL WHERE attempt_id=? AND question_id=?", attempt.attemptId(), q1);
                        return null;
                    }).when(spy).updateAnswers(org.mockito.ArgumentMatchers.anyList());
                }
                var service = new com.codemastery.modules.assessment.application.service.AttemptService(spy, quizRepository,
                        grading, access, identities, guard, skillGraph,
                        java.time.Clock.fixed(now.plusSeconds(2), java.time.ZoneOffset.UTC), eventBusContext);
                assertThrows(IllegalStateException.class, () -> service.submit(courseId, quizId, attempt.attemptId(), answers(false)));
                assertTrue(probe.events.isEmpty());
                assertCommittedMasks(attempt.attemptId(), true, false);
                at(now.plusSeconds(3)).submit(courseId, quizId, attempt.attemptId(), answers(true));
                assertCommittedMasks(attempt.attemptId(), true, true);
                assertEquals(1, probe.events.size());
            }
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"duplicate", "manualFirst", "expiryFirst"})
        void concurrentTerminalWritersPublishOneCommittedTransition(String order) throws Exception {
            asLearner();
            var attempt = at(now).start(courseId, quizId).attempt();
            at(now.plusSeconds(1)).saveAnswers(courseId, quizId, attempt.attemptId(), answers(true));
            var entered = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            try (var probe = new QuizEventProbe(); var workers = new AttemptWorkers(2)) {
                probe.onEvent = event -> {
                    entered.countDown();
                    try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                };
                java.util.concurrent.Callable<Object> manual = () -> {
                    asLearner(); return at(now.plusSeconds(2)).submit(courseId, quizId, attempt.attemptId(), answers(false));
                };
                java.util.concurrent.Callable<Object> expiry = () -> {
                    at(attempt.expiresAt()).finalizeExpired(attempt.attemptId()); return null;
                };
                var first = workers.submit(order.equals("expiryFirst") ? expiry : manual);
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    var second = workers.submit(order.equals("manualFirst") ? expiry : manual);
                    workers.awaitRootWait();
                    release.countDown();
                    awaitWorker(first, 10, TimeUnit.SECONDS);
                    awaitWorker(second, 10, TimeUnit.SECONDS);
                } finally { release.countDown(); }
                assertEquals(1, probe.events.size());
                assertEquals(List.of(TransactionSynchronization.STATUS_COMMITTED), probe.completions);
                assertCommittedMasks(attempt.attemptId(), order.equals("expiryFirst"), true);
                assertEquals(order.equals("expiryFirst") ? attempt.expiresAt() : now.plusSeconds(2), probe.events.getFirst().submittedAt());
                at(now.plusSeconds(100)).submit(courseId, quizId, attempt.attemptId(), answers(false));
                at(now.plusSeconds(100)).finalizeExpired(attempt.attemptId());
                assertEquals(1, probe.events.size());
            }
        }

        @Test
        void ct13QuizOutcomeAggregatesMultiLearnerLatestPairsHalfUpAndIgnoresUnsubmittedAndHiddenCourseFilter() {
            UUID hiddenTestQuiz = UUID.randomUUID();
            UUID diagnosticQuiz = UUID.randomUUID();
            addQuiz(hiddenTestQuiz, "test");
            addQuiz(diagnosticQuiz, "diagnostic");

            // Learner 1: quizId latest is 50.00 (supersedes older 100.00); hiddenTestQuiz is 100.00; diagnostic 0.00 excluded.
            terminal(quizId, 1, now, now.plusSeconds(10), true, true);
            terminal(quizId, 2, now.plusSeconds(20), now.plusSeconds(30), true, false);
            terminal(hiddenTestQuiz, 1, now, now.plusSeconds(10), true, true);
            terminal(diagnosticQuiz, 1, now.plusSeconds(40), now.plusSeconds(50), false, false);

            // Second joined learner: 50.00 on quizId, plus an unsubmitted attempt on hiddenTestQuiz (must not count).
            jdbc.update("INSERT INTO attempts VALUES (?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), foreignLearner, quizId, 1,
                    java.time.OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC),
                    java.time.OffsetDateTime.ofInstant(now.plusSeconds(60), java.time.ZoneOffset.UTC),
                    java.time.OffsetDateTime.ofInstant(now.plusSeconds(15), java.time.ZoneOffset.UTC),
                    new BigDecimal("50.00"));
            jdbc.update("INSERT INTO attempts VALUES (?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), foreignLearner, hiddenTestQuiz, 1,
                    java.time.OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC),
                    java.time.OffsetDateTime.ofInstant(now.plusSeconds(60), java.time.ZoneOffset.UTC),
                    null, null);

            jdbc.update("UPDATE quizzes SET publication_status='hidden' WHERE id=?", hiddenTestQuiz);
            jdbc.update("UPDATE courses SET publication_status='hidden' WHERE id=?", courseId);

            // (50.00 + 100.00 + 50.00) / 3 = 66.666... -> HALF_UP 66.67 over hidden Course and hidden test Quiz.
            var combined = snapshot(() -> live.readQuizOutcome(courseId, Set.of(learner, foreignLearner)));
            assertEquals(courseId, combined.courseId());
            assertEquals(3L, combined.scoredLearnerQuizPairs());
            assertEquals(new BigDecimal("66.67"), combined.averageQuizScore());

            var singleLearner = snapshot(() -> live.readQuizOutcome(courseId, Set.of(foreignLearner)));
            assertEquals(1L, singleLearner.scoredLearnerQuizPairs());
            assertEquals(new BigDecimal("50.00"), singleLearner.averageQuizScore());

            var emptyPopulation = snapshot(() -> live.readQuizOutcome(courseId, Set.of()));
            assertEquals(0L, emptyPopulation.scoredLearnerQuizPairs());
            assertNull(emptyPopulation.averageQuizScore());
        }
    }

}
