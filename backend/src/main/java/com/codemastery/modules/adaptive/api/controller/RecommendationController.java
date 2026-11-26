package com.codemastery.modules.adaptive.api.controller;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.adaptive.api.dto.RecommendationResponse.PracticeRecommendation;
import com.codemastery.modules.adaptive.application.recommendation.RecommendationService;
import org.springframework.core.MethodParameter;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Recommendation REST bindings. Implemented: {@code b8ReadPracticeRecommendation}
 * ({@code CT07-PRACTICE-RECOMMENDATION-2026-11-15-r1}, ADR013 §19.4).
 *
 * <p>Order: 401 (security chain, which only requires authentication on this route), 400 malformed
 * path or any query parameter, 403 for a non-Learner and CT03's outcomes (inside CT33
 * {@code readLearnerState}), 404 for a Lesson that is not a currently published Lesson of the Course,
 * 500 for a provider or integrity failure. Every response, including errors, is {@code no-store}.
 */
@RestController
public class RecommendationController {

    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final RecommendationService recommendations;

    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @GetMapping("/api/courses/{courseId}/lessons/{lessonId}/practice-recommendation")
    public ResponseEntity<PracticeRecommendation> practiceRecommendation(@PathVariable String courseId,
            @PathVariable String lessonId, @RequestParam MultiValueMap<String, String> params) {
        if (!params.isEmpty()) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid query");
        }
        UUID course = id(courseId);
        UUID lesson = id(lessonId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(PracticeRecommendation.from(recommendations.readPracticeRecommendation(course, lesson)));
    }

    private static UUID id(String value) {
        if (value == null || !UUID_TEXT.matcher(value).matches()) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid path parameter");
        }
        return UUID.fromString(value);
    }

    /** OpenAPI requires {@code Cache-Control: no-store} on this operation's MVC error responses too. */
    @ControllerAdvice
    public static class PracticeRecommendationCacheAdvice implements ResponseBodyAdvice<Object> {

        private static final Pattern PATH = Pattern.compile("/api/courses/[^/]+/lessons/[^/]+/practice-recommendation");

        @Override
        public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
            return true;
        }

        @Override
        public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request,
                ServerHttpResponse response) {
            if (PATH.matcher(request.getURI().getPath()).matches()) {
                response.getHeaders().setCacheControl("no-store");
            }
            return body;
        }
    }
}
