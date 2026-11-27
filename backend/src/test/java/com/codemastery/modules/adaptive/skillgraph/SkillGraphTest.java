package com.codemastery.modules.adaptive.skillgraph;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService;
import com.codemastery.modules.adaptive.domain.model.LessonSkill;
import com.codemastery.modules.adaptive.domain.model.Skill;
import com.codemastery.modules.adaptive.domain.model.SkillGraph;
import com.codemastery.modules.adaptive.domain.model.SkillPrerequisite;
import com.codemastery.modules.adaptive.domain.repository.SkillRepository;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.CourseManagementService;
import com.codemastery.modules.course.application.service.CourseStructureService;
import com.codemastery.modules.course.application.service.CourseStructureService.LessonReference;
import com.codemastery.modules.course.application.service.CourseStructureService.StructureSnapshot;
import com.codemastery.support.fixtures.SkillGraphFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SkillGraphTest {

    private static final UUID COURSE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_COURSE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SKILL_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SKILL_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID SKILL_C = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID LESSON = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    private static Skill skill(UUID id) {
        return new Skill(id, COURSE, "skill " + id, null);
    }

    private static SkillPrerequisite edge(UUID skillId, UUID prerequisiteSkillId) {
        return new SkillPrerequisite(UUID.randomUUID(), COURSE, skillId, prerequisiteSkillId);
    }

    /** FT02 structural consumers only; these local sentinels are not CT15 datasets or CT36 oracles. */
    @Nested
    class StructuralFixtures {

        private static final UUID EDGE = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
        private static final UUID MAPPING = UUID.fromString("ffffffff-0000-0000-0000-000000000006");

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void preservesSkillValuesAndNullableDescription(boolean withDescription) {
            String description = withDescription ? "Caller description" : null;
            Skill value = SkillGraphFixtures.skill(SKILL_A, COURSE, " Caller name ", description);

            assertAll(
                    () -> assertEquals(SKILL_A, value.id()),
                    () -> assertEquals(COURSE, value.courseId()),
                    () -> assertEquals(" Caller name ", value.name()),
                    () -> assertEquals(description, value.description()));
            assertEquals(value, SkillGraphFixtures.skillBuilder().id(SKILL_A).courseId(COURSE)
                    .name(" Caller name ").description(description).build());
            if (!withDescription) {
                assertEquals(value, SkillGraphFixtures.skill(SKILL_A, COURSE, " Caller name "));
            }
        }

        @Test
        void preservesCourseQualifiedPrerequisiteDirectionThroughFactoryAndBuilder() {
            SkillPrerequisite value = SkillGraphFixtures.skillPrerequisite(EDGE, COURSE, SKILL_B, SKILL_A);

            assertAll(
                    () -> assertEquals(EDGE, value.id()),
                    () -> assertEquals(COURSE, value.courseId()),
                    () -> assertEquals(SKILL_B, value.skillId()),
                    () -> assertEquals(SKILL_A, value.prerequisiteSkillId()));
            assertEquals(value, SkillGraphFixtures.skillPrerequisiteBuilder().id(EDGE).courseId(COURSE)
                    .skillId(SKILL_B).prerequisiteSkillId(SKILL_A).build());
        }

        @Test
        void preservesLessonMappingIdentityAndReferencesThroughFactoryAndBuilder() {
            LessonSkill value = SkillGraphFixtures.lessonSkill(MAPPING, LESSON, SKILL_B);

            assertAll(
                    () -> assertEquals(MAPPING, value.id()),
                    () -> assertEquals(LESSON, value.lessonId()),
                    () -> assertEquals(SKILL_B, value.skillId()));
            assertEquals(value, SkillGraphFixtures.lessonSkillBuilder().id(MAPPING)
                    .lessonId(LESSON).skillId(SKILL_B).build());
        }

        @Test
        void referenceBuildersReplaceEveryFieldWithoutChangingEarlierRecords() {
            var skills = SkillGraphFixtures.skillBuilder().id(SKILL_A).courseId(COURSE)
                    .name("First").description("First description");
            var prerequisites = SkillGraphFixtures.skillPrerequisiteBuilder().id(EDGE).courseId(COURSE)
                    .skillId(SKILL_B).prerequisiteSkillId(SKILL_A);
            var mappings = SkillGraphFixtures.lessonSkillBuilder().id(MAPPING).lessonId(LESSON).skillId(SKILL_B);
            Skill firstSkill = skills.build();
            SkillPrerequisite firstEdge = prerequisites.build();
            LessonSkill firstMapping = mappings.build();

            assertAll(
                    () -> assertEquals(new Skill(SKILL_C, OTHER_COURSE, "Second", null),
                            skills.id(SKILL_C).courseId(OTHER_COURSE).name("Second").description(null).build()),
                    () -> assertEquals(new SkillPrerequisite(MAPPING, OTHER_COURSE, SKILL_C, SKILL_B),
                            prerequisites.id(MAPPING).courseId(OTHER_COURSE)
                                    .skillId(SKILL_C).prerequisiteSkillId(SKILL_B).build()),
                    () -> assertEquals(new LessonSkill(EDGE, SKILL_C, SKILL_A),
                            mappings.id(EDGE).lessonId(SKILL_C).skillId(SKILL_A).build()),
                    () -> assertEquals(new Skill(SKILL_A, COURSE, "First", "First description"), firstSkill),
                    () -> assertEquals(new SkillPrerequisite(EDGE, COURSE, SKILL_B, SKILL_A), firstEdge),
                    () -> assertEquals(new LessonSkill(MAPPING, LESSON, SKILL_B), firstMapping));
        }

        @Test
        void referenceOnlyConstructionAddsNoDefaultsOrGraphValidation() {
            // Unchecked records support incomplete inputs; this does not assert database validity.
            assertAll(
                    () -> assertEquals(new Skill(null, null, null, null),
                            SkillGraphFixtures.skill(null, null, null, null)),
                    () -> assertEquals(new Skill(null, null, null, null), SkillGraphFixtures.skillBuilder().build()),
                    () -> assertEquals(new SkillPrerequisite(null, null, null, null),
                            SkillGraphFixtures.skillPrerequisite(null, null, null, null)),
                    () -> assertEquals(new SkillPrerequisite(null, null, null, null),
                            SkillGraphFixtures.skillPrerequisiteBuilder().build()),
                    () -> assertEquals(new LessonSkill(null, null, null),
                            SkillGraphFixtures.lessonSkill(null, null, null)),
                    () -> assertEquals(new LessonSkill(null, null, null), SkillGraphFixtures.lessonSkillBuilder().build()));
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void graphHelpersForwardReferencesIntoAnImmutableContainer(boolean useBuilder) {
            Skill first = SkillGraphFixtures.skill(SKILL_A, COURSE, "A", null);
            Skill second = SkillGraphFixtures.skill(SKILL_B, COURSE, "B", "Description");
            SkillPrerequisite prerequisite = SkillGraphFixtures.skillPrerequisite(EDGE, COURSE, SKILL_B, SKILL_A);
            LessonSkill mapping = SkillGraphFixtures.lessonSkill(MAPPING, LESSON, SKILL_B);
            List<Skill> skills = new ArrayList<>(List.of(second, first));
            List<SkillPrerequisite> prerequisites = new ArrayList<>(List.of(prerequisite));
            List<LessonSkill> mappings = new ArrayList<>(List.of(mapping));

            SkillGraph graph = useBuilder
                    ? SkillGraphFixtures.skillGraphBuilder().courseId(COURSE).skills(skills)
                            .prerequisites(prerequisites).mappings(mappings).build()
                    : SkillGraphFixtures.skillGraph(COURSE, skills, prerequisites, mappings);
            skills.clear();
            prerequisites.clear();
            mappings.clear();

            assertAll(
                    () -> assertEquals(COURSE, graph.courseId()),
                    () -> assertEquals(2, graph.skills().size()),
                    () -> assertSame(first, graph.skills().stream().filter(s -> s.id().equals(SKILL_A)).findFirst().orElseThrow()),
                    () -> assertSame(second, graph.skills().stream().filter(s -> s.id().equals(SKILL_B)).findFirst().orElseThrow()),
                    () -> assertEquals(List.of(prerequisite), graph.prerequisites()),
                    () -> assertSame(prerequisite, graph.prerequisites().getFirst()),
                    () -> assertEquals(List.of(mapping), graph.mappings()),
                    () -> assertSame(mapping, graph.mappings().getFirst()),
                    () -> assertThrows(UnsupportedOperationException.class, () -> graph.skills().add(first)),
                    () -> assertThrows(UnsupportedOperationException.class, () -> graph.prerequisites().add(prerequisite)),
                    () -> assertThrows(UnsupportedOperationException.class, () -> graph.mappings().add(mapping)));
            assertNull(graph.skills().stream().filter(s -> s.id().equals(SKILL_A)).findFirst().orElseThrow().description());
        }

        @Test
        void emptyFactoryAndDefaultBuilderCreateValidEmptyDrafts() {
            for (SkillGraph graph : List.of(SkillGraphFixtures.emptySkillGraph(COURSE),
                    SkillGraphFixtures.skillGraphBuilder().courseId(COURSE).build(),
                    SkillGraphFixtures.skillGraph(COURSE, List.of(), List.of(), List.of()))) {
                assertAll(
                        () -> assertEquals(COURSE, graph.courseId()),
                        () -> assertEquals(List.of(), graph.skills()),
                        () -> assertEquals(List.of(), graph.prerequisites()),
                        () -> assertEquals(List.of(), graph.mappings()));
            }
        }

        @Test
        void graphBuilderReplacesCourseAndCollectionsWithoutChangingEarlierGraph() {
            Skill original = SkillGraphFixtures.skill(SKILL_A, COURSE, "Original");
            Skill prerequisite = SkillGraphFixtures.skill(SKILL_B, COURSE, "Prerequisite");
            Skill replacement = SkillGraphFixtures.skill(SKILL_C, OTHER_COURSE, "Replacement");
            SkillPrerequisite edge = SkillGraphFixtures.skillPrerequisite(EDGE, COURSE, SKILL_A, SKILL_B);
            var builder = SkillGraphFixtures.skillGraphBuilder().courseId(COURSE).skills(List.of(original, prerequisite))
                    .prerequisites(List.of(edge))
                    .mappings(List.of(SkillGraphFixtures.lessonSkill(MAPPING, LESSON, SKILL_A)));
            SkillGraph first = builder.build();
            SkillGraph second = builder.courseId(OTHER_COURSE).skills(List.of(replacement))
                    .prerequisites(List.of()).mappings(List.of()).build();

            assertAll(
                    () -> assertEquals(COURSE, first.courseId()),
                    () -> assertEquals(List.of(original, prerequisite), first.skills()),
                    () -> assertEquals(List.of(edge), first.prerequisites()),
                    () -> assertEquals(List.of(new LessonSkill(MAPPING, LESSON, SKILL_A)), first.mappings()),
                    () -> assertEquals(OTHER_COURSE, second.courseId()),
                    () -> assertEquals(List.of(replacement), second.skills()),
                    () -> assertEquals(List.of(), second.prerequisites()),
                    () -> assertEquals(List.of(), second.mappings()));
        }

        @ParameterizedTest
        @ValueSource(strings = {"courseId", "skills", "prerequisites", "mappings"})
        void explicitNullGraphInputsReachProductionValidation(String input) {
            UUID courseId = input.equals("courseId") ? null : COURSE;
            List<Skill> skills = input.equals("skills") ? null : List.of();
            List<SkillPrerequisite> prerequisites = input.equals("prerequisites") ? null : List.of();
            List<LessonSkill> mappings = input.equals("mappings") ? null : List.of();

            // Null forwarding only: the Representation group owns the graph-invariant oracles.
            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> SkillGraphFixtures.skillGraph(courseId, skills, prerequisites, mappings)),
                    () -> assertThrows(NullPointerException.class,
                            () -> SkillGraphFixtures.skillGraphBuilder().courseId(courseId).skills(skills)
                                    .prerequisites(prerequisites).mappings(mappings).build()));
            if (input.equals("courseId")) {
                assertThrows(NullPointerException.class, () -> SkillGraphFixtures.emptySkillGraph(null));
            }
        }
    }

    @Nested
    class Representation {

        @Test
        void rejectsDirectCycle() {
            assertThrows(SkillGraph.CycleException.class, () -> new SkillGraph(COURSE,
                    List.of(skill(SKILL_A), skill(SKILL_B)),
                    List.of(edge(SKILL_A, SKILL_B), edge(SKILL_B, SKILL_A)), List.of()));
        }

        @Test
        void rejectsTransitiveCycle() {
            assertThrows(SkillGraph.CycleException.class, () -> new SkillGraph(COURSE,
                    List.of(skill(SKILL_A), skill(SKILL_B), skill(SKILL_C)),
                    List.of(edge(SKILL_A, SKILL_B), edge(SKILL_B, SKILL_C), edge(SKILL_C, SKILL_A)),
                    List.of()));
        }

        @Test
        void rejectsSelfEdge() {
            assertThrows(IllegalArgumentException.class, () -> new SkillGraph(COURSE,
                    List.of(skill(SKILL_A)), List.of(edge(SKILL_A, SKILL_A)), List.of()));
        }

        @Test
        void rejectsForeignCourseSkill() {
            assertThrows(IllegalArgumentException.class, () -> new SkillGraph(COURSE,
                    List.of(new Skill(SKILL_A, OTHER_COURSE, "foreign", null)), List.of(), List.of()));
        }

        @Test
        void rejectsDuplicateIdentityAndDuplicatePairs() {
            assertThrows(IllegalArgumentException.class, () -> new SkillGraph(COURSE,
                    List.of(skill(SKILL_A), skill(SKILL_A)), List.of(), List.of()));
            assertThrows(IllegalArgumentException.class, () -> new SkillGraph(COURSE,
                    List.of(skill(SKILL_A), skill(SKILL_B)),
                    List.of(edge(SKILL_A, SKILL_B), edge(SKILL_A, SKILL_B)), List.of()));
            assertThrows(IllegalArgumentException.class, () -> new SkillGraph(COURSE,
                    List.of(skill(SKILL_A)),
                    List.of(), List.of(new LessonSkill(UUID.randomUUID(), LESSON, SKILL_A),
                            new LessonSkill(UUID.randomUUID(), LESSON, SKILL_A))));
        }

        @Test
        void rejectsEdgeAndMappingThatNameAnUnknownSkill() {
            assertThrows(IllegalArgumentException.class, () -> new SkillGraph(COURSE,
                    List.of(skill(SKILL_A)), List.of(edge(SKILL_A, SKILL_B)), List.of()));
            assertThrows(IllegalArgumentException.class, () -> new SkillGraph(COURSE,
                    List.of(skill(SKILL_A)), List.of(),
                    List.of(new LessonSkill(UUID.randomUUID(), LESSON, SKILL_B))));
        }

        @Test
        void sortsSkillsAndPairsByLowercaseUuidText() {
            var graph = new SkillGraph(COURSE,
                    List.of(skill(SKILL_C), skill(SKILL_A), skill(SKILL_B)),
                    List.of(edge(SKILL_C, SKILL_B), edge(SKILL_C, SKILL_A)),
                    List.of(new LessonSkill(UUID.randomUUID(), LESSON, SKILL_C)));

            assertEquals(List.of(SKILL_A, SKILL_B, SKILL_C),
                    graph.skills().stream().map(Skill::id).toList());
            assertEquals(List.of(SKILL_A, SKILL_B),
                    graph.prerequisites().stream().map(SkillPrerequisite::prerequisiteSkillId).toList());
        }

        @Test
        void anArbitrarilyLongPathStillRejectsTheClosingEdgeWithoutExhaustingTheStack() {
            int length = 50_000;
            List<Skill> chain = new ArrayList<>();
            List<SkillPrerequisite> edges = new ArrayList<>();
            for (int index = 0; index < length; index++) {
                chain.add(skill(new UUID(7, index)));
                if (index > 0) {
                    edges.add(edge(new UUID(7, index - 1), new UUID(7, index)));
                }
            }

            assertEquals(length - 1, new SkillGraph(COURSE, chain, edges, List.of()).prerequisites().size());
            edges.add(edge(new UUID(7, length - 1), new UUID(7, 0)));
            assertThrows(SkillGraph.CycleException.class, () -> new SkillGraph(COURSE, chain, edges, List.of()));
        }

        @Test
        void aDiamondIsAcyclicAndAReverseEdgeAnywhereOnItIsACycle() {
            UUID d = UUID.fromString("dddddddd-0000-0000-0000-00000000000d");
            List<Skill> nodes = List.of(skill(SKILL_A), skill(SKILL_B), skill(SKILL_C), skill(d));
            List<SkillPrerequisite> diamond = List.of(edge(SKILL_A, SKILL_B), edge(SKILL_A, SKILL_C),
                    edge(SKILL_B, d), edge(SKILL_C, d));

            var graph = new SkillGraph(COURSE, nodes, diamond, List.of());
            assertTrue(graph.hasDependency(SKILL_B, d));
            List<SkillPrerequisite> closing = new ArrayList<>(diamond);
            closing.add(edge(d, SKILL_A));
            assertThrows(SkillGraph.CycleException.class, () -> new SkillGraph(COURSE, nodes, closing, List.of()));
        }

        @Test
        void emptyGraphIsAValidDraft() {
            var graph = new SkillGraph(COURSE, List.of(), List.of(), List.of());

            assertEquals(COURSE, graph.courseId());
            assertEquals(List.of(), graph.skills());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Commands {

        @Mock
        private SkillRepository skills;
        @Mock
        private CourseAccessService access;
        @Mock
        private CourseManagementService courseConfiguration;
        @Mock
        private CourseStructureService structure;
        @Mock
        private PlatformTransactionManager transactions;

        private SkillGraphService service;

        @BeforeEach
        void setUp() {
            service = new SkillGraphService(skills, access, courseConfiguration, structure, transactions);
            lenient().when(courseConfiguration.<Object>withCourseConfigurationWrite(any(), any()))
                    .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        }

        private void storedAs(SkillPrerequisite... edges) {
            lenient().when(skills.findSkills(COURSE))
                    .thenReturn(List.of(skill(SKILL_A), skill(SKILL_B), skill(SKILL_C)));
            lenient().when(skills.findPrerequisites(COURSE)).thenReturn(List.of(edges));
            lenient().when(skills.findMappingsForSkills(any())).thenReturn(List.of());
        }

        @Test
        void addingAClosingEdgeIsRefusedAndWritesNothing() {
            storedAs(edge(SKILL_A, SKILL_B), edge(SKILL_B, SKILL_C));

            var refused = assertThrows(BusinessRuleException.class,
                    () -> service.addPrerequisite(COURSE, SKILL_C, SKILL_A));

            assertEquals(409, refused.getStatus().value());
            verify(skills, never()).insertPrerequisite(any());
        }

        @Test
        void aSelfEdgeIsBadRequestBeforeAnyLockOrRead() {
            var refused = assertThrows(BusinessRuleException.class,
                    () -> service.addPrerequisite(COURSE, SKILL_A, SKILL_A));

            assertEquals(400, refused.getStatus().value());
            verify(courseConfiguration, never()).withCourseConfigurationWrite(any(), any());
            verify(skills, never()).insertPrerequisite(any());
        }

        @Test
        void anUnknownEndpointIsConcealedNotFoundAndWritesNothing() {
            storedAs();

            assertThrows(ResourceNotFoundException.class,
                    () -> service.addPrerequisite(COURSE, SKILL_A, UUID.randomUUID()));
            verify(skills, never()).insertPrerequisite(any());
        }

        @Test
        void anExistingEdgeIsANoOp() {
            storedAs(edge(SKILL_A, SKILL_B));

            service.addPrerequisite(COURSE, SKILL_A, SKILL_B);

            verify(skills, never()).insertPrerequisite(any());
        }

        @Test
        void aNewAcyclicEdgeIsInsertedOnce() {
            storedAs();

            service.addPrerequisite(COURSE, SKILL_A, SKILL_B);

            verify(skills).insertPrerequisite(any(SkillPrerequisite.class));
        }

        @Test
        void removingAnAbsentEdgeIsANoOpAfterValidatingBothEndpoints() {
            storedAs();

            service.removePrerequisite(COURSE, SKILL_A, SKILL_B);

            verify(skills, never()).deletePrerequisite(any(), any(), any());
        }

        @Test
        void mappingCallsCourseAncestryThenTheCourseScopedSkill() {
            when(skills.findSkill(COURSE, SKILL_A)).thenReturn(Optional.of(skill(SKILL_A)));
            when(skills.findMapping(LESSON, SKILL_A)).thenReturn(Optional.empty());

            service.mapLesson(COURSE, LESSON, SKILL_A);

            verify(access).requireLessonOwnership(COURSE, LESSON);
            verify(skills).insertMapping(any(LessonSkill.class));
        }

        @Test
        void mappingAForeignSkillIsConcealedNotFound() {
            when(skills.findSkill(COURSE, SKILL_A)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.mapLesson(COURSE, LESSON, SKILL_A));
            verify(skills, never()).insertMapping(any());
        }

        @Test
        void mappingAnExistingPairIsANoOp() {
            when(skills.findSkill(COURSE, SKILL_A)).thenReturn(Optional.of(skill(SKILL_A)));
            when(skills.findMapping(LESSON, SKILL_A))
                    .thenReturn(Optional.of(new LessonSkill(UUID.randomUUID(), LESSON, SKILL_A)));

            service.mapLesson(COURSE, LESSON, SKILL_A);

            verify(skills, never()).insertMapping(any());
        }

        @Test
        void deletingAReferencedSkillConflictsWithoutDeleting() {
            storedAs(edge(SKILL_A, SKILL_B));

            var refused = assertThrows(BusinessRuleException.class, () -> service.deleteSkill(COURSE, SKILL_A));

            assertEquals(409, refused.getStatus().value());
            verify(skills, never()).deleteSkill(any(), any());
        }

        @Test
        void deletingAMissingSkillIsConcealedNotFound() {
            storedAs();

            assertThrows(ResourceNotFoundException.class,
                    () -> service.deleteSkill(COURSE, UUID.randomUUID()));
            verify(skills, never()).deleteSkill(any(), any());
        }

        @Test
        void deletingAnUnreferencedSkillProceeds() {
            storedAs();
            lenient().when(skills.findMappingsForSkills(any())).thenReturn(List.of());

            service.deleteSkill(COURSE, SKILL_C);

            verify(skills).deleteSkill(COURSE, SKILL_C);
        }

        @Test
        void aCorruptPersistedCycleFailsClosedAsServerError() {
            lenient().when(skills.findSkills(COURSE)).thenReturn(List.of(skill(SKILL_A), skill(SKILL_B)));
            lenient().when(skills.findPrerequisites(COURSE))
                    .thenReturn(List.of(edge(SKILL_A, SKILL_B), edge(SKILL_B, SKILL_A)));
            lenient().when(skills.findMappingsForSkills(any())).thenReturn(List.of());

            assertThrows(IllegalStateException.class, () -> service.deleteSkill(COURSE, SKILL_A));
        }

        @Test
        void skillIdsAreCompleteCourseScopedAndSorted() {
            when(skills.findSkills(COURSE)).thenReturn(List.of(skill(SKILL_C), skill(SKILL_A)));

            assertEquals(List.of(SKILL_A, SKILL_C), service.readSkillIds(COURSE));
            verify(access).requireCourseOwnership(COURSE);
        }

        @Test
        void requireSkillInCourseAcceptsOnlyTheSameCourseSkill() {
            when(skills.findSkill(COURSE, SKILL_A)).thenReturn(Optional.of(skill(SKILL_A)));

            service.requireSkillInCourse(COURSE, SKILL_A);

            verify(access).requireCourseOwnership(COURSE);
        }

        @Test
        void requireSkillInCourseConcealsAForeignSkill() {
            when(skills.findSkill(COURSE, SKILL_A)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.requireSkillInCourse(COURSE, SKILL_A));
        }

        @ParameterizedTest
        @ValueSource(strings = {"\u2003", "\u00a0", "\u3000 \u2028", "\ufeff", "\u0085", "\u001c", "ok\u0000"})
        void textBlankUnderAnyWhitespaceRuleOrHoldingNulIsBadRequestBeforeTheGuard(String text) {
            var name = assertThrows(BusinessRuleException.class, () -> service.createSkill(COURSE, text, null));
            var description = assertThrows(BusinessRuleException.class,
                    () -> service.updateSkill(COURSE, SKILL_A, "Loops", text));

            assertEquals(400, name.getStatus().value());
            assertEquals(400, description.getStatus().value());
            verify(courseConfiguration, never()).withCourseConfigurationWrite(any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"\ud800", "\udc00", "Loops \ud83d", "\ude00 Loops", "A\udbff\udbffB", "B\udc00\ud800C",
                "😀\ud800"})
        void anUnpairedSurrogateIsUnstorableTextAndBadRequestBeforeTheGuard(String text) {
            var name = assertThrows(BusinessRuleException.class, () -> service.createSkill(COURSE, text, null));
            var description = assertThrows(BusinessRuleException.class,
                    () -> service.updateSkill(COURSE, SKILL_A, "Loops", text));
            var renamed = assertThrows(BusinessRuleException.class,
                    () -> service.updateSkill(COURSE, SKILL_A, text, null));
            var described = assertThrows(BusinessRuleException.class,
                    () -> service.createSkill(COURSE, "Loops", text));

            for (var refused : List.of(name, description, renamed, described)) {
                assertEquals(400, refused.getStatus().value());
            }
            verify(courseConfiguration, never()).withCourseConfigurationWrite(any(), any());
            verify(skills, never()).insertSkill(any());
            verify(skills, never()).updateSkill(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"😀", "Graphs 𝔸", "􏿿", "᠎", "é́ 𐀀"})
        void validSurrogatePairsAndNonSpaceCharactersAreStoredUnchanged(String text) {
            when(skills.insertSkill(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(skills.findSkill(COURSE, SKILL_A)).thenReturn(Optional.of(skill(SKILL_A)));
            when(skills.updateSkill(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));

            var created = service.createSkill(COURSE, text, text);
            var updated = service.updateSkill(COURSE, SKILL_A, text, text);

            assertEquals(text, created.name());
            assertEquals(text, created.description());
            assertEquals(text, updated.name());
            assertEquals(text, updated.description());
        }

        @Test
        void validTextAndANullDescriptionAreStoredUnderTheGuard() {
            when(skills.insertSkill(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));

            var created = service.createSkill(COURSE, "  Loops\u00e9 ", null);

            assertEquals("  Loops\u00e9 ", created.name());
            assertEquals(null, created.description());
            verify(courseConfiguration).withCourseConfigurationWrite(any(), any());
        }

        @Test
        void theOwnerReadAdmitsAndLoadsInsideOneReadOnlyRepeatableReadSnapshot() {
            storedAs(edge(SKILL_B, SKILL_A));
            var order = inOrder(transactions, access, skills);

            var graph = service.readGraph(COURSE);

            ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
            order.verify(transactions).getTransaction(definition.capture());
            order.verify(access).requireCourseOwnership(COURSE);
            order.verify(skills).findSkills(COURSE);
            order.verify(transactions).commit(any());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ, definition.getValue().getIsolationLevel());
            assertTrue(definition.getValue().isReadOnly());
            assertEquals(List.of(SKILL_A, SKILL_B, SKILL_C),
                    graph.skills().stream().map(SkillGraphService.SkillReference::id).toList());
            assertEquals(List.of(new SkillGraphService.PrerequisiteReference(SKILL_B, SKILL_A)), graph.prerequisites());
        }

        @Test
        void aCorruptPersistedGraphIsAServerErrorOnReadNeverARepairedGraph() {
            lenient().when(skills.findSkills(COURSE)).thenReturn(List.of(skill(SKILL_A)));
            lenient().when(skills.findPrerequisites(COURSE)).thenReturn(List.of(edge(SKILL_A, SKILL_B)));
            lenient().when(skills.findMappingsForSkills(any())).thenReturn(List.of());

            assertThrows(IllegalStateException.class, () -> service.readGraph(COURSE));
        }

        @Test
        void theClosingEdgeOfAnArbitrarilyLongStoredPathIsRefused() {
            List<Skill> chain = new ArrayList<>();
            List<SkillPrerequisite> edges = new ArrayList<>();
            for (int index = 0; index < 5_000; index++) {
                chain.add(skill(new UUID(9, index)));
                if (index > 0) {
                    edges.add(edge(new UUID(9, index - 1), new UUID(9, index)));
                }
            }
            when(skills.findSkills(COURSE)).thenReturn(chain);
            when(skills.findPrerequisites(COURSE)).thenReturn(edges);
            when(skills.findMappingsForSkills(any())).thenReturn(List.of());

            var refused = assertThrows(BusinessRuleException.class,
                    () -> service.addPrerequisite(COURSE, new UUID(9, 4_999), new UUID(9, 0)));

            assertEquals(409, refused.getStatus().value());
            verify(skills, never()).insertPrerequisite(any());
        }

        @Test
        void removingAPresentEdgeDeletesOnlyThatScopedPair() {
            storedAs(edge(SKILL_A, SKILL_B), edge(SKILL_B, SKILL_C));

            service.removePrerequisite(COURSE, SKILL_A, SKILL_B);

            verify(skills).deletePrerequisite(COURSE, SKILL_A, SKILL_B);
            verify(skills, never()).deletePrerequisite(COURSE, SKILL_B, SKILL_C);
        }

        @Test
        void unmappingAForeignLessonIsConcealedBeforeAnySkillLookupOrWrite() {
            doThrow(new ResourceNotFoundException("Resource not available"))
                    .when(access).requireLessonOwnership(COURSE, LESSON);

            assertThrows(ResourceNotFoundException.class, () -> service.unmapLesson(COURSE, LESSON, SKILL_A));
            verify(skills, never()).findSkill(any(), any());
            verify(skills, never()).deleteMapping(any(), any());
        }

        // CT40 structural projection (ADR005 §§3.1, 4.1): every assembled graph proves its mappings.

        private void mappedAs(LessonSkill... mappings) {
            lenient().when(skills.findSkills(COURSE)).thenReturn(List.of(skill(SKILL_A), skill(SKILL_B)));
            lenient().when(skills.findPrerequisites(COURSE)).thenReturn(List.of());
            lenient().when(skills.findMappingsForSkills(any())).thenReturn(List.of(mappings));
        }

        private static LessonReference lessonRow(UUID lessonId, boolean published) {
            return new LessonReference(lessonId, UUID.randomUUID(), 1, 1, published);
        }

        @Test
        void mappingsOfTheCoursesOwnLessonsIncludingHiddenOnesAreReadAfterTheMappingsInTheSameSnapshot() {
            UUID hidden = new UUID(4, 2);
            mappedAs(new LessonSkill(UUID.randomUUID(), LESSON, SKILL_A), new LessonSkill(UUID.randomUUID(), hidden, SKILL_B));
            when(structure.readStructure(COURSE)).thenReturn(new StructureSnapshot(COURSE,
                    List.of(lessonRow(LESSON, true), lessonRow(hidden, false))));
            var order = inOrder(transactions, access, skills, structure);

            var graph = service.readGraph(COURSE);

            order.verify(transactions).getTransaction(any());
            order.verify(access).requireCourseOwnership(COURSE);
            order.verify(skills).findMappingsForSkills(any());
            order.verify(structure).readStructure(COURSE);
            order.verify(transactions).commit(any());
            assertEquals(2, graph.mappings().size());
        }

        @Test
        void aGraphWithoutMappingsNeedsNoStructuralRead() {
            mappedAs();

            service.readGraph(COURSE);

            verifyNoInteractions(structure);
        }

        @Test
        void aMappingToALessonOutsideTheCourseFailsClosedOnReadAndOnEveryGraphCommand() {
            mappedAs(new LessonSkill(UUID.randomUUID(), LESSON, SKILL_A));
            lenient().when(structure.readStructure(COURSE)).thenReturn(new StructureSnapshot(COURSE, List.of()));

            assertThrows(IllegalStateException.class, () -> service.readGraph(COURSE));
            assertThrows(IllegalStateException.class, () -> service.addPrerequisite(COURSE, SKILL_A, SKILL_B));
            assertThrows(IllegalStateException.class, () -> service.removePrerequisite(COURSE, SKILL_A, SKILL_B));
            assertThrows(IllegalStateException.class, () -> service.deleteSkill(COURSE, SKILL_B));
            verify(skills, never()).insertPrerequisite(any());
            verify(skills, never()).deletePrerequisite(any(), any(), any());
            verify(skills, never()).deleteSkill(any(), any());
        }

        @Test
        void anInconsistentOrForeignStructureProjectionIsAProviderFailureNeverAPartialGraph() {
            mappedAs(new LessonSkill(UUID.randomUUID(), LESSON, SKILL_A));

            when(structure.readStructure(COURSE)).thenReturn(new StructureSnapshot(OTHER_COURSE, List.of(lessonRow(LESSON, true))));
            assertThrows(IllegalStateException.class, () -> service.readGraph(COURSE));

            when(structure.readStructure(COURSE)).thenReturn(new StructureSnapshot(COURSE,
                    List.of(lessonRow(LESSON, true), lessonRow(LESSON, true))));
            assertThrows(IllegalStateException.class, () -> service.readGraph(COURSE));

            when(structure.readStructure(COURSE)).thenThrow(new IllegalStateException("Inconsistent Course structure"));
            assertThrows(IllegalStateException.class, () -> service.readGraph(COURSE));
        }

        @Test
        void mappingCommandsKeepTheCt03AssertionAndNeverReadTheProjection() {
            when(skills.findSkill(COURSE, SKILL_A)).thenReturn(Optional.of(skill(SKILL_A)));

            service.mapLesson(COURSE, LESSON, SKILL_A);
            service.unmapLesson(COURSE, LESSON, SKILL_A);

            verify(access, org.mockito.Mockito.times(2)).requireLessonOwnership(COURSE, LESSON);
            verifyNoInteractions(structure);
        }
    }
}
