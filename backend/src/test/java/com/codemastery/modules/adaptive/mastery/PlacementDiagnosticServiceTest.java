package com.codemastery.modules.adaptive.mastery;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.adaptive.application.mastery.PlacementDiagnosticService;
import com.codemastery.modules.adaptive.application.mastery.PlacementDiagnosticService.PlacementOffer;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.model.Skill;
import com.codemastery.modules.adaptive.domain.repository.LearnerSkillMasteryRepository;
import com.codemastery.modules.adaptive.domain.repository.SkillRepository;
import com.codemastery.modules.assessment.application.service.AttemptService;
import com.codemastery.modules.course.application.service.LessonProgressService;
import com.codemastery.modules.course.application.service.LessonProgressService.LearnerCourseState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class PlacementDiagnosticServiceTest {

    private static final UUID COURSE = new UUID(0, 1);
    private static final UUID LEARNER = new UUID(0, 2);
    private static final UUID ENROLLMENT = new UUID(0, 3);
    private static final UUID SKILL_1 = new UUID(0, 10);
    private static final UUID SKILL_2 = new UUID(0, 11);
    private static final UUID DIAGNOSTIC_QUIZ = new UUID(0, 20);
    private static final Map<String, Object> POLICY = Map.of(
            "weak", Map.of("numerator", 1, "denominator", 2),
            "mastered", Map.of("numerator", 4, "denominator", 5),
            "minimumQuestionsPerSkill", 6);

    private final LessonProgressService learnerStates = mock(LessonProgressService.class);
    private final SkillRepository skills = mock(SkillRepository.class);
    private final LearnerSkillMasteryRepository masteries = mock(LearnerSkillMasteryRepository.class);
    private final AttemptService attempts = mock(AttemptService.class);
    private final PlacementDiagnosticService service =
            new PlacementDiagnosticService(learnerStates, skills, masteries, attempts);

    @BeforeEach
    void reportingSnapshot() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Isolation.REPEATABLE_READ.value());
    }

    @AfterEach
    void clearTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "completed"})
    void offersServerChosenPlacementQuizWhenAdaptiveReadyWithSkillsAndNoStoredMastery(String membership) {
        when(learnerStates.readLearnerState(COURSE)).thenReturn(state(membership, true, POLICY));
        when(skills.findSkills(COURSE)).thenReturn(List.of(
                new Skill(SKILL_1, COURSE, "Variables", null),
                new Skill(SKILL_2, COURSE, "Loops", null)));
        when(masteries.findStates(LEARNER, Set.of(SKILL_1, SKILL_2))).thenReturn(Map.of());
        when(attempts.readMyPlacementQuizId(COURSE)).thenReturn(DIAGNOSTIC_QUIZ);

        PlacementOffer offer = service.readPlacementOffer(COURSE);

        assertTrue(offer.placementOffered());
        assertEquals(DIAGNOSTIC_QUIZ, offer.placementQuizId());

        InOrder order = inOrder(learnerStates, skills, masteries, attempts);
        order.verify(learnerStates).readLearnerState(COURSE);
        order.verify(skills).findSkills(COURSE);
        order.verify(masteries).findStates(LEARNER, Set.of(SKILL_1, SKILL_2));
        order.verify(attempts).readMyPlacementQuizId(COURSE);
        verifyNoMoreInteractions(learnerStates, skills, masteries, attempts);
    }

    @Test
    void returnsNoOfferWhenAssessmentReturnsNullPlacementQuizId() {
        when(learnerStates.readLearnerState(COURSE)).thenReturn(state("active", true, POLICY));
        when(skills.findSkills(COURSE)).thenReturn(List.of(new Skill(SKILL_1, COURSE, "Variables", null)));
        when(masteries.findStates(LEARNER, Set.of(SKILL_1))).thenReturn(Map.of());
        when(attempts.readMyPlacementQuizId(COURSE)).thenReturn(null);

        PlacementOffer offer = service.readPlacementOffer(COURSE);

        assertEquals(PlacementOffer.none(), offer);
    }

    @Test
    void doesNotOfferWhenLearnerAlreadyHasAnyStoredMasteryRowIncludingExplicitUnknown() {
        when(learnerStates.readLearnerState(COURSE)).thenReturn(state("active", true, POLICY));
        when(skills.findSkills(COURSE)).thenReturn(List.of(
                new Skill(SKILL_1, COURSE, "Variables", null),
                new Skill(SKILL_2, COURSE, "Loops", null)));
        when(masteries.findStates(LEARNER, Set.of(SKILL_1, SKILL_2)))
                .thenReturn(Map.of(SKILL_1, MasteryState.UNKNOWN));

        PlacementOffer offer = service.readPlacementOffer(COURSE);

        assertFalse(offer.placementOffered());
        assertNull(offer.placementQuizId());
        verifyNoInteractions(attempts);
    }

    @Test
    void doesNotOfferWhenCourseHasNoSkills() {
        when(learnerStates.readLearnerState(COURSE)).thenReturn(state("active", true, POLICY));
        when(skills.findSkills(COURSE)).thenReturn(List.of());

        PlacementOffer offer = service.readPlacementOffer(COURSE);

        assertEquals(PlacementOffer.none(), offer);
        verifyNoInteractions(masteries, attempts);
    }

    @Test
    void doesNotOfferWhenAdaptiveDisabledOrPolicyMissing() {
        when(learnerStates.readLearnerState(COURSE)).thenReturn(state("active", false, POLICY));
        assertEquals(PlacementOffer.none(), service.readPlacementOffer(COURSE));

        when(learnerStates.readLearnerState(COURSE)).thenReturn(state("active", false, null));
        assertEquals(PlacementOffer.none(), service.readPlacementOffer(COURSE));

        when(learnerStates.readLearnerState(COURSE)).thenReturn(state("active", true, null));
        assertEquals(PlacementOffer.none(), service.readPlacementOffer(COURSE));

        verifyNoInteractions(skills, masteries, attempts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"left", "none"})
    void doesNotOfferForNonJoinedMembership(String membership) {
        UUID enrollmentId = "none".equals(membership) ? null : ENROLLMENT;
        when(learnerStates.readLearnerState(COURSE))
                .thenReturn(new LearnerCourseState(COURSE, LEARNER, enrollmentId, membership, true, POLICY, List.of()));

        assertEquals(PlacementOffer.none(), service.readPlacementOffer(COURSE));
        verifyNoInteractions(skills, masteries, attempts);
    }

    @Test
    void admissionFailurePropagatesBeforeReadingAdaptiveOrAssessment() {
        doThrow(new AccessDeniedException("Access denied")).when(learnerStates).readLearnerState(COURSE);
        assertThrows(AccessDeniedException.class, () -> service.readPlacementOffer(COURSE));

        doThrow(new ResourceNotFoundException("Resource not available")).when(learnerStates).readLearnerState(COURSE);
        assertThrows(ResourceNotFoundException.class, () -> service.readPlacementOffer(COURSE));

        verifyNoInteractions(skills, masteries, attempts);
    }

    @Test
    void rejectsNullCourseIdAndCorruptProviderOutputs() {
        assertThrows(IllegalArgumentException.class, () -> service.readPlacementOffer((UUID) null));
        verifyNoInteractions(learnerStates, skills, masteries, attempts);

        when(learnerStates.readLearnerState(COURSE)).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service.readPlacementOffer(COURSE));

        when(learnerStates.readLearnerState(COURSE))
                .thenReturn(new LearnerCourseState(UUID.randomUUID(), LEARNER, ENROLLMENT, "active", true, POLICY, List.of()));
        assertThrows(IllegalStateException.class, () -> service.readPlacementOffer(COURSE));

        when(learnerStates.readLearnerState(COURSE)).thenReturn(state("active", true, POLICY));
        when(skills.findSkills(COURSE)).thenReturn(List.of(new Skill(SKILL_1, UUID.randomUUID(), "Foreign", null)));
        assertThrows(IllegalStateException.class, () -> service.readPlacementOffer(COURSE));

        when(skills.findSkills(COURSE)).thenReturn(Arrays.asList(
                new Skill(SKILL_1, COURSE, "Variables", null),
                new Skill(SKILL_1, COURSE, "Duplicate", null)));
        assertThrows(IllegalStateException.class, () -> service.readPlacementOffer(COURSE));

        when(skills.findSkills(COURSE)).thenReturn(List.of(new Skill(SKILL_1, COURSE, "Variables", null)));
        when(masteries.findStates(LEARNER, Set.of(SKILL_1))).thenReturn(Map.of(SKILL_2, MasteryState.LEARNING));
        assertThrows(IllegalStateException.class, () -> service.readPlacementOffer(COURSE));

        Map<UUID, MasteryState> nullState = new HashMap<>();
        nullState.put(SKILL_1, null);
        when(masteries.findStates(LEARNER, Set.of(SKILL_1))).thenReturn(nullState);
        assertThrows(IllegalStateException.class, () -> service.readPlacementOffer(COURSE));

        verifyNoInteractions(attempts);
    }

    @Test
    void placementOfferEnforcesSynchronizedOfferedAndQuizIdInvariant() {
        assertThrows(IllegalArgumentException.class, () -> new PlacementOffer(true, null));
        assertThrows(IllegalArgumentException.class, () -> new PlacementOffer(false, DIAGNOSTIC_QUIZ));
    }

    @Nested
    class SnapshotPrecondition {

        @Test
        void refusesCallFromStateWithoutReadOnlyRepeatableReadSnapshot() {
            clearTransactionSynchronization();
            LearnerCourseState admitted = state("active", true, POLICY);

            assertThrows(IllegalStateException.class, () -> service.readPlacementOffer(admitted));

            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Isolation.REPEATABLE_READ.value());
            assertThrows(IllegalStateException.class, () -> service.readPlacementOffer(admitted));

            TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
            TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Isolation.READ_COMMITTED.value());
            assertThrows(IllegalStateException.class, () -> service.readPlacementOffer(admitted));

            verifyNoInteractions(learnerStates, skills, masteries, attempts);
        }

        @Test
        void joinsCallerSnapshotAndReAdmitsPublicStateBeforeReadingMastery() {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
            TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Isolation.REPEATABLE_READ.value());

            when(learnerStates.readLearnerState(COURSE)).thenReturn(state("active", true, POLICY));
            when(skills.findSkills(COURSE)).thenReturn(List.of(new Skill(SKILL_1, COURSE, "Variables", null)));
            when(masteries.findStates(LEARNER, Set.of(SKILL_1))).thenReturn(Map.of());
            when(attempts.readMyPlacementQuizId(COURSE)).thenReturn(DIAGNOSTIC_QUIZ);

            PlacementOffer offer = service.readPlacementOffer(state("active", true, POLICY));

            assertEquals(new PlacementOffer(true, DIAGNOSTIC_QUIZ), offer);
            org.mockito.Mockito.verify(learnerStates).readLearnerState(COURSE);
        }
    }

    @Test
    void publicStateCannotReadAnotherLearnerOrStaleConfiguration() {
        when(learnerStates.readLearnerState(COURSE)).thenReturn(state("active", true, POLICY));
        for (var supplied : List.of(
                new LearnerCourseState(COURSE, SKILL_1, ENROLLMENT, "active", true, POLICY, List.of()),
                new LearnerCourseState(COURSE, LEARNER, SKILL_1, "active", true, POLICY, List.of()),
                state("completed", true, POLICY), state("active", false, POLICY), state("active", true, null))) {
            assertThrows(IllegalStateException.class, () -> service.readPlacementOffer(supplied));
        }
        verifyNoInteractions(skills, masteries, attempts);
    }

    @Test
    void publicStateRechecksAdmissionEvenWhenSuppliedStateWouldSuppressOffer() {
        doThrow(new AccessDeniedException("Access denied")).when(learnerStates).readLearnerState(COURSE);
        assertThrows(AccessDeniedException.class, () -> service.readPlacementOffer(state("active", false, null)));
        verifyNoInteractions(skills, masteries, attempts);
    }

    @Test
    void courseEntryRefusesAnIncompatibleExistingTransactionBeforeAnyProviderRead() {
        clearTransactionSynchronization();
        assertThrows(IllegalStateException.class, () -> service.readPlacementOffer(COURSE));
        reportingSnapshot();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        assertThrows(IllegalStateException.class, () -> service.readPlacementOffer(COURSE));
        verifyNoInteractions(learnerStates, skills, masteries, attempts);
    }

    @Test
    void placementAndGuidanceHaveOnlyTheApprovedProductionCallers() throws Exception {
        var root = java.nio.file.Path.of("src/main/java/com/codemastery");
        var allowed = Map.of(
                "readMyPlacementQuizId", Set.of("modules/adaptive/application/mastery/PlacementDiagnosticService.java"),
                "readPlacementOffer", Set.of("modules/adaptive/application/mastery/MasteryQueryService.java"),
                "readAdaptiveGuidance", Set.of("modules/adaptive/api/controller/MasteryController.java"),
                "requireLearnerMappedLessonsInCourse", Set.of("modules/adaptive/application/mastery/MasteryQueryService.java"),
                "findCurrentGuidance", Set.of("modules/adaptive/application/mastery/MasteryQueryService.java"));
        Map<String, Set<String>> actual = new HashMap<>();
        allowed.keySet().forEach(name -> actual.put(name, new java.util.TreeSet<>()));
        try (var files = java.nio.file.Files.walk(root)) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (var call : calledMethods(java.nio.file.Files.readString(file))) {
                    if (actual.containsKey(call)) actual.get(call).add(root.relativize(file).toString());
                }
            }
        }
        assertEquals(allowed, actual);
        assertTrue(calledMethods("class C { void x(){ service.readPlacementOffer(s); } }").contains("readPlacementOffer"));
        assertTrue(calledMethods("class C { Object x = service::readPlacementOffer; }").contains("readPlacementOffer"));
        assertFalse(calledMethods("class C { void readPlacementOffer(){} String x = \"readPlacementOffer()\"; }").contains("readPlacementOffer"));
    }

    private static Set<String> calledMethods(String code) throws Exception {
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        var source = new javax.tools.SimpleJavaFileObject(java.net.URI.create("string:///Caller.java"),
                javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreErrors) { return code; }
        };
        try (var manager = compiler.getStandardFileManager(null, null, java.nio.charset.StandardCharsets.UTF_8)) {
            var task = (com.sun.source.util.JavacTask) compiler.getTask(null, manager, null,
                    List.of("-proc:none"), null, List.of(source));
            Set<String> calls = new java.util.HashSet<>();
            var scanner = new com.sun.source.util.TreeScanner<Void, Void>() {
                @Override public Void visitMethodInvocation(com.sun.source.tree.MethodInvocationTree node, Void unused) {
                    var select = node.getMethodSelect();
                    calls.add(select instanceof com.sun.source.tree.MemberSelectTree member
                            ? member.getIdentifier().toString() : select.toString());
                    return super.visitMethodInvocation(node, unused);
                }
                @Override public Void visitMemberReference(com.sun.source.tree.MemberReferenceTree node, Void unused) {
                    calls.add(node.getName().toString());
                    return super.visitMemberReference(node, unused);
                }
            };
            for (var tree : task.parse()) scanner.scan(tree, null);
            return calls;
        }
    }

    private static LearnerCourseState state(String membership, boolean adaptiveEnabled, Map<String, Object> policy) {
        return new LearnerCourseState(COURSE, LEARNER, ENROLLMENT, membership, adaptiveEnabled, policy, List.of());
    }
}
