package com.codemastery.modules.admin.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.modules.admin.api.dto.AdminRequests.AuditLogSearch;
import com.codemastery.modules.admin.api.dto.ReportingResponses.AuditLogEntry;
import com.codemastery.modules.admin.application.service.AuditLogService;
import com.codemastery.modules.admin.application.service.AuditLogService.AuditLogView;
import org.springframework.core.MethodParameter;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;

@RestController
public class AdminReportingController {

    private static final String AUDIT_LOGS_PATH = "/api/admin/audit-logs";

    private final AuditLogService auditLogs;

    public AdminReportingController(AuditLogService auditLogs) {
        this.auditLogs = auditLogs;
    }

    /**
     * CT31.read, ADR012 section 12 stage order: SecurityConfig requires authentication (401), the query
     * is parsed here and validated by AuditLogService (400), which then requires an Administrator (403)
     * before any lookup.
     */
    @GetMapping(AUDIT_LOGS_PATH)
    public ResponseEntity<PageResponse<AuditLogEntry>> listAuditLogs(
            @RequestParam MultiValueMap<String, String> params) {
        AuditLogSearch search = AuditLogSearch.parse(params);
        PageResponse<AuditLogView> page = auditLogs.search(
                search.actorId(), search.action(), search.targetType(), search.targetReference(),
                search.occurredFrom(), search.occurredTo(), search.page(), search.size());
        List<AuditLogEntry> entries = page.content().stream()
                .map(view -> new AuditLogEntry(view.id(), view.actorId(), view.actorDisplayName(), view.action(),
                        view.targetType(), view.targetReference(), view.occurredAt()))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new PageResponse<>(entries, page.page(), page.size(), page.totalElements(), page.totalPages()));
    }

    /** Admin owns the cache policy for this private response, including MVC error advice. */
    @ControllerAdvice
    public static class AuditLogCacheAdvice implements ResponseBodyAdvice<Object> {
        @Override
        public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
            return true;
        }

        @Override
        public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request,
                ServerHttpResponse response) {
            if (AUDIT_LOGS_PATH.equals(request.getURI().getPath())) {
                response.getHeaders().setCacheControl("no-store");
            }
            return body;
        }
    }
}
