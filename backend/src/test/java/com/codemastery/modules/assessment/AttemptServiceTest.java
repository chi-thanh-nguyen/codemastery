package com.codemastery.modules.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.assessment.api.dto.QuizRequest.PublicationRequest;
import com.codemastery.modules.assessment.api.dto.QuizRequest.WriteQuizRequest;
import com.codemastery.modules.assessment.application.service.QuestionSelectionService;
import com.codemastery.modules.assessment.application.service.QuizService;
import com.codemastery.modules.assessment.application.service.QuizService.QuizInput;
import com.codemastery.modules.assessment.application.service.QuizService.QuizSummary;
import com.codemastery.modules.assessment.application.service.QuizService.QuizView;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.model.Quiz;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.assessment.domain.repository.QuizRepository.Member;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.CourseManagementService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.codemastery.modules.assessment.application.scheduler.AttemptAutoSubmitScheduler;
import com.codemastery.modules.assessment.application.service.AttemptService;
import com.codemastery.modules.assessment.application.service.AttemptService.AnswerInput;
import com.codemastery.modules.assessment.application.service.AttemptService.AnswersInput;
import com.codemastery.modules.assessment.application.service.AttemptService.AttemptRead;
import com.codemastery.modules.assessment.application.service.AttemptService.AttemptResult;
import com.codemastery.modules.assessment.application.service.AttemptService.AttemptSummary;
import com.codemastery.modules.assessment.application.service.AttemptService.AttemptView;
import com.codemastery.modules.assessment.application.service.AttemptService.StartOutcome;
import com.codemastery.modules.assessment.application.service.QuizGradingService;
import com.codemastery.modules.assessment.domain.model.Attempt;
import com.codemastery.modules.assessment.domain.model.AttemptAnswer;
import com.codemastery.modules.assessment.domain.repository.AttemptRepository;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.mockito.ArgumentCaptor;

/**
 * ADR008 §5 placement for CT23 quiz authoring and publication (AS06; ADR007 §§5–7, B3-O01/O03).
 * Doubles stand in for the CT40 projection and ADR005 Course guard, which are not implemented: the
 * guard double only records its use and runs the callback, so these tests prove composition and
 * lifecycle rules, not locking, PostgreSQL behavior or real integration.
 */
class AttemptServiceTest {

    private static final UUID COURSE = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID SKILL = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final UUID FOREIGN_SKILL = UUID.fromString("00000000-0000-0000-0000-00000000a002");
    private static final UUID QUIZ = UUID.fromString("00000000-0000-0000-0000-00000000b001");
    private static final UUID Q1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID Q2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID Q3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private QuizRepository quizzes;
    private CourseAccessService courseAccess;
    private CourseManagementService guard;
    private SkillGraphService skillGraph;
    private final List<UUID> guardedCourses = new ArrayList<>();
    private List<UUID> courseSkills;
    private QuizService service;

