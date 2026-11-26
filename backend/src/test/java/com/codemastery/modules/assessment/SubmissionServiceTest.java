package com.codemastery.modules.assessment;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import com.codemastery.modules.assessment.api.controller.AssignmentController;
import com.codemastery.modules.assessment.api.dto.AssignmentRequests;
import com.codemastery.modules.assessment.api.dto.AssignmentRequests.PublicationRequest;
import com.codemastery.modules.assessment.api.dto.AssignmentRequests.WriteAssignmentRequest;
import com.codemastery.modules.assessment.api.dto.AssignmentResponses.OwnedAssignmentResponse;
import com.codemastery.modules.assessment.application.service.AssignmentService;
import com.codemastery.modules.assessment.application.service.AssignmentService.AssignmentInput;
import com.codemastery.modules.assessment.application.service.AssignmentService.OwnedAssignmentView;
import com.codemastery.modules.assessment.domain.model.Assignment;
import com.codemastery.modules.assessment.domain.repository.AssignmentRepository;
import com.codemastery.modules.assessment.domain.repository.AssignmentRepository.SubmissionCounts;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.AssignmentEntity;
import com.codemastery.modules.assessment.infrastructure.persistence.mapper.AssignmentMapper;
import com.codemastery.modules.course.application.service.CourseAccessService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpMediaTypeNotSupportedException;

/**
 * CT10.rest owner assignment authoring unit evidence (ADR010 §§8, 10, 12, 14; AS09 #160).
 */
class SubmissionServiceTest {

    private AssignmentRepository repository;
    private CourseAccessService access;
    private AssignmentService service;
    private UUID courseId;
    private UUID lessonId;
    private UUID assignmentId;

    @BeforeEach
    void setUp() {
        repository = mock(AssignmentRepository.class);
        access = mock(CourseAccessService.class);
        service = new AssignmentService(repository, access);
        courseId = UUID.randomUUID();
        lessonId = UUID.randomUUID();
        assignmentId = UUID.randomUUID();
    }

    @Nested
    class RequestAndMapperRepresentations {

