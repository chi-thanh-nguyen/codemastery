package com.codemastery.integration.repository;

import com.codemastery.integration.AbstractIntegrationTest;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
