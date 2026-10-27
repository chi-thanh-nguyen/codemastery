package com.codemastery.modules.course.api.controller;

import com.codemastery.modules.course.api.dto.CourseSummaryResponse.CoursePage;
import com.codemastery.modules.course.api.dto.CourseDetailResponse;
import com.codemastery.modules.course.api.dto.CourseSearchRequest;
import com.codemastery.modules.course.application.service.CourseCatalogueService;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
public class CourseCatalogueController {
    private final CourseCatalogueService catalogue;
    public CourseCatalogueController(CourseCatalogueService catalogue) { this.catalogue = catalogue; }

    @GetMapping
    public CoursePage list(@RequestParam MultiValueMap<String, String> params) {
        var request = CourseSearchRequest.parse(params, false);
        return catalogue.list(request.page(), request.size(), request.query(), request.filter());
    }
    @GetMapping("/{courseId}")
    public CourseDetailResponse detail(@PathVariable String courseId, @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return catalogue.detail(CourseSearchRequest.parseCourseId(courseId));
    }
}
