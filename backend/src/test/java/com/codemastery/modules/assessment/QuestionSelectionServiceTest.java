package com.codemastery.modules.assessment;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService;
import com.codemastery.modules.assessment.application.port.PracticeDifficultyProvider;
import com.codemastery.modules.assessment.application.service.QuestionSelectionService;
import com.codemastery.modules.assessment.application.service.QuestionSelectionService.CandidateQuestion;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void practiceDifficultyProviderExposesExactCt07SignatureInAssessmentPortPackage() throws Exception {
        assertEquals("com.codemastery.modules.assessment.application.port",
                PracticeDifficultyProvider.class.getPackageName());
        assertTrue(PracticeDifficultyProvider.class.isInterface());
        assertTrue(Modifier.isPublic(PracticeDifficultyProvider.class.getModifiers()));

        Method[] methods = PracticeDifficultyProvider.class.getDeclaredMethods();
        assertEquals(1, methods.length);
        Method target = PracticeDifficultyProvider.class.getMethod("targetDifficulty", UUID.class, UUID.class);
        assertEquals(DifficultyLevel.class, target.getReturnType());
        assertArrayEquals(new Class<?>[]{UUID.class, UUID.class}, target.getParameterTypes());
    }

    @Test
    void fallbackOrderMatchesApprovedCt07OrdersAndReturnsImmutableLists() {
        assertEquals(List.of(DifficultyLevel.EASY, DifficultyLevel.MEDIUM, DifficultyLevel.HARD),
                QuestionSelectionService.fallbackOrder(DifficultyLevel.EASY));
        assertEquals(List.of(DifficultyLevel.MEDIUM, DifficultyLevel.EASY, DifficultyLevel.HARD),
                QuestionSelectionService.fallbackOrder(DifficultyLevel.MEDIUM));
        assertEquals(List.of(DifficultyLevel.HARD, DifficultyLevel.MEDIUM, DifficultyLevel.EASY),
                QuestionSelectionService.fallbackOrder(DifficultyLevel.HARD));
        assertThrows(NullPointerException.class, () -> QuestionSelectionService.fallbackOrder(null));

        for (DifficultyLevel level : DifficultyLevel.values()) {
            List<DifficultyLevel> order = QuestionSelectionService.fallbackOrder(level);
            assertThrows(UnsupportedOperationException.class, () -> order.add(DifficultyLevel.EASY));
        }
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
        assertThrows(UnsupportedOperationException.class, () -> selected.add(q1));
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
    void resolvesPerSkillFallbackIndependentlyAndPreservesMembershipOrderWithoutCountCap() {
        UUID skillEasyFallbackMedium = UUID.randomUUID();
        UUID skillHardFallbackEasy = UUID.randomUUID();

        List<CandidateQuestion> expected = new ArrayList<>();
        List<CandidateQuestion> scrambled = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            CandidateQuestion matching = new CandidateQuestion(
                    UUID.randomUUID(), skillEasyFallbackMedium, DifficultyLevel.MEDIUM, i * 2);
            CandidateQuestion harderExcluded = new CandidateQuestion(
                    UUID.randomUUID(), skillEasyFallbackMedium, DifficultyLevel.HARD, i * 2 - 1);
            expected.add(matching);
            scrambled.add(harderExcluded);
            scrambled.add(matching);
        }
        CandidateQuestion secondSkillMatch = new CandidateQuestion(
                UUID.randomUUID(), skillHardFallbackEasy, DifficultyLevel.EASY, 30);
        expected.add(secondSkillMatch);
        scrambled.addFirst(secondSkillMatch);

        Map<UUID, DifficultyLevel> targets = Map.of(
                skillEasyFallbackMedium, DifficultyLevel.EASY,
                skillHardFallbackEasy, DifficultyLevel.HARD
        );

        List<CandidateQuestion> firstRun = QuestionSelectionService.selectPracticeQuestions(scrambled, targets);
        List<CandidateQuestion> secondRun = QuestionSelectionService.selectPracticeQuestions(scrambled, targets);

        assertEquals(13, firstRun.size());
        assertEquals(expected, firstRun);
        assertEquals(firstRun, secondRun);
    }

    @Test
    void rejectsInvalidCandidateDefinitionsDuplicatesNullsAndMissingTargets() {
        UUID skillId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        assertThrows(NullPointerException.class,
                () -> new CandidateQuestion(null, skillId, DifficultyLevel.EASY, 1));
        assertThrows(NullPointerException.class,
                () -> new CandidateQuestion(questionId, null, DifficultyLevel.EASY, 1));
        assertThrows(NullPointerException.class,
                () -> new CandidateQuestion(questionId, skillId, null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new CandidateQuestion(questionId, skillId, DifficultyLevel.EASY, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CandidateQuestion(questionId, skillId, DifficultyLevel.EASY, -1));

        CandidateQuestion q1 = new CandidateQuestion(questionId, skillId, DifficultyLevel.EASY, 1);

        assertTrue(QuestionSelectionService.selectPracticeQuestions(List.of(), Map.of()).isEmpty());
        assertThrows(NullPointerException.class,
                () -> QuestionSelectionService.selectPracticeQuestions(null, Map.of()));
        assertThrows(NullPointerException.class,
                () -> QuestionSelectionService.selectPracticeQuestions(List.of(), null));

        List<CandidateQuestion> withNullCandidate = new ArrayList<>();
        withNullCandidate.add(null);
        assertThrows(NullPointerException.class,
                () -> QuestionSelectionService.selectPracticeQuestions(
                        withNullCandidate, Map.of(skillId, DifficultyLevel.EASY)));

        Map<UUID, DifficultyLevel> nullKeyMap = new HashMap<>();
        nullKeyMap.put(null, DifficultyLevel.EASY);
        assertThrows(IllegalArgumentException.class,
                () -> QuestionSelectionService.selectPracticeQuestions(List.of(), nullKeyMap));

        Map<UUID, DifficultyLevel> nullValueMap = new HashMap<>();
        nullValueMap.put(skillId, null);
        assertThrows(IllegalArgumentException.class,
                () -> QuestionSelectionService.selectPracticeQuestions(List.of(), nullValueMap));

        assertThrows(IllegalArgumentException.class,
                () -> QuestionSelectionService.selectPracticeQuestions(List.of(q1), new HashMap<>()));

        CandidateQuestion duplicateQuestionId =
                new CandidateQuestion(questionId, skillId, DifficultyLevel.MEDIUM, 2);
        assertThrows(IllegalArgumentException.class,
                () -> QuestionSelectionService.selectPracticeQuestions(
                        List.of(q1, duplicateQuestionId), Map.of(skillId, DifficultyLevel.EASY)));

        CandidateQuestion duplicatePosition =
                new CandidateQuestion(UUID.randomUUID(), skillId, DifficultyLevel.MEDIUM, 1);
        assertThrows(IllegalArgumentException.class,
                () -> QuestionSelectionService.selectPracticeQuestions(
                        List.of(q1, duplicatePosition), Map.of(skillId, DifficultyLevel.EASY)));
    }

    @Test
    void preservesExistingCt23ExplicitCompositionWithoutDifficultyFiltering() {
        QuizRepository quizzes = mock(QuizRepository.class);
        SkillGraphService skillGraph = mock(SkillGraphService.class);
        UUID courseId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID easyId = UUID.randomUUID();
        UUID hardId = UUID.randomUUID();

        Question easy = new Question(easyId, skillId, "Easy prompt", "single_choice", "easy",
                new String[]{"A", "B"}, new Boolean[]{true, false});
        Question hard = new Question(hardId, skillId, "Hard prompt", "true_false", "hard",
                new String[]{"True", "False"}, new Boolean[]{false, true});

        when(skillGraph.readSkillIds(courseId)).thenReturn(List.of(skillId));
        when(quizzes.findQuestionsInSkills(List.of(hardId, easyId), List.of(skillId)))
                .thenReturn(List.of(easy, hard));

        QuestionSelectionService service = new QuestionSelectionService(quizzes, skillGraph);
        List<Question> ordered = service.requireOrderedCourseQuestions(courseId, List.of(hardId, easyId));

        assertEquals(List.of(hard, easy), ordered);
        service.requireCourseMembers(courseId, ordered);
        assertThrows(ResourceNotFoundException.class, () -> {
            when(quizzes.findQuestionsInSkills(List.of(UUID.randomUUID()), List.of(skillId))).thenReturn(List.of());
            service.requireOrderedCourseQuestions(courseId, List.of(UUID.randomUUID()));
        });
    }

    /**
     * ADR011 §8 and Leader Decision E02 (ADR013 §§19.2, 19.8 B8-P08, 19.10):
     * PracticeDifficultyProvider and QuestionSelectionService.selectPracticeQuestions have no
     * production caller, and only Assessment's port file and Adaptive's PracticeDifficultySelector
     * may reference PracticeDifficultyProvider.
     */
    @Test
    void portAndQuestionLevelSelectionHaveNoProductionCallersAndPreserveBoundaries() throws Exception {
        Path root = Path.of("src", "main", "java", "com", "codemastery");
        Path portFile = root.resolve("modules/assessment/application/port/PracticeDifficultyProvider.java");
        Path selectorFile = root.resolve("modules/adaptive/application/decision/PracticeDifficultySelector.java");
        Set<Path> allowedPortTypeReferences = Set.of(portFile, selectorFile);

        List<Path> portCallers = new ArrayList<>();
        List<Path> selectionCallers = new ArrayList<>();
        List<Path> portTypeRefs = new ArrayList<>();

        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                SourceFacts facts = analyzeSource(Files.readString(file));
                if (facts.calledMethods().contains("targetDifficulty")) {
                    portCallers.add(file);
                }
                if (facts.calledMethods().contains("selectPracticeQuestions")) {
                    selectionCallers.add(file);
                }
                if (facts.identifiers().contains("PracticeDifficultyProvider")) {
                    portTypeRefs.add(file);
                }
            }
        }

        assertEquals(List.of(), portCallers, () -> "Unapproved PracticeDifficultyProvider callers: " + portCallers);
        assertEquals(List.of(), selectionCallers, () -> "Unapproved AS07.select callers under E02: " + selectionCallers);
        assertTrue(allowedPortTypeReferences.containsAll(portTypeRefs),
                () -> "Unapproved PracticeDifficultyProvider type references: " + portTypeRefs);

        // Assessment's port and selection files must not import Adaptive decision/mastery/recommendation internals.
        assertEquals(Set.of(), analyzeSource(Files.readString(portFile)).adaptiveImports());
        assertEquals(
                Set.of("com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService"),
                analyzeSource(Files.readString(
                        root.resolve("modules/assessment/application/service/QuestionSelectionService.java")))
                        .adaptiveImports());
    }

    @ParameterizedTest
    @ValueSource(strings = {"targetDifficulty", "selectPracticeQuestions"})
    void sourceScannerDetectsInvocationsAndReferencesWithoutMatchingDeclarationsOrNonCode(String method)
            throws Exception {
        for (String expression : List.of("target." + method + "(a, b)", method + "(a, b)", "target :: " + method)) {
            SourceFacts facts = analyzeSource("class Caller { void run() { var r = " + expression + "; } }");
            assertTrue(facts.calledMethods().contains(method), expression);
        }
        String nonCalls = """
                class Caller {
                    Object %1$s(Object a, Object b) { return null; }
                    // target.%1$s(a, b); target::%1$s
                    /* target.%1$s(a, b); target::%1$s */
                    String s = "target.%1$s(a, b); target::%1$s";
                    void other() { target.%1$sOther(a, b); }
                }
                """.formatted(method);
        assertFalse(analyzeSource(nonCalls).calledMethods().contains(method));
    }

    private record SourceFacts(Set<String> calledMethods, Set<String> identifiers, Set<String> adaptiveImports) {}

    private static SourceFacts analyzeSource(String source) throws IOException {
        if (source.isBlank()) {
            return new SourceFacts(Set.of(), Set.of(), Set.of());
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Caller allowlist tests require the project's JDK");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var input = new SimpleJavaFileObject(URI.create("string:///Source.java"), JavaFileObject.Kind.SOURCE) {
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
            Set<String> adaptiveImports = new HashSet<>();
            var scanner = new TreeScanner<Void, Void>() {
                @Override
                public Void visitImport(ImportTree node, Void unused) {
                    String imported = node.getQualifiedIdentifier().toString();
                    if (imported.startsWith("com.codemastery.modules.adaptive.")) {
                        adaptiveImports.add(imported);
                    }
                    return super.visitImport(node, unused);
                }

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
            return new SourceFacts(calls, identifiers, adaptiveImports);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
