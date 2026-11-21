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
    private final List<UUID> guardedCourses = new ArrayList<>();
    private List<UUID> courseSkills;
    private QuizService service;

    @BeforeEach
    void setUp() {
        quizzes = mock(QuizRepository.class);
        courseAccess = mock(CourseAccessService.class);
        guard = mock(CourseManagementService.class);
        courseSkills = List.of(SKILL);
        QuestionSelectionService selection = new QuestionSelectionService(quizzes) {
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
                    new QuizInput("Title\u0000", "practice", 60, List.of()),
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
            QuizService production = new QuizService(quizzes, new QuestionSelectionService(quizzes), courseAccess, guard);

            assertSame(concealed, assertThrows(ResourceNotFoundException.class, () -> production.create(COURSE, input(List.of()))));
            assertSame(concealed, assertThrows(ResourceNotFoundException.class, () -> production.update(COURSE, QUIZ, input(List.of()))));
            assertSame(concealed, assertThrows(ResourceNotFoundException.class, () -> production.delete(COURSE, QUIZ)));
            assertSame(concealed, assertThrows(ResourceNotFoundException.class, () -> production.publish(COURSE, QUIZ, true)));
            assertSame(concealed, assertThrows(ResourceNotFoundException.class, () -> production.publish(COURSE, QUIZ, false)));
            verify(guard, times(5)).withCourseConfigurationWrite(eq(COURSE), any());
            verifyNoInteractions(quizzes);
        }

        @Test
        void insideTheRealGuardWritesNeedingCt40StillFailClosedBeforeAnyWrite() {
            when(guard.withCourseConfigurationWrite(eq(COURSE), any())).thenAnswer(
                    invocation -> invocation.<Supplier<?>>getArgument(1).get());
            QuizService production = new QuizService(quizzes, new QuestionSelectionService(quizzes), courseAccess, guard);

            assertThrows(IllegalStateException.class, () -> production.create(COURSE, input(List.of(Q1))));
            verify(quizzes, never()).save(any());
            verify(quizzes, never()).replaceMembership(any(), any());
        }

        @Test
        void withoutCt40SelectionFailsClosedRatherThanSubstitutingAScope() {
            QuestionSelectionService unintegrated = new QuestionSelectionService(quizzes);

            assertThrows(IllegalStateException.class,
                    () -> unintegrated.requireOrderedCourseQuestions(COURSE, List.of(Q1)));
            assertThrows(IllegalStateException.class,
                    () -> unintegrated.requireCourseMembers(COURSE, List.of(question(Q1, SKILL))));
            verifyNoInteractions(quizzes);
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
}