        @Test
        void parsesClosedWriteAndPublicationBodiesAndNormalisesRfc3339Deadlines() {
            String json = """
                    {
                      "title": "  Week 1 Project  ",
                      "instructions": "  # Instructions\\nSubmit a ZIP archive.\\n",
                      "deadlineAt": "2026-11-25t18:30:05.123456+07:00",
                      "lessonId": "%s"
                    }
                    """.formatted(lessonId.toString().toUpperCase());

            WriteAssignmentRequest parsed = AssignmentRequests.parseWrite(json).orElseThrow();
            assertAll(
                    () -> assertEquals("  Week 1 Project  ", parsed.title()),
                    () -> assertEquals("  # Instructions\nSubmit a ZIP archive.\n", parsed.instructions()),
                    () -> assertEquals(Instant.parse("2026-11-25T11:30:05.123456Z"), parsed.deadlineAt()),
                    () -> assertEquals(lessonId, parsed.lessonId()));

            WriteAssignmentRequest withoutLesson = AssignmentRequests.parseWrite("""
                    {"title":"Lab","instructions":"Body","deadlineAt":"2020-01-01T00:00:00Z","lessonId":null}
                    """).orElseThrow();
            assertNull(withoutLesson.lessonId());
            assertEquals(Instant.parse("2020-01-01T00:00:00Z"), withoutLesson.deadlineAt());

            // Trailing zeros past microsecond represent an exact microsecond instant.
            assertEquals(
                    Optional.of(Instant.parse("2026-11-25T11:30:05.123456Z")),
                    AssignmentRequests.parseMicrosecondInstant("2026-11-25T11:30:05.123456000Z"));

            assertEquals(
                    Optional.of(new PublicationRequest("published")),
                    AssignmentRequests.parsePublication("{\"publicationStatus\":\"published\"}"));
            assertEquals(
                    Optional.of(new PublicationRequest("hidden")),
                    AssignmentRequests.parsePublication("{\"publicationStatus\":\"hidden\"}"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "{}",
                "[]",
                "{\"title\":\"A\",\"instructions\":\"B\",\"deadlineAt\":\"2026-11-25T00:00:00Z\"}",
                "{\"title\":\"A\",\"instructions\":\"B\",\"deadlineAt\":\"2026-11-25T00:00:00Z\",\"lessonId\":null,\"extra\":1}",
                "{\"title\":\"A\",\"title\":\"B\",\"instructions\":\"B\",\"deadlineAt\":\"2026-11-25T00:00:00Z\",\"lessonId\":null}",
                "{\"title\":123,\"instructions\":\"B\",\"deadlineAt\":\"2026-11-25T00:00:00Z\",\"lessonId\":null}",
                "{\"title\":\"A\",\"instructions\":null,\"deadlineAt\":\"2026-11-25T00:00:00Z\",\"lessonId\":null}",
                "{\"title\":\"A\",\"instructions\":\"B\",\"deadlineAt\":\"2026-11-25T00:00:00.1234567Z\",\"lessonId\":null}",
                "{\"title\":\"A\",\"instructions\":\"B\",\"deadlineAt\":\"2026-11-25T00:00:00.000000001Z\",\"lessonId\":null}",
                "{\"title\":\"A\",\"instructions\":\"B\",\"deadlineAt\":\"2026-11-25T00:00:00\",\"lessonId\":null}",
                "{\"title\":\"A\",\"instructions\":\"B\",\"deadlineAt\":\"2026-11-25T00:00Z\",\"lessonId\":null}",
                "{\"title\":\"A\",\"instructions\":\"B\",\"deadlineAt\":\"2026-11-25T00:00:00+07\",\"lessonId\":null}",
                "{\"title\":\"A\",\"instructions\":\"B\",\"deadlineAt\":\"2026-02-30T00:00:00Z\",\"lessonId\":null}",
                "{\"title\":\"A\",\"instructions\":\"B\",\"deadlineAt\":\"2026-11-25T00:00:00Z\",\"lessonId\":\"not-a-uuid\"}",
                "{\"title\":\"A\",\"instructions\":\"B\",\"deadlineAt\":\"2026-11-25T00:00:00Z\",\"lessonId\":true}",
                "{\"title\":\"A\",\"instructions\":\"B\",\"deadlineAt\":\"2026-11-25T00:00:00Z\",\"lessonId\":null} trailing"
        })
        void rejectsMalformedOrSubMicrosecondWriteBodies(String invalidJson) {
            assertTrue(AssignmentRequests.parseWrite(invalidJson).isEmpty());
        }

        @Test
        void rejectsMalformedUtf8AndUnsupportedMediaTypes() {
            byte[] invalidUtf8 = new byte[] {(byte) 0xC3, (byte) 0x28};
            assertTrue(AssignmentRequests.parseWrite(invalidUtf8).isEmpty());
            assertTrue(AssignmentRequests.parsePublication(invalidUtf8).isEmpty());
            assertThrows(HttpMediaTypeNotSupportedException.class, () -> AssignmentRequests.requireJson(null));
            assertThrows(HttpMediaTypeNotSupportedException.class, () -> AssignmentRequests.requireJson("text/plain"));
            assertThrows(HttpMediaTypeNotSupportedException.class, () -> AssignmentRequests.requireJson("not a media type"));
        }

        @Test
        void mapperAndResponseRoundTripDomainAndDtoFields() {
            Instant deadline = Instant.parse("2026-12-01T23:59:00.654321Z");
            Assignment domain = new Assignment(
                    assignmentId, courseId, lessonId, "Homework 1", "Line 1\n  Line 2", deadline, "published");
            AssignmentEntity entity = AssignmentMapper.toEntity(domain);
            assertEquals(domain, AssignmentMapper.toDomain(entity));

            OwnedAssignmentResponse response = OwnedAssignmentResponse.from(new OwnedAssignmentView(
                    assignmentId, courseId, null, "Homework 1", "Line 1\n  Line 2", deadline, "hidden", 5, 2));
            assertAll(
                    () -> assertEquals(assignmentId, response.id()),
                    () -> assertEquals(courseId, response.courseId()),
                    () -> assertNull(response.lessonId()),
                    () -> assertEquals("2026-12-01T23:59:00.654321Z", response.deadlineAt()),
                    () -> assertEquals("hidden", response.publicationStatus()),
                    () -> assertEquals(5, response.submissionCount()),
                    () -> assertEquals(2, response.gradedCount()));
        }
    }

