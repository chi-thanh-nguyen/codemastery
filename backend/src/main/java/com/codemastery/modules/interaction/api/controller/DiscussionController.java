package com.codemastery.modules.interaction.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.interaction.api.dto.InteractionRequests;
import com.codemastery.modules.interaction.api.dto.InteractionResponses.DiscussionPostResponse;
import com.codemastery.modules.interaction.api.dto.InteractionResponses.DiscussionThread;
import com.codemastery.modules.interaction.application.service.DiscussionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
public class DiscussionController {
    private final DiscussionService discussions;

    public DiscussionController(DiscussionService discussions) { this.discussions = discussions; }

    @GetMapping("/api/lessons/{lessonId}/discussion")
    public ResponseEntity<PageResponse<DiscussionThread>> list(@PathVariable String lessonId,
            @RequestParam MultiValueMap<String, String> params) {
        var lesson = InteractionRequests.parseId(lessonId);
        if (!Set.of("page", "size").containsAll(params.keySet())) throw new BusinessRuleException("Invalid query");
        int page = InteractionRequests.pageValue(params, "page", PageResponse.DEFAULT_PAGE);
        int size = InteractionRequests.pageValue(params, "size", PageResponse.DEFAULT_SIZE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(discussions.list(lesson, page, size));
    }

    @PostMapping(path = "/api/lessons/{lessonId}/discussion", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DiscussionPostResponse> create(@PathVariable String lessonId, HttpServletRequest request)
            throws org.springframework.web.HttpMediaTypeNotSupportedException {
        InteractionRequests.requireJson(request.getContentType());
        InteractionRequests.noQuery(request);
        var lesson = InteractionRequests.parseId(lessonId);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(
                discussions.create(lesson, () -> InteractionRequests.DiscussionPostWrite.parse(InteractionRequests.readBody(request))));
    }
}
