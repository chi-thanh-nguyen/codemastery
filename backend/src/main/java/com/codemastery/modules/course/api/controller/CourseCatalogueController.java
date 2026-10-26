package com.codemastery.modules.course.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.course.api.dto.CourseDetailResponse;
import com.codemastery.modules.course.api.dto.CourseSearchRequest;
import com.codemastery.modules.course.api.dto.CourseSummaryResponse;
import com.codemastery.modules.course.application.service.CourseCatalogueService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/courses")
public class CourseCatalogueController {

    private static final Set<String> SUPPORTED_CATALOGUE_QUERY_PARAMS = Set.of("page", "size");

    private final CourseCatalogueService courseCatalogueService;

    public CourseCatalogueController(CourseCatalogueService courseCatalogueService) {
        this.courseCatalogueService = Objects.requireNonNull(
                courseCatalogueService, "courseCatalogueService must not be null");
    }


    @GetMapping
    public ResponseEntity<PageResponse<CourseSummaryResponse>> listPublishedCourses(
            @RequestParam(name = "page", required = false) @Min(0) Integer page,
            @RequestParam(name = "size", required = false) @Min(1) @Max(PageResponse.MAX_SIZE) Integer size,
            @RequestParam MultiValueMap<String, String> queryParams) {
        rejectUnsupportedCatalogueParams(queryParams);
        return ResponseEntity.ok(courseCatalogueService.listPublishedCourses(CourseSearchRequest.of(page, size)));
    }


    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDetailResponse> getPublishedCourse(
            @PathVariable("courseId") UUID courseId,
            @RequestParam MultiValueMap<String, String> queryParams) {
        rejectAnyQueryParams(queryParams);
        return ResponseEntity.ok(courseCatalogueService.getPublishedCourseDetail(courseId));
    }

    private static void rejectUnsupportedCatalogueParams(MultiValueMap<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            if (!SUPPORTED_CATALOGUE_QUERY_PARAMS.contains(entry.getKey())) {
                throw new BusinessRuleException("Unsupported query parameter");
            }
            List<String> values = entry.getValue();
            if (values != null && values.size() > 1) {
                throw new BusinessRuleException("Duplicate query parameter");
            }
        }
    }

    private static void rejectAnyQueryParams(MultiValueMap<String, String> queryParams) {
        if (queryParams != null && !queryParams.isEmpty()) {
            throw new BusinessRuleException("Unsupported query parameter");
        }
    }
}
