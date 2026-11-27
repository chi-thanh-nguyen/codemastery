package com.codemastery.modules.interaction.api.controller;

import com.codemastery.modules.interaction.api.dto.InteractionRequests;
import com.codemastery.modules.interaction.api.dto.InteractionRequests.ContentReportWrite;
import com.codemastery.modules.interaction.api.dto.InteractionResponses.ContentReportReceipt;
import com.codemastery.modules.interaction.application.service.ContentReportService;
import com.codemastery.modules.interaction.domain.model.ContentReport;
import org.springframework.core.MethodParameter;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;
import java.util.UUID;

@RestController
public class ContentReportController {

    private final ContentReportService reports;

    public ContentReportController(ContentReportService reports) {
        this.reports = reports;
    }

    /** CT30.reports, Lesson target: Learner or Instructor admitted by CT03 to the addressed Lesson. */
    @PostMapping(path = "/api/lessons/{lessonId}/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ContentReportReceipt> reportLesson(
            @PathVariable String lessonId, @RequestBody Map<String, Object> body) {
        UUID lesson = InteractionRequests.parseId(lessonId);
        return created(reports.reportLesson(lesson, () -> ContentReportWrite.parse(body).reason()));
    }

    /** CT30.reports, post target: the post must be a visible post of the addressed Lesson. */
    @PostMapping(path = "/api/lessons/{lessonId}/discussion/{postId}/reports",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ContentReportReceipt> reportPost(
            @PathVariable String lessonId, @PathVariable String postId, @RequestBody Map<String, Object> body) {
        UUID lesson = InteractionRequests.parseId(lessonId);
        UUID post = InteractionRequests.parseId(postId);
        return created(reports.reportPost(lesson, post, () -> ContentReportWrite.parse(body).reason()));
    }

    private static ResponseEntity<ContentReportReceipt> created(ContentReport report) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(new ContentReportReceipt(report.getId(), report.getStatus().getCode(), report.getCreatedAt()));
    }

    /** Interaction owns the cache policy for these private responses, including MVC error advice. */
    @ControllerAdvice
    public static class ReportCacheAdvice implements ResponseBodyAdvice<Object> {
        @Override
        public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
            return true;
        }

        @Override
        public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request,
                ServerHttpResponse response) {
            String path = request.getURI().getPath();
            if (path.matches("/api/lessons/[^/]+/reports") || path.matches("/api/lessons/[^/]+/discussion/[^/]+/reports")) {
                response.getHeaders().setCacheControl("no-store");
            }
            return body;
        }
    }
}
