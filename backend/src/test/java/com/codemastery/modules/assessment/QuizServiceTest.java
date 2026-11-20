package com.codemastery.modules.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.assessment.api.dto.QuizRequest.PublicationRequest;
import com.codemastery.modules.assessment.api.dto.QuizRequest.WriteQuizRequest;
import com.codemastery.modules.assessment.api.dto.QuizResponses.OwnedQuizResponse;
import com.codemastery.modules.assessment.api.dto.QuizResponses.QuizSummaryResponse;
import com.codemastery.modules.assessment.application.service.QuestionSelectionService;
import com.codemastery.modules.assessment.application.service.QuizService;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.model.Quiz;
import com.codemastery.modules.assessment.domain.repository.QuestionRepository;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.assessment.domain.repository.QuizRepository.QuizQuestionMembership;
import com.codemastery.modules.course.application.service.CourseAccessService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
class QuizServiceTest {

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private CourseAccessService courseAccessService;

    @Mock
    private ObjectProvider<QuestionSelectionService.SkillCourseLookup> skillCourseLookupProvider;

    @Mock
    private QuestionSelectionService.SkillCourseLookup skillCourseLookup;

    private QuizService quizService;

    private UUID courseId;
    private UUID otherCourseId;
    private UUID skillId;
    private UUID otherSkillId;
    private UUID quizId;
    private UUID q1Id;
    private UUID q2Id;

    @BeforeEach
    void setUp() {
        lenient().when(skillCourseLookupProvider.getIfAvailable()).thenReturn(skillCourseLookup);
        QuestionSelectionService selectionService =
                new QuestionSelectionService(questionRepository, skillCourseLookupProvider);
        quizService = new QuizService(quizRepository, selectionService, courseAccessService);

        courseId = UUID.randomUUID();
        otherCourseId = UUID.randomUUID();
        skillId = UUID.randomUUID();
        otherSkillId = UUID.randomUUID();
        quizId = UUID.randomUUID();
        q1Id = UUID.randomUUID();
        q2Id = UUID.randomUUID();
    }

    // --- 1. Composition, Ordering & Same-Course Scope ---

    @Test
    void createQuiz_validOrderedQuestions_savesHiddenQuizAndPreservesOrder() {
        Question q1 = sampleQuestion(q1Id, skillId, "First");
        Question q2 = sampleQuestion(q2Id, skillId, "Second");
        WriteQuizRequest request = new WriteQuizRequest(
                "Midterm Diagnostic",
                "diagnostic",
                1800,
                List.of(q2Id, q1Id)
        );

        when(questionRepository.findAllByIds(Set.of(q2Id, q1Id))).thenReturn(List.of(q1, q2));
        when(skillCourseLookup.findCourseIdBySkillId(skillId)).thenReturn(Optional.of(courseId));
        when(quizRepository.save(any(Quiz.class), eq(List.of(q2Id, q1Id))))
                .thenAnswer(inv -> inv.getArgument(0));

        OwnedQuizResponse response = quizService.createQuiz(courseId, request);

        assertNotNull(response.id());
        assertEquals(courseId, response.courseId());
        assertEquals("Midterm Diagnostic", response.title());
        assertEquals("diagnostic", response.kind());
        assertEquals(1800, response.timeLimitSeconds());
        assertFalse(response.published());
        assertEquals(2, response.questionCount());
        assertEquals(1, response.questions().get(0).position());
        assertEquals(q2Id, response.questions().get(0).question().id());
        assertEquals(2, response.questions().get(1).position());
        assertEquals(q1Id, response.questions().get(1).question().id());
        verify(courseAccessService).requireCourseOwnership(courseId);
    }

