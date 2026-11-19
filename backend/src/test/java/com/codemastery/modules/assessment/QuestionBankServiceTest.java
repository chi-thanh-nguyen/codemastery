package com.codemastery.modules.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.assessment.api.dto.QuestionRequest;
import com.codemastery.modules.assessment.api.dto.QuestionResponse;
import com.codemastery.modules.assessment.application.service.QuestionBankService;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.repository.AttemptRepository;
import com.codemastery.modules.assessment.domain.repository.QuestionRepository;
import com.codemastery.modules.course.application.service.CourseAccessService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class QuestionBankServiceTest {

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private CourseAccessService courseAccessService;

    @Mock
    private ObjectProvider<QuestionBankService.SkillLookup> skillLookupProvider;

    @Mock
    private QuestionBankService.SkillLookup skillLookup;

    private QuestionBankService service;

    private UUID courseId;
    private UUID otherCourseId;
    private UUID skillId;
    private UUID questionId;

    @BeforeEach
    void setUp() {
        when(skillLookupProvider.getIfAvailable()).thenReturn(skillLookup);
        service = new QuestionBankService(
                questionRepository, attemptRepository, courseAccessService, skillLookupProvider);
        courseId = UUID.randomUUID();
        otherCourseId = UUID.randomUUID();
        skillId = UUID.randomUUID();
        questionId = UUID.randomUUID();
    }

    // --- 1. Create Question & Tagging / V004 Validation ---

    @Test
    void createQuestion_validSingleChoice_savesAndReturnsResponse() {
        QuestionRequest request = new QuestionRequest(
                skillId,
                "What is the time complexity of binary search?",
                "single_choice",
                "easy",
                List.of("O(log n)", "O(n)"),
                List.of(true, false)
        );
        when(skillLookup.findCourseIdBySkillId(skillId)).thenReturn(Optional.of(courseId));
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> inv.getArgument(0));

        QuestionResponse response = service.createQuestion(courseId, request);

        assertNotNull(response.id());
        assertEquals(skillId, response.skillId());
        assertEquals("single_choice", response.kind());
        assertEquals("easy", response.difficulty());
        assertEquals(List.of("O(log n)", "O(n)"), response.options());
        assertEquals(List.of(true, false), response.correctOptions());
        verify(courseAccessService).requireCourseOwnership(courseId);
        verify(questionRepository).save(any(Question.class));
    }

    @Test
    void createQuestion_validMultipleChoice_savesAndReturnsResponse() {
        QuestionRequest request = new QuestionRequest(
                skillId,
                "Select all primitive types in Java",
                "multiple_choice",
                "medium",
                List.of("int", "String", "boolean"),
                List.of(true, false, true)
        );
        when(skillLookup.findCourseIdBySkillId(skillId)).thenReturn(Optional.of(courseId));
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> inv.getArgument(0));

        QuestionResponse response = service.createQuestion(courseId, request);

        assertEquals("multiple_choice", response.kind());
        assertEquals(List.of(true, false, true), response.correctOptions());
    }

    @Test
    void createQuestion_validTrueFalse_savesAndReturnsResponse() {
        QuestionRequest request = new QuestionRequest(
                skillId,
                "Java interfaces can have default methods.",
                "true_false",
                "hard",
                List.of("True", "False"),
                List.of(true, false)
        );
        when(skillLookup.findCourseIdBySkillId(skillId)).thenReturn(Optional.of(courseId));
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> inv.getArgument(0));

        QuestionResponse response = service.createQuestion(courseId, request);

        assertEquals("true_false", response.kind());
        assertEquals(List.of("True", "False"), response.options());
    }

    @Test
    void createQuestion_skillBelongsToAnotherCourse_throwsResourceNotFound() {
        QuestionRequest request = validSingleChoiceRequest();
        when(skillLookup.findCourseIdBySkillId(skillId)).thenReturn(Optional.of(otherCourseId));

        assertThrows(ResourceNotFoundException.class, () -> service.createQuestion(courseId, request));
        verify(questionRepository, never()).save(any());
    }

    @Test
    void createQuestion_skillNotFound_throwsResourceNotFound() {
        QuestionRequest request = validSingleChoiceRequest();
        when(skillLookup.findCourseIdBySkillId(skillId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createQuestion(courseId, request));
        verify(questionRepository, never()).save(any());
    }

    @Test
    void createQuestion_invalidDifficulty_throwsBadRequest() {
        QuestionRequest request = new QuestionRequest(
                skillId, "Prompt", "single_choice", "extreme",
                List.of("A", "B"), List.of(true, false)
        );

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class, () -> service.createQuestion(courseId, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void createQuestion_invalidKind_throwsBadRequest() {
        QuestionRequest request = new QuestionRequest(
                skillId, "Prompt", "essay", "easy",
                List.of("A", "B"), List.of(true, false)
        );

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class, () -> service.createQuestion(courseId, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void createQuestion_trueFalseWrongOptionLabels_throwsBadRequest() {
        QuestionRequest request = new QuestionRequest(
                skillId, "Prompt", "true_false", "easy",
                List.of("true", "false"), List.of(true, false)
        );

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class, () -> service.createQuestion(courseId, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void createQuestion_singleChoiceWithMultipleTrueAnswers_throwsBadRequest() {
        QuestionRequest request = new QuestionRequest(
                skillId, "Prompt", "single_choice", "easy",
                List.of("A", "B"), List.of(true, true)
        );

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class, () -> service.createQuestion(courseId, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void createQuestion_multipleChoiceWithNoTrueAnswer_throwsBadRequest() {
        QuestionRequest request = new QuestionRequest(
                skillId, "Prompt", "multiple_choice", "medium",
                List.of("A", "B"), List.of(false, false)
        );

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class, () -> service.createQuestion(courseId, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void createQuestion_mismatchedOptionAndMaskLengths_throwsBadRequest() {
        QuestionRequest request = new QuestionRequest(
                skillId, "Prompt", "single_choice", "easy",
                List.of("A", "B", "C"), List.of(true, false)
        );

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class, () -> service.createQuestion(courseId, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // --- 2. Ownership Refusal & Answer-Key Concealment ---

    @Test
    void createQuestion_nonOwnerOrLearner_throwsAccessDenied() {
        doThrow(new AccessDeniedException("Access denied"))
                .when(courseAccessService).requireCourseOwnership(courseId);

        assertThrows(AccessDeniedException.class,
                () -> service.createQuestion(courseId, validSingleChoiceRequest()));
        verify(questionRepository, never()).save(any());
    }

    @Test
    void getQuestion_learnerOrNonOwner_throwsAccessDeniedAndConcealsAnswerKey() {
        doThrow(new AccessDeniedException("Access denied"))
                .when(courseAccessService).requireCourseOwnership(courseId);

        assertThrows(AccessDeniedException.class, () -> service.getQuestion(courseId, questionId));
        verify(questionRepository, never()).findById(any());
    }

    @Test
    void getQuestion_owningInstructor_returnsQuestionWithAnswerKey() {
        Question existing = sampleQuestion(questionId, skillId);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(existing));
        when(skillLookup.findCourseIdBySkillId(skillId)).thenReturn(Optional.of(courseId));

        QuestionResponse response = service.getQuestion(courseId, questionId);

        assertEquals(questionId, response.id());
        assertEquals(List.of(true, false), response.correctOptions());
    }

    // --- 3. Listing & Pagination ---

    @Test
    void listQuestions_withSkillAndDifficulty_returnsPagedResults() {
        Question q1 = sampleQuestion(UUID.randomUUID(), skillId);
        Question q2 = sampleQuestion(UUID.randomUUID(), skillId);
        Question q3 = sampleQuestion(UUID.randomUUID(), skillId);
        when(skillLookup.findCourseIdBySkillId(skillId)).thenReturn(Optional.of(courseId));
        when(questionRepository.findBySkillIdAndDifficulty(skillId, "easy"))
                .thenReturn(List.of(q1, q2, q3));

        PageResponse<QuestionResponse> page = service.listQuestions(courseId, skillId, "easy", 0, 2);

        assertEquals(2, page.content().size());
        assertEquals(0, page.page());
        assertEquals(2, page.size());
        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
    }

    // --- 4. Replace & Delete Rules (Unused vs Referenced in Quiz/Attempt) ---

    @Test
    void replaceQuestion_unusedQuestion_updatesAndReturnsResponse() {
        Question existing = sampleQuestion(questionId, skillId);
        QuestionRequest updateRequest = new QuestionRequest(
                skillId,
                "Updated prompt",
                "true_false",
                "hard",
                List.of("True", "False"),
                List.of(false, true)
        );
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(existing));
        when(skillLookup.findCourseIdBySkillId(skillId)).thenReturn(Optional.of(courseId));
        when(questionRepository.isUsedInQuiz(questionId)).thenReturn(false);
        when(attemptRepository.hasAnswersForQuestion(questionId)).thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> inv.getArgument(0));

        QuestionResponse response = service.replaceQuestion(courseId, questionId, updateRequest);

        assertEquals(questionId, response.id());
        assertEquals("Updated prompt", response.prompt());
        assertEquals("hard", response.difficulty());
        assertEquals(List.of(false, true), response.correctOptions());
    }

    @Test
    void replaceQuestion_usedInAttempt_throwsConflict() {
        Question existing = sampleQuestion(questionId, skillId);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(existing));
        when(skillLookup.findCourseIdBySkillId(skillId)).thenReturn(Optional.of(courseId));
        when(questionRepository.isUsedInQuiz(questionId)).thenReturn(false);
        when(attemptRepository.hasAnswersForQuestion(questionId)).thenReturn(true);

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> service.replaceQuestion(courseId, questionId, validSingleChoiceRequest())
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void replaceQuestion_usedInQuiz_throwsConflict() {
        Question existing = sampleQuestion(questionId, skillId);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(existing));
        when(skillLookup.findCourseIdBySkillId(skillId)).thenReturn(Optional.of(courseId));
        when(questionRepository.isUsedInQuiz(questionId)).thenReturn(true);

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> service.replaceQuestion(courseId, questionId, validSingleChoiceRequest())
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void deleteQuestion_unusedQuestion_deletesAndReturnsDeletedDefinition() {
        Question existing = sampleQuestion(questionId, skillId);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(existing));
        when(skillLookup.findCourseIdBySkillId(skillId)).thenReturn(Optional.of(courseId));
        when(questionRepository.isUsedInQuiz(questionId)).thenReturn(false);
        when(attemptRepository.hasAnswersForQuestion(questionId)).thenReturn(false);

        QuestionResponse deleted = service.deleteQuestion(courseId, questionId);

        assertEquals(questionId, deleted.id());
        verify(questionRepository).deleteById(questionId);
    }

    @Test
    void deleteQuestion_usedInAttempt_throwsConflict() {
        Question existing = sampleQuestion(questionId, skillId);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(existing));
        when(skillLookup.findCourseIdBySkillId(skillId)).thenReturn(Optional.of(courseId));
        when(questionRepository.isUsedInQuiz(questionId)).thenReturn(false);
        when(attemptRepository.hasAnswersForQuestion(questionId)).thenReturn(true);

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> service.deleteQuestion(courseId, questionId)
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(questionRepository, never()).deleteById(any());
    }

    private QuestionRequest validSingleChoiceRequest() {
        return new QuestionRequest(
                skillId,
                "Sample prompt",
                "single_choice",
                "easy",
                List.of("Option A", "Option B"),
                List.of(true, false)
        );
    }

    private Question sampleQuestion(UUID id, UUID targetSkillId) {
        return new Question(
                id,
                targetSkillId,
                "Sample prompt",
                "single_choice",
                "easy",
                new String[]{"Option A", "Option B"},
                new Boolean[]{true, false}
        );
    }
}