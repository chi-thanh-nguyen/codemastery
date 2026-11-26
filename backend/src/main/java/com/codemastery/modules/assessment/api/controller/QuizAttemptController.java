package com.codemastery.modules.assessment.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.assessment.api.dto.AttemptResponses;
import com.codemastery.modules.assessment.api.dto.AttemptResponses.AttemptReadResponse;
import com.codemastery.modules.assessment.api.dto.AttemptResponses.AttemptResponse;
import com.codemastery.modules.assessment.api.dto.AttemptResponses.AttemptResultResponse;
import com.codemastery.modules.assessment.api.dto.AttemptResponses.AttemptSummaryResponse;
import com.codemastery.modules.assessment.api.dto.SubmitAttemptRequest.AnswerEntryRequest;
import com.codemastery.modules.assessment.api.dto.SubmitAttemptRequest.AnswersWriteRequest;
import com.codemastery.modules.assessment.application.service.AttemptService;
import com.codemastery.modules.assessment.application.service.AttemptService.AnswerInput;
import com.codemastery.modules.assessment.application.service.AttemptService.AnswersInput;
import com.codemastery.modules.assessment.application.service.AttemptService.StartOutcome;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * CT04 quiz attempt REST boundary (ADR007 §§8–13, OpenAPI b3*Attempt*).
 * Enforces closed JSON parsing, UUID validation, and {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/courses/{courseId}/quizzes/{quizId}")
public class QuizAttemptController {

    private final AttemptService attempts;
    private final ObjectMapper strictMapper;

    public QuizAttemptController(AttemptService attempts, ObjectMapper objectMapper) {
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.strictMapper = Objects.requireNonNull(objectMapper, "objectMapper")
                .copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        this.strictMapper.coercionConfigFor(LogicalType.Boolean)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
        this.strictMapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    @PostMapping("/attempts")
    public ResponseEntity<AttemptResponse> start(@PathVariable String courseId,
                                                 @PathVariable String quizId,
                                                 HttpServletRequest request) {
        UUID parsedCourseId = parseUuid(courseId);
        UUID parsedQuizId = parseUuid(quizId);
        requireEmptyBody(request);

        StartOutcome outcome = attempts.start(parsedCourseId, parsedQuizId);
        AttemptResponse body = AttemptResponse.from(outcome.attempt());
        if (outcome.created()) {
            URI location = URI.create("/api/courses/" + parsedCourseId + "/quizzes/" + parsedQuizId
                    + "/attempts/" + body.attemptId());
            return ResponseEntity.created(location)
                    .cacheControl(CacheControl.noStore())
                    .body(body);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping("/attempts")
    public ResponseEntity<PageResponse<AttemptSummaryResponse>> history(
            @PathVariable String courseId,
            @PathVariable String quizId,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        UUID parsedCourseId = parseUuid(courseId);
        UUID parsedQuizId = parseUuid(quizId);
        int parsedPage = parsePageInt(page, "page");
        int parsedSize = parsePageInt(size, "size");

        var history = attempts.history(parsedCourseId, parsedQuizId, parsedPage, parsedSize);
        PageResponse<AttemptSummaryResponse> body = new PageResponse<>(
                history.content().stream().map(AttemptSummaryResponse::from).toList(),
                history.page(),
                history.size(),
                history.totalElements()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping("/attempts/{attemptId}")
    public ResponseEntity<AttemptReadResponse> read(@PathVariable String courseId,
                                                    @PathVariable String quizId,
                                                    @PathVariable String attemptId) {
        UUID parsedCourseId = parseUuid(courseId);
        UUID parsedQuizId = parseUuid(quizId);
        UUID parsedAttemptId = parseUuid(attemptId);

        AttemptReadResponse body = AttemptResponses.fromRead(
                attempts.read(parsedCourseId, parsedQuizId, parsedAttemptId));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @PutMapping("/attempts/{attemptId}/answers")
    public ResponseEntity<AttemptResponse> saveAnswers(@PathVariable String courseId,
                                                       @PathVariable String quizId,
                                                       @PathVariable String attemptId,
                                                       HttpServletRequest request) {
        UUID parsedCourseId = parseUuid(courseId);
        UUID parsedQuizId = parseUuid(quizId);
        UUID parsedAttemptId = parseUuid(attemptId);
        AnswersInput input = parseAnswersBody(request);

        AttemptResponse body = AttemptResponse.from(
                attempts.saveAnswers(parsedCourseId, parsedQuizId, parsedAttemptId, input));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @PostMapping("/attempts/{attemptId}/submission")
    public ResponseEntity<AttemptResultResponse> submit(@PathVariable String courseId,
                                                        @PathVariable String quizId,
                                                        @PathVariable String attemptId,
                                                        HttpServletRequest request) {
        UUID parsedCourseId = parseUuid(courseId);
        UUID parsedQuizId = parseUuid(quizId);
        UUID parsedAttemptId = parseUuid(attemptId);
        AnswersInput input = parseAnswersBody(request);

        AttemptResultResponse body = AttemptResultResponse.from(
                attempts.submit(parsedCourseId, parsedQuizId, parsedAttemptId, input));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping("/attempts/{attemptId}/result")
    public ResponseEntity<AttemptResultResponse> readResult(@PathVariable String courseId,
                                                            @PathVariable String quizId,
                                                            @PathVariable String attemptId) {
        UUID parsedCourseId = parseUuid(courseId);
        UUID parsedQuizId = parseUuid(quizId);
        UUID parsedAttemptId = parseUuid(attemptId);

        AttemptResultResponse body = AttemptResultResponse.from(
                attempts.readResult(parsedCourseId, parsedQuizId, parsedAttemptId));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping("/owned-attempts/{attemptId}/result")
    public ResponseEntity<AttemptResultResponse> readOwnedResult(@PathVariable String courseId,
                                                                 @PathVariable String quizId,
                                                                 @PathVariable String attemptId) {
        UUID parsedCourseId = parseUuid(courseId);
        UUID parsedQuizId = parseUuid(quizId);
        UUID parsedAttemptId = parseUuid(attemptId);

        AttemptResultResponse body = AttemptResultResponse.from(
                attempts.readOwnedResult(parsedCourseId, parsedQuizId, parsedAttemptId));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private AnswersInput parseAnswersBody(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            throw new BusinessRuleException("JSON request body is required");
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            if (!MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)) {
                throw new BusinessRuleException("Content-Type must be application/json");
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Invalid Content-Type");
        }

        byte[] raw;
        try {
            raw = request.getInputStream().readAllBytes();
        } catch (IOException ex) {
            throw new BusinessRuleException("Unreadable request body");
        }
        String text = new String(raw, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw new BusinessRuleException("Request body must not be empty");
        }

        AnswersWriteRequest dto;
        try {
            dto = strictMapper.readValue(text, AnswersWriteRequest.class);
        } catch (JacksonException ex) {
            throw new BusinessRuleException("Malformed answers payload");
        }
        if (dto == null || dto.answers() == null) {
            throw new BusinessRuleException("Answers list is required");
        }
        List<AnswerInput> items = new ArrayList<>(dto.answers().size());
        for (AnswerEntryRequest entry : dto.answers()) {
            if (entry == null) {
                throw new BusinessRuleException("Answer entry must not be null");
            }
            items.add(new AnswerInput(entry.questionId(), entry.selectedOptions()));
        }
        return new AnswersInput(items);
    }

    private static void requireEmptyBody(HttpServletRequest request) {
        try {
            byte[] raw = request.getInputStream().readAllBytes();
            if (raw.length > 0) {
                throw new BusinessRuleException("Start attempt accepts no request body");
            }
        } catch (IOException ex) {
            throw new BusinessRuleException("Unreadable request body");
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.length() != 36) {
            throw new BusinessRuleException("Invalid UUID path parameter");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Invalid UUID path parameter");
        }
    }

    private static int parsePageInt(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new BusinessRuleException("Invalid " + name + " query parameter");
        }
    }

    /** Ensures {@code Cache-Control: no-store} on all success and error responses from this controller. */
    @ControllerAdvice(assignableTypes = QuizAttemptController.class)
    static class NoStoreAdvice implements ResponseBodyAdvice<Object> {

        @Override
        public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
            return true;
        }

        @Override
        public Object beforeBodyWrite(Object body,
                                      MethodParameter returnType,
                                      MediaType selectedContentType,
                                      Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                      ServerHttpRequest request,
                                      ServerHttpResponse response) {
            response.getHeaders().setCacheControl(CacheControl.noStore());
            response.getHeaders().remove(HttpHeaders.PRAGMA);
            return body;
        }
    }
}