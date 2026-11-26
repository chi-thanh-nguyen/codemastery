package com.codemastery.modules.assessment.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.modules.assessment.api.dto.AssignmentRequests;
import com.codemastery.modules.assessment.api.dto.AssignmentRequests.GradeSubmissionRequest;
import com.codemastery.modules.assessment.api.dto.AssignmentRequests.PublicationRequest;
import com.codemastery.modules.assessment.api.dto.AssignmentRequests.WriteAssignmentRequest;
import com.codemastery.modules.assessment.api.dto.AssignmentResponses.LearnerAssignmentResponse;
import com.codemastery.modules.assessment.api.dto.AssignmentResponses.LearnerAssignmentSummaryResponse;
import com.codemastery.modules.assessment.api.dto.AssignmentResponses.LearnerSubmissionResponse;
import com.codemastery.modules.assessment.api.dto.AssignmentResponses.OwnedAssignmentResponse;
import com.codemastery.modules.assessment.api.dto.AssignmentResponses.SubmissionSummaryResponse;
import com.codemastery.modules.assessment.application.service.AssignmentService;
import com.codemastery.modules.assessment.application.service.AssignmentService.LearnerAssignmentSummaryView;
import com.codemastery.modules.assessment.application.service.AssignmentService.OwnedAssignmentView;
import com.codemastery.modules.assessment.application.service.SubmissionService;
import com.codemastery.modules.assessment.application.service.SubmissionService.FilePart;
import com.codemastery.modules.assessment.application.service.SubmissionService.SubmissionFile;
import com.codemastery.modules.assessment.application.service.SubmissionService.SubmissionOutcome;
import com.codemastery.modules.assessment.application.service.SubmissionService.SubmissionSummaryView;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * CT10.rest assignment and submission REST boundary (ADR010 §§11, 12, 14).
 * Multipart endpoints receive {@link MultipartHttpServletRequest} lazily and never read parts
 * before stage-3 admission in {@link SubmissionService}.
 */
@RestController
@RequestMapping("/api/courses/{courseId}")
public class AssignmentController {

    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern PAGE_NUMBER = Pattern.compile("[0-9]+");
    private static final Set<String> PAGE_PARAMETERS = Set.of("page", "size");

    private final AssignmentService assignmentService;
    private final SubmissionService submissionService;

    public AssignmentController(AssignmentService assignmentService) {
        this(assignmentService, null);
    }

    @Autowired
    public AssignmentController(AssignmentService assignmentService, SubmissionService submissionService) {
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService");
        this.submissionService = submissionService;
    }

    @PostMapping("/assignments")
    public ResponseEntity<OwnedAssignmentResponse> create(
            @PathVariable String courseId,
            HttpServletRequest http,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType)
            throws HttpMediaTypeNotSupportedException {
        AssignmentRequests.requireJson(contentType);
        noQuery(http);
        UUID course = id(courseId);
        assignmentService.requireOwnedCourse(course);
        WriteAssignmentRequest request = AssignmentRequests.parseWrite(readBody(http))
                .orElseThrow(AssignmentRequests::invalid);
        return response(HttpStatus.CREATED,
                OwnedAssignmentResponse.from(assignmentService.create(course, request.toInput())));
    }

