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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.codemastery.infrastructure.storage.FileStorage;
import com.codemastery.infrastructure.storage.FileStorage.ObjectNotFoundException;
import com.codemastery.infrastructure.storage.FileStorage.StoredObject;
import com.codemastery.modules.assessment.api.dto.AssignmentRequests.GradeSubmissionRequest;
import com.codemastery.modules.assessment.application.event.AssignmentGradedEvent;
import com.codemastery.modules.assessment.application.service.AssignmentService.LearnerAssignmentView;
import com.codemastery.modules.assessment.application.service.SubmissionService;
import com.codemastery.modules.assessment.application.service.SubmissionService.FilePart;
import com.codemastery.modules.assessment.application.service.SubmissionService.GradeInput;
import com.codemastery.modules.assessment.application.service.SubmissionService.SubmissionFile;
import com.codemastery.modules.assessment.application.service.SubmissionService.SubmissionOutcome;
import com.codemastery.modules.assessment.application.service.SubmissionService.SubmissionSummaryView;
import com.codemastery.modules.assessment.domain.model.Submission;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import static org.mockito.Mockito.lenient;

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

    @Nested
        class SubmissionsAndGrading {

        private AssignmentRepository repo;
        private CourseAccessService access;
        private UserAccountService identities;
        private FileStorage storage;
        private ApplicationEventPublisher events;
        private MutableClock clock;
        private SubmissionService submissionService;
        private AssignmentService learnerAssignmentService;
        private Map<UUID, String> displayNames = Map.of();

        private final UUID courseId = UUID.fromString("00000000-0000-0000-0000-00000000c101");
        private final UUID lessonId = UUID.fromString("00000000-0000-0000-0000-00000000d101");
        private final UUID assignmentId = UUID.fromString("00000000-0000-0000-0000-00000000a101");
        private final UUID learnerId = UUID.fromString("00000000-0000-0000-0000-00000000e101");

        @BeforeEach
        void setUpSubmissions() {
                repo = mock(AssignmentRepository.class);
                access = mock(CourseAccessService.class);
                identities = mock(UserAccountService.class);
                storage = mock(FileStorage.class);
                events = mock(ApplicationEventPublisher.class);
                clock = new MutableClock(Instant.parse("2026-11-26T10:00:00Z"));
                var enrollments = mock(com.codemastery.modules.course.application.service.EnrollmentService.class);
                lenient().when(enrollments.learnerDisplayNames(any(), any())).thenAnswer(call -> displayNames);
                submissionService = new SubmissionService(
                        repo, access, identities, enrollments, storage, null, events, null, 1024 * 1024, clock);
                lenient().when(repo.existsInCourse(assignmentId, courseId)).thenReturn(true);
                learnerAssignmentService = new AssignmentService(repo, access, identities, clock);
                lenient().when(identities.requireCurrentIdentity())
                        .thenReturn(new CurrentIdentity(learnerId, IdentityRole.LEARNER));
        }

        private Assignment publishedAssignment(UUID lesson, Instant deadline) {
                return new Assignment(assignmentId, courseId, lesson, "Lab 1", "Upload PDF or ZIP", deadline, "published");
        }

        private static byte[] pdfBytes() {
                return "%PDF-1.7\n%sample".getBytes(StandardCharsets.US_ASCII);
        }

        private static byte[] zipBytes() {
                return new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};
        }

        @Test
        void submitBeforeDeadlineCreates201AndReplacementBeforeDeadlineReplacesWithLaterTimestamp() {
                Instant deadline = Instant.parse("2026-11-26T11:00:00Z");
                Assignment assignment = publishedAssignment(lessonId, deadline);
                when(repo.findInCourse(assignmentId, courseId)).thenReturn(Optional.of(assignment));
                when(repo.findInCourseForUpdate(assignmentId, courseId)).thenReturn(Optional.of(assignment));
                when(repo.findSubmissionByAssignmentAndLearner(assignmentId, learnerId)).thenReturn(Optional.empty());
                when(repo.findSubmissionByAssignmentAndLearnerForUpdate(assignmentId, learnerId))
                        .thenReturn(Optional.empty());

                SubmissionOutcome created = submissionService.submitOrReplace(
                        courseId, assignmentId, () -> new FilePart("report.pdf", pdfBytes().length, new ByteArrayInputStream(pdfBytes())));
                assertTrue(created.created());
                assertEquals("report.pdf", created.submission().originalFilename());
                assertEquals(Instant.parse("2026-11-26T10:00:00Z"), created.submission().submittedAt());
                assertFalse(created.submission().graded());

                ArgumentCaptor<String> firstKeyCaptor = ArgumentCaptor.forClass(String.class);
                verify(storage).store(firstKeyCaptor.capture(), any(), eq((long) pdfBytes().length), eq("application/pdf"));
                String firstKey = firstKeyCaptor.getValue();
                assertTrue(firstKey.matches("submissions/[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));

                // Replacement without advancing clock fails with 500 and deletes the new object (ADR010 §8)
                UUID subId = UUID.randomUUID();
                Submission existing = new Submission(subId, assignmentId, learnerId, firstKey,
                        "report.pdf", Instant.parse("2026-11-26T10:00:00Z"), null, null, null);
                when(repo.findSubmissionByAssignmentAndLearner(assignmentId, learnerId)).thenReturn(Optional.of(existing));
                when(repo.findSubmissionByAssignmentAndLearnerForUpdate(assignmentId, learnerId))
                        .thenReturn(Optional.of(existing));

                assertThrows(IllegalStateException.class, () -> submissionService.submitOrReplace(
                        courseId, assignmentId, () -> new FilePart("solution.zip", zipBytes().length, new ByteArrayInputStream(zipBytes()))));
                verify(repo, never()).replaceSubmissionFile(any(), any(), any(), any(), any(), any());

                // Advance clock by 1 microsecond -> replacement succeeds (200) and deletes superseded key
                clock.advanceMicros(1);
                SubmissionOutcome replaced = submissionService.submitOrReplace(
                        courseId, assignmentId, () -> new FilePart("solution.zip", zipBytes().length, new ByteArrayInputStream(zipBytes())));
                assertFalse(replaced.created());
                assertEquals("solution.zip", replaced.submission().originalFilename());
                assertEquals(Instant.parse("2026-11-26T10:00:00.000001Z"), replaced.submission().submittedAt());
                verify(storage).delete(firstKey);
        }

        @Test
        void lateOrGradedSubmissionIsRejectedWith409WithoutWritingToStorage() {
                Instant deadline = Instant.parse("2026-11-26T10:00:00Z"); // equality now == deadline is late!
                Assignment assignment = publishedAssignment(null, deadline);
                when(repo.findInCourse(assignmentId, courseId)).thenReturn(Optional.of(assignment));

                BusinessRuleException lateEx = assertThrows(BusinessRuleException.class, () -> submissionService.submitOrReplace(
                        courseId, assignmentId, () -> new FilePart("late.pdf", pdfBytes().length, new ByteArrayInputStream(pdfBytes()))));
                assertEquals(HttpStatus.CONFLICT, lateEx.getStatus());
                verifyNoInteractions(storage);

                // Before deadline, but already graded -> 409 without storage write
                Assignment openAssignment = publishedAssignment(null, Instant.parse("2026-11-26T12:00:00Z"));
                Submission graded = new Submission(
                        UUID.randomUUID(), assignmentId, learnerId,
                        "submissions/11111111-1111-4111-8111-111111111111", "ok.pdf",
                        Instant.parse("2026-11-26T09:55:00Z"), new BigDecimal("95.00"), "Good",
                        Instant.parse("2026-11-26T09:58:00Z"));
                when(repo.findInCourse(assignmentId, courseId)).thenReturn(Optional.of(openAssignment));
                when(repo.findSubmissionByAssignmentAndLearner(assignmentId, learnerId)).thenReturn(Optional.of(graded));

                BusinessRuleException gradedEx = assertThrows(BusinessRuleException.class, () -> submissionService.submitOrReplace(
                        courseId, assignmentId, () -> new FilePart("new.pdf", pdfBytes().length, new ByteArrayInputStream(pdfBytes()))));
                assertEquals(HttpStatus.CONFLICT, gradedEx.getStatus());
                verifyNoInteractions(storage);
        }

        @Test
        void invalidFileTypesSignaturesFilenamesAndOversizedPartsFollowPrecedence() {
                Assignment open = publishedAssignment(null, Instant.parse("2026-11-26T12:00:00Z"));
                when(repo.findInCourse(assignmentId, courseId)).thenReturn(Optional.of(open));

                // Wrong signature with .pdf extension -> 400
                byte[] fakePdf = "NOTPDF".getBytes(StandardCharsets.US_ASCII);
                assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessRuleException.class,
                        () -> submissionService.submitOrReplace(courseId, assignmentId,
                                () -> new FilePart("bad.pdf", fakePdf.length, new ByteArrayInputStream(fakePdf)))).getStatus());

                // Disallowed extension -> 400
                assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessRuleException.class,
                        () -> submissionService.submitOrReplace(courseId, assignmentId,
                                () -> new FilePart("script.sh", 10, new ByteArrayInputStream(new byte[10])))).getStatus());

                // Path separator in filename -> 400
                assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessRuleException.class,
                        () -> submissionService.submitOrReplace(courseId, assignmentId,
                                () -> new FilePart("../evil.pdf", pdfBytes().length, new ByteArrayInputStream(pdfBytes())))).getStatus());

                // Oversized -> MaxUploadSizeExceededException (413)
                assertThrows(MaxUploadSizeExceededException.class,
                        () -> submissionService.submitOrReplace(courseId, assignmentId,
                                () -> new FilePart("big.pdf", 2 * 1024 * 1024, new ByteArrayInputStream(pdfBytes()))));
                verifyNoInteractions(storage);
        }

        @Test
        void gradingPublishesCt27EventInsideTransactionEnforcesPreconditionAndMakesScoreVisibleToLearner() {
                UUID subId = UUID.randomUUID();
                Instant submittedAt = Instant.parse("2026-11-26T09:15:02.123456Z");
                Assignment assignment = publishedAssignment(lessonId, Instant.parse("2026-11-26T12:00:00Z"));
                Submission ungraded = new Submission(
                        subId, assignmentId, learnerId,
                        "submissions/22222222-2222-4222-8222-222222222222", "lab.pdf",
                        submittedAt, null, null, null);

                when(repo.findInCourseForUpdate(assignmentId, courseId)).thenReturn(Optional.of(assignment));
                when(repo.findSubmissionInAssignmentForUpdate(subId, assignmentId)).thenReturn(Optional.of(ungraded));
                when(repo.findSubmissionInAssignment(subId, assignmentId)).thenReturn(Optional.of(ungraded));
                displayNames = Map.of(learnerId, "Minh Tan");

                // Stale submittedAt -> 409, no event published
                BusinessRuleException stale = assertThrows(BusinessRuleException.class,
                        () -> submissionService.gradeSubmission(courseId, assignmentId, subId,
                                new GradeInput(new BigDecimal("88.50"), "Nice work", Instant.parse("2026-11-26T09:15:02.123000Z"))));
                assertEquals(HttpStatus.CONFLICT, stale.getStatus());
                verifyNoInteractions(events);

                // Matching microsecond token -> saves grade, publishes CT27 event, regrade=false
                SubmissionSummaryView gradedView = submissionService.gradeSubmission(
                        courseId, assignmentId, subId,
                        new GradeInput(new BigDecimal("88.50"), "Nice work\nKeep it up", submittedAt));
                assertTrue(gradedView.graded());
                assertEquals("Minh Tan", gradedView.learnerDisplayName());
                assertEquals(new BigDecimal("88.50"), gradedView.gradePercentage());
                assertEquals("Nice work\nKeep it up", gradedView.feedback());

                ArgumentCaptor<AssignmentGradedEvent> eventCaptor = ArgumentCaptor.forClass(AssignmentGradedEvent.class);
                verify(events).publishEvent(eventCaptor.capture());
                AssignmentGradedEvent publishedEvent = eventCaptor.getValue();
                assertEquals(subId, publishedEvent.submissionId());
                assertEquals(assignmentId, publishedEvent.assignmentId());
                assertEquals(courseId, publishedEvent.courseId());
                assertEquals(lessonId, publishedEvent.lessonId());
                assertEquals(learnerId, publishedEvent.learnerId());
                assertEquals("Lab 1", publishedEvent.assignmentTitle());
                assertTrue(publishedEvent.assignmentPublished());
                assertFalse(publishedEvent.regrade());

                // Learner read exposes score and feedback (and never object_key)
                Submission gradedRow = new Submission(
                        subId, assignmentId, learnerId,
                        "submissions/22222222-2222-4222-8222-222222222222", "lab.pdf",
                        submittedAt, new BigDecimal("88.50"), "Nice work\nKeep it up",
                        Instant.parse("2026-11-26T10:00:00Z"));
                when(repo.findInCourse(assignmentId, courseId)).thenReturn(Optional.of(assignment));
                when(repo.findSubmissionByAssignmentAndLearner(assignmentId, learnerId)).thenReturn(Optional.of(gradedRow));
                LearnerAssignmentView learnerView = learnerAssignmentService.readForLearner(courseId, assignmentId);
                assertNotNull(learnerView.submission());
                assertTrue(learnerView.submission().graded());
                assertEquals(new BigDecimal("88.50"), learnerView.submission().gradePercentage());
                assertEquals("Nice work\nKeep it up", learnerView.submission().feedback());

                // Grade wire parser enforces microsecond precision and closed JSON
                assertTrue(AssignmentRequests.parseGrade(
                        "{\"gradePercentage\":95.5,\"feedback\":\"Solid\",\"submittedAt\":\"2026-11-26T09:15:02.123456Z\"}")
                        .isPresent());
                assertTrue(AssignmentRequests.parseGrade(
                        "{\"gradePercentage\":95.5,\"feedback\":\"Solid\",\"submittedAt\":\"2026-11-26T09:15:02.1234567Z\"}")
                        .isEmpty());
        }

        @Test
        void downloadReReadsOnceOnConcurrentReplacementAndRefusesUnauthorizedOwner() throws IOException {
                UUID subId = UUID.randomUUID();
                String oldKey = "submissions/33333333-3333-4333-8333-333333333333";
                String newKey = "submissions/44444444-4444-4444-8444-444444444444";
                Submission oldRow = new Submission(subId, assignmentId, learnerId, oldKey, "v1.pdf",
                        Instant.parse("2026-11-26T09:50:00Z"), null, null, null);
                Submission newRow = new Submission(subId, assignmentId, learnerId, newKey, "v2.zip",
                        Instant.parse("2026-11-26T09:55:00Z"), null, null, null);

                when(repo.existsInCourse(assignmentId, courseId)).thenReturn(true);
                when(repo.findSubmissionInAssignment(subId, assignmentId))
                        .thenReturn(Optional.of(oldRow))
                        .thenReturn(Optional.of(newRow));
                when(storage.open(oldKey)).thenThrow(new ObjectNotFoundException());
                when(storage.open(newKey)).thenReturn(
                        new StoredObject(zipBytes().length, "application/zip", new ByteArrayInputStream(zipBytes())));

                try (SubmissionFile downloaded = submissionService.downloadSubmissionForOwner(courseId, assignmentId, subId)) {
                assertEquals("v2.zip", downloaded.originalFilename());
                assertEquals("application/zip", downloaded.mediaType());
                assertArrayEquals(zipBytes(), downloaded.content().readAllBytes());
                }

                // Ownership denial blocks download before any repository or storage call
                doThrow(new ResourceNotFoundException("Not owner")).when(access).requireCourseOwnership(courseId);
                assertThrows(ResourceNotFoundException.class,
                        () -> submissionService.downloadSubmissionForOwner(courseId, assignmentId, subId));
        }

        @Test
        void learnerDetailUsesOnlyTheLessonGateAndMissingAssignmentStillAdmitsCourse() {
                var assignment = publishedAssignment(lessonId, clock.instant().plusSeconds(60));
                when(repo.findInCourse(assignmentId, courseId)).thenReturn(Optional.of(assignment));
                learnerAssignmentService.readForLearner(courseId, assignmentId);
                verify(access).requireLearnerLessonAccess(courseId, lessonId);
                verify(access, never()).requireLearnerCourseAccess(any());
                org.mockito.Mockito.clearInvocations(access);
                when(repo.findInCourse(assignmentId, courseId)).thenReturn(Optional.empty());
                assertThrows(ResourceNotFoundException.class, () -> learnerAssignmentService.readForLearner(courseId, assignmentId));
                verify(access).requireLearnerCourseAccess(courseId);
        }

        @Test
        void unroundedGradeFractionsCannotBecomeValidAndResourceAdmissionPrecedesShape() {
                var parsed = AssignmentRequests.parseGrade("{\"gradePercentage\":99.9900000000000000000001,\"feedback\":\"ok\",\"submittedAt\":\"2026-11-26T09:15:02.123456Z\"}").orElseThrow();
                UUID id = UUID.randomUUID();
                when(repo.findSubmissionInAssignment(id, assignmentId)).thenReturn(Optional.of(new Submission(id,
                        assignmentId, learnerId, "unused", "a.pdf", clock.instant(), null, null, null)));
                assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessRuleException.class,
                        () -> submissionService.gradeSubmission(courseId, assignmentId, id, parsed.toInput())).getStatus());
                assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessRuleException.class,
                        () -> submissionService.gradeSubmission(courseId, assignmentId, id,
                                new GradeInput(new BigDecimal("50"), "\u00a0\ufeff", clock.instant()))).getStatus());
                when(repo.existsInCourse(assignmentId, courseId)).thenReturn(false);
                assertThrows(ResourceNotFoundException.class,
                        () -> submissionService.gradeSubmission(courseId, assignmentId, id, null));
                verifyNoInteractions(events, storage);
        }

        @Test
        void learnerPageFiltersHiddenLessonsBeforeCountingAndPaging() {
                UUID hiddenLesson = UUID.randomUUID(), firstId = UUID.randomUUID(), hiddenId = UUID.randomUUID(), lastId = UUID.randomUUID();
                when(repo.countPublishedInCourse(courseId)).thenReturn(3L);
                when(repo.findPublishedPageInCourse(courseId, 0, 100)).thenReturn(List.of(
                        new Assignment(firstId, courseId, lessonId, "First", "I", clock.instant().plusSeconds(1), "published"),
                        new Assignment(hiddenId, courseId, hiddenLesson, "Hidden", "I", clock.instant().plusSeconds(2), "published"),
                        new Assignment(lastId, courseId, null, "Last", "I", clock.instant().plusSeconds(3), "published")));
                doThrow(new ResourceNotFoundException("Unavailable")).when(access).requireLearnerLessonAccess(courseId, hiddenLesson);
                var page = learnerAssignmentService.listForLearner(courseId, 1, 1);
                assertEquals(2, page.totalElements()); assertEquals(2, page.totalPages());
                assertEquals(lastId, page.content().getFirst().id());
                verify(repo).findSubmissionsByAssignmentsAndLearner(List.of(lastId), learnerId);
                verify(access).requireLearnerLessonAccess(courseId, lessonId);
        }

        private static final class MutableClock extends Clock {
                private Instant instant;

                private MutableClock(Instant initial) {
                this.instant = initial;
                }

                private void advanceMicros(long micros) {
                this.instant = this.instant.plusNanos(micros * 1_000L);
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
