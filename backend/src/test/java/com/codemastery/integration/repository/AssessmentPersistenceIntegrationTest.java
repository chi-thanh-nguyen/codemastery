package com.codemastery.integration.repository;

import com.codemastery.integration.AbstractIntegrationTest;
import com.codemastery.modules.assessment.domain.model.Attempt;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.model.Quiz;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class AssessmentPersistenceIntegrationTest extends AbstractIntegrationTest {

    private static final String JWT_SECRET = ephemeralJwtSecret();
    private static final Instant START = Instant.parse("2026-11-11T03:00:00.123456Z");

    @DynamicPropertySource
    static void registerTestProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
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
}