    @BeforeEach
    void setUp() {
        quizzes = mock(QuizRepository.class);
        courseAccess = mock(CourseAccessService.class);
        guard = mock(CourseManagementService.class);
        skillGraph = mock(SkillGraphService.class);
        courseSkills = List.of(SKILL);
        QuestionSelectionService selection = new QuestionSelectionService(quizzes, skillGraph) {
            @Override
            protected List<UUID> readSkillIds(UUID courseId) {
                return courseSkills;
            }
        };
        service = new QuizService(quizzes, selection, courseAccess, guard) {
            @Override
            protected <T> T withCourseConfigurationWrite(UUID courseId, Supplier<T> work) {
                guardedCourses.add(courseId);
                return work.get();
            }
        };
        lenient().when(quizzes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(quizzes.findMembers(anyCollection())).thenReturn(Map.of());
        lenient().when(quizzes.countMembers(anyCollection())).thenReturn(Map.of());
    }

    private static Question question(UUID id, UUID skill) {
        return new Question(id, skill, "Predict the output of print(" + id.toString().substring(35) + ")",
                "single_choice", "easy", new String[] {"1", "2"}, new Boolean[] {true, false});
    }

    private static Quiz quiz(String status) {
        return new Quiz(QUIZ, COURSE, "Loops", "practice", 600, status);
    }

    private static QuizInput input(List<UUID> questionIds) {
        return new QuizInput("Loops", "practice", 600, questionIds);
    }

    private void bank(Question... questions) {
        when(quizzes.findQuestionsInSkills(anyCollection(), anyCollection())).thenAnswer(invocation -> {
            List<UUID> requested = new ArrayList<>(invocation.getArgument(0));
            List<UUID> skills = new ArrayList<>(invocation.getArgument(1));
            return Arrays.stream(questions)
                    .filter(question -> requested.contains(question.id()) && skills.contains(question.skill_id()))
                    .toList();
        });
    }

    private void stored(Quiz quiz, Question... members) {
        when(quizzes.findInCourseForUpdate(QUIZ, COURSE)).thenReturn(Optional.of(quiz));
        when(quizzes.findInCourse(QUIZ, COURSE)).thenReturn(Optional.of(quiz));
        List<Member> rows = new ArrayList<>();
        for (int index = 0; index < members.length; index++) {
            rows.add(new Member(index + 1, members[index]));
        }
        lenient().when(quizzes.findMembers(List.of(QUIZ))).thenReturn(rows.isEmpty() ? Map.of() : Map.of(QUIZ, rows));
    }

    @Nested
    class Composition {

        @Test
        void createPersistsAHiddenQuizWithTheExactSuppliedOrderInsideTheCourseGuard() {
            bank(question(Q1, SKILL), question(Q2, SKILL), question(Q3, SKILL));

            QuizView view = service.create(COURSE, input(List.of(Q3, Q1, Q2)));

            assertFalse(view.published());
            assertEquals(List.of(Q3, Q1, Q2), view.questions().stream().map(item -> item.question().id()).toList());
            assertEquals(List.of(1, 2, 3), view.questions().stream().map(QuizService.QuizQuestionView::position).toList());
            assertEquals(3, view.questionCount());
            assertEquals(List.of(true, false), view.questions().getFirst().question().correctOptions());
            assertEquals(List.of(COURSE), guardedCourses);
            InOrder order = inOrder(courseAccess, quizzes);
            order.verify(courseAccess).requireCourseOwnership(COURSE);
            order.verify(quizzes).save(any());
            order.verify(quizzes).replaceMembership(view.id(), List.of(Q3, Q1, Q2));
        }

        @Test
        void anEmptyHiddenDraftIsAllowedAndNeedsNoSkillProjection() {
            courseSkills = null; // would be a provider failure if consulted

            QuizView view = service.create(COURSE, input(List.of()));

            assertEquals(0, view.questionCount());
            verify(quizzes).replaceMembership(view.id(), List.of());
            verify(quizzes, never()).findQuestionsInSkills(anyCollection(), anyCollection());
        }

        @Test
        void foreignOrMissingQuestionsAreTheSameSafe404WithNoWrite() {
            bank(question(Q1, SKILL), question(Q2, FOREIGN_SKILL));

            assertThrows(ResourceNotFoundException.class, () -> service.create(COURSE, input(List.of(Q1, Q2))));
            assertThrows(ResourceNotFoundException.class, () -> service.create(COURSE, input(List.of(Q1, Q3))));
            verify(quizzes, never()).save(any());
            verify(quizzes, never()).replaceMembership(any(), anyList());
        }

        @Test
        void aStoredQuestionViolatingV004IsAnIntegrityFailureNotAClientError() {
            bank(new Question(Q1, SKILL, "Prompt", "single_choice", "easy",
                    new String[] {"1", "2"}, new Boolean[] {true, true}));

            assertThrows(IllegalStateException.class, () -> service.create(COURSE, input(List.of(Q1))));
            verify(quizzes, never()).save(any());
        }

        @Test
        void anInvalidCt40ProjectionIsAProviderFailure() {
            courseSkills = Arrays.asList(SKILL, SKILL);
            bank(question(Q1, SKILL));

            assertThrows(IllegalStateException.class, () -> service.create(COURSE, input(List.of(Q1))));
        }

        @Test
        void invalidInputIs400AfterCourseAdmissionAndBeforeTheGuard() {
            List<QuizInput> invalid = List.of(
                    new QuizInput("   ", "practice", 60, List.of()),
                    new QuizInput(" ", "practice", 60, List.of()),
                    new QuizInput("\uFEFF", "practice", 60, List.of()),
                    new QuizInput("\u0085 \u001C", "practice", 60, List.of()),
                    new QuizInput("Title\u0000", "practice", 60, List.of()),
                    new QuizInput("Title\uD800", "practice", 60, List.of()),
                    new QuizInput("\uDC00", "practice", 60, List.of()),
                    new QuizInput(null, "practice", 60, List.of()),
                    new QuizInput("Title", "Practice", 60, List.of()),
                    new QuizInput("Title", "exam", 60, List.of()),
                    new QuizInput("Title", "practice", 0, List.of()),
                    new QuizInput("Title", "practice", -1, List.of()),
                    new QuizInput("Title", "practice", null, List.of()),
                    new QuizInput("Title", "practice", 60, null),
                    new QuizInput("Title", "practice", 60, Arrays.asList(Q1, null)),
                    new QuizInput("Title", "practice", 60, List.of(Q1, Q1)));
            for (QuizInput value : invalid) {
                BusinessRuleException failure = assertThrows(BusinessRuleException.class, () -> service.create(COURSE, value));
                assertEquals(HttpStatus.BAD_REQUEST, failure.getStatus());
            }
            assertThrows(BusinessRuleException.class, () -> service.create(COURSE, null));
            assertTrue(guardedCourses.isEmpty());
            verify(quizzes, never()).save(any());
        }

        @Test
        void courseDenialPrecedesInputValidationAndTouchesNothing() {
            doThrow(new AccessDeniedException("Access denied")).when(courseAccess).requireCourseOwnership(COURSE);

            assertThrows(AccessDeniedException.class, () -> service.create(COURSE, input(null)));
            assertThrows(AccessDeniedException.class, () -> service.update(COURSE, QUIZ, input(List.of())));
            assertThrows(AccessDeniedException.class, () -> service.delete(COURSE, QUIZ));
            assertThrows(AccessDeniedException.class, () -> service.publish(COURSE, QUIZ, true));
            assertThrows(AccessDeniedException.class, () -> service.readOwned(COURSE, QUIZ));
            assertThrows(AccessDeniedException.class, () -> service.listOwned(COURSE, 0, 20));
            assertTrue(guardedCourses.isEmpty());
            verifyNoInteractions(quizzes);
        }

        @Test
        void updateReplacesTheWholeDefinitionOfAHiddenNeverAttemptedQuiz() {
            stored(quiz("hidden"), question(Q1, SKILL));
            bank(question(Q2, SKILL), question(Q3, SKILL));

            QuizView view = service.update(COURSE, QUIZ, new QuizInput("Loops II", "test", 120, List.of(Q3, Q2)));

            assertEquals("Loops II", view.title());
            assertEquals("test", view.kind());
            assertEquals(120, view.timeLimitSeconds());
            assertEquals(List.of(Q3, Q2), view.questions().stream().map(item -> item.question().id()).toList());
            InOrder order = inOrder(quizzes);
            order.verify(quizzes).findInCourseForUpdate(QUIZ, COURSE);
            order.verify(quizzes).hasAttempts(QUIZ);
            order.verify(quizzes).save(new Quiz(QUIZ, COURSE, "Loops II", "test", 120, "hidden"));
            order.verify(quizzes).replaceMembership(QUIZ, List.of(Q3, Q2));
        }

        @Test
        void publishedOrAttemptedQuizzesAreFrozenForEditAndDelete() {
            stored(quiz("published"), question(Q1, SKILL));
            assertConflict(() -> service.update(COURSE, QUIZ, input(List.of(Q1))));
            assertConflict(() -> service.delete(COURSE, QUIZ));

            stored(quiz("hidden"), question(Q1, SKILL));
            when(quizzes.hasAttempts(QUIZ)).thenReturn(true);
            assertConflict(() -> service.update(COURSE, QUIZ, input(List.of(Q1))));
            assertConflict(() -> service.delete(COURSE, QUIZ));

            verify(quizzes, never()).save(any());
            verify(quizzes, never()).replaceMembership(any(), anyList());
            verify(quizzes, never()).delete(any());
        }

        @Test
        void deleteRemovesOnlyAHiddenNeverAttemptedQuizAndReturnsItsDefinition() {
            stored(quiz("hidden"), question(Q1, SKILL), question(Q2, SKILL));

            QuizView deleted = service.delete(COURSE, QUIZ);

            assertEquals(List.of(Q1, Q2), deleted.questions().stream().map(item -> item.question().id()).toList());
            verify(quizzes).delete(QUIZ);
        }

        @Test
        void aMissingOrForeignQuizIs404InsideTheGuard() {
            when(quizzes.findInCourseForUpdate(QUIZ, COURSE)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.update(COURSE, QUIZ, input(List.of())));
            assertThrows(ResourceNotFoundException.class, () -> service.delete(COURSE, QUIZ));
            assertThrows(ResourceNotFoundException.class, () -> service.publish(COURSE, QUIZ, false));
            assertEquals(List.of(COURSE, COURSE, COURSE), guardedCourses);
        }
    }

    @Nested
    class Publication {

        @Test
        void anEmptyValidDraftCannotBePublished409() {
            stored(quiz("hidden"));

            assertConflict(() -> service.publish(COURSE, QUIZ, true));
            verify(quizzes, never()).save(any());
        }

        @Test
        void publishingRevalidatesSameCourseMembershipAndPreservesOrder() {
            stored(quiz("hidden"), question(Q2, SKILL), question(Q1, SKILL));

            QuizView view = service.publish(COURSE, QUIZ, true);

            assertTrue(view.published());
            assertEquals(List.of(Q2, Q1), view.questions().stream().map(item -> item.question().id()).toList());
            verify(quizzes).save(new Quiz(QUIZ, COURSE, "Loops", "practice", 600, "published"));
        }

        @Test
        void aMemberOutsideTheCourseOrAnInvalidStoredMemberIsAnIntegrityFailure() {
            stored(quiz("hidden"), question(Q1, FOREIGN_SKILL));
            assertThrows(IllegalStateException.class, () -> service.publish(COURSE, QUIZ, true));

            stored(quiz("hidden"), new Question(Q1, SKILL, "Prompt", "true_false", "easy",
                    new String[] {"Yes", "No"}, new Boolean[] {true, false}));
            assertThrows(IllegalStateException.class, () -> service.publish(COURSE, QUIZ, true));

            // A stored prompt that is blank under the shared rule (U+FEFF only) is an integrity failure too.
            stored(quiz("hidden"), new Question(Q1, SKILL, "\uFEFF", "true_false", "easy",
                    new String[] {"True", "False"}, new Boolean[] {true, false}));
            assertThrows(IllegalStateException.class, () -> service.publish(COURSE, QUIZ, true));

            verify(quizzes, never()).save(any());
        }

        @Test
        void publicationIsIdempotentAndRepublishingStillRevalidates() {
            stored(quiz("published"), question(Q1, SKILL));

            assertTrue(service.publish(COURSE, QUIZ, true).published());
            verify(quizzes, never()).save(any());

            courseSkills = List.of(FOREIGN_SKILL);
            assertThrows(IllegalStateException.class, () -> service.publish(COURSE, QUIZ, true));
        }

        @Test
        void hidingNeedsNoValidationDeletesNothingAndKeepsAnAttemptedQuizFrozen() {
            stored(quiz("published"), question(Q1, SKILL));
            when(quizzes.hasAttempts(QUIZ)).thenReturn(true);

            QuizView hidden = service.publish(COURSE, QUIZ, false);

            assertFalse(hidden.published());
            verify(quizzes).save(new Quiz(QUIZ, COURSE, "Loops", "practice", 600, "hidden"));
            verify(quizzes, never()).delete(any());
            verify(quizzes, never()).replaceMembership(any(), anyList());

            stored(quiz("hidden"), question(Q1, SKILL));
            assertConflict(() -> service.update(COURSE, QUIZ, input(List.of(Q1))));
        }

        @Test
        void hidingAnAlreadyHiddenEmptyDraftIsANoOp() {
            stored(quiz("hidden"));

            assertFalse(service.publish(COURSE, QUIZ, false).published());
            verify(quizzes, never()).save(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"{\"publicationStatus\":\"published\"}", "{\"publicationStatus\":\"hidden\"}"})
        void publicationWriteMapsTheApprovedStatusToTheServiceBoolean(String json) {
            assertEquals(json.contains("\"published\""), PublicationRequest.parse(json).orElseThrow().published());
        }

        @ParameterizedTest
        @ValueSource(strings = {"{\"published\":true}", "{\"publicationStatus\":\"PUBLISHED\"}",
                "{\"publicationStatus\":true}", "{\"publicationStatus\":null}",
                "{\"publicationStatus\":\"hidden\",\"publicationStatus\":\"published\"}",
                "{\"publicationStatus\":\"hidden\",\"x\":1}", "{}", "[]", "\"hidden\"", "{\"publicationStatus\":\"hidden\"} {}"})
        void anyOtherPublicationBodyIsRejected(String json) {
            assertTrue(PublicationRequest.parse(json).isEmpty());
        }
    }

    @Nested
    class Reads {

        @Test
        void ownerReadReturnsTheOrderedDefinitionIncludingKeys() {
            stored(quiz("hidden"), question(Q2, SKILL), question(Q1, SKILL));

            QuizView view = service.readOwned(COURSE, QUIZ);

            assertEquals(List.of(Q2, Q1), view.questions().stream().map(item -> item.question().id()).toList());
            assertEquals(List.of(true, false), view.questions().get(1).question().correctOptions());
            assertTrue(guardedCourses.isEmpty());
        }

        @Test
        void corruptStoredPositionsOrQuizRowsAreIntegrityFailures() {
            when(quizzes.findInCourse(QUIZ, COURSE)).thenReturn(Optional.of(quiz("hidden")));
            when(quizzes.findMembers(List.of(QUIZ))).thenReturn(Map.of(QUIZ, List.of(new Member(2, question(Q1, SKILL)))));
            assertThrows(IllegalStateException.class, () -> service.readOwned(COURSE, QUIZ));

            when(quizzes.findInCourse(QUIZ, COURSE)).thenReturn(Optional.of(
                    new Quiz(QUIZ, UUID.randomUUID(), "Loops", "practice", 600, "hidden")));
            when(quizzes.findMembers(List.of(QUIZ))).thenReturn(Map.of());
            assertThrows(IllegalStateException.class, () -> service.readOwned(COURSE, QUIZ));
        }

        @Test
        void ownerListPagesInRepositoryOrderWithFilteringBeforeCounting() {
            Quiz first = new Quiz(UUID.randomUUID(), COURSE, "A", "test", 60, "published");
            Quiz second = new Quiz(UUID.randomUUID(), COURSE, "B", "diagnostic", 60, "hidden");
            when(quizzes.countInCourse(COURSE, false)).thenReturn(3L);
            when(quizzes.findPageInCourse(COURSE, false, 0, 2)).thenReturn(List.of(first, second));
            when(quizzes.findMembers(List.of(first.id(), second.id())))
                    .thenReturn(Map.of(first.id(), List.of(new Member(1, question(Q1, SKILL)))));

            PageResponse<QuizView> page = service.listOwned(COURSE, 0, 2);

            assertEquals(List.of("A", "B"), page.content().stream().map(QuizView::title).toList());
            assertEquals(List.of(1, 0), page.content().stream().map(QuizView::questionCount).toList());
            assertEquals(3, page.totalElements());
            assertEquals(2, page.totalPages());
        }

        @Test
        void aPagePastTheEndIsEmptyWithoutARowQuery() {
            when(quizzes.countInCourse(COURSE, false)).thenReturn(1L);

            PageResponse<QuizView> page = service.listOwned(COURSE, Integer.MAX_VALUE, 100);

            assertTrue(page.content().isEmpty());
            verify(quizzes, never()).findPageInCourse(any(), anyBoolean(), anyInt(), anyInt());
        }

        @Test
        void invalidPagingIs400BeforeAnyAdmissionOrRead() {
            for (int[] invalid : new int[][] {{-1, 20}, {0, 0}, {0, 101}}) {
                assertThrows(BusinessRuleException.class, () -> service.listOwned(COURSE, invalid[0], invalid[1]));
                assertThrows(BusinessRuleException.class, () -> service.listForLearner(COURSE, invalid[0], invalid[1]));
            }
            verifyNoInteractions(courseAccess, quizzes);
        }

        @Test
        void learnerListUsesLearnerAdmissionAndReturnsPublishedMetadataOnly() {
            Quiz published = new Quiz(QUIZ, COURSE, "Loops", "practice", 600, "published");
            when(quizzes.countInCourse(COURSE, true)).thenReturn(1L);
            when(quizzes.findPageInCourse(COURSE, true, 0, 20)).thenReturn(List.of(published));
            when(quizzes.countMembers(List.of(QUIZ))).thenReturn(Map.of(QUIZ, 4));

            PageResponse<QuizSummary> page = service.listForLearner(COURSE, 0, 20);

            assertEquals(List.of(new QuizSummary(QUIZ, COURSE, "Loops", "practice", 600, true, 4)), page.content());
            verify(courseAccess).requireLearnerCourseAccess(COURSE);
            verify(courseAccess, never()).requireCourseOwnership(any());
            verify(quizzes, never()).findMembers(anyCollection());
            assertEquals(List.of("id", "courseId", "title", "kind", "timeLimitSeconds", "published", "questionCount"),
                    Arrays.stream(QuizSummary.class.getRecordComponents()).map(component -> component.getName()).toList());
        }

        @Test
        void learnerDenialStopsBeforeAnyQuizRead() {
            doThrow(new AccessDeniedException("Access denied")).when(courseAccess).requireLearnerCourseAccess(COURSE);
            assertThrows(AccessDeniedException.class, () -> service.listForLearner(COURSE, 0, 20));
            doThrow(new ResourceNotFoundException("Resource not available"))
                    .when(courseAccess).requireLearnerCourseAccess(COURSE);
            assertThrows(ResourceNotFoundException.class, () -> service.listForLearner(COURSE, 0, 20));
            verifyNoInteractions(quizzes);
        }

        @Test
        void aRepositoryRowThatIsNotPublishedNeverReachesALearner() {
            when(quizzes.countInCourse(COURSE, true)).thenReturn(1L);
            when(quizzes.findPageInCourse(COURSE, true, 0, 20)).thenReturn(List.of(quiz("hidden")));

            assertThrows(IllegalStateException.class, () -> service.listForLearner(COURSE, 0, 20));
        }
    }

    @Nested
    class ApprovedBoundaries {

        @Test
        void everyWriteDelegatesToTheCo14GuardAndItsRefusalPrecedesAnyQuizAccess() {
            ResourceNotFoundException concealed = new ResourceNotFoundException("Resource not available");
            when(guard.withCourseConfigurationWrite(eq(COURSE), any())).thenThrow(concealed);
            QuizService production = new QuizService(quizzes, new QuestionSelectionService(quizzes, skillGraph), courseAccess, guard);

            assertSame(concealed, assertThrows(ResourceNotFoundException.class, () -> production.create(COURSE, input(List.of()))));
            assertSame(concealed, assertThrows(ResourceNotFoundException.class, () -> production.update(COURSE, QUIZ, input(List.of()))));
            assertSame(concealed, assertThrows(ResourceNotFoundException.class, () -> production.delete(COURSE, QUIZ)));
            assertSame(concealed, assertThrows(ResourceNotFoundException.class, () -> production.publish(COURSE, QUIZ, true)));
            assertSame(concealed, assertThrows(ResourceNotFoundException.class, () -> production.publish(COURSE, QUIZ, false)));
            verify(guard, times(5)).withCourseConfigurationWrite(eq(COURSE), any());
            verifyNoInteractions(quizzes);
        }

        @Test
        void insideTheRealGuardMembershipIsScopedByTheProducersSkillSet() {
            when(guard.withCourseConfigurationWrite(eq(COURSE), any())).thenAnswer(
                    invocation -> invocation.<Supplier<?>>getArgument(1).get());
            when(skillGraph.readSkillIds(COURSE)).thenReturn(List.of(SKILL));
            when(quizzes.findQuestionsInSkills(List.of(Q1), List.of(SKILL))).thenReturn(List.of(question(Q1, SKILL)));
            QuizService production = new QuizService(quizzes, new QuestionSelectionService(quizzes, skillGraph), courseAccess, guard);

            production.create(COURSE, input(List.of(Q1)));

            var order = inOrder(guard, skillGraph, quizzes);
            order.verify(guard).withCourseConfigurationWrite(eq(COURSE), any());
            order.verify(skillGraph).readSkillIds(COURSE);
            order.verify(quizzes).findQuestionsInSkills(List.of(Q1), List.of(SKILL));
            order.verify(quizzes).replaceMembership(any(), eq(List.of(Q1)));
        }

        @Test
        void aProducerFailureInsideTheGuardWritesNothing() {
            when(guard.withCourseConfigurationWrite(eq(COURSE), any())).thenAnswer(
                    invocation -> invocation.<Supplier<?>>getArgument(1).get());
            when(skillGraph.readSkillIds(COURSE)).thenThrow(new IllegalStateException("provider failure"));
            QuizService production = new QuizService(quizzes, new QuestionSelectionService(quizzes, skillGraph), courseAccess, guard);

            assertThrows(IllegalStateException.class, () -> production.create(COURSE, input(List.of(Q1))));
            verify(quizzes, never()).save(any());
            verify(quizzes, never()).replaceMembership(any(), any());
        }

        @Test
        void selectionNeverWidensAnInvalidProducerProjection() {
            QuestionSelectionService selection = new QuestionSelectionService(quizzes, skillGraph);
            when(skillGraph.readSkillIds(COURSE)).thenReturn(java.util.Arrays.asList(SKILL, null));

            assertThrows(IllegalStateException.class,
                    () -> selection.requireOrderedCourseQuestions(COURSE, List.of(Q1)));
            assertThrows(IllegalStateException.class,
                    () -> selection.requireCourseMembers(COURSE, List.of(question(Q1, SKILL))));
            verifyNoInteractions(quizzes);
        }

        @Test
        void publicationRevalidatesMembersAgainstTheProducersCurrentSkillSet() {
            QuestionSelectionService selection = new QuestionSelectionService(quizzes, skillGraph);
            when(skillGraph.readSkillIds(COURSE)).thenReturn(List.of(SKILL));

            selection.requireCourseMembers(COURSE, List.of(question(Q1, SKILL)));
            assertThrows(IllegalStateException.class,
                    () -> selection.requireCourseMembers(COURSE, List.of(question(Q2, FOREIGN_SKILL))));
        }

        @Test
        void quizWriteBodyIsClosedAndCarriesTheExactOrderedMembership() {
            String ids = "[\"" + Q2 + "\",\"" + Q1.toString().toUpperCase() + "\"]";
            WriteQuizRequest parsed = WriteQuizRequest.parse(
                    "{\"title\":\"Loops\",\"kind\":\"practice\",\"timeLimitSeconds\":2147483647,\"questionIds\":" + ids + "}")
                    .orElseThrow();
            assertEquals(new QuizInput("Loops", "practice", Integer.MAX_VALUE, List.of(Q2, Q1)), parsed.toInput());

            String valid = "{\"title\":\"T\",\"kind\":\"practice\",\"timeLimitSeconds\":60,\"questionIds\":[]}";
            assertTrue(WriteQuizRequest.parse(valid).isPresent());
            for (String body : List.of(
                    valid.replace("}", ",\"extra\":1}"),
                    valid.replace("\"kind\"", "\"title\":\"dup\",\"kind\""),
                    valid.replace(",\"questionIds\":[]", ""),
                    valid.replace("60", "\"60\""),
                    valid.replace("60", "60.5"),
                    valid.replace("60", "60.0"),
                    valid.replace("60", "2147483648"),
                    valid.replace("60", "null"),
                    valid.replace("\"T\"", "1"),
                    valid.replace("\"practice\"", "null"),
                    valid.replace("[]", "null"),
                    valid.replace("[]", "[null]"),
                    valid.replace("[]", "[\"1-1-1-1-1\"]"),
                    valid.replace("[]", "[\"" + Q1 + "\",\"" + Q1 + "\"]"),
                    valid.replace("[]", "[\"" + Q1 + "\",\"" + Q1.toString().toUpperCase() + "\"]"),
                    valid + " {}",
                    "[]", "{", "", "null")) {
                assertTrue(WriteQuizRequest.parse(body).isEmpty(), body);
            }
            assertTrue(WriteQuizRequest.parse(null).isEmpty());
        }

        @Test
        void inputAndViewsAreDeeplyImmutableCopies() {
            List<UUID> source = new ArrayList<>(List.of(Q1));
            QuizInput copied = new QuizInput("T", "practice", 60, source);
            source.add(Q2);
            assertEquals(List.of(Q1), copied.questionIds());
            assertThrows(UnsupportedOperationException.class, () -> copied.questionIds().add(Q3));

            bank(question(Q1, SKILL));
            QuizView view = service.create(COURSE, input(List.of(Q1)));
            assertThrows(UnsupportedOperationException.class, () -> view.questions().clear());
            assertThrows(UnsupportedOperationException.class,
                    () -> view.questions().getFirst().question().correctOptions().set(0, false));
        }
    }

    private static void assertConflict(org.junit.jupiter.api.function.Executable executable) {
        BusinessRuleException failure = assertThrows(BusinessRuleException.class, executable);
        assertEquals(HttpStatus.CONFLICT, failure.getStatus());
    }

@Nested
class TimedAttempts {

    private AttemptRepository attemptRepo;
    private QuizRepository quizRepo;
    private CourseAccessService access;
    private UserAccountService identities;
    private MutableClock clock;
    private org.springframework.transaction.support.TransactionTemplate readTx;
    private AttemptService attemptService;
    private CourseManagementService attemptGuard;
    private SkillGraphService attemptSkills;

    private final UUID learnerId = UUID.fromString("00000000-0000-0000-0000-00000000e001");

    @BeforeEach
    void setUpAttempts() {
        attemptRepo = mock(AttemptRepository.class);
        quizRepo = mock(QuizRepository.class);
        access = mock(CourseAccessService.class);
        identities = mock(UserAccountService.class);
        clock = new MutableClock(Instant.parse("2026-11-26T10:00:00Z"));
        CourseManagementService guard = mock(CourseManagementService.class);
        attemptGuard = guard;
        org.springframework.transaction.support.TransactionTemplate tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
                    @Override protected Object doGetTransaction() { return new Object(); }
                    @Override protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {}
                    @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {}
                    @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {}
                });
        readTx = new org.springframework.transaction.support.TransactionTemplate(tx.getTransactionManager());
        readTx.setReadOnly(true);
        readTx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        lenient().when(guard.withLearnerAssessmentWrite(eq(COURSE), any())).thenAnswer(
                call -> tx.execute(status -> call.<Supplier<?>>getArgument(1).get()));
        lenient().when(guard.withAssessmentExpiryWrite(eq(COURSE), any())).thenAnswer(
                call -> tx.execute(status -> call.<Supplier<?>>getArgument(1).get()));
        SkillGraphService skillGraph = mock(SkillGraphService.class);
        attemptSkills = skillGraph;
        attemptService = new AttemptService(
                attemptRepo, quizRepo, new QuizGradingService(), access, identities, guard, skillGraph, clock);
        lenient().when(identities.requireCurrentIdentity())
                .thenReturn(new CurrentIdentity(learnerId, UserAccountService.IdentityRole.LEARNER));
    }

