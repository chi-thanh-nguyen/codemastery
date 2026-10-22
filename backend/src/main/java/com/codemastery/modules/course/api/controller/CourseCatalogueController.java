package com.codemastery.modules.course.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.course.api.dto.CourseDetailResponse;
import com.codemastery.modules.course.api.dto.CourseSearchRequest;
import com.codemastery.modules.course.api.dto.CourseSummaryResponse;
import com.codemastery.modules.course.application.service.CourseCatalogueService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for public published-course catalogue and detail endpoints
 * ({@code CO05.I-min} / {@code CT21.catalogue} / {@code CT18.minimum} / {@code CT03.catalogue}).
 */
@RestController
@RequestMapping("/api/courses")
public class CourseCatalogueController {

    private static final Set<String> SUPPORTED_CATALOGUE_QUERY_PARAMS = Set.of("page", "size");

    private final CourseCatalogueService courseCatalogueService;

    public CourseCatalogueController(CourseCatalogueService courseCatalogueService) {
        this.courseCatalogueService = Objects.requireNonNull(
                courseCatalogueService, "courseCatalogueService must not be null");
    }

    /**
     * {@code GET /api/courses?page=0&size=20} ({@code operationId: listPublishedCourses}).
     *
     * <p>Rejects any unapproved search/filter/sort query parameter or duplicate pagination parameter
     * with HTTP 400 ({@link BusinessRuleException}) per {@code CT21.catalogue}.
     */
    @GetMapping
    public PageResponse<CourseSummaryResponse> listPublishedCourses(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam MultiValueMap<String, String> queryParams) {
        validateCatalogueQueryParams(queryParams);
        return courseCatalogueService.listPublishedCourses(CourseSearchRequest.of(page, size));
    }

    /**
     * Convenience overload for direct programmatic/unit-test invocation with {@link CourseSearchRequest}.
     */
    public PageResponse<CourseSummaryResponse> listPublishedCourses(CourseSearchRequest request) {
        return courseCatalogueService.listPublishedCourses(request);
    }

    /**
     * Convenience overload for direct programmatic/unit-test invocation with {@code page} and {@code size}.
     */
    public PageResponse<CourseSummaryResponse> listPublishedCourses(Integer page, Integer size) {
        return courseCatalogueService.listPublishedCourses(CourseSearchRequest.of(page, size));
    }

    /**
     * {@code GET /api/courses/{courseId}} ({@code operationId: getPublishedCourse}).
     *
     * <p>Returns published course detail and ordered syllabus. Missing or hidden courses return
     * uniform HTTP 404 per {@code CT03.catalogue}; unexpected query parameters return HTTP 400.
     */
    @GetMapping("/{courseId}")
    public CourseDetailResponse getPublishedCourse(
            @PathVariable("courseId") UUID courseId,
            @RequestParam MultiValueMap<String, String> queryParams) {
        validateNoQueryParams(queryParams);
        return courseCatalogueService.getPublishedCourseDetail(courseId);
    }

    /**
     * Convenience overload for direct programmatic/unit-test invocation with {@code courseId}.
     */
    public CourseDetailResponse getPublishedCourse(UUID courseId) {
        return courseCatalogueService.getPublishedCourseDetail(courseId);
    }

    private static void validateCatalogueQueryParams(MultiValueMap<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            String paramName = entry.getKey();
            if (!SUPPORTED_CATALOGUE_QUERY_PARAMS.contains(paramName)) {
                throw new BusinessRuleException("Unsupported query parameter: " + paramName);
            }
            List<String> values = entry.getValue();
            if (values != null && values.size() > 1) {
                throw new BusinessRuleException("Duplicate query parameter: " + paramName);
            }
        }
    }

    private static void validateNoQueryParams(MultiValueMap<String, String> queryParams) {
        if (queryParams != null && !queryParams.isEmpty()) {
            throw new BusinessRuleException("Unsupported query parameters on course detail endpoint");
        }
    }
}
