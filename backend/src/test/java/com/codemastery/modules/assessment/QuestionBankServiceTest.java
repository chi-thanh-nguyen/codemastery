package com.codemastery.modules.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.GlobalExceptionHandler;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.assessment.api.controller.QuestionBankController;
import com.codemastery.modules.assessment.api.dto.QuestionRequest;
import com.codemastery.modules.assessment.application.service.AttemptService;
import com.codemastery.modules.assessment.application.service.QuestionBankService;
import com.codemastery.modules.assessment.application.service.QuestionBankService.CoverageSnapshot;
import com.codemastery.modules.assessment.application.service.QuestionBankService.QuestionInput;
import com.codemastery.modules.assessment.application.service.QuestionBankService.QuestionView;
import com.codemastery.modules.assessment.application.service.QuizGradingService;
import com.codemastery.modules.assessment.domain.model.Attempt;
import com.codemastery.modules.assessment.domain.model.AttemptAnswer;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.model.Quiz;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService;
import com.codemastery.modules.assessment.domain.repository.AttemptRepository;
import com.codemastery.modules.assessment.domain.repository.QuestionRepository;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.assessment.domain.repository.QuizRepository.Member;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.CourseManagementService;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * CT23.questions/bindings unit evidence (ADR007 §§4, 7). The Course configuration guard (CO14 #177)
 * and the CT40 producer (AD07 #165) are integrated by delegation; {@code ProducerDelegation} proves
 * each seam reaches its producer. The remaining cases use a test subclass that emulates the approved
 * producer semantics; real-producer evidence is the PostgreSQL suites.
 */
class QuestionBankServiceTest {

    private QuestionRepository questions;
    private CourseAccessService access;
    private CourseManagementService guard;
    private SkillGraphService skillGraph;
    private UUID courseId;
    private UUID skillId;
    private UUID otherSkillId;
    private UUID foreignSkillId;
    private UUID questionId;
    private List<String> calls;
    private int guardDepth;

    @BeforeEach
    void setUp() {
        questions = mock(QuestionRepository.class);
        access = mock(CourseAccessService.class);
        guard = mock(CourseManagementService.class);
        skillGraph = mock(SkillGraphService.class);
        courseId = UUID.randomUUID();
        skillId = UUID.randomUUID();
        otherSkillId = UUID.randomUUID();
        foreignSkillId = UUID.randomUUID();
        questionId = UUID.randomUUID();
        calls = new ArrayList<>();
        guardDepth = 0;
    }

    /** Emulates CT40 and the Course guard with their approved semantics; records call order. */
    private QuestionBankService integrated() {
        return integrated(List.of(skillId, otherSkillId));
    }

    private QuestionBankService integrated(List<UUID> courseSkills) {
        return new QuestionBankService(questions, access, guard, skillGraph) {
            @Override
            protected void requireSkillInCourse(UUID course, UUID skill) {
                calls.add("requireSkillInCourse");
                if (!course.equals(courseId) || !courseSkills.contains(skill)) {
                    throw new ResourceNotFoundException("Skill not available");
                }
            }

            @Override
            protected List<UUID> readSkillIds(UUID course) {
                calls.add("readSkillIds");
                return course.equals(courseId) ? courseSkills : List.of();
            }

            @Override
            protected <T> T withCourseConfigurationWrite(UUID course, Supplier<T> work) {
                calls.add("guard");
                guardDepth++;
                try {
                    return work.get();
                } finally {
                    guardDepth--;
                }
            }
        };
    }

    private QuestionInput input(String kind, List<String> options, List<Boolean> key) {
        return new QuestionInput(skillId, "Predict the output", kind, "easy", options, key);
    }

    private QuestionInput validInput() {
        return input("single_choice", List.of("1", "2"), List.of(true, false));
    }

    private Question stored(UUID id, UUID skill) {
        return new Question(id, skill, "Sample prompt", "single_choice", "medium",
                new String[]{"Option A", "Option B"}, new Boolean[]{false, true});
    }

    private void answerSaveInsideGuard() {
        when(questions.save(any(Question.class))).thenAnswer(invocation -> {
            assertTrue(guardDepth > 0, "writes must run inside the Course guard");
            calls.add("save");
            return invocation.getArgument(0);
        });
    }

    private void lockReturns(Question question) {
        when(questions.findInSkillsForUpdate(eq(questionId), anyCollection())).thenAnswer(invocation -> {
            assertTrue(guardDepth > 0, "the lifecycle row lock must be taken inside the Course guard");
            assertEquals(List.of(skillId, otherSkillId), List.copyOf(invocation.<java.util.Collection<UUID>>getArgument(1)));
            calls.add("lock");
            return Optional.ofNullable(question);
        });
    }

    private static HttpStatus statusOf(BusinessRuleException exception) {
        return exception.getStatus();
    }

    @Nested
    class ProducerDelegation {

        private QuestionBankService production() {
            when(guard.withCourseConfigurationWrite(eq(courseId), any())).thenAnswer(invocation -> {
                guardDepth++;
                try {
                    return invocation.<Supplier<?>>getArgument(1).get();
                } finally {
                    guardDepth--;
                }
            });
            return new QuestionBankService(questions, access, guard, skillGraph);
        }

        @Test
        void createValidatesTheSkillThroughCt40InsideTheGuardBeforeSaving() {
            QuestionBankService production = production();
            answerSaveInsideGuard();
            org.mockito.Mockito.doAnswer(invocation -> {
                assertTrue(guardDepth > 0, "the CT40 tag check runs under the held Course root");
                calls.add("requireSkillInCourse");
                return null;
            }).when(skillGraph).requireSkillInCourse(courseId, skillId);

            production.create(courseId, validInput());

            assertEquals(List.of("requireSkillInCourse", "save"), calls);
        }

        @Test
        void aSkillOutsideTheCourseIsTheProducersConcealed404AndNothingIsSaved() {
            QuestionBankService production = production();
            ResourceNotFoundException concealed = new ResourceNotFoundException("Resource not available");
            doThrow(concealed).when(skillGraph).requireSkillInCourse(courseId, skillId);

            assertSame(concealed, assertThrows(ResourceNotFoundException.class, () -> production.create(courseId, validInput())));
            verify(questions, never()).save(any());
        }

        @Test
        void scopedOperationsUseTheProducersCompleteSkillSet() {
            QuestionBankService production = production();
            when(skillGraph.readSkillIds(courseId)).thenReturn(List.of(skillId, otherSkillId));
            lockReturns(stored(questionId, skillId));
            answerSaveInsideGuard();
            when(questions.findInSkills(eq(questionId), anyCollection())).thenReturn(Optional.of(stored(questionId, skillId)));

            production.update(courseId, questionId, validInput());
            production.read(courseId, questionId);

            verify(skillGraph, org.mockito.Mockito.times(2)).readSkillIds(courseId);
            verify(skillGraph).requireSkillInCourse(courseId, skillId);
            verify(questions).findInSkills(questionId, List.of(skillId, otherSkillId));
        }

        @Test
        void anInconsistentProducerProjectionIsAServerErrorNeverAWiderScope() {
            QuestionBankService production = production();
            when(skillGraph.readSkillIds(courseId)).thenReturn(List.of(skillId, skillId));

            assertThrows(IllegalStateException.class, () -> production.read(courseId, questionId));
            assertThrows(IllegalStateException.class, () -> production.delete(courseId, questionId));
            verifyNoInteractions(questions);
        }

        @Test
        void everyMutationDelegatesToTheCo14GuardAndItsRefusalPrecedesAnyBankAccess() {
            ResourceNotFoundException concealed = new ResourceNotFoundException("Resource not available");
            when(guard.withCourseConfigurationWrite(eq(courseId), any())).thenThrow(concealed);
            QuestionBankService production = new QuestionBankService(questions, access, guard, skillGraph);
            List<Executable> mutations = List.of(
                    () -> production.create(courseId, validInput()),
                    () -> production.update(courseId, questionId, validInput()),
                    () -> production.delete(courseId, questionId));
            for (Executable mutation : mutations) {
                assertSame(concealed, assertThrows(ResourceNotFoundException.class, mutation));
            }
            verify(guard, org.mockito.Mockito.times(3)).withCourseConfigurationWrite(eq(courseId), any());
            verifyNoInteractions(questions);
        }

        @Test
        void invalidDefinitionIsStill400BeforeAnyProducerIsReached() {
            QuestionBankService production = new QuestionBankService(questions, access, guard, skillGraph);
            BusinessRuleException invalid = assertThrows(BusinessRuleException.class,
                    () -> production.create(courseId, input("essay", List.of("A", "B"), List.of(true, false))));
            assertEquals(HttpStatus.BAD_REQUEST, statusOf(invalid));
            verifyNoInteractions(questions, guard, skillGraph);
        }

        @Test
        void mutationsDeclareNoOwnTransactionAndReadsUseOneRepeatableReadSnapshot() throws Exception {
            for (String name : List.of("create", "update", "delete")) {
                Method method = Arrays.stream(QuestionBankService.class.getMethods())
                        .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
                assertNull(method.getAnnotation(Transactional.class),
                        name + " must leave the transaction to the Course configuration guard");
            }
            for (String name : List.of("read", "list")) {
                Transactional tx = Arrays.stream(QuestionBankService.class.getMethods())
                        .filter(m -> m.getName().equals(name)).findFirst().orElseThrow()
                        .getAnnotation(Transactional.class);
                assertNotNull(tx);
                assertTrue(tx.readOnly());
                assertEquals(Isolation.REPEATABLE_READ, tx.isolation());
            }
        }
    }

    @Nested
    class Admission {

        @Test
        void deniedOwnershipStopsEveryOperationBeforeValidationScopeOrPersistence() {
            doThrow(new AccessDeniedException("Access denied")).when(access).requireCourseOwnership(courseId);
            QuestionBankService service = integrated();
            QuestionInput invalid = input("essay", List.of("A"), List.of(true));
            List<Executable> operations = List.of(
                    () -> service.create(courseId, invalid),
                    () -> service.update(courseId, questionId, invalid),
                    () -> service.delete(courseId, questionId),
                    () -> service.read(courseId, questionId),
                    () -> service.list(courseId, 0, 20));
            for (Executable operation : operations) {
                assertThrows(AccessDeniedException.class, operation);
            }
            assertTrue(calls.isEmpty());
            verifyNoInteractions(questions);
        }

        @Test
        void nullIdentifiersAreInternalErrors() {
            QuestionBankService service = integrated();
            assertThrows(IllegalArgumentException.class, () -> service.read(null, questionId));
            assertThrows(IllegalArgumentException.class, () -> service.delete(courseId, null));
            verifyNoInteractions(access, questions);
        }
    }

    @Nested
    class Validation {

        static Stream<Arguments> invalidDefinitions() {
            UUID skill = UUID.randomUUID();
            List<String> ab = List.of("A", "B");
            List<Boolean> tf = List.of(true, false);
            return Stream.of(
                    Arguments.of("blank prompt", new QuestionInput(skill, " \t\n", "single_choice", "easy", ab, tf)),
                    Arguments.of("Unicode-space prompt", new QuestionInput(skill, "  ", "single_choice", "easy", ab, tf)),
                    // Blank as in CO06/AD07: U+FEFF (OpenAPI's ECMA-262 \s), U+0085 (PostgreSQL), U+001C (Java).
                    Arguments.of("U+FEFF-only prompt", new QuestionInput(skill, "\uFEFF", "single_choice", "easy", ab, tf)),
                    Arguments.of("U+0085-only prompt", new QuestionInput(skill, "\u0085", "single_choice", "easy", ab, tf)),
                    Arguments.of("U+FEFF and U+001C option", new QuestionInput(skill, "P", "single_choice", "easy", List.of("A", "\uFEFF\u001C"), tf)),
                    Arguments.of("NUL in prompt", new QuestionInput(skill, "a\0b", "single_choice", "easy", ab, tf)),
                    Arguments.of("unpaired high surrogate in prompt", new QuestionInput(skill, "a\uD800", "single_choice", "easy", ab, tf)),
                    Arguments.of("unpaired low surrogate option", new QuestionInput(skill, "P", "single_choice", "easy", List.of("A", "\uDC00"), tf)),
                    Arguments.of("null prompt", new QuestionInput(skill, null, "single_choice", "easy", ab, tf)),
                    Arguments.of("null skill", new QuestionInput(null, "P", "single_choice", "easy", ab, tf)),
                    Arguments.of("unknown kind", new QuestionInput(skill, "P", "essay", "easy", ab, tf)),
                    Arguments.of("case-changed kind", new QuestionInput(skill, "P", "Single_choice", "easy", ab, tf)),
                    Arguments.of("unknown difficulty", new QuestionInput(skill, "P", "single_choice", "extreme", ab, tf)),
                    Arguments.of("case-changed difficulty", new QuestionInput(skill, "P", "single_choice", "Easy", ab, tf)),
                    Arguments.of("one option", new QuestionInput(skill, "P", "single_choice", "easy", List.of("A"), List.of(true))),
                    Arguments.of("length mismatch", new QuestionInput(skill, "P", "single_choice", "easy", List.of("A", "B", "C"), tf)),
                    Arguments.of("blank option", new QuestionInput(skill, "P", "single_choice", "easy", List.of("A", " "), tf)),
                    Arguments.of("null option", new QuestionInput(skill, "P", "single_choice", "easy", Arrays.asList("A", null), tf)),
                    Arguments.of("null key element", new QuestionInput(skill, "P", "single_choice", "easy", ab, Arrays.asList(true, null))),
                    Arguments.of("null options", new QuestionInput(skill, "P", "single_choice", "easy", null, tf)),
                    Arguments.of("lower-case true_false labels", new QuestionInput(skill, "P", "true_false", "easy", List.of("true", "false"), tf)),
                    Arguments.of("reordered true_false labels", new QuestionInput(skill, "P", "true_false", "easy", List.of("False", "True"), tf)),
                    Arguments.of("single_choice with two keys", new QuestionInput(skill, "P", "single_choice", "easy", ab, List.of(true, true))),
                    Arguments.of("single_choice without key", new QuestionInput(skill, "P", "single_choice", "easy", ab, List.of(false, false))),
                    Arguments.of("true_false with two keys", new QuestionInput(skill, "P", "true_false", "easy", List.of("True", "False"), List.of(true, true))),
                    Arguments.of("multiple_choice without key", new QuestionInput(skill, "P", "multiple_choice", "easy", ab, List.of(false, false))),
                    Arguments.of("null input", null));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidDefinitions")
        void invalidDefinitionsAre400WithNoScopeLockOrWrite(String scenario, QuestionInput invalid) {
            QuestionBankService service = integrated();
            for (Executable operation : List.<Executable>of(() -> service.create(courseId, invalid),
                    () -> service.update(courseId, questionId, invalid))) {
                BusinessRuleException exception = assertThrows(BusinessRuleException.class, operation);
                assertEquals(HttpStatus.BAD_REQUEST, statusOf(exception));
            }
            assertTrue(calls.isEmpty());
            verifyNoInteractions(questions);
        }

        @Test
        void everyApprovedKindAndDifficultyIsAcceptedWithoutTrimming() {
            answerSaveInsideGuard();
            QuestionBankService service = integrated();
            List<QuestionInput> valid = List.of(
                    new QuestionInput(skillId, "  keep  spaces ", "single_choice", "easy", List.of(" a ", "b"), List.of(false, true)),
                    new QuestionInput(skillId, "Select all", "multiple_choice", "medium", List.of("a", "b", "c"), List.of(true, false, true)),
                    new QuestionInput(skillId, "Statement", "true_false", "hard", List.of("True", "False"), List.of(false, true)),
                    new QuestionInput(skillId, "Emoji \uD83D\uDE00 pair", "single_choice", "easy", List.of("\uD83D\uDE00", "b"), List.of(true, false)),
                    new QuestionInput(skillId, "\uFEFF\u0085Kept\uFEFF", "single_choice", "easy", List.of("\uFEFFa", "b"), List.of(true, false)));
            for (QuestionInput definition : valid) {
                QuestionView view = service.create(courseId, definition);
                assertEquals(definition.prompt(), view.prompt());
                assertEquals(definition.options(), view.options());
                assertEquals(definition.correctOptions(), view.correctOptions());
                assertEquals(definition.kind(), view.kind());
                assertEquals(definition.difficulty(), view.difficulty());
            }
        }
    }

    @Nested
    class Create {

        @Test
        void createValidatesTheSkillAndWritesInsideTheGuardWithANewIdentifier() {
            answerSaveInsideGuard();
            QuestionView view = integrated().create(courseId, validInput());

            assertNotNull(view.id());
            assertEquals(skillId, view.skillId());
            assertEquals(List.of(true, false), view.correctOptions());
            assertEquals(List.of("guard", "requireSkillInCourse", "save"), calls);
            verify(access).requireCourseOwnership(courseId);
        }

        @Test
        void foreignOrMissingSkillIsTheSameSafe404WithoutAWrite() {
            QuestionInput foreign = new QuestionInput(foreignSkillId, "P", "single_choice", "easy",
                    List.of("A", "B"), List.of(true, false));
            assertThrows(ResourceNotFoundException.class, () -> integrated().create(courseId, foreign));
            verify(questions, never()).save(any());
        }

        @Test
        void inputListsAreCopiedOnEntry() {
            List<String> options = new ArrayList<>(List.of("A", "B"));
            QuestionInput copied = new QuestionInput(skillId, "P", "single_choice", "easy", options, List.of(true, false));
            options.set(0, "changed");
            assertEquals(List.of("A", "B"), copied.options());
            assertThrows(UnsupportedOperationException.class, () -> copied.options().add("C"));
        }
    }

    @Nested
    class Replace {

        @Test
        void quizLinkedButUnansweredQuestionIsReplacedAtTheSameIdentifier() {
            lockReturns(stored(questionId, skillId));
            when(questions.isReferencedByAttemptAnswer(questionId)).thenReturn(false);
            answerSaveInsideGuard();
            QuestionInput replacement = new QuestionInput(otherSkillId, "Updated prompt", "true_false", "hard",
                    List.of("True", "False"), List.of(false, true));

            QuestionView view = integrated().update(courseId, questionId, replacement);

            assertEquals(questionId, view.id());
            assertEquals(otherSkillId, view.skillId());
            assertEquals("Updated prompt", view.prompt());
            assertEquals(List.of(false, true), view.correctOptions());
            assertEquals(List.of("guard", "readSkillIds", "lock", "requireSkillInCourse", "save"), calls);
            // A QuizQuestion reference alone never freezes a definition (ADR007 §4).
            verify(questions, never()).isReferencedByQuizQuestion(any());
        }

        @Test
        void anyAttemptAnswerReferenceFreezesTheDefinitionWith409() {
            lockReturns(stored(questionId, skillId));
            when(questions.isReferencedByAttemptAnswer(questionId)).thenReturn(true);

            BusinessRuleException conflict = assertThrows(BusinessRuleException.class,
                    () -> integrated().update(courseId, questionId, validInput()));
            assertEquals(HttpStatus.CONFLICT, statusOf(conflict));
            verify(questions, never()).save(any());
        }

        @Test
        void missingOrForeignQuestionIs404AfterAdmission() {
            lockReturns(null);
            assertThrows(ResourceNotFoundException.class, () -> integrated().update(courseId, questionId, validInput()));
            verify(questions, never()).save(any());
            verify(questions, never()).isReferencedByAttemptAnswer(any());
        }

        @Test
        void retaggingToAForeignSkillIs404WithoutAWrite() {
            lockReturns(stored(questionId, skillId));
            QuestionInput foreign = new QuestionInput(foreignSkillId, "P", "single_choice", "easy",
                    List.of("A", "B"), List.of(true, false));
            assertThrows(ResourceNotFoundException.class, () -> integrated().update(courseId, questionId, foreign));
            verify(questions, never()).save(any());
        }
    }

    @Nested
    class Delete {

        @Test
        void unreferencedQuestionIsDeletedAndItsDefinitionReturned() {
            lockReturns(stored(questionId, skillId));
            when(questions.isReferencedByQuizQuestion(questionId)).thenReturn(false);
            when(questions.isReferencedByAttemptAnswer(questionId)).thenReturn(false);

            QuestionView deleted = integrated().delete(courseId, questionId);

            assertEquals(questionId, deleted.id());
            assertEquals(List.of("Option A", "Option B"), deleted.options());
            verify(questions).delete(questionId);
            assertEquals(List.of("guard", "readSkillIds", "lock"), calls);
        }

        @ParameterizedTest
        @ValueSource(strings = {"quiz", "attempt", "both"})
        void anyQuizQuestionOrAttemptAnswerReferencePreventsDeletionWith409(String reference) {
            lockReturns(stored(questionId, skillId));
            org.mockito.Mockito.lenient().when(questions.isReferencedByQuizQuestion(questionId))
                    .thenReturn(!reference.equals("attempt"));
            org.mockito.Mockito.lenient().when(questions.isReferencedByAttemptAnswer(questionId))
                    .thenReturn(!reference.equals("quiz"));

            BusinessRuleException conflict = assertThrows(BusinessRuleException.class,
                    () -> integrated().delete(courseId, questionId));
            assertEquals(HttpStatus.CONFLICT, statusOf(conflict));
            verify(questions, never()).delete(any());
        }

        @Test
        void missingOrForeignQuestionIs404() {
            lockReturns(null);
            assertThrows(ResourceNotFoundException.class, () -> integrated().delete(courseId, questionId));
            verify(questions, never()).delete(any());
        }
    }

    @Nested
    class Reads {

        @Test
        void ownerReadIsScopedToTheCompleteCourseSkillSetAndIncludesTheKey() {
            when(questions.findInSkills(questionId, List.of(skillId, otherSkillId)))
                    .thenReturn(Optional.of(stored(questionId, skillId)));
            QuestionView view = integrated().read(courseId, questionId);
            assertEquals(List.of(false, true), view.correctOptions());
            assertEquals(List.of("readSkillIds"), calls);
        }

        @Test
        void foreignOrMissingQuestionIsTheSameSafe404() {
            when(questions.findInSkills(eq(questionId), anyCollection())).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> integrated().read(courseId, questionId));
        }

        @Test
        void viewsAreDeeplyImmutableCopiesOfTheStoredArrays() {
            Question row = stored(questionId, skillId);
            when(questions.findInSkills(eq(questionId), anyCollection())).thenReturn(Optional.of(row));
            QuestionView view = integrated().read(courseId, questionId);
            row.options()[0] = "mutated";
            row.correct_options()[0] = true;
            assertEquals(List.of("Option A", "Option B"), view.options());
            assertEquals(List.of(false, true), view.correctOptions());
            assertThrows(UnsupportedOperationException.class, () -> view.correctOptions().set(0, true));
        }

        @Test
        void malformedStoredDefinitionIsAnIntegrityFailureNotAnOmission() {
            Question malformed = new Question(questionId, skillId, "P", "single_choice", "easy",
                    new String[]{"A", "B"}, new Boolean[]{true, true});
            when(questions.findInSkills(eq(questionId), anyCollection())).thenReturn(Optional.of(malformed));
            assertThrows(IllegalStateException.class, () -> integrated().read(courseId, questionId));
        }

        @Test
        void listReturnsTheRepositoryOrderWithCoherentMetadata() {
            UUID low = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
            UUID high = UUID.fromString("80000000-0000-0000-0000-000000000000");
            when(questions.countInSkills(List.of(skillId, otherSkillId))).thenReturn(5L);
            when(questions.findPageInSkills(List.of(skillId, otherSkillId), 1, 2))
                    .thenReturn(List.of(stored(low, skillId), stored(high, otherSkillId)));

            PageResponse<QuestionView> page = integrated().list(courseId, 1, 2);

            assertEquals(List.of(low, high), page.content().stream().map(QuestionView::id).toList());
            assertEquals(1, page.page());
            assertEquals(2, page.size());
            assertEquals(5, page.totalElements());
            assertEquals(3, page.totalPages());
        }

        @Test
        void pagesPastTheEndAndUnrepresentableOffsetsAreEmptyWithoutARowQuery() {
            when(questions.countInSkills(anyCollection())).thenReturn(3L);
            for (int[] request : new int[][]{{1, 3}, {5, 20}, {Integer.MAX_VALUE, 100}, {21474837, 100}}) {
                PageResponse<QuestionView> page = integrated().list(courseId, request[0], request[1]);
                assertTrue(page.content().isEmpty());
                assertEquals(3, page.totalElements());
            }
            verify(questions, never()).findPageInSkills(anyCollection(), anyInt(), anyInt());
        }

        @Test
        void courseWithoutSkillsHasAnEmptyBank() {
            when(questions.countInSkills(List.of())).thenReturn(0L);
            PageResponse<QuestionView> page = integrated(List.of()).list(courseId, 0, 20);
            assertEquals(0, page.totalElements());
            assertEquals(0, page.totalPages());
        }

        @ParameterizedTest
        @ValueSource(strings = {"-1,20", "0,0", "0,101"})
        void invalidPageRequestsAre400(String request) {
            String[] parts = request.split(",");
            BusinessRuleException invalid = assertThrows(BusinessRuleException.class,
                    () -> integrated().list(courseId, Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            assertEquals(HttpStatus.BAD_REQUEST, statusOf(invalid));
            verifyNoInteractions(questions);
        }

        @Test
        void invalidCt40ProjectionIsAProviderFailure() {
            UUID duplicate = UUID.randomUUID();
            assertThrows(IllegalStateException.class,
                    () -> integrated(List.of(duplicate, duplicate)).list(courseId, 0, 20));
            assertThrows(IllegalStateException.class,
                    () -> integrated(Arrays.asList(duplicate, null)).read(courseId, questionId));
            verifyNoInteractions(questions);
        }
    }

    @Nested
    class ClosedRequestBody {

        private static final String SKILL = "0b7f9f7e-6a43-4e0b-9a59-3a1f2c4d5e6f";

        private static String body(String skillId, String prompt, String kind, String difficulty,
                                   String options, String key) {
            return "{\"skillId\":" + skillId + ",\"prompt\":" + prompt + ",\"kind\":" + kind
                    + ",\"difficulty\":" + difficulty + ",\"options\":" + options + ",\"correctOptions\":" + key + "}";
        }

        @Test
        void exactClosedObjectParsesWithoutCoercionOrTrimming() {
            QuestionRequest request = QuestionRequest.parse(body("\"" + SKILL + "\"", "\" P \"", "\"single_choice\"",
                    "\"easy\"", "[\"A\",\" B\"]", "[true,false]")).orElseThrow();
            assertEquals(UUID.fromString(SKILL), request.skillId());
            assertEquals(" P ", request.prompt());
            assertEquals(List.of("A", " B"), request.options());
            assertEquals(List.of(true, false), request.correctOptions());
        }

        static Stream<Arguments> rejectedBodies() {
            String s = "\"" + SKILL + "\"";
            String valid = body(s, "\"P\"", "\"single_choice\"", "\"easy\"", "[\"A\",\"B\"]", "[true,false]");
            return Stream.of(
                    Arguments.of("unknown member", valid.replace("}", ",\"extra\":1}")),
                    Arguments.of("duplicate member", valid.replace("\"prompt\":\"P\"", "\"prompt\":\"P\",\"prompt\":\"Q\"")),
                    Arguments.of("missing member", valid.replace(",\"difficulty\":\"easy\"", "")),
                    Arguments.of("null member", body(s, "null", "\"single_choice\"", "\"easy\"", "[\"A\",\"B\"]", "[true,false]")),
                    Arguments.of("number prompt", body(s, "123", "\"single_choice\"", "\"easy\"", "[\"A\",\"B\"]", "[true,false]")),
                    Arguments.of("boolean kind", body(s, "\"P\"", "true", "\"easy\"", "[\"A\",\"B\"]", "[true,false]")),
                    Arguments.of("string mask", body(s, "\"P\"", "\"single_choice\"", "\"easy\"", "[\"A\",\"B\"]", "[\"true\",\"false\"]")),
                    Arguments.of("integer mask", body(s, "\"P\"", "\"single_choice\"", "\"easy\"", "[\"A\",\"B\"]", "[1,0]")),
                    Arguments.of("null mask element", body(s, "\"P\"", "\"single_choice\"", "\"easy\"", "[\"A\",\"B\"]", "[true,null]")),
                    Arguments.of("number options", body(s, "\"P\"", "\"single_choice\"", "\"easy\"", "[1,2]", "[true,false]")),
                    Arguments.of("scalar options", body(s, "\"P\"", "\"single_choice\"", "\"easy\"", "\"A\"", "[true,false]")),
                    Arguments.of("object options", body(s, "\"P\"", "\"single_choice\"", "\"easy\"", "{\"a\":1}", "[true,false]")),
                    Arguments.of("non-UUID skill", body("\"nope\"", "\"P\"", "\"single_choice\"", "\"easy\"", "[\"A\",\"B\"]", "[true,false]")),
                    Arguments.of("non-canonical UUID skill", body("\"1-1-1-1-1\"", "\"P\"", "\"single_choice\"", "\"easy\"", "[\"A\",\"B\"]", "[true,false]")),
                    Arguments.of("number skill", body("42", "\"P\"", "\"single_choice\"", "\"easy\"", "[\"A\",\"B\"]", "[true,false]")),
                    Arguments.of("array body", "[" + valid + "]"),
                    Arguments.of("trailing tokens", valid + " {}"),
                    Arguments.of("truncated JSON", valid.substring(0, valid.length() - 2)),
                    Arguments.of("empty body", ""),
                    Arguments.of("JSON null", "null"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("rejectedBodies")
        void anythingButTheExactClosedShapeIsRejected(String scenario, String json) {
            assertTrue(QuestionRequest.parse(json).isEmpty(), scenario);
        }

        @Test
        void nullBodyIsRejected() {
            assertTrue(QuestionRequest.parse(null).isEmpty());
        }
    }

    /** Controller precedence and cache policy with the shared CT18 advice; the full chain is QA01's IT. */
    @Nested
    class Controller {

        private QuestionBankService service;
        private MockMvc mvc;
        private final String course = UUID.randomUUID().toString();
        private final String question = UUID.randomUUID().toString();

        @BeforeEach
        void mvc() {
            service = mock(QuestionBankService.class);
            mvc = MockMvcBuilders.standaloneSetup(new QuestionBankController(service, access))
                    .setControllerAdvice(new GlobalExceptionHandler(), new QuestionBankController.QuestionBankCacheAdvice())
                    .build();
        }

        private String validBody() {
            return "{\"skillId\":\"" + skillId + "\",\"prompt\":\"P\",\"kind\":\"single_choice\",\"difficulty\":\"easy\","
                    + "\"options\":[\"A\",\"B\"],\"correctOptions\":[true,false]}";
        }

        @Test
        void createReturns201WithNoStoreAndTheOwnerRepresentation() throws Exception {
            when(service.create(eq(UUID.fromString(course)), any())).thenAnswer(invocation -> {
                QuestionInput in = invocation.getArgument(1);
                return new QuestionView(UUID.fromString(question), in.skillId(), in.prompt(), in.kind(),
                        in.difficulty(), in.options(), in.correctOptions());
            });
            mvc.perform(post("/api/courses/{c}/questions", course).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.id").value(question))
                    .andExpect(jsonPath("$.correctOptions[0]").value(true))
                    .andExpect(jsonPath("$.length()").value(7));
        }

        @Test
        void listAcceptsOnlyPageAndSizeAndRendersTheB3QuestionPage() throws Exception {
            when(service.list(UUID.fromString(course), 0, 20)).thenReturn(new PageResponse<>(List.of(), 0, 20, 0));
            mvc.perform(get("/api/courses/{c}/questions", course))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.length()").value(5))
                    .andExpect(jsonPath("$.totalPages").value(0));
            for (String query : List.of("skillId=" + skillId, "difficulty=easy", "page=-1", "page=a", "size=0",
                    "size=101", "page=2147483648", "page=0&page=1", "page=%2B1")) {
                mvc.perform(get("/api/courses/{c}/questions?" + query, course))
                        .andExpect(status().isBadRequest())
                        .andExpect(header().string("Cache-Control", "no-store"));
            }
            verify(service).list(UUID.fromString(course), 0, 20);
        }

        @Test
        void malformedIdentifiersAndUnexpectedQueriesAre400BeforeAdmission() throws Exception {
            mvc.perform(get("/api/courses/not-a-uuid/questions/{q}", question)).andExpect(status().isBadRequest());
            mvc.perform(get("/api/courses/{c}/questions/1-1-1-1-1", course)).andExpect(status().isBadRequest());
            mvc.perform(delete("/api/courses/{c}/questions/{q}?force=true", course, question)).andExpect(status().isBadRequest());
            verifyNoInteractions(service, access);
        }

        @Test
        void malformedBodyIsReportedOnlyAfterCourseAdmission() throws Exception {
            mvc.perform(put("/api/courses/{c}/questions/{q}", course, question).contentType(MediaType.APPLICATION_JSON)
                            .content(validBody().replace("}", ",\"extra\":true}")))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"));
            verify(access).requireCourseOwnership(UUID.fromString(course));

            doThrow(new ResourceNotFoundException("hidden")).when(access).requireCourseOwnership(UUID.fromString(course));
            mvc.perform(post("/api/courses/{c}/questions", course).contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("Cache-Control", "no-store"));
            verifyNoInteractions(service);
        }

        @Test
        void nonJsonMediaTypesAreB3Invalid400AfterAdmissionAndConflictsAre409WithNoStore() throws Exception {
            for (MediaType type : List.of(MediaType.TEXT_PLAIN, MediaType.APPLICATION_XML, MediaType.APPLICATION_PROBLEM_JSON)) {
                mvc.perform(post("/api/courses/{c}/questions", course).contentType(type).content(validBody()))
                        .andExpect(status().isBadRequest())
                        .andExpect(header().string("Cache-Control", "no-store"));
            }
            mvc.perform(post("/api/courses/{c}/questions", course).content(validBody()))
                    .andExpect(status().isBadRequest());
            verify(access, org.mockito.Mockito.times(4)).requireCourseOwnership(UUID.fromString(course));
            verify(service, never()).create(any(), any());
            when(service.delete(UUID.fromString(course), UUID.fromString(question)))
                    .thenThrow(new BusinessRuleException(HttpStatus.CONFLICT, "referenced"));
            mvc.perform(delete("/api/courses/{c}/questions/{q}", course, question))
                    .andExpect(status().isConflict())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }

        @Test
        void missingProviderIsASafeGeneric500WithNoStore() throws Exception {
            when(service.read(any(), any())).thenThrow(new IllegalStateException("CT40 ... not implemented"));
            String body = mvc.perform(get("/api/courses/{c}/questions/{q}", course, question))
                    .andExpect(status().isInternalServerError())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("CT40"));
            assertNotEquals(-1, body.indexOf("An unexpected error occurred."));
        }
    }

    /**
     * AS18 (#164) local evidence for CT40.coverage (ADR005 §3.3) and the ADR011 §§4/12 execution-only
     * companion. Transactions come from a recording test manager; real Spring/PostgreSQL behavior is
     * {@code AssessmentPersistenceIntegrationTest$TimedAttemptRuntime}.
     */
    @Nested
    class As18CoverageLookup {

        private final org.springframework.transaction.PlatformTransactionManager txManager =
                new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
                    @Override protected Object doGetTransaction() { return new Object(); }
                    @Override protected void doBegin(Object transaction,
                            org.springframework.transaction.TransactionDefinition definition) {}
                    @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {}
                    @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {}
                };

        private <T> T inTx(boolean readOnly, int isolation, int propagation, Supplier<T> work) {
            var tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
            tx.setReadOnly(readOnly);
            tx.setIsolationLevel(isolation);
            tx.setPropagationBehavior(propagation);
            return tx.execute(status -> work.get());
        }

        private <T> T ownerSnapshot(Supplier<T> work) {
            return inTx(true, org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ,
                    org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED, work);
        }

        private <T> T courseRootUnit(Supplier<T> work) {
            return inTx(false, org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED,
                    org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED, work);
        }

        private Question valid(UUID skill, String kind, String difficulty) {
            return switch (kind) {
                case "true_false" -> new Question(UUID.randomUUID(), skill, "TF", kind, difficulty,
                        new String[] {"True", "False"}, new Boolean[] {false, true});
                case "multiple_choice" -> new Question(UUID.randomUUID(), skill, "MC", kind, difficulty,
                        new String[] {"A", "B", "C"}, new Boolean[] {true, false, true});
                default -> new Question(UUID.randomUUID(), skill, "SC", kind, difficulty,
                        new String[] {"A", "B"}, new Boolean[] {true, false});
            };
        }

        @Test
        void coverageSnapshotIsNestedDeeplyImmutableAndEnforcesInvariantsWithoutAnswerKeys() {
            assertEquals(QuestionBankService.class, CoverageSnapshot.class.getEnclosingClass());
            assertEquals(List.of("UUID courseId", "Map questionCounts"),
                    Arrays.stream(CoverageSnapshot.class.getRecordComponents())
                            .map(c -> c.getType().getSimpleName() + " " + c.getName())
                            .toList());
            assertEquals("java.util.Map<java.util.UUID, java.lang.Long>",
                    CoverageSnapshot.class.getRecordComponents()[1].getGenericType().getTypeName());

            Map<UUID, Long> mutable = new HashMap<>();
            mutable.put(skillId, 2L);
            mutable.put(otherSkillId, 0L);
            CoverageSnapshot snapshot = new CoverageSnapshot(courseId, mutable);
            mutable.put(foreignSkillId, 9L);

            assertEquals(courseId, snapshot.courseId());
            assertEquals(Map.of(skillId, 2L, otherSkillId, 0L), snapshot.questionCounts());
            assertThrows(UnsupportedOperationException.class, () -> snapshot.questionCounts().put(foreignSkillId, 1L));

            assertThrows(NullPointerException.class, () -> new CoverageSnapshot(null, Map.of()));
            assertThrows(NullPointerException.class, () -> new CoverageSnapshot(courseId, null));
            Map<UUID, Long> nullKey = new HashMap<>();
            nullKey.put(null, 1L);
            assertThrows(NullPointerException.class, () -> new CoverageSnapshot(courseId, nullKey));
            Map<UUID, Long> nullVal = new HashMap<>();
            nullVal.put(skillId, null);
            assertThrows(NullPointerException.class, () -> new CoverageSnapshot(courseId, nullVal));
            assertThrows(IllegalArgumentException.class, () -> new CoverageSnapshot(courseId, Map.of(skillId, -1L)));
        }

        @Test
        void readCoverageRejectsIncoherentTransactionContextsBeforeAdmissionOrQueries() throws Exception {
            Transactional tx = QuestionBankService.class.getMethod("readCoverage", UUID.class, Set.class)
                    .getAnnotation(Transactional.class);
            assertNotNull(tx);
            assertTrue(tx.readOnly());
            assertEquals(Isolation.REPEATABLE_READ, tx.isolation());
            assertEquals(org.springframework.transaction.annotation.Propagation.REQUIRED, tx.propagation());

            QuestionBankService production = new QuestionBankService(questions, access, guard, skillGraph);
            assertThrows(IllegalArgumentException.class, () -> ownerSnapshot(() -> production.readCoverage(null, Set.of(skillId))));
            // The annotation is not active on a plain instance: no transaction means no coherent snapshot.
            assertThrows(IllegalStateException.class, () -> production.readCoverage(courseId, Set.of(skillId)));
            int rc = org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED;
            int rr = org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ;
            int serializable = org.springframework.transaction.TransactionDefinition.ISOLATION_SERIALIZABLE;
            int dflt = org.springframework.transaction.TransactionDefinition.ISOLATION_DEFAULT;
            int required = org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED;
            for (int[] context : List.of(new int[] {1, rc}, new int[] {1, dflt}, new int[] {1, serializable},
                    new int[] {0, rr}, new int[] {0, dflt}, new int[] {0, serializable})) {
                assertThrows(IllegalStateException.class, () -> inTx(context[0] == 1, context[1], required,
                        () -> production.readCoverage(courseId, Set.of(skillId))), Arrays.toString(context));
            }
            // A synchronization-only (non-actual) context is not a transaction either.
            assertThrows(IllegalStateException.class, () -> inTx(true, rr,
                    org.springframework.transaction.TransactionDefinition.PROPAGATION_SUPPORTS,
                    () -> production.readCoverage(courseId, Set.of(skillId))));
            verifyNoInteractions(access, skillGraph, questions);
        }

        @Test
        void readCoverageEnforcesOwnershipScopeExactZeroCountsAndCompletePopulation() {
            QuestionBankService production = new QuestionBankService(questions, access, guard, skillGraph);

            doThrow(new AccessDeniedException("Denied")).when(access).requireCourseOwnership(courseId);
            assertThrows(AccessDeniedException.class, () -> ownerSnapshot(() -> production.readCoverage(courseId, null)));
            assertThrows(AccessDeniedException.class, () -> ownerSnapshot(() -> production.readCoverage(courseId, Set.of(skillId))));
            verifyNoInteractions(skillGraph, questions);

            org.mockito.Mockito.reset(access);
            Set<UUID> withNull = new java.util.HashSet<>();
            withNull.add(skillId);
            withNull.add(null);
            assertThrows(IllegalArgumentException.class, () -> ownerSnapshot(() -> production.readCoverage(courseId, null)));
            assertThrows(IllegalArgumentException.class, () -> ownerSnapshot(() -> production.readCoverage(courseId, withNull)));
            verifyNoInteractions(skillGraph, questions);

            CoverageSnapshot empty = ownerSnapshot(() -> production.readCoverage(courseId, Set.of()));
            assertEquals(courseId, empty.courseId());
            assertEquals(Map.of(), empty.questionCounts());
            verifyNoInteractions(skillGraph, questions);

            when(skillGraph.readSkillIds(courseId)).thenReturn(List.of(skillId, otherSkillId));
            assertThrows(ResourceNotFoundException.class,
                    () -> ownerSnapshot(() -> production.readCoverage(courseId, Set.of(skillId, foreignSkillId))));
            verifyNoInteractions(questions);

            // Every approved kind and difficulty counts; Question has no publication flag.
            List<Question> population = new ArrayList<>();
            for (String kind : List.of("single_choice", "multiple_choice", "true_false")) {
                for (String difficulty : List.of("easy", "medium", "hard")) {
                    population.add(valid(skillId, kind, difficulty));
                }
            }
            when(questions.findAllInSkills(Set.of(skillId, otherSkillId))).thenReturn(population);
            CoverageSnapshot counts = ownerSnapshot(() -> production.readCoverage(courseId, Set.of(skillId, otherSkillId)));
            assertEquals(courseId, counts.courseId());
            assertEquals(Map.of(skillId, 9L, otherSkillId, 0L), counts.questionCounts());

            // Joining the Course-root READ COMMITTED write unit reads the same population.
            assertEquals(Map.of(skillId, 9L, otherSkillId, 0L),
                    courseRootUnit(() -> production.readCoverage(courseId, Set.of(skillId, otherSkillId))).questionCounts());

            // Only requested skills are queried; the owner path never uses the actorless assertion.
            when(questions.findAllInSkills(Set.of(otherSkillId))).thenReturn(List.of());
            assertEquals(Map.of(otherSkillId, 0L),
                    ownerSnapshot(() -> production.readCoverage(courseId, Set.of(otherSkillId))).questionCounts());
            verify(skillGraph, never()).requireAssessmentSkillsInCourse(any(), any());
            verify(skillGraph, never()).requireSkillInCourse(any(), any());

            // A malformed CT40 projection is a provider failure, never a narrower scope.
            when(skillGraph.readSkillIds(courseId)).thenReturn(List.of(skillId, skillId));
            assertThrows(IllegalStateException.class, () -> ownerSnapshot(() -> production.readCoverage(courseId, Set.of(skillId))));
        }

        static Stream<Arguments> malformedStoredDefinitions() {
            UUID skill = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
            UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
            return Stream.of(
                    Arguments.of("blank prompt", new Question(id, skill, " \u00a0\ufeff", "single_choice", "easy",
                            new String[] {"A", "B"}, new Boolean[] {true, false})),
                    Arguments.of("blank option", new Question(id, skill, "P", "single_choice", "easy",
                            new String[] {"   ", "B"}, new Boolean[] {true, false})),
                    Arguments.of("NUL in option", new Question(id, skill, "P", "multiple_choice", "easy",
                            new String[] {"A\u0000", "B"}, new Boolean[] {true, false})),
                    Arguments.of("unknown kind", new Question(id, skill, "P", "essay", "easy",
                            new String[] {"A", "B"}, new Boolean[] {true, false})),
                    Arguments.of("unknown difficulty", new Question(id, skill, "P", "single_choice", "expert",
                            new String[] {"A", "B"}, new Boolean[] {true, false})),
                    Arguments.of("one option", new Question(id, skill, "P", "single_choice", "easy",
                            new String[] {"A"}, new Boolean[] {true})),
                    Arguments.of("length mismatch", new Question(id, skill, "P", "single_choice", "easy",
                            new String[] {"A", "B"}, new Boolean[] {true, false, false})),
                    Arguments.of("null key element", new Question(id, skill, "P", "single_choice", "easy",
                            new String[] {"A", "B"}, new Boolean[] {true, null})),
                    Arguments.of("null option element", new Question(id, skill, "P", "single_choice", "easy",
                            new String[] {"A", null}, new Boolean[] {true, false})),
                    Arguments.of("two keys single choice", new Question(id, skill, "P", "single_choice", "easy",
                            new String[] {"A", "B"}, new Boolean[] {true, true})),
                    Arguments.of("no key multiple choice", new Question(id, skill, "P", "multiple_choice", "easy",
                            new String[] {"A", "B"}, new Boolean[] {false, false})),
                    Arguments.of("true_false options", new Question(id, skill, "P", "true_false", "easy",
                            new String[] {"Yes", "No"}, new Boolean[] {true, false})),
                    Arguments.of("null options", new Question(id, skill, "P", "single_choice", "easy",
                            null, new Boolean[] {true, false})),
                    Arguments.of("null skill", new Question(id, null, "P", "single_choice", "easy",
                            new String[] {"A", "B"}, new Boolean[] {true, false})),
                    Arguments.of("null id", new Question(null, skill, "P", "single_choice", "easy",
                            new String[] {"A", "B"}, new Boolean[] {true, false})),
                    Arguments.of("null row", null));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("malformedStoredDefinitions")
        void malformedStoredDefinitionsFailClosedInsteadOfDisappearingFromCoverage(String label, Question malformed) {
            UUID skill = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
            QuestionBankService production = new QuestionBankService(questions, access, guard, skillGraph);
            when(skillGraph.readSkillIds(courseId)).thenReturn(List.of(skill));
            List<Question> rows = new ArrayList<>();
            rows.add(valid(skill, "single_choice", "easy"));
            rows.add(malformed);
            when(questions.findAllInSkills(Set.of(skill))).thenReturn(rows);
            assertThrows(IllegalStateException.class,
                    () -> ownerSnapshot(() -> production.readCoverage(courseId, Set.of(skill))), label);
        }

        @Test
        void inconsistentProviderRowsFailClosed() {
            QuestionBankService production = new QuestionBankService(questions, access, guard, skillGraph);
            when(skillGraph.readSkillIds(courseId)).thenReturn(List.of(skillId, otherSkillId));
            when(questions.findAllInSkills(Set.of(skillId))).thenReturn(null);
            assertThrows(IllegalStateException.class, () -> ownerSnapshot(() -> production.readCoverage(courseId, Set.of(skillId))));
            // A row of an unrequested skill (even a Course skill) or a repeated identity is inconsistent.
            when(questions.findAllInSkills(Set.of(skillId))).thenReturn(List.of(valid(otherSkillId, "single_choice", "easy")));
            assertThrows(IllegalStateException.class, () -> ownerSnapshot(() -> production.readCoverage(courseId, Set.of(skillId))));
            Question q = valid(skillId, "single_choice", "easy");
            when(questions.findAllInSkills(Set.of(skillId))).thenReturn(List.of(q, q));
            assertThrows(IllegalStateException.class, () -> ownerSnapshot(() -> production.readCoverage(courseId, Set.of(skillId))));
        }

        /** Real AttemptService submission callback over doubles; the event publisher stands in for the listener. */
        private final class SubmissionHarness {
            final AttemptRepository attempts = mock(AttemptRepository.class);
            final QuizRepository quizzes = mock(QuizRepository.class);
            final UserAccountService identities = mock(UserAccountService.class);
            final UUID quizId = UUID.randomUUID();
            final UUID attemptId = UUID.randomUUID();
            final Question quizQuestion = new Question(questionId, skillId, "Q1", "single_choice", "easy",
                    new String[] {"A", "B"}, new Boolean[] {true, false});
            final Quiz publishedQuiz = new Quiz(quizId, courseId, "Practice", "practice", 600, "published");
            final QuestionBankService bank = new QuestionBankService(questions, access, guard, skillGraph);
            Runnable onEvent = () -> {};

            SubmissionHarness() {
                UUID learnerId = UUID.randomUUID();
                Attempt active = new Attempt(attemptId, learnerId, quizId, 1, Instant.parse("2026-11-26T09:55:00Z"),
                        Instant.parse("2026-11-26T10:05:00Z"), null, null);
                AttemptAnswer captured = new AttemptAnswer(UUID.randomUUID(), attemptId, questionId, 1,
                        new Boolean[] {false, false}, null);
                AttemptAnswer graded = new AttemptAnswer(captured.id(), attemptId, questionId, 1,
                        new Boolean[] {true, false}, true);
                when(identities.requireCurrentIdentity())
                        .thenReturn(new CurrentIdentity(learnerId, UserAccountService.IdentityRole.LEARNER));
                when(guard.withLearnerAssessmentWrite(eq(courseId), any()))
                        .thenAnswer(inv -> courseRootUnit(() -> inv.<Supplier<?>>getArgument(1).get()));
                when(quizzes.findInCourse(quizId, courseId)).thenReturn(Optional.of(publishedQuiz));
                when(quizzes.findInCourseForUpdate(quizId, courseId)).thenReturn(Optional.of(publishedQuiz));
                when(quizzes.findMembers(List.of(quizId))).thenReturn(Map.of(quizId, List.of(new Member(1, quizQuestion))));
                when(attempts.findInQuizForUpdate(attemptId, quizId)).thenReturn(Optional.of(active));
                when(attempts.findAnswersInPositionOrderForUpdate(attemptId)).thenReturn(List.of(captured), List.of(graded));
                when(attempts.save(any())).thenAnswer(inv -> inv.getArgument(0));
            }

            AttemptService service() {
                ApplicationEventPublisher publisher = event -> onEvent.run();
                return new AttemptService(attempts, quizzes, new QuizGradingService(), access, identities, guard,
                        skillGraph, Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZoneOffset.UTC), publisher);
            }

            void submit() {
                service().submit(courseId, quizId, attemptId, new AttemptService.AnswersInput(
                        List.of(new AttemptService.AnswerInput(questionId, List.of(true, false)))));
            }

            AttemptService.AnswersInput answers() {
                return new AttemptService.AnswersInput(List.of(new AttemptService.AnswerInput(questionId, List.of(true, false))));
            }
        }

        @Test
        void executionCoverageCountsInsideTheRealSubmissionScopeAndRefusesEveryOtherContext() throws Exception {
            assertNull(QuestionBankService.class.getMethod("readCoverageForExecution", UUID.class, Set.class)
                    .getAnnotation(Transactional.class), "must join the submission callback without its own transaction");
            SubmissionHarness harness = new SubmissionHarness();
            QuestionBankService bank = harness.bank;

            // B6-V09: no transaction, an owner snapshot or a Course-root-shaped unit without the marker.
            assertThrows(IllegalStateException.class, () -> bank.readCoverageForExecution(courseId, Set.of(skillId)));
            assertThrows(IllegalStateException.class,
                    () -> ownerSnapshot(() -> bank.readCoverageForExecution(courseId, Set.of(skillId))));
            assertThrows(IllegalStateException.class,
                    () -> courseRootUnit(() -> bank.readCoverageForExecution(courseId, Set.of(skillId))));
            assertThrows(IllegalArgumentException.class, () -> bank.readCoverageForExecution(null, Set.of(skillId)));
            verifyNoInteractions(access, skillGraph, questions);

            when(questions.findAllInSkills(Set.of(skillId, otherSkillId))).thenReturn(List.of(harness.quizQuestion));
            doThrow(new IllegalStateException("foreign skill"))
                    .when(skillGraph).requireAssessmentSkillsInCourse(courseId, Set.of(skillId, foreignSkillId));
            List<CoverageSnapshot> observed = new ArrayList<>();
            harness.onEvent = () -> {
                assertThrows(IllegalStateException.class,
                        () -> bank.readCoverageForExecution(UUID.randomUUID(), Set.of(skillId)));
                assertThrows(IllegalArgumentException.class, () -> bank.readCoverageForExecution(courseId, null));
                assertThrows(IllegalStateException.class,
                        () -> bank.readCoverageForExecution(courseId, Set.of(skillId, foreignSkillId)));
                // Suspension (REQUIRES_NEW/NOT_SUPPORTED) needs a real manager: PostgreSQL suite covers it.
                // Another thread never observes the thread-bound marker.
                var other = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        bank.readCoverageForExecution(courseId, Set.of(skillId));
                        return null;
                    } catch (RuntimeException expected) {
                        return expected;
                    }
                });
                assertTrue(other.join() instanceof IllegalStateException);
                assertEquals(Map.of(), bank.readCoverageForExecution(courseId, Set.of()).questionCounts());
                observed.add(bank.readCoverageForExecution(courseId, Set.of(skillId, otherSkillId)));
            };
            harness.submit();

            assertEquals(1, observed.size());
            assertEquals(courseId, observed.getFirst().courseId());
            assertEquals(Map.of(skillId, 1L, otherSkillId, 0L), observed.getFirst().questionCounts());
            verify(skillGraph).requireAssessmentSkillsInCourse(courseId, Set.of(skillId, otherSkillId));
            verify(questions, never()).findAllInSkills(Set.of(skillId, foreignSkillId));
            verify(questions, never()).findAllInSkills(Set.of());
            verify(questions, never()).findAllInSkills(Set.of(skillId));
            verify(access, never()).requireCourseOwnership(any());
            verify(skillGraph, never()).readSkillIds(any());
            verify(skillGraph, never()).requireSkillInCourse(any(), any());
            assertThrows(IllegalStateException.class, () -> bank.readCoverageForExecution(courseId, Set.of(skillId)));
            assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.getResourceMap().isEmpty());
        }

        @Test
        void startAndAnswerSaveCallbacksNeverGrantExecutionCoverage() {
            SubmissionHarness harness = new SubmissionHarness();
            List<Throwable> refusals = new ArrayList<>();
            final class ProbeStop extends RuntimeException {}
            when(harness.quizzes.findInCourseForUpdate(harness.quizId, courseId)).thenAnswer(inv -> {
                refusals.add(assertThrows(IllegalStateException.class,
                        () -> harness.bank.readCoverageForExecution(courseId, Set.of(skillId))));
                throw new ProbeStop();
            });
            AttemptService service = harness.service();
            assertThrows(ProbeStop.class, () -> service.start(courseId, harness.quizId));
            assertThrows(ProbeStop.class, () -> service.saveAnswers(courseId, harness.quizId, harness.attemptId,
                    harness.answers()));
            assertEquals(2, refusals.size());
            verify(questions, never()).findAllInSkills(any());
        }

        @Test
        void failedSubmissionLeavesNoExecutionScopeBehind() {
            SubmissionHarness harness = new SubmissionHarness();
            when(questions.findAllInSkills(Set.of(skillId))).thenReturn(List.of(harness.quizQuestion));
            List<CoverageSnapshot> observed = new ArrayList<>();
            harness.onEvent = () -> {
                observed.add(harness.bank.readCoverageForExecution(courseId, Set.of(skillId)));
                throw new IllegalStateException("listener fault after a successful read");
            };
            assertThrows(IllegalStateException.class, harness::submit);
            assertEquals(Map.of(skillId, 1L), observed.getFirst().questionCounts());
            assertThrows(IllegalStateException.class,
                    () -> harness.bank.readCoverageForExecution(courseId, Set.of(skillId)));
            assertThrows(IllegalStateException.class,
                    () -> courseRootUnit(() -> harness.bank.readCoverageForExecution(courseId, Set.of(skillId))));
            assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.getResourceMap().isEmpty());
        }
    }
}