    /**
     * U6: the deadline range is checked on the UTC instant after the offset is applied, because
     * responses render that instant and the approved consumers decode a four-digit RFC 3339 year.
     */
    @Nested
    class RepresentableDeadlineRange {

        /** The frontend CT10 decoder's shape (assessmentAuthoringApi.ts {@code rfc3339Micros}). */
        private final java.util.regex.Pattern consumerShape = java.util.regex.Pattern.compile(
                "^(\\d{4})-(\\d{2})-(\\d{2})[Tt](\\d{2}):(\\d{2}):(\\d{2})(?:\\.\\d{1,6})?([Zz]|[+-]\\d{2}:\\d{2})$");

        @ParameterizedTest
        @ValueSource(strings = {
                "9999-12-31T23:59:59.999999Z|9999-12-31T23:59:59.999999Z",
                "9999-12-31T23:59:59.999999+00:00|9999-12-31T23:59:59.999999Z",
                "9999-12-31T23:59:59.999999000Z|9999-12-31T23:59:59.999999Z",
                "9999-12-31T18:59:59.999999-05:00|9999-12-31T23:59:59.999999Z",
                "10000-01-01T00:00:00+00:00|reject",
                "9999-12-31T23:59:59-05:00|reject",
                "9999-12-31T23:59:59.999999-00:01|reject",
                "9999-12-31T19:00:00-05:00|reject",
                "0000-01-01T00:00:00Z|0000-01-01T00:00:00Z",
                "0000-01-01T00:00:00-05:00|0000-01-01T05:00:00Z",
                "0001-01-01T03:00:00.000001+07:00|0000-12-31T20:00:00.000001Z",
                "0000-01-01T00:00:00+00:01|reject",
                "0000-01-01T04:59:59.999999+05:00|reject",
                "2026-11-25T18:30:05.123456+07:00|2026-11-25T11:30:05.123456Z"
        })
        void checksTheNormalizedUtcInstantAndRendersOnlyDecodableDeadlines(String row) {
            String input = row.substring(0, row.indexOf('|'));
            String expected = row.substring(row.indexOf('|') + 1);
            Optional<Instant> parsed = AssignmentRequests.parseMicrosecondInstant(input);
            if ("reject".equals(expected)) {
                assertTrue(parsed.isEmpty(), input);
                assertTrue(AssignmentRequests.parseWrite(
                        "{\"title\":\"A\",\"instructions\":\"B\",\"deadlineAt\":\"" + input + "\",\"lessonId\":null}")
                        .isEmpty(), input);
                return;
            }
            assertEquals(Optional.of(Instant.parse(expected)), parsed, input);
            String rendered = OwnedAssignmentResponse.from(new OwnedAssignmentView(
                    assignmentId, courseId, null, "T", "I", parsed.orElseThrow(), "hidden", 0, 0)).deadlineAt();
            assertEquals(expected, rendered);
            assertTrue(consumerShape.matcher(rendered).matches(), rendered);
        }

        @Test
        void serviceRejectsUnrepresentableInstantsOnCreateAndReplaceWithoutWriting() {
            // replace locks the row first (ADR010 §14 Stage 3), then validates the body.
            when(repository.findInCourseForUpdate(assignmentId, courseId)).thenReturn(Optional.of(new Assignment(
                    assignmentId, courseId, null, "T", "I", Instant.parse("2026-12-01T00:00:00Z"), "hidden")));
            for (Instant outside : List.of(
                    Instant.parse("9999-12-31T23:59:59.999999Z").plusNanos(1_000),
                    Instant.parse("+10000-01-01T04:59:59Z"),
                    Instant.parse("0000-01-01T00:00:00Z").minusNanos(1_000))) {
                BusinessRuleException created = assertThrows(BusinessRuleException.class,
                        () -> service.create(courseId, new AssignmentInput("T", "I", outside, null)));
                assertEquals(HttpStatus.BAD_REQUEST, created.getStatus());
                BusinessRuleException replaced = assertThrows(BusinessRuleException.class,
                        () -> service.replace(courseId, assignmentId, new AssignmentInput("T", "I", outside, null)));
                assertEquals(HttpStatus.BAD_REQUEST, replaced.getStatus());
            }
            verify(repository, never()).insert(any());
            verify(repository, never()).updateDefinition(any(), any(), any(), any(), any(), any());
            assertTrue(AssignmentService.isRepresentableDeadline(AssignmentService.EARLIEST_REPRESENTABLE_DEADLINE));
            assertTrue(AssignmentService.isRepresentableDeadline(AssignmentService.LATEST_REPRESENTABLE_DEADLINE));
            assertFalse(AssignmentService.isRepresentableDeadline(null));
        }