    @GetMapping("/owned-assignments")
    public ResponseEntity<PageResponse<OwnedAssignmentResponse>> listOwned(
            @PathVariable String courseId,
            @RequestParam MultiValueMap<String, String> params) {
        UUID course = id(courseId);
        int[] paging = page(params);
        PageResponse<OwnedAssignmentView> result = assignmentService.listOwned(course, paging[0], paging[1]);
        return response(HttpStatus.OK, new PageResponse<>(
                result.content().stream().map(OwnedAssignmentResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()));
    }

    @GetMapping("/owned-assignments/{assignmentId}")
    public ResponseEntity<OwnedAssignmentResponse> readOwned(
            @PathVariable String courseId,
            @PathVariable String assignmentId,
            HttpServletRequest http) {
        noQuery(http);
        UUID course = id(courseId);
        UUID assignment = id(assignmentId);
        return response(HttpStatus.OK,
                OwnedAssignmentResponse.from(assignmentService.readOwned(course, assignment)));
    }

    @PutMapping("/owned-assignments/{assignmentId}")
    public ResponseEntity<OwnedAssignmentResponse> replace(
            @PathVariable String courseId,
            @PathVariable String assignmentId,
            HttpServletRequest http,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType)
            throws HttpMediaTypeNotSupportedException {
        AssignmentRequests.requireJson(contentType);
        noQuery(http);
        UUID course = id(courseId);
        UUID assignment = id(assignmentId);
        assignmentService.requireOwnedAssignment(course, assignment);
        WriteAssignmentRequest request = AssignmentRequests.parseWrite(readBody(http))
                .orElseThrow(AssignmentRequests::invalid);
        return response(HttpStatus.OK,
                OwnedAssignmentResponse.from(assignmentService.replace(course, assignment, request.toInput())));
    }

    @PutMapping("/owned-assignments/{assignmentId}/publication")
    public ResponseEntity<OwnedAssignmentResponse> setPublication(
            @PathVariable String courseId,
            @PathVariable String assignmentId,
            HttpServletRequest http,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType)
            throws HttpMediaTypeNotSupportedException {
        AssignmentRequests.requireJson(contentType);
        noQuery(http);
        UUID course = id(courseId);
        UUID assignment = id(assignmentId);
        assignmentService.requireOwnedAssignment(course, assignment);
        PublicationRequest request = AssignmentRequests.parsePublication(readBody(http))
                .orElseThrow(AssignmentRequests::invalid);
        return response(HttpStatus.OK, OwnedAssignmentResponse.from(
                assignmentService.setPublication(course, assignment, request.publicationStatus())));
    }

    @GetMapping("/assignments")
    public ResponseEntity<PageResponse<LearnerAssignmentSummaryResponse>> listForLearner(
            @PathVariable String courseId,
            @RequestParam MultiValueMap<String, String> params) {
        UUID course = id(courseId);
        int[] paging = page(params);
        PageResponse<LearnerAssignmentSummaryView> result =
                assignmentService.listForLearner(course, paging[0], paging[1]);
        return response(HttpStatus.OK, new PageResponse<>(
                result.content().stream().map(LearnerAssignmentSummaryResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()));
    }

    @GetMapping("/assignments/{assignmentId}")
    public ResponseEntity<LearnerAssignmentResponse> readForLearner(
            @PathVariable String courseId,
            @PathVariable String assignmentId,
            HttpServletRequest http) {
        noQuery(http);
        UUID course = id(courseId);
        UUID assignment = id(assignmentId);
        return response(HttpStatus.OK,
                LearnerAssignmentResponse.from(assignmentService.readForLearner(course, assignment)));
    }

    @PutMapping(path = "/assignments/{assignmentId}/submission", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LearnerSubmissionResponse> submitOrReplace(
            @PathVariable String courseId,
            @PathVariable String assignmentId,
            MultipartHttpServletRequest request,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType)
            throws HttpMediaTypeNotSupportedException {
        requireMultipart(contentType);
        noQuery(request);
        UUID course = id(courseId);
        UUID assignment = id(assignmentId);
        SubmissionOutcome outcome = submissions().submitOrReplace(course, assignment, () -> filePart(request));
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return response(status, LearnerSubmissionResponse.from(outcome.submission()));
    }

    @GetMapping("/assignments/{assignmentId}/submission/file")
    public void downloadOwnSubmission(
            @PathVariable String courseId,
            @PathVariable String assignmentId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        noQuery(request);
        UUID course = id(courseId);
        UUID assignment = id(assignmentId);
        try (SubmissionFile file = submissions().downloadOwnSubmission(course, assignment)) {
            streamAttachment(file, response);
        } catch (IOException | RuntimeException failure) {
            if (!response.isCommitted()) {
                response.reset();
            }
            throw failure;
        }
    }

    @GetMapping("/owned-assignments/{assignmentId}/submissions")
    public ResponseEntity<PageResponse<SubmissionSummaryResponse>> listSubmissions(
            @PathVariable String courseId,
            @PathVariable String assignmentId,
            @RequestParam MultiValueMap<String, String> params) {
        UUID course = id(courseId);
        UUID assignment = id(assignmentId);
        int[] paging = page(params);
        PageResponse<SubmissionSummaryView> result =
                submissions().listSubmissionsForOwner(course, assignment, paging[0], paging[1]);
        return response(HttpStatus.OK, new PageResponse<>(
                result.content().stream().map(SubmissionSummaryResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()));
    }

    @GetMapping("/owned-assignments/{assignmentId}/submissions/{submissionId}/file")
    public void downloadSubmission(
            @PathVariable String courseId,
            @PathVariable String assignmentId,
            @PathVariable String submissionId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        noQuery(request);
        UUID course = id(courseId);
        UUID assignment = id(assignmentId);
        UUID submission = id(submissionId);
        try (SubmissionFile file = submissions().downloadSubmissionForOwner(course, assignment, submission)) {
            streamAttachment(file, response);
        } catch (IOException | RuntimeException failure) {
            if (!response.isCommitted()) {
                response.reset();
            }
            throw failure;
        }
    }

    @PutMapping("/owned-assignments/{assignmentId}/submissions/{submissionId}/grade")
    public ResponseEntity<SubmissionSummaryResponse> gradeSubmission(
            @PathVariable String courseId,
            @PathVariable String assignmentId,
            @PathVariable String submissionId,
            HttpServletRequest http,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType)
            throws HttpMediaTypeNotSupportedException {
        AssignmentRequests.requireJson(contentType);
        noQuery(http);
        UUID course = id(courseId);
        UUID assignment = id(assignmentId);
        UUID submission = id(submissionId);
        submissions().requireOwnedSubmission(course, assignment, submission);
        GradeSubmissionRequest request = AssignmentRequests.parseGrade(readBody(http))
                .orElseThrow(AssignmentRequests::invalid);
        return response(HttpStatus.OK, SubmissionSummaryResponse.from(
                submissions().gradeSubmission(course, assignment, submission, request.toInput())));
    }

    private SubmissionService submissions() {
        return Objects.requireNonNull(submissionService, "submissionService");
    }

    private static void streamAttachment(SubmissionFile file, HttpServletResponse response) throws IOException {
        response.setContentType(file.mediaType());
        response.setContentLengthLong(file.contentLength());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition(file.originalFilename()));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setStatus(HttpStatus.OK.value());
        file.content().transferTo(response.getOutputStream());
    }

    private static String disposition(String filename) {
        StringBuilder ascii = new StringBuilder();
        filename.codePoints().forEach(c -> ascii.append(c < 128 && c != '"' && c != '\\' ? (char) c : '_'));
        StringBuilder encoded = new StringBuilder();
        for (byte b : filename.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 255;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || "!#$&+-.^_`|~".indexOf(c) >= 0) {
                encoded.append((char) c);
            } else {
                encoded.append("%%%02X".formatted(c));
            }
        }
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private static FilePart filePart(MultipartHttpServletRequest request) {
        List<Part> parts;
        try {
            parts = List.copyOf(request.getParts());
        } catch (MaxUploadSizeExceededException tooLarge) {
            throw tooLarge;
        } catch (IllegalStateException tooLarge) {
            throw new MaxUploadSizeExceededException(-1);
        } catch (ServletException | IOException | MultipartException malformed) {
            throw AssignmentRequests.invalid();
        }
        if (parts.size() != 1 || !"file".equals(parts.getFirst().getName())) {
            throw AssignmentRequests.invalid();
        }
        Part file = parts.getFirst();
        if (file.getSubmittedFileName() == null) {
            throw AssignmentRequests.invalid();
        }
        try {
            return new FilePart(file.getSubmittedFileName(), file.getSize(), file.getInputStream());
        } catch (IOException malformed) {
            throw AssignmentRequests.invalid();
        }
    }

    private static void requireMultipart(String contentType) throws HttpMediaTypeNotSupportedException {
        MediaType type;
        try {
            type = contentType == null ? null : MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException invalid) {
            throw new HttpMediaTypeNotSupportedException("Invalid Content-Type");
        }
        if (type == null || !MediaType.MULTIPART_FORM_DATA.isCompatibleWith(type)) {
            throw new HttpMediaTypeNotSupportedException(type, List.of(MediaType.MULTIPART_FORM_DATA));
        }
    }

    private static byte[] readBody(HttpServletRequest http) {
        try {
            return http.getInputStream().readAllBytes();
        } catch (IOException ioFailure) {
            throw new UncheckedIOException(ioFailure);
        }
    }

    private static int[] page(MultiValueMap<String, String> params) {
        if (!PAGE_PARAMETERS.containsAll(params.keySet())
                || params.values().stream().anyMatch(values -> values.size() != 1)) {
            throw AssignmentRequests.invalid();
        }
        int page = number(params.getFirst("page"), PageResponse.DEFAULT_PAGE);
        int size = number(params.getFirst("size"), PageResponse.DEFAULT_SIZE);
        PageResponse.validateRequest(page, size);
        return new int[] {page, size};
    }

    private static UUID id(String value) {
        if (value == null || !UUID_TEXT.matcher(value).matches()) {
            throw AssignmentRequests.invalid();
        }
        return UUID.fromString(value);
    }

    private static int number(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (!PAGE_NUMBER.matcher(value).matches()) {
            throw AssignmentRequests.invalid();
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException unrepresentable) {
            throw AssignmentRequests.invalid();
        }
    }

    private static void noQuery(HttpServletRequest http) {
        String query = http.getQueryString();
        if (query != null && !query.isEmpty()) {
            throw AssignmentRequests.invalid();
        }
    }

    private static <T> ResponseEntity<T> response(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }

    /** Assignment and submission responses, including MVC error advice, are never cached. */
    @ControllerAdvice
    public static class AssignmentCacheAdvice implements ResponseBodyAdvice<Object> {
        private static final Pattern ASSIGNMENT_PATH = Pattern.compile(
                "/api/courses/[^/]+/(assignments(/[^/]+(/submission(/file)?)?)?|owned-assignments(/[^/]+(/publication|/submissions(/[^/]+(/file|/grade))?)?)?)");

        @Override
        public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
            return true;
        }

        @Override
        public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                      Class<? extends HttpMessageConverter<?>> converterType,
                                      ServerHttpRequest request, ServerHttpResponse response) {
            if (ASSIGNMENT_PATH.matcher(request.getURI().getPath()).matches()) {
                response.getHeaders().setCacheControl("no-store");
            }
            return body;
        }
    }
}