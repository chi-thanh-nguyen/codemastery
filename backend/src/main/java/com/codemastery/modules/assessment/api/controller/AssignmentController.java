package com.codemastery.modules.assessment.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.assessment.api.dto.AssignmentRequests;
import com.codemastery.modules.assessment.api.dto.AssignmentRequests.PublicationRequest;
import com.codemastery.modules.assessment.api.dto.AssignmentRequests.WriteAssignmentRequest;
import com.codemastery.modules.assessment.api.dto.AssignmentResponses.OwnedAssignmentResponse;
import com.codemastery.modules.assessment.application.service.AssignmentService;
import com.codemastery.modules.assessment.application.service.AssignmentService.OwnedAssignmentView;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.MethodParameter;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * CT10.rest Instructor assignment authoring and owner read endpoints (ADR010 §§12, 14;
 * OpenAPI b5CreateAssignment, b5ListOwnedAssignments, b5ReadOwnedAssignment,
 * b5ReplaceAssignment, b5SetAssignmentPublication).
 *
 * <p>Precedence follows ADR010 §14 stage by stage:
 * 1. 401 authentication (security filter chain),
 * 2. 415 request media type (POST/PUT) and 400 malformed path UUID or query parameter,
 * 3. CT03 403/404 role/ownership and assignment-in-course 404 before reading the request body,
 * 4. 400 closed JSON body shape and field validation,
 * 7. locked READ COMMITTED write and committed representation.
 */
@RestController
@RequestMapping("/api/courses/{courseId}")
public class AssignmentController {

    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern PAGE_NUMBER = Pattern.compile("[0-9]+");
    private static final Set<String> PAGE_PARAMETERS = Set.of("page", "size");

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService");
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

    /** Assignment responses, including MVC error advice, are never cached (AuthenticationNoStore). */
    @ControllerAdvice
    public static class AssignmentCacheAdvice implements ResponseBodyAdvice<Object> {
        private static final Pattern ASSIGNMENT_PATH = Pattern.compile(
                "/api/courses/[^/]+/(assignments|owned-assignments(/[^/]+(/publication)?)?)");

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