    @Test
    void createQuiz_questionBelongsToAnotherCourse_throwsResourceNotFound() {
        Question foreignQuestion = sampleQuestion(q1Id, otherSkillId, "Foreign");
        WriteQuizRequest request = new WriteQuizRequest(
                "Cross-course Quiz",
                "practice",
                600,
                List.of(q1Id)
        );

        when(questionRepository.findAllByIds(Set.of(q1Id))).thenReturn(List.of(foreignQuestion));
        when(skillCourseLookup.findCourseIdBySkillId(otherSkillId)).thenReturn(Optional.of(otherCourseId));

        assertThrows(ResourceNotFoundException.class, () -> quizService.createQuiz(courseId, request));
        verify(quizRepository, never()).save(any(), any());
    }

    @Test
    void createQuiz_duplicateQuestionIds_throwsBadRequest() {
        WriteQuizRequest request = new WriteQuizRequest(
                "Duplicate Quiz",
                "test",
                900,
                List.of(q1Id, q1Id)
        );

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> quizService.createQuiz(courseId, request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(quizRepository, never()).save(any(), any());
    }

    @Test
    void createQuiz_nonPositiveTimeLimit_throwsBadRequest() {
        WriteQuizRequest request = new WriteQuizRequest(
                "No Timer Quiz",
                "test",
                0,
                List.of()
        );

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> quizService.createQuiz(courseId, request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // --- 2. Ownership Refusal (CT03) ---

    @Test
    void createQuiz_nonOwner_throwsAccessDenied() {
        doThrow(new AccessDeniedException("Access denied"))
                .when(courseAccessService).requireCourseOwnership(courseId);

        assertThrows(AccessDeniedException.class, () -> quizService.createQuiz(
                courseId,
                new WriteQuizRequest("Quiz", "practice", 300, List.of())
        ));
        verify(quizRepository, never()).save(any(), any());
    }

    @Test
    void getOwnedQuiz_learnerOrNonOwner_throwsAccessDeniedAndConcealsKeys() {
        doThrow(new AccessDeniedException("Access denied"))
                .when(courseAccessService).requireCourseOwnership(courseId);

        assertThrows(AccessDeniedException.class, () -> quizService.getOwnedQuiz(courseId, quizId));
        verify(quizRepository, never()).findByIdAndCourseId(any(), any());
    }

    // --- 3. Learner Visibility & Access Gating (CT03) ---

    @Test
    void listLearnerQuizzes_enrolledLearner_returnsOnlyPublishedSummariesWithoutKeys() {
        Quiz publishedQuiz = new Quiz(quizId, courseId, "Week 1 Practice", "practice", 600, "published");
        when(quizRepository.findPublishedByCourseId(courseId)).thenReturn(List.of(publishedQuiz));
        when(quizRepository.findMembershipsByQuizId(quizId)).thenReturn(List.of(
                new QuizQuestionMembership(UUID.randomUUID(), quizId, q1Id, 1),
                new QuizQuestionMembership(UUID.randomUUID(), quizId, q2Id, 2)
        ));

        PageResponse<QuizSummaryResponse> page = quizService.listLearnerQuizzes(courseId, 0, 20);

        assertEquals(1, page.content().size());
        QuizSummaryResponse summary = page.content().get(0);
        assertEquals(quizId, summary.id());
        assertEquals("Week 1 Practice", summary.title());
        assertEquals(600, summary.timeLimitSeconds());
        assertTrue(summary.published());
        assertEquals(2, summary.questionCount());
        verify(courseAccessService).requireLearnerCourseAccess(courseId);
        verify(quizRepository, never()).findByCourseId(any());
    }

    @Test
    void listLearnerQuizzes_unenrolledOrIneligibleLearner_throwsAccessDenied() {
        doThrow(new AccessDeniedException("Access denied"))
                .when(courseAccessService).requireLearnerCourseAccess(courseId);

        assertThrows(AccessDeniedException.class, () -> quizService.listLearnerQuizzes(courseId, 0, 20));
        verify(quizRepository, never()).findPublishedByCourseId(any());
    }

    // --- 4. Publication Validation & Refusal (AS06.publish) ---

    @Test
    void setQuizPublication_emptyQuiz_refusesPublicationWithBadRequest() {
        Quiz emptyHiddenQuiz = new Quiz(quizId, courseId, "Empty Draft", "test", 900, "hidden");
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(emptyHiddenQuiz));
        when(quizRepository.findMembershipsByQuizId(quizId)).thenReturn(List.of());

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> quizService.setQuizPublication(courseId, quizId, new PublicationRequest(true))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(quizRepository, never()).updatePublicationStatus(any(), any());
    }

    @Test
    void setQuizPublication_questionOutsideCourse_refusesPublicationWithBadRequest() {
        Quiz draftWithForeignQuestion = new Quiz(quizId, courseId, "Invalid Draft", "test", 900, "hidden");
        Question foreignQuestion = sampleQuestion(q1Id, otherSkillId, "Foreign");

        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(draftWithForeignQuestion));
        when(quizRepository.findMembershipsByQuizId(quizId)).thenReturn(List.of(
                new QuizQuestionMembership(UUID.randomUUID(), quizId, q1Id, 1)
        ));
        when(questionRepository.findAllByIds(Set.of(q1Id))).thenReturn(List.of(foreignQuestion));
        when(skillCourseLookup.findCourseIdBySkillId(otherSkillId)).thenReturn(Optional.of(otherCourseId));

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> quizService.setQuizPublication(courseId, quizId, new PublicationRequest(true))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(quizRepository, never()).updatePublicationStatus(any(), any());
    }

    @Test
    void setQuizPublication_validNonEmptySameCourseQuiz_publishesSuccessfully() {
        Quiz draft = new Quiz(quizId, courseId, "Ready Quiz", "test", 1200, "hidden");
        Quiz published = new Quiz(quizId, courseId, "Ready Quiz", "test", 1200, "published");
        Question q1 = sampleQuestion(q1Id, skillId, "Valid");

        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(draft));
        when(quizRepository.findMembershipsByQuizId(quizId)).thenReturn(List.of(
                new QuizQuestionMembership(UUID.randomUUID(), quizId, q1Id, 1)
        ));
        when(questionRepository.findAllByIds(Set.of(q1Id))).thenReturn(List.of(q1));
        when(skillCourseLookup.findCourseIdBySkillId(skillId)).thenReturn(Optional.of(courseId));
        when(quizRepository.updatePublicationStatus(quizId, "published")).thenReturn(published);

        OwnedQuizResponse response = quizService.setQuizPublication(
                courseId, quizId, new PublicationRequest(true));

        assertTrue(response.published());
        assertEquals(1, response.questionCount());
        verify(quizRepository).updatePublicationStatus(quizId, "published");
    }

    // --- 5. Freeze / Conflict Rules on Published or Attempted Quizzes ---

    @Test
    void replaceUnusedHiddenQuiz_alreadyPublished_throwsConflict() {
        Quiz published = new Quiz(quizId, courseId, "Published Quiz", "test", 600, "published");
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(published));

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> quizService.replaceUnusedHiddenQuiz(
                        courseId, quizId, new WriteQuizRequest("New Title", "test", 600, List.of()))
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(quizRepository, never()).save(any(), any());
    }

    @Test
    void deleteUnusedHiddenQuiz_hasExistingAttempts_throwsConflict() {
        Quiz hiddenWithAttempts = new Quiz(quizId, courseId, "Attempted Quiz", "practice", 600, "hidden");
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(hiddenWithAttempts));
        when(quizRepository.hasAttempts(quizId)).thenReturn(true);

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> quizService.deleteUnusedHiddenQuiz(courseId, quizId)
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(quizRepository, never()).deleteById(any());
    }

    private Question sampleQuestion(UUID id, UUID targetSkillId, String prompt) {
        return new Question(
                id,
                targetSkillId,
                prompt,
                "single_choice",
                "easy",
                new String[]{"Option A", "Option B"},
                new Boolean[]{true, false}
        );
    }
}