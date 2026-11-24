package com.codemastery.modules.assessment;

import com.codemastery.modules.assessment.application.port.PracticeDifficultyProvider;
import com.codemastery.modules.assessment.application.service.QuestionSelectionService;
import com.codemastery.modules.assessment.application.service.QuestionSelectionService.CandidateQuestion;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionSelectionServiceTest {

    /**
     * Conforming CT07.difficulty port double without referencing Adaptive internals.
     * Mapping per ADR011 §8: UNKNOWN/WEAK -> EASY, LEARNING -> MEDIUM, MASTERED -> HARD.
     */
    private static PracticeDifficultyProvider conformingPortDouble(
            UUID expectedCourseId,
            Map<UUID, String> masteryStateBySkill
    ) {
        return (courseId, skillId) -> {
            if (!expectedCourseId.equals(courseId)) {
                throw new IllegalArgumentException("Unexpected courseId: " + courseId);
            }
            String state = masteryStateBySkill.getOrDefault(skillId, "UNKNOWN");
            return switch (state) {
                case "UNKNOWN", "WEAK" -> DifficultyLevel.EASY;
                case "LEARNING" -> DifficultyLevel.MEDIUM;
                case "MASTERED" -> DifficultyLevel.HARD;
                default -> throw new IllegalArgumentException("Unsupported mastery state: " + state);
            };
        };
    }

    @Test
    void selectsExactTargetDifficultyForEachMasteryStateViaConformingPort() {
        UUID courseId = UUID.randomUUID();
        UUID unknownSkill = UUID.randomUUID();
        UUID weakSkill = UUID.randomUUID();
        UUID learningSkill = UUID.randomUUID();
        UUID masteredSkill = UUID.randomUUID();

        PracticeDifficultyProvider port = conformingPortDouble(courseId, Map.of(
                weakSkill, "WEAK",
                learningSkill, "LEARNING",
                masteredSkill, "MASTERED"
        ));

        Map<UUID, DifficultyLevel> targets = Map.of(
                unknownSkill, port.targetDifficulty(courseId, unknownSkill),
                weakSkill, port.targetDifficulty(courseId, weakSkill),
                learningSkill, port.targetDifficulty(courseId, learningSkill),
                masteredSkill, port.targetDifficulty(courseId, masteredSkill)
        );

        assertEquals(DifficultyLevel.EASY, targets.get(unknownSkill));
        assertEquals(DifficultyLevel.EASY, targets.get(weakSkill));
        assertEquals(DifficultyLevel.MEDIUM, targets.get(learningSkill));
        assertEquals(DifficultyLevel.HARD, targets.get(masteredSkill));

        CandidateQuestion q1 = new CandidateQuestion(UUID.randomUUID(), unknownSkill, DifficultyLevel.EASY, 1);
        CandidateQuestion q2 = new CandidateQuestion(UUID.randomUUID(), unknownSkill, DifficultyLevel.HARD, 2);
        CandidateQuestion q3 = new CandidateQuestion(UUID.randomUUID(), weakSkill, DifficultyLevel.EASY, 3);
        CandidateQuestion q4 = new CandidateQuestion(UUID.randomUUID(), learningSkill, DifficultyLevel.MEDIUM, 4);
        CandidateQuestion q5 = new CandidateQuestion(UUID.randomUUID(), masteredSkill, DifficultyLevel.EASY, 5);
        CandidateQuestion q6 = new CandidateQuestion(UUID.randomUUID(), masteredSkill, DifficultyLevel.HARD, 6);

        List<CandidateQuestion> selected = QuestionSelectionService.selectPracticeQuestions(
                List.of(q6, q1, q4, q2, q5, q3),
                targets
        );

        assertEquals(List.of(q1, q3, q4, q6), selected);
    }

    @Test
    void easyTargetFallsBackToMediumThenHard() {
        UUID courseId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        PracticeDifficultyProvider port = conformingPortDouble(courseId, Map.of(skillId, "WEAK"));
        Map<UUID, DifficultyLevel> targets = Map.of(skillId, port.targetDifficulty(courseId, skillId));

        CandidateQuestion medium1 = new CandidateQuestion(UUID.randomUUID(), skillId, DifficultyLevel.MEDIUM, 2);
        CandidateQuestion hard1 = new CandidateQuestion(UUID.randomUUID(), skillId, DifficultyLevel.HARD, 1);
        CandidateQuestion medium2 = new CandidateQuestion(UUID.randomUUID(), skillId, DifficultyLevel.MEDIUM, 3);

        assertEquals(
                List.of(medium1, medium2),
                QuestionSelectionService.selectPracticeQuestions(List.of(medium2, hard1, medium1), targets)
        );

        assertEquals(
                List.of(hard1),
                QuestionSelectionService.selectPracticeQuestions(List.of(hard1), targets)
        );
    }

    @Test
    void mediumTargetFallsBackToEasyBeforeHard() {
        UUID courseId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        PracticeDifficultyProvider port = conformingPortDouble(courseId, Map.of(skillId, "LEARNING"));
        Map<UUID, DifficultyLevel> targets = Map.of(skillId, port.targetDifficulty(courseId, skillId));

        CandidateQuestion easy1 = new CandidateQuestion(UUID.randomUUID(), skillId, DifficultyLevel.EASY, 2);
        CandidateQuestion hard1 = new CandidateQuestion(UUID.randomUUID(), skillId, DifficultyLevel.HARD, 1);

        assertEquals(
                List.of(easy1),
                QuestionSelectionService.selectPracticeQuestions(List.of(hard1, easy1), targets)
        );

        assertEquals(
                List.of(hard1),
                QuestionSelectionService.selectPracticeQuestions(List.of(hard1), targets)
        );
    }

    @Test
    void hardTargetFallsBackToMediumThenEasy() {
        UUID courseId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        PracticeDifficultyProvider port = conformingPortDouble(courseId, Map.of(skillId, "MASTERED"));
        Map<UUID, DifficultyLevel> targets = Map.of(skillId, port.targetDifficulty(courseId, skillId));

        CandidateQuestion easy1 = new CandidateQuestion(UUID.randomUUID(), skillId, DifficultyLevel.EASY, 1);
        CandidateQuestion medium1 = new CandidateQuestion(UUID.randomUUID(), skillId, DifficultyLevel.MEDIUM, 2);

        assertEquals(
                List.of(medium1),
                QuestionSelectionService.selectPracticeQuestions(List.of(easy1, medium1), targets)
        );

        assertEquals(
                List.of(easy1),
                QuestionSelectionService.selectPracticeQuestions(List.of(easy1), targets)
        );
    }

    @Test
    void preservesMembershipOrderWithoutCountCapAcrossMultipleSkills() {
        UUID skillA = UUID.randomUUID();
        UUID skillB = UUID.randomUUID();

        CandidateQuestion a1 = new CandidateQuestion(UUID.randomUUID(), skillA, DifficultyLevel.EASY, 1);
        CandidateQuestion b1 = new CandidateQuestion(UUID.randomUUID(), skillB, DifficultyLevel.HARD, 2);
        CandidateQuestion a2 = new CandidateQuestion(UUID.randomUUID(), skillA, DifficultyLevel.EASY, 3);
        CandidateQuestion b2 = new CandidateQuestion(UUID.randomUUID(), skillB, DifficultyLevel.HARD, 4);
        CandidateQuestion a3 = new CandidateQuestion(UUID.randomUUID(), skillA, DifficultyLevel.EASY, 5);

        List<CandidateQuestion> selected = QuestionSelectionService.selectPracticeQuestions(
                List.of(a3, b1, a1, b2, a2),
                Map.of(skillA, DifficultyLevel.EASY, skillB, DifficultyLevel.HARD)
        );

        assertEquals(List.of(a1, b1, a2, b2, a3), selected);
    }

    @Test
    void rejectsMissingTargetDifficultyWithoutInventingFallback() {
        UUID skillId = UUID.randomUUID();
        CandidateQuestion q1 = new CandidateQuestion(UUID.randomUUID(), skillId, DifficultyLevel.EASY, 1);

        assertTrue(QuestionSelectionService.selectPracticeQuestions(List.of(), Map.of()).isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> QuestionSelectionService.selectPracticeQuestions(List.of(q1), new HashMap<>())
        );
    }
}