package com.codemastery.modules.assessment.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.assessment.api.dto.QuestionRequest;
import com.codemastery.modules.assessment.api.dto.QuestionResponse;
import com.codemastery.modules.assessment.application.service.QuestionBankService;
import com.codemastery.modules.course.application.service.CourseAccessService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
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
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * CT23.bindings Instructor question-bank operations (ADR007 §7, OpenAPI b3*Question*).
 * Precedence: 401/role (security chain), 400 path/query, CT03 403/404, then body 400 (B3Invalid,
 * including a non-JSON media type; the contract defines no 415).
 */
@RestController
@RequestMapping("/api/courses/{courseId}/questions")
public class QuestionBankController {

    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern PAGE_NUMBER = Pattern.compile("[0-9]+");

    private final QuestionBankService questionBank;
    private final CourseAccessService courseAccess;

    public QuestionBankController(QuestionBankService questionBank, CourseAccessService courseAccess) {
        this.questionBank = questionBank;
        this.courseAccess = courseAccess;
    }

    @PostMapping
    public ResponseEntity<QuestionResponse> create(@PathVariable String courseId,
            HttpServletRequest http,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestBody(required = false) String body) {
        noQuery(http);
        UUID course = id(courseId);
        QuestionRequest request = body(course, contentType, body);
        return response(HttpStatus.CREATED, QuestionResponse.from(questionBank.create(course, request.toInput())));
    }

    @GetMapping
    public ResponseEntity<PageResponse<QuestionResponse>> list(@PathVariable String courseId,
            @RequestParam MultiValueMap<String, String> params) {
        UUID course = id(courseId);
        if (!Set.of("page", "size").containsAll(params.keySet())
                || params.values().stream().anyMatch(values -> values.size() != 1)) {
            throw invalid();
        }
        int page = number(params.getFirst("page"), PageResponse.DEFAULT_PAGE);
        int size = number(params.getFirst("size"), PageResponse.DEFAULT_SIZE);
        PageResponse.validateRequest(page, size);
        PageResponse<QuestionBankService.QuestionView> result = questionBank.list(course, page, size);
        return response(HttpStatus.OK, new PageResponse<>(
                result.content().stream().map(QuestionResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @GetMapping("/{questionId}")
    public ResponseEntity<QuestionResponse> read(@PathVariable String courseId, @PathVariable String questionId,
            HttpServletRequest http) {
        noQuery(http);
        return response(HttpStatus.OK, QuestionResponse.from(questionBank.read(id(courseId), id(questionId))));
    }

    @PutMapping("/{questionId}")
    public ResponseEntity<QuestionResponse> update(@PathVariable String courseId, @PathVariable String questionId,
            HttpServletRequest http,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestBody(required = false) String body) {
        noQuery(http);
        UUID course = id(courseId);
        UUID question = id(questionId);
        QuestionRequest request = body(course, contentType, body);
        return response(HttpStatus.OK, QuestionResponse.from(questionBank.update(course, question, request.toInput())));
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<QuestionResponse> delete(@PathVariable String courseId, @PathVariable String questionId,
            HttpServletRequest http) {
        noQuery(http);
        return response(HttpStatus.OK, QuestionResponse.from(questionBank.delete(id(courseId), id(questionId))));
    }

    /**
     * A body that is not exactly the closed JSON object, including a non-JSON media type, is B3Invalid
     * (400). It is reported only after CT03 admission, so it never precedes 403/404.
     */
    private QuestionRequest body(UUID course, String contentType, String body) {
        Optional<QuestionRequest> request = isJson(contentType) ? QuestionRequest.parse(body) : Optional.empty();
        return request.orElseGet(() -> {
            courseAccess.requireCourseOwnership(course);
            throw invalid();
        });
    }

    private static boolean isJson(String contentType) {
        try {
            return contentType != null && MediaType.APPLICATION_JSON.equalsTypeAndSubtype(MediaType.parseMediaType(contentType));
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static UUID id(String value) {
        if (value == null || !UUID_TEXT.matcher(value).matches()) {
            throw invalid();
        }
        return UUID.fromString(value);
    }

    private static int number(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (!PAGE_NUMBER.matcher(value).matches()) {
            throw invalid();
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException unrepresentable) {
            throw invalid();
        }
    }

    /**
     * The URI query only: a form-encoded body also becomes servlet parameters, but it is body input,
     * reported as 400 only after CT03 admission by {@link #body}.
     */
    private static void noQuery(HttpServletRequest http) {
        String query = http.getQueryString();
        if (query != null && !query.isEmpty()) {
            throw invalid();
        }
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException("Invalid question bank request");
    }

    private static <T> ResponseEntity<T> response(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }

    /** Question-bank responses, including MVC error advice, are never stored (AuthenticationNoStore). */
    @ControllerAdvice
    public static class QuestionBankCacheAdvice implements ResponseBodyAdvice<Object> {
        private static final Pattern QUESTION_PATH = Pattern.compile("/api/courses/[^/]+/questions(/[^/]+)?");

        @Override
        public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
            return true;
        }

        @Override
        public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request,
                ServerHttpResponse response) {
            if (QUESTION_PATH.matcher(request.getURI().getPath()).matches()) {
                response.getHeaders().setCacheControl("no-store");
            }
            return body;
        }
    }
}
