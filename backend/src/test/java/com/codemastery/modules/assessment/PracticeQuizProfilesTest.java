package com.codemastery.modules.assessment;

import com.codemastery.modules.assessment.application.service.QuestionSelectionService;
import com.codemastery.modules.assessment.application.service.QuizService;
import com.codemastery.modules.assessment.application.service.QuizService.PracticeQuizProfile;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.assessment.domain.repository.QuizRepository.PracticeQuizDifficultyRow;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.CourseManagementService;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeQuizProfilesTest {

    @Mock
    private QuizRepository quizzes;

    @Mock
    private QuestionSelectionService selection;

    @Mock
    private CourseAccessService courseAccess;

    @Mock
    private CourseManagementService courseConfiguration;

    @InjectMocks
    private QuizService quizService;

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
    }

    private static void enterReadOnlyRepeatableReadSnapshot() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Isolation.REPEATABLE_READ.value());
    }

    @Test
    void declaresMandatoryReadOnlyRepeatableReadTransactionAndMinimalRecordShape() throws Exception {
        Method method = QuizService.class.getMethod("readPracticeQuizProfiles", UUID.class, Set.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional, "readPracticeQuizProfiles must declare @Transactional");
        assertEquals(Propagation.MANDATORY, transactional.propagation());
        assertTrue(transactional.readOnly());
        assertEquals(Isolation.REPEATABLE_READ, transactional.isolation());

        List<String> componentNames = Arrays.stream(PracticeQuizProfile.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("quizId", "title", "difficulties"), componentNames);
    }

    @Test
    void rejectsAbsentReadWriteOrNonRepeatableReadCallerTransactionBeforeRepositoryAccess() {
        UUID courseId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();

        // No active transaction (even for an empty skill set).
        assertThrows(IllegalStateException.class,
                () -> quizService.readPracticeQuizProfiles(courseId, Set.of(skillId)));
        assertThrows(IllegalStateException.class,
                () -> quizService.readPracticeQuizProfiles(courseId, Set.of()));

        // Read-write transaction is rejected.
        enterReadOnlyRepeatableReadSnapshot();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        assertThrows(IllegalStateException.class,
                () -> quizService.readPracticeQuizProfiles(courseId, Set.of(skillId)));

        // Wrong isolation levels are rejected.
        enterReadOnlyRepeatableReadSnapshot();
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Isolation.READ_COMMITTED.value());
        assertThrows(IllegalStateException.class,
                () -> quizService.readPracticeQuizProfiles(courseId, Set.of(skillId)));

        enterReadOnlyRepeatableReadSnapshot();
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
        assertThrows(IllegalStateException.class,
                () -> quizService.readPracticeQuizProfiles(courseId, Set.of(skillId)));

        verifyNoInteractions(quizzes, selection, courseAccess, courseConfiguration);
    }

    @Test
    void emptySkillIdsReturnsEmptyListWithoutQueryingRepository() {
        enterReadOnlyRepeatableReadSnapshot();
        UUID courseId = UUID.randomUUID();

        List<PracticeQuizProfile> result = quizService.readPracticeQuizProfiles(courseId, Set.of());

        assertTrue(result.isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> result.add(new PracticeQuizProfile(UUID.randomUUID(), "Quiz", Set.of(DifficultyLevel.EASY))));
        verifyNoInteractions(quizzes, selection, courseAccess, courseConfiguration);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aggregatesMinimalDifficultyRowsOrdersDeterministicallyAndReturnsImmutableProfiles() {
        enterReadOnlyRepeatableReadSnapshot();
        UUID courseId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        // Java signed UUID.compareTo puts 80.../ff... before 00.../7f...; ADR013 §19.2 requires canonical lowercase UUID text.
        UUID tie00 = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID tie80 = UUID.fromString("80000000-0000-4000-8000-000000000001");
        UUID zebraQuiz = UUID.fromString("11111111-0000-4000-8000-000000000001");
        UUID alphaMixedQuiz = UUID.fromString("22222222-0000-4000-8000-000000000001");

        Set<UUID> mutableCallerSkills = new HashSet<>();
        mutableCallerSkills.add(skillId);

        when(quizzes.findPublishedPracticeQuizDifficultyRows(courseId, Set.of(skillId)))
                .thenReturn(List.of(
                        new PracticeQuizDifficultyRow(alphaMixedQuiz, "alpha Mixed", "hard"),
                        new PracticeQuizDifficultyRow(tie80, "Loops Tie", "medium"),
                        new PracticeQuizDifficultyRow(zebraQuiz, "Zebra Easy", "easy"),
                        new PracticeQuizDifficultyRow(tie00, "Loops Tie", "easy"),
                        new PracticeQuizDifficultyRow(alphaMixedQuiz, "alpha Mixed", "easy"),
                        new PracticeQuizDifficultyRow(tie00, "Loops Tie", "easy")
                ));

        List<PracticeQuizProfile> profiles = quizService.readPracticeQuizProfiles(courseId, mutableCallerSkills);
        mutableCallerSkills.clear();

        ArgumentCaptor<Set<UUID>> capturedSkills = ArgumentCaptor.forClass(Set.class);
        verify(quizzes, times(1)).findPublishedPracticeQuizDifficultyRows(
                org.mockito.ArgumentMatchers.eq(courseId), capturedSkills.capture());
        assertEquals(Set.of(skillId), capturedSkills.getValue());
        assertThrows(UnsupportedOperationException.class, () -> capturedSkills.getValue().add(UUID.randomUUID()));

        // String.compareTo UTF-16 order: 'L' (0x4C) < 'Z' (0x5A) < 'a' (0x61); ties broken by lowercase UUID text.
        assertEquals(List.of(
                new PracticeQuizProfile(tie00, "Loops Tie", Set.of(DifficultyLevel.EASY)),
                new PracticeQuizProfile(tie80, "Loops Tie", Set.of(DifficultyLevel.MEDIUM)),
                new PracticeQuizProfile(zebraQuiz, "Zebra Easy", Set.of(DifficultyLevel.EASY)),
                new PracticeQuizProfile(alphaMixedQuiz, "alpha Mixed", Set.of(DifficultyLevel.EASY, DifficultyLevel.HARD))
        ), profiles);

        assertThrows(UnsupportedOperationException.class, () -> profiles.add(profiles.getFirst()));
        assertThrows(UnsupportedOperationException.class,
                () -> profiles.getFirst().difficulties().add(DifficultyLevel.HARD));
        verifyNoInteractions(selection, courseAccess, courseConfiguration);
    }

    @Test
    void rejectsNullArgumentsAndInvalidStoredRows() {
        UUID courseId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> quizService.readPracticeQuizProfiles(null, Set.of(skillId)));
        assertThrows(NullPointerException.class,
                () -> quizService.readPracticeQuizProfiles(courseId, null));

        Set<UUID> withNull = new HashSet<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> quizService.readPracticeQuizProfiles(courseId, withNull));

        enterReadOnlyRepeatableReadSnapshot();

        UUID quizId = UUID.randomUUID();
        for (List<PracticeQuizDifficultyRow> badRows : List.of(
                Collections.singletonList((PracticeQuizDifficultyRow) null),
                List.of(new PracticeQuizDifficultyRow(null, "Valid Title", "easy")),
                List.of(new PracticeQuizDifficultyRow(quizId, null, "easy")),
                List.of(new PracticeQuizDifficultyRow(quizId, "   \u0085\uFEFF", "easy")),
                List.of(new PracticeQuizDifficultyRow(quizId, "Bad\0Title", "easy")),
                List.of(new PracticeQuizDifficultyRow(quizId, "Bad\uD800Title", "easy")),
                List.of(new PracticeQuizDifficultyRow(quizId, "Valid Title", null)),
                List.of(new PracticeQuizDifficultyRow(quizId, "Valid Title", "extreme")),
                List.of(new PracticeQuizDifficultyRow(quizId, "Valid Title", "EASY")),
                List.of(
                        new PracticeQuizDifficultyRow(quizId, "Title A", "easy"),
                        new PracticeQuizDifficultyRow(quizId, "Title B", "medium"))
        )) {
            when(quizzes.findPublishedPracticeQuizDifficultyRows(courseId, Set.of(skillId))).thenReturn(badRows);
            assertThrows(IllegalStateException.class,
                    () -> quizService.readPracticeQuizProfiles(courseId, Set.of(skillId)));
        }

        when(quizzes.findPublishedPracticeQuizDifficultyRows(courseId, Set.of(skillId))).thenReturn(null);
        assertThrows(IllegalStateException.class,
                () -> quizService.readPracticeQuizProfiles(courseId, Set.of(skillId)));
    }

    /**
     * ADR013 §19.3 and oracle B8-P09: only Adaptive RecommendationService may call
     * QuizService.readPracticeQuizProfiles (after readLearnerState admission), the repository query
     * stays encapsulated in Assessment, and no Assessment REST controller or DTO exposes it.
     */
    @Test
    void onlyAdaptiveRecommendationServiceMayCallReadPracticeQuizProfiles() throws Exception {
        Path root = Path.of("src", "main", "java", "com", "codemastery");
        Path recommendationService =
                root.resolve("modules/adaptive/application/recommendation/RecommendationService.java");
        Set<Path> allowedProfileCallers = Set.of(recommendationService);
        Set<Path> allowedRepoCallers = Set.of(
                root.resolve("modules/assessment/application/service/QuizService.java"),
                root.resolve("modules/assessment/infrastructure/persistence/repository/QuizRepositoryImpl.java"));
        Path assessmentApiDir = root.resolve("modules/assessment/api");

        List<Path> profileCallers = new ArrayList<>();
        List<Path> repoCallers = new ArrayList<>();
        List<Path> apiExposures = new ArrayList<>();

        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                SourceScan scan = scanSource(source);
                if (scan.calledMethods().contains("readPracticeQuizProfiles")) {
                    profileCallers.add(file);
                }
                if (scan.calledMethods().contains("findPublishedPracticeQuizDifficultyRows")) {
                    repoCallers.add(file);
                }
                if (file.startsWith(assessmentApiDir)
                        && (scan.identifiers().contains("PracticeQuizProfile")
                        || scan.calledMethods().contains("readPracticeQuizProfiles"))) {
                    apiExposures.add(file);
                }
            }
        }

        assertTrue(allowedProfileCallers.containsAll(profileCallers),
                () -> "Unapproved CT23-PRACTICE-PROFILES callers: " + profileCallers);
        if (profileCallers.contains(recommendationService)) {
            assertTrue(
                    scanSource(Files.readString(recommendationService)).calledMethods().contains("readLearnerState"),
                    "RecommendationService must admit the Learner via readLearnerState in the same composition");
        }
        assertTrue(allowedRepoCallers.containsAll(repoCallers),
                () -> "Unapproved findPublishedPracticeQuizDifficultyRows callers: " + repoCallers);
        assertEquals(List.of(), apiExposures,
                () -> "PracticeQuizProfile must not be exposed through Assessment REST APIs: " + apiExposures);
    }

    @ParameterizedTest
    @ValueSource(strings = {"readPracticeQuizProfiles", "findPublishedPracticeQuizDifficultyRows"})
    void callerScannerDetectsInvocationsAndReferencesWithoutMatchingDeclarationsOrNonCode(String method)
            throws Exception {
        for (String expression : List.of("service." + method + "(c, s)", method + "(c, s)", "service :: " + method)) {
            assertTrue(scanSource("class Caller { void run() { var r = " + expression + "; } }")
                    .calledMethods().contains(method), expression);
        }
        String nonCalls = """
                class Caller {
                    Object %1$s(Object c, Object s) { return null; }
                    // service.%1$s(c, s); service::%1$s
                    /* service.%1$s(c, s); service::%1$s */
                    String literal = "service.%1$s(c, s); service::%1$s";
                    void other() { service.%1$sExtra(c, s); }
                }
                """.formatted(method);
        assertFalse(scanSource(nonCalls).calledMethods().contains(method));
    }

    private record SourceScan(Set<String> calledMethods, Set<String> identifiers) {}

    private static SourceScan scanSource(String source) throws IOException {
        if (source.isBlank()) {
            return new SourceScan(Set.of(), Set.of());
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Caller allowlist tests require the project's JDK");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var input = new SimpleJavaFileObject(URI.create("string:///Caller.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        try (var fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            var task = (JavacTask) compiler.getTask(
                    null, fileManager, diagnostics, List.of("-proc:none"), null, List.of(input));
            Set<String> calls = new HashSet<>();
            Set<String> identifiers = new HashSet<>();
            var scanner = new TreeScanner<Void, Void>() {
                @Override
                public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                    if (node.getMethodSelect() instanceof MemberSelectTree member) {
                        calls.add(member.getIdentifier().toString());
                    } else if (node.getMethodSelect() instanceof IdentifierTree identifier) {
                        calls.add(identifier.getName().toString());
                    }
                    return super.visitMethodInvocation(node, unused);
                }

                @Override
                public Void visitMemberReference(MemberReferenceTree node, Void unused) {
                    calls.add(node.getName().toString());
                    return super.visitMemberReference(node, unused);
                }

                @Override
                public Void visitIdentifier(IdentifierTree node, Void unused) {
                    identifiers.add(node.getName().toString());
                    return super.visitIdentifier(node, unused);
                }

                @Override
                public Void visitMemberSelect(MemberSelectTree node, Void unused) {
                    identifiers.add(node.getIdentifier().toString());
                    return super.visitMemberSelect(node, unused);
                }
            };
            for (CompilationUnitTree unit : task.parse()) {
                scanner.scan(unit, null);
            }
            assertFalse(
                    diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR),
                    () -> "Cannot parse Java source for allowlist scan: " + diagnostics.getDiagnostics());
            return new SourceScan(calls, identifiers);
        }
    }
}