    private List<Member> twoQuestions() {
        Question q1 = new Question(Q1, SKILL, "1 + 1 = ?", "single_choice", "easy",
                new String[] {"1", "2"}, new Boolean[] {false, true});
        Question q2 = new Question(Q2, SKILL, "Select primes", "multiple_choice", "medium",
                new String[] {"2", "3", "4"}, new Boolean[] {true, true, false});
        return List.of(new Member(1, q1), new Member(3, q2)); // verifies non-contiguous positions
    }

    @Test
    void startCreatesNewAttemptAndResumesExistingActiveAttemptWithoutResettingTimer() {
        Quiz published = quiz("published");
        when(quizRepo.findInCourse(QUIZ, COURSE)).thenReturn(Optional.of(published));
        when(quizRepo.findInCourseForUpdate(QUIZ, COURSE)).thenReturn(Optional.of(published));
        when(quizRepo.findMembers(List.of(QUIZ))).thenReturn(Map.of(QUIZ, twoQuestions()));
        when(attemptRepo.findUnsubmittedByQuizAndLearner(QUIZ, learnerId)).thenReturn(Optional.empty());
        when(attemptRepo.findMaxAttemptNumber(QUIZ, learnerId)).thenReturn(2);
        when(attemptRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StartOutcome created = attemptService.start(COURSE, QUIZ);
        assertTrue(created.created());
        assertEquals(3, created.attempt().attemptNumber());
        assertEquals(Instant.parse("2026-11-26T10:10:00Z"), created.attempt().expiresAt());
        assertEquals("active", created.attempt().status());
        assertEquals(List.of(false, false), created.attempt().questions().get(0).selectedOptions());
        assertEquals(List.of(false, false, false), created.attempt().questions().get(1).selectedOptions());

        clock.advanceSeconds(120);
        Attempt activeRow = new Attempt(created.attempt().attemptId(), learnerId, QUIZ, 3,
                created.attempt().startedAt(), created.attempt().expiresAt(), null, null);
        when(attemptRepo.findUnsubmittedByQuizAndLearner(QUIZ, learnerId)).thenReturn(Optional.of(activeRow));
        when(attemptRepo.findAnswersInPositionOrder(activeRow.id())).thenReturn(List.of(
                new AttemptAnswer(UUID.randomUUID(), activeRow.id(), Q1, 1, new Boolean[] {false, false}, null),
                new AttemptAnswer(UUID.randomUUID(), activeRow.id(), Q2, 3, new Boolean[] {false, false, false}, null)));

        StartOutcome resumed = attemptService.start(COURSE, QUIZ);
        assertFalse(resumed.created());
        assertEquals(created.attempt().attemptId(), resumed.attempt().attemptId());
        assertEquals(Instant.parse("2026-11-26T10:10:00Z"), resumed.attempt().expiresAt());
    }

    @Test
    void startRejectsOverdueUnsubmittedAttemptAndDiagnosticQuizWith409() {
        Quiz published = quiz("published");
        when(quizRepo.findInCourse(QUIZ, COURSE)).thenReturn(Optional.of(published));
        when(quizRepo.findInCourseForUpdate(QUIZ, COURSE)).thenReturn(Optional.of(published));
        when(quizRepo.findMembers(List.of(QUIZ))).thenReturn(Map.of(QUIZ, twoQuestions()));
        Attempt overdue = new Attempt(UUID.randomUUID(), learnerId, QUIZ, 1,
                Instant.parse("2026-11-26T09:50:00Z"), Instant.parse("2026-11-26T10:00:00Z"), null, null);
        when(attemptRepo.findUnsubmittedByQuizAndLearner(QUIZ, learnerId)).thenReturn(Optional.of(overdue));

        assertConflict(() -> attemptService.start(COURSE, QUIZ));

        Quiz diagnostic = new Quiz(QUIZ, COURSE, "Diag", "diagnostic", 600, "published");
        when(quizRepo.findInCourse(QUIZ, COURSE)).thenReturn(Optional.of(diagnostic));
        when(quizRepo.findInCourseForUpdate(QUIZ, COURSE)).thenReturn(Optional.of(diagnostic));
        when(attemptRepo.hasBlockingDiagnosticAttempt(COURSE,learnerId,QUIZ)).thenReturn(true);
        assertConflict(() -> attemptService.start(COURSE, QUIZ));
    }

    @Test
    void manualSubmitGradesExactMasksAndIsIdempotentOnRepeatedSubmit() {
        UUID attemptId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-11-26T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-11-26T10:10:00Z");
        clock.advanceSeconds(300);

        Quiz published = quiz("published");
        Attempt active = new Attempt(attemptId, learnerId, QUIZ, 1, startedAt, expiresAt, null, null);
        AttemptAnswer a1 = new AttemptAnswer(UUID.randomUUID(), attemptId, Q1, 1, new Boolean[] {false, false}, null);
        AttemptAnswer a2 = new AttemptAnswer(UUID.randomUUID(), attemptId, Q2, 3, new Boolean[] {false, false, false}, null);

        when(quizRepo.findInCourseForUpdate(QUIZ, COURSE)).thenReturn(Optional.of(published));
        when(quizRepo.findMembers(List.of(QUIZ))).thenReturn(Map.of(QUIZ, twoQuestions()));
        when(attemptRepo.findInQuizForUpdate(attemptId, QUIZ)).thenReturn(Optional.of(active));
        when(attemptRepo.findAnswersInPositionOrderForUpdate(attemptId)).thenReturn(List.of(a1, a2));
        when(attemptRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswersInput submission = new AnswersInput(List.of(
                new AnswerInput(Q2, List.of(true, false, false)),
                new AnswerInput(Q1, List.of(false, true))));

        AttemptResult result = attemptService.submit(COURSE, QUIZ, attemptId, submission);
        assertEquals("submitted", result.status());
        assertEquals(Instant.parse("2026-11-26T10:05:00Z"), result.submittedAt());
        assertEquals(new BigDecimal("50.00"), result.scorePercentage());
        assertTrue(result.answers().get(0).correct());
        assertFalse(result.answers().get(1).correct());

        Attempt submitted = new Attempt(attemptId, learnerId, QUIZ, 1, startedAt, expiresAt,
                Instant.parse("2026-11-26T10:05:00Z"), new BigDecimal("50.00"));
        AttemptAnswer g1 = new AttemptAnswer(a1.id(), attemptId, Q1, 1, new Boolean[] {false, true}, true);
        AttemptAnswer g2 = new AttemptAnswer(a2.id(), attemptId, Q2, 3, new Boolean[] {true, false, false}, false);
        when(attemptRepo.findInQuizForUpdate(attemptId, QUIZ)).thenReturn(Optional.of(submitted));
        when(attemptRepo.findAnswersInPositionOrderForUpdate(attemptId)).thenReturn(List.of(g1, g2));

        AttemptResult repeated = attemptService.submit(COURSE, QUIZ, attemptId, new AnswersInput(List.of(
                new AnswerInput(Q1, List.of(true, false)),
                new AnswerInput(Q2, List.of(true, true, false)))));
        assertEquals(new BigDecimal("50.00"), repeated.scorePercentage());
        verify(attemptRepo, times(1)).save(any());
    }

    @Test
    void lateSaveAndSubmitAreRejectedAtExactExpiryBoundary() {
        UUID attemptId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-11-26T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-11-26T10:10:00Z");
        clock.advanceSeconds(600); // now == expiresAt -> expired

        Attempt overdue = new Attempt(attemptId, learnerId, QUIZ, 1, startedAt, expiresAt, null, null);
        when(quizRepo.findInCourseForUpdate(QUIZ, COURSE)).thenReturn(Optional.of(quiz("published")));
        when(quizRepo.findMembers(List.of(QUIZ))).thenReturn(Map.of(QUIZ, twoQuestions()));
        when(attemptRepo.findInQuizForUpdate(attemptId, QUIZ)).thenReturn(Optional.of(overdue));
        when(attemptRepo.findAnswersInPositionOrderForUpdate(attemptId)).thenReturn(List.of(
                new AttemptAnswer(UUID.randomUUID(), attemptId, Q1, 1, new Boolean[] {false, false}, null),
                new AttemptAnswer(UUID.randomUUID(), attemptId, Q2, 3, new Boolean[] {false, false, false}, null)));

        AnswersInput input = new AnswersInput(List.of(
                new AnswerInput(Q1, List.of(false, true)),
                new AnswerInput(Q2, List.of(true, true, false))));

        assertConflict(() -> attemptService.saveAnswers(COURSE, QUIZ, attemptId, input));
        assertConflict(() -> attemptService.submit(COURSE, QUIZ, attemptId, input));
        verify(attemptRepo, never()).save(any());
    }

    @Test
    void finalizeExpiredUsesLogicalExpiresAtAndSchedulerContinuesOnFailure() {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-00000000f001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-00000000f002");
        Instant startedAt = Instant.parse("2026-11-26T09:50:00Z");
        Instant expiresAt = Instant.parse("2026-11-26T10:00:00Z");
        clock.advanceSeconds(180);

        Attempt first = new Attempt(firstId, learnerId, QUIZ, 1, startedAt, expiresAt, null, null);
        Attempt second = new Attempt(secondId, learnerId, QUIZ, 2, startedAt, expiresAt, null, null);
        Quiz hiddenQuiz = quiz("hidden");

        when(attemptRepo.findDueUnsubmitted(Instant.parse("2026-11-26T10:03:00Z"), null, null, 10))
                .thenReturn(List.of(first, second));
        when(attemptRepo.findById(firstId)).thenReturn(Optional.empty());
        when(attemptRepo.findById(secondId)).thenReturn(Optional.of(second));
        when(quizRepo.findById(QUIZ)).thenReturn(Optional.of(hiddenQuiz));
        when(quizRepo.findInCourseForUpdate(QUIZ, COURSE)).thenReturn(Optional.of(hiddenQuiz));
        when(quizRepo.findMembers(List.of(QUIZ))).thenReturn(Map.of(QUIZ, twoQuestions()));
        when(attemptRepo.findInQuizForUpdate(secondId, QUIZ)).thenReturn(Optional.of(second));
        when(attemptRepo.findAnswersInPositionOrderForUpdate(secondId)).thenReturn(List.of(
                new AttemptAnswer(UUID.randomUUID(), secondId, Q1, 1, new Boolean[] {false, true}, null),
                new AttemptAnswer(UUID.randomUUID(), secondId, Q2, 3, new Boolean[] {true, true, false}, null)));
        when(attemptRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AttemptAutoSubmitScheduler scheduler =
                new AttemptAutoSubmitScheduler(attemptRepo, attemptService, 5000, 10, clock);
        assertEquals(2, scheduler.runPass());

        ArgumentCaptor<Attempt> captor = ArgumentCaptor.forClass(Attempt.class);
        verify(attemptRepo).save(captor.capture());
        assertEquals(secondId, captor.getValue().id());
        assertEquals(expiresAt, captor.getValue().submitted_at());
        assertEquals(new BigDecimal("100.00"), captor.getValue().score_percentage());
    }

    @Test
    void readResultRejectsUnsubmittedWith409AndHistoryShowsDerivedStatus() {
        UUID attemptId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-11-26T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-11-26T10:10:00Z");
        Attempt unsubmitted = new Attempt(attemptId, learnerId, QUIZ, 1, startedAt, expiresAt, null, null);

        when(quizRepo.findInCourse(QUIZ, COURSE)).thenReturn(Optional.of(quiz("published")));
        when(attemptRepo.findInQuizForLearner(attemptId, QUIZ, learnerId)).thenReturn(Optional.of(unsubmitted));

        assertConflict(() -> readTx.execute(status -> attemptService.readResult(COURSE, QUIZ, attemptId)));

        clock.advanceSeconds(601);
        when(quizRepo.findMembers(List.of(QUIZ))).thenReturn(Map.of(QUIZ, twoQuestions()));
        when(attemptRepo.findAnswersInPositionOrder(attemptId)).thenReturn(List.of(
                new AttemptAnswer(UUID.randomUUID(), attemptId, Q1, 1, new Boolean[] {false, false}, null),
                new AttemptAnswer(UUID.randomUUID(), attemptId, Q2, 3, new Boolean[] {false, false, false}, null)));
        AttemptRead read = readTx.execute(status -> attemptService.read(COURSE, QUIZ, attemptId));
        assertEquals("overdue", assertInstanceOf(AttemptView.class, read).status());

        when(attemptRepo.countByQuizAndLearner(QUIZ, learnerId)).thenReturn(1L);
        when(attemptRepo.findPageByQuizAndLearner(QUIZ, learnerId, 0, 20)).thenReturn(List.of(unsubmitted));
        PageResponse<AttemptSummary> page = readTx.execute(status -> attemptService.history(COURSE, QUIZ, 0, 20));
        assertEquals("overdue", page.content().getFirst().status());
        assertNull(page.content().getFirst().submittedAt());
        assertNull(page.content().getFirst().scorePercentage());

        doThrow(new ResourceNotFoundException("Denied")).when(access).requireLearnerCourseAccess(COURSE);
        assertThrows(ResourceNotFoundException.class, () -> attemptService.start(COURSE, QUIZ));
    }

    @Test
    void closedAnswerParserRejectsCoercionDuplicatesUnknownMembersAndTrailingTokens() {
        String valid = "{\"answers\":[{\"questionId\":\"" + Q1 + "\",\"selectedOptions\":[true,false]}]}";
        assertTrue(com.codemastery.modules.assessment.api.dto.SubmitAttemptRequest.AnswersWriteRequest.parse(
                valid.getBytes(java.nio.charset.StandardCharsets.UTF_8)).isPresent());
        for (String invalid : List.of(valid + " {}", valid.replace("true", "1"), valid.replace("true", "\"true\""),
                valid.replace("true", "null"), valid.replace(Q1.toString(), "1-1-1-1-1"),
                valid.replace("\"answers\":", "\"answers\":[],\"answers\":"),
                valid.replace("\"questionId\":", "\"extra\":true,\"questionId\":"), "null", "[]", "{", "")) {
            assertTrue(com.codemastery.modules.assessment.api.dto.SubmitAttemptRequest.AnswersWriteRequest.parse(
                    invalid.getBytes(java.nio.charset.StandardCharsets.UTF_8)).isEmpty(), invalid);
        }
    }

    @Test
    void assessmentCompanionAndGuardCallersStayInsideTheirApprovedAllowlist() throws Exception {
        var root = java.nio.file.Path.of("src", "main", "java");
        java.util.Map<String,java.util.Set<String>> allowed = java.util.Map.of(
                ".requireAssessmentSkillsInCourse(", java.util.Set.of("AttemptService.java"),
                ".withLearnerAssessmentWrite(", java.util.Set.of("AttemptService.java"),
                ".withAssessmentExpiryWrite(", java.util.Set.of("AttemptService.java"),
                ".finalizeExpired(", java.util.Set.of("AttemptAutoSubmitScheduler.java"),
                ".readLatestSkillEvidence(", java.util.Set.of("MasteryUpdateService.java"),
                ".readMyLatestSkillEvidence(", java.util.Set.of("MasteryQueryService.java"),
                ".readQuizOutcome(", java.util.Set.of("PlatformMetricsService.java", "AttemptService.java"),
                ".readMyPlacementQuizId(", java.util.Set.of("PlacementDiagnosticService.java"));
        try(var files=java.nio.file.Files.walk(root)) {
            for(var file:files.filter(path->path.toString().endsWith(".java")).toList()) {
                String code=java.nio.file.Files.readString(file);
                for(var entry:allowed.entrySet()) if(code.contains(entry.getKey())) {
                    assertTrue(entry.getValue().contains(file.getFileName().toString()), file+": "+entry.getKey());
                }
            }
        }
    }

    @Test
    void maxAttemptNumberAndRegressedClockCannotCreateOrMutateAnAttempt() {
        when(quizRepo.findInCourse(QUIZ, COURSE)).thenReturn(Optional.of(quiz("published")));
        when(quizRepo.findInCourseForUpdate(QUIZ, COURSE)).thenReturn(Optional.of(quiz("published")));
        when(quizRepo.findMembers(List.of(QUIZ))).thenReturn(Map.of(QUIZ, twoQuestions()));
        when(attemptRepo.findMaxAttemptNumber(QUIZ,learnerId)).thenReturn(Integer.MAX_VALUE);
        assertConflict(()->attemptService.start(COURSE,QUIZ));
        Attempt future = new Attempt(UUID.randomUUID(), learnerId, QUIZ, 1,
                clock.instant().plusSeconds(1),clock.instant().plusSeconds(600),null,null);
        when(attemptRepo.findUnsubmittedByQuizAndLearner(QUIZ,learnerId)).thenReturn(Optional.of(future));
        when(attemptRepo.findAnswersInPositionOrder(future.id())).thenReturn(List.of(
                new AttemptAnswer(UUID.randomUUID(),future.id(),Q1,1,new Boolean[]{false,false},null),
                new AttemptAnswer(UUID.randomUUID(),future.id(),Q2,3,new Boolean[]{false,false,false},null)));
        assertThrows(IllegalStateException.class,()->attemptService.start(COURSE,QUIZ));
        verify(attemptRepo,never()).save(any());
    }

    @Test
    void incorrectProviderScoreOrPerQuestionCorrectnessFailsBeforeAnyWrite() {
        UUID id=UUID.randomUUID();
        Attempt active=new Attempt(id,learnerId,QUIZ,1,clock.instant(),clock.instant().plusSeconds(600),null,null);
        when(quizRepo.findInCourseForUpdate(QUIZ,COURSE)).thenReturn(Optional.of(quiz("published")));
        when(quizRepo.findMembers(List.of(QUIZ))).thenReturn(Map.of(QUIZ,twoQuestions()));
        when(attemptRepo.findInQuizForUpdate(id,QUIZ)).thenReturn(Optional.of(active));
        when(attemptRepo.findAnswersInPositionOrderForUpdate(id)).thenReturn(List.of(
                new AttemptAnswer(UUID.randomUUID(),id,Q1,1,new Boolean[]{false,false},null),
                new AttemptAnswer(UUID.randomUUID(),id,Q2,3,new Boolean[]{false,false,false},null)));
        QuizGradingService provider=mock(QuizGradingService.class);
        when(provider.grade(anyList())).thenReturn(
                new QuizGradingService.Grade(new BigDecimal("100.00"),List.of(
                    new QuizGradingService.GradedItem(Q1,true),new QuizGradingService.GradedItem(Q2,false))),
                new QuizGradingService.Grade(new BigDecimal("50.00"),List.of(
                    new QuizGradingService.GradedItem(Q1,false),new QuizGradingService.GradedItem(Q2,true))));
        AttemptService checked=new AttemptService(attemptRepo,quizRepo,provider,access,identities,attemptGuard,attemptSkills,clock);
        AnswersInput input=new AnswersInput(List.of(new AnswerInput(Q1,List.of(false,true)),new AnswerInput(Q2,List.of(false,false,false))));
        assertThrows(IllegalStateException.class,()->checked.submit(COURSE,QUIZ,id,input));
        assertThrows(IllegalStateException.class,()->checked.submit(COURSE,QUIZ,id,input));
        verify(attemptRepo,never()).save(any());verify(attemptRepo,never()).updateAnswers(anyList());
    }

    @Test
    void executionOnlyReadRejectsBeforeAnyRepositoryAccess() {
        assertThrows(IllegalStateException.class,
                ()->attemptService.readLatestSkillEvidence(COURSE,learnerId,java.util.Set.of(SKILL)));
        verifyNoInteractions(attemptRepo,quizRepo);
    }

    @Test
    void failedEarliestCandidateCannotStarveEqualDeadlineKeysetSuccessors() {
        Instant due=clock.instant();
        Attempt first=new Attempt(UUID.fromString("00000000-0000-0000-0000-000000000001"),learnerId,QUIZ,1,
                due.minusSeconds(60),due,null,null);
        Attempt second=new Attempt(UUID.fromString("00000000-0000-0000-0000-000000000002"),learnerId,QUIZ,2,
                due.minusSeconds(60),due,null,null);
        AttemptService finalizer=mock(AttemptService.class);
        doThrow(new IllegalStateException("fixture diagnostic must not be logged")).when(finalizer).finalizeExpired(first.id());
        when(attemptRepo.findDueUnsubmitted(due,null,null,1)).thenReturn(List.of(first));
        when(attemptRepo.findDueUnsubmitted(due,due,first.id(),1)).thenReturn(List.of(second));
        when(attemptRepo.findDueUnsubmitted(due,due,second.id(),1)).thenReturn(List.of());
        assertEquals(1,new AttemptAutoSubmitScheduler(attemptRepo,finalizer,1,1,clock).runPass());
        verify(finalizer).finalizeExpired(second.id());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant initial) {
            this.instant = initial;
        }

        private void advanceSeconds(long seconds) {
            this.instant = this.instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
}