        @Test
        void controllerAnswers400ForTheReproducedDeadlineOnCreateAndReplace() throws Exception {
            MockMvc mvc = MockMvcBuilders.standaloneSetup(
                            new AssignmentController(service),
                            new AssignmentController.AssignmentCacheAdvice())
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();
            when(repository.existsInCourse(assignmentId, courseId)).thenReturn(true);
            String body = "{\"title\":\"A\",\"instructions\":\"B\","
                    + "\"deadlineAt\":\"9999-12-31T23:59:59-05:00\",\"lessonId\":null}";

            mvc.perform(post("/api/courses/{c}/assignments", courseId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body.getBytes(StandardCharsets.UTF_8)))
                    .andExpect(status().isBadRequest());
            mvc.perform(put("/api/courses/{c}/owned-assignments/{a}", courseId, assignmentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body.getBytes(StandardCharsets.UTF_8)))
                    .andExpect(status().isBadRequest());
            verify(repository, never()).insert(any());
            verify(repository, never()).updateDefinition(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    class ServiceBehavior {

        @Test
        void createTrimsTitlePreservesInstructionsVerbatimAcceptsPastDeadlinesAndStartsHidden() {
            Instant pastDeadline = Instant.parse("2021-05-10T08:00:00Z");
            when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

            OwnedAssignmentView created = service.create(courseId, new AssignmentInput(
                    " \t Project 1 \u0085 ",
                    "  Keep leading spaces\n\tand indentation  ",
                    pastDeadline,
                    lessonId));

            verify(access).requireCourseOwnership(courseId);
            verify(access).requireLessonOwnership(courseId, lessonId);
            assertAll(
                    () -> assertEquals(courseId, created.courseId()),
                    () -> assertEquals(lessonId, created.lessonId()),
                    () -> assertEquals("Project 1", created.title()),
                    () -> assertEquals("  Keep leading spaces\n\tand indentation  ", created.instructions()),
                    () -> assertEquals(pastDeadline, created.deadlineAt()),
                    () -> assertEquals("hidden", created.publicationStatus()),
                    () -> assertEquals(0, created.submissionCount()),
                    () -> assertEquals(0, created.gradedCount()));
        }

        @Test
        void createRejectsBlankOrUnstorableTextBeforeLessonLookup() {
            Instant deadline = Instant.parse("2026-12-01T00:00:00Z");
            List<AssignmentInput> invalidInputs = List.of(
                    new AssignmentInput(" \t\n\uFEFF\u0085 ", "Instructions", deadline, lessonId),
                    new AssignmentInput("Title\u0000bad", "Instructions", deadline, lessonId),
                    new AssignmentInput("Title\uD800bad", "Instructions", deadline, lessonId),
                    new AssignmentInput("Title", " \t\n\u0085 ", deadline, lessonId),
                    new AssignmentInput("Title", "Instructions\u0000bad", deadline, lessonId),
                    new AssignmentInput("Title", "Instructions", Instant.parse("2026-12-01T00:00:00.0000001Z"), lessonId));

            for (AssignmentInput invalid : invalidInputs) {
                BusinessRuleException thrown = assertThrows(
                        BusinessRuleException.class, () -> service.create(courseId, invalid));
                assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatus());
            }
            verify(access, never()).requireLessonOwnership(any(), any());
            verifyNoInteractions(repository);
        }

        @Test
        void replaceLocksRowPreservesPublicationStatusAndReturnsCurrentCounts() {
            Instant initialDeadline = Instant.parse("2026-12-01T00:00:00Z");
            Instant updatedDeadline = Instant.parse("2026-12-15T12:00:00.500Z");
            Assignment existing = new Assignment(
                    assignmentId, courseId, lessonId, "Old", "Old instructions", initialDeadline, "published");
            when(repository.findInCourseForUpdate(assignmentId, courseId)).thenReturn(Optional.of(existing));
            when(repository.countSubmissions(List.of(assignmentId)))
                    .thenReturn(Map.of(assignmentId, new SubmissionCounts(7, 4)));

            OwnedAssignmentView replaced = service.replace(courseId, assignmentId, new AssignmentInput(
                    " Updated Title ", "Updated instructions", updatedDeadline, null));

            verify(repository).updateDefinition(
                    assignmentId, courseId, null, "Updated Title", "Updated instructions", updatedDeadline);
            assertAll(
                    () -> assertNull(replaced.lessonId()),
                    () -> assertEquals("Updated Title", replaced.title()),
                    () -> assertEquals("published", replaced.publicationStatus()),
                    () -> assertEquals(7, replaced.submissionCount()),
                    () -> assertEquals(4, replaced.gradedCount()));
        }

        @Test
        void setPublicationIsIdempotentWhenUnchangedAndUpdatesWhenChanged() {
            Instant deadline = Instant.parse("2026-12-01T00:00:00Z");
            Assignment existing = new Assignment(
                    assignmentId, courseId, null, "Title", "Instructions", deadline, "hidden");
            when(repository.findInCourseForUpdate(assignmentId, courseId)).thenReturn(Optional.of(existing));
            when(repository.countSubmissions(List.of(assignmentId))).thenReturn(Map.of());

            OwnedAssignmentView unchanged = service.setPublication(courseId, assignmentId, "hidden");
            assertEquals("hidden", unchanged.publicationStatus());
            verify(repository, never()).updatePublicationStatus(any(), any(), any());

            OwnedAssignmentView published = service.setPublication(courseId, assignmentId, "published");
            assertEquals("published", published.publicationStatus());
            verify(repository).updatePublicationStatus(assignmentId, courseId, "published");
        }

        @Test
        void listOwnedSkipsRowQueryWhenOffsetExceedsTotal() {
            when(repository.countInCourse(courseId)).thenReturn(3L);
            PageResponse<OwnedAssignmentView> page = service.listOwned(courseId, 2, 2);
            assertEquals(List.of(), page.content());
            assertEquals(3L, page.totalElements());
            assertEquals(2, page.totalPages());
            verify(repository, never()).findPageInCourse(any(), any(Integer.class), any(Integer.class));
        }
    }

    @Nested
    class ControllerStagePrecedence {

        private MockMvc mvc;

        @BeforeEach
        void setUpMvc() {
            mvc = MockMvcBuilders.standaloneSetup(
                            new AssignmentController(service),
                            new AssignmentController.AssignmentCacheAdvice())
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();
        }

        @Test
        void stage2MediaTypeAndPathChecksRunBeforeStage3AdmissionAndBodyIsNotReadOnStage3Rejection() throws Exception {
            doThrow(new AccessDeniedException("Denied")).when(access).requireCourseOwnership(courseId);

            // Stage 2: wrong Content-Type is 415 before CT03 403.
            mvc.perform(post("/api/courses/{c}/assignments", courseId)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("not json"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(header().string("Cache-Control", "no-store"));
            verifyNoInteractions(access);

            // Stage 2: malformed path UUID is 400 before CT03 403.
            mvc.perform(get("/api/courses/not-a-uuid/owned-assignments"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"));
            verifyNoInteractions(access);

            // Stage 3: missing assignment in owned course returns 404 even when JSON body is malformed.
            AssignmentService freshService = new AssignmentService(repository, mock(CourseAccessService.class));
            when(repository.existsInCourse(assignmentId, courseId)).thenReturn(false);
            MockMvc freshMvc = MockMvcBuilders.standaloneSetup(
                            new AssignmentController(freshService),
                            new AssignmentController.AssignmentCacheAdvice())
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();
            freshMvc.perform(put("/api/courses/{c}/owned-assignments/{a}", courseId, assignmentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{".getBytes(StandardCharsets.UTF_8)))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.detail").value("The requested resource is unavailable."));
        }
    }
}
