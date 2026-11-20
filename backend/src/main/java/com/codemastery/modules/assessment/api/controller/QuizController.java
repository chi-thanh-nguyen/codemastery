package com.codemastery.modules.assessment.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.assessment.api.dto.QuizRequest.PublicationRequest;
import com.codemastery.modules.assessment.api.dto.QuizRequest.WriteQuizRequest;
import com.codemastery.modules.assessment.api.dto.QuizResponses.OwnedQuizResponse;
import com.codemastery.modules.assessment.api.dto.QuizResponses.QuizSummaryResponse;
import com.codemastery.modules.assessment.application.service.QuizService;
import com.codemastery.modules.course.application.service.CourseAccessService;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * CT23.bindings quiz operations (ADR007 §§5–7, OpenAPI b3*Quiz*). Authoring and owner reads are
 * Instructor-only; the learner list is Learner-only (both bound in the security chain).
 * Precedence: 401/role (security chain), 400 path/query, CT03 403/404, then body 400 (B3Invalid,
 * including a non-JSON media type; the contract defines no 415), then lifecycle 409.
 */
@RestController
@RequestMapping("/api/courses/{courseId}")
public class QuizController {

    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern PAGE_NUMBER = Pattern.compile("[0-9]+");
    private static final Set<String> PAGE_PARAMETERS = Set.of("page", "size");

    private final QuizService quizService;
    private final CourseAccessService courseAccess;

    public QuizController(QuizService quizService, CourseAccessService courseAccess) {
        this.quizService = Objects.requireNonNull(quizService);
        this.courseAccess = Objects.requireNonNull(courseAccess);
    }

    @PostMapping("/quizzes")
    public ResponseEntity<OwnedQuizResponse> create(@PathVariable String courseId,
            @RequestParam MultiValueMap<String, String> params,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestBody(required = false) String body) {
        noQuery(params);
        UUID course = id(courseId);
        WriteQuizRequest request = body(course, contentType, body, WriteQuizRequest::parse);
        return response(HttpStatus.CREATED, OwnedQuizResponse.from(quizService.create(course, request.toInput())));
    }

    @GetMapping("/quizzes")
    public ResponseEntity<PageResponse<QuizSummaryResponse>> listForLearner(@PathVariable String courseId,
            @RequestParam MultiValueMap<String, String> params) {
        UUID course = id(courseId);
        int[] page = page(params);
        PageResponse<QuizService.QuizSummary> result = quizService.listForLearner(course, page[0], page[1]);
        return response(HttpStatus.OK, new PageResponse<>(
                result.content().stream().map(QuizSummaryResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @GetMapping("/owned-quizzes")
    public ResponseEntity<PageResponse<OwnedQuizResponse>> listOwned(@PathVariable String courseId,
            @RequestParam MultiValueMap<String, String> params) {
        UUID course = id(courseId);
        int[] page = page(params);
        PageResponse<QuizService.QuizView> result = quizService.listOwned(course, page[0], page[1]);
        return response(HttpStatus.OK, new PageResponse<>(
                result.content().stream().map(OwnedQuizResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @GetMapping("/quizzes/{quizId}")
    public ResponseEntity<OwnedQuizResponse> readOwned(@PathVariable String courseId, @PathVariable String quizId,
            @RequestParam MultiValueMap<String, String> params) {
        noQuery(params);
        return response(HttpStatus.OK, OwnedQuizResponse.from(quizService.readOwned(id(courseId), id(quizId))));
    }

    @PutMapping("/quizzes/{quizId}")
    public ResponseEntity<OwnedQuizResponse> update(@PathVariable String courseId, @PathVariable String quizId,
            @RequestParam MultiValueMap<String, String> params,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestBody(required = false) String body) {
        noQuery(params);
        UUID course = id(courseId);
        UUID quiz = id(quizId);
        WriteQuizRequest request = body(course, contentType, body, WriteQuizRequest::parse);
        return response(HttpStatus.OK, OwnedQuizResponse.from(quizService.update(course, quiz, request.toInput())));
    }

    @DeleteMapping("/quizzes/{quizId}")
    public ResponseEntity<OwnedQuizResponse> delete(@PathVariable String courseId, @PathVariable String quizId,
            @RequestParam MultiValueMap<String, String> params) {
        noQuery(params);
        return response(HttpStatus.OK, OwnedQuizResponse.from(quizService.delete(id(courseId), id(quizId))));
    }

    @PutMapping("/quizzes/{quizId}/publication")
    public ResponseEntity<OwnedQuizResponse> publish(@PathVariable String courseId, @PathVariable String quizId,
            @RequestParam MultiValueMap<String, String> params,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestBody(required = false) String body) {
        noQuery(params);
        UUID course = id(courseId);
        UUID quiz = id(quizId);
        PublicationRequest request = body(course, contentType, body, PublicationRequest::parse);
        return response(HttpStatus.OK, OwnedQuizResponse.from(quizService.publish(course, quiz, request.published())));
    }

    /**
     * A body that is not exactly the closed JSON object, including a non-JSON media type, is B3Invalid
     * (400). It is reported only after CT03 admission, so it never precedes 403/404.
     */
    private <T> T body(UUID course, String contentType, String body, Function<String, Optional<T>> parser) {
        Optional<T> request = isJson(contentType) ? parser.apply(body) : Optional.empty();
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

    private static int[] page(MultiValueMap<String, String> params) {
        if (!PAGE_PARAMETERS.containsAll(params.keySet())
                || params.values().stream().anyMatch(values -> values.size() != 1)) {
            throw invalid();
        }
        int page = number(params.getFirst("page"), PageResponse.DEFAULT_PAGE);
        int size = number(params.getFirst("size"), PageResponse.DEFAULT_SIZE);
        PageResponse.validateRequest(page, size);
        return new int[] {page, size};
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

    private static void noQuery(MultiValueMap<String, String> params) {
        if (!params.isEmpty()) {
            throw invalid();
        }
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException("Invalid quiz request");
    }

    private static <T> ResponseEntity<T> response(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }

    /** Quiz responses, including MVC error advice, are never stored (AuthenticationNoStore). */
    @ControllerAdvice
    public static class QuizCacheAdvice implements ResponseBodyAdvice<Object> {
        private static final Pattern QUIZ_PATH =
                Pattern.compile("/api/courses/[^/]+/(owned-quizzes|quizzes(/[^/]+(/publication)?)?)");

        @Override
        public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
            return true;
        }

        @Override
        public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request,
                ServerHttpResponse response) {
            if (QUIZ_PATH.matcher(request.getURI().getPath()).matches()) {
                response.getHeaders().setCacheControl("no-store");
            }
            return body;
        }
    }
}
