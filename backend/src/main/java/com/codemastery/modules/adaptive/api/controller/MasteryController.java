package com.codemastery.modules.adaptive.api.controller;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.adaptive.api.dto.MasteryResponses.AdaptiveGuidance;
import com.codemastery.modules.adaptive.application.mastery.MasteryQueryService;
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

/** CT33.rest course guidance: authentication, syntax, CT03 admission, coherent read-only snapshot. */
@RestController
public class MasteryController {

    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final MasteryQueryService masteries;

    public MasteryController(MasteryQueryService masteries) {
        this.masteries = masteries;
    }

    @GetMapping("/api/courses/{courseId}/adaptive-guidance")
    public ResponseEntity<AdaptiveGuidance> adaptiveGuidance(@PathVariable String courseId,
            @RequestParam MultiValueMap<String, String> params) {
        if (!params.isEmpty()) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid query");
        }
        UUID course = id(courseId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(AdaptiveGuidance.from(masteries.readAdaptiveGuidance(course)));
    }

    private static UUID id(String value) {
        if (value == null || !UUID_TEXT.matcher(value).matches()) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid path parameter");
        }
        return UUID.fromString(value);
    }

    /** OpenAPI requires {@code Cache-Control: no-store} on this operation's MVC error responses too. */
    @ControllerAdvice
    public static class AdaptiveGuidanceCacheAdvice implements ResponseBodyAdvice<Object> {

        private static final Pattern PATH = Pattern.compile("/api/courses/[^/]+/adaptive-guidance");

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
