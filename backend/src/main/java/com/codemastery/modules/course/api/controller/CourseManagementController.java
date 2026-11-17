package com.codemastery.modules.course.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.modules.course.api.dto.CourseRequest;
import com.codemastery.modules.course.api.dto.CourseSearchRequest;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedCourse;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedCourseSummary;
import com.codemastery.modules.course.application.service.CourseManagementService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/instructor/courses")
public class CourseManagementController {
    private final CourseManagementService management;

    public CourseManagementController(CourseManagementService management) {
        this.management = management;
    }

    @GetMapping
    public ResponseEntity<PageResponse<OwnedCourseSummary>> listOwnedCourses(@RequestParam MultiValueMap<String, String> params) {
        if (params.containsKey("status")) CourseSearchRequest.noQuery(params);
        var request = CourseSearchRequest.parse(params, true);
        return ok(management.listOwnedCourses(request.page(), request.size()));
    }

    @PostMapping
    public ResponseEntity<OwnedCourse> createOwnedCourse(@RequestBody CourseRequest body,
                                                         @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(management.createOwnedCourse(body));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<OwnedCourse> getOwnedCourse(@PathVariable String courseId,
                                                      @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return ok(management.getOwnedCourse(CourseSearchRequest.parseCourseId(courseId)));
    }

    @PutMapping("/{courseId}")
    public ResponseEntity<OwnedCourse> updateOwnedCourse(@PathVariable String courseId, @RequestBody CourseRequest body,
                                                         @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return ok(management.updateOwnedCourse(CourseSearchRequest.parseCourseId(courseId), body));
    }

    @PutMapping("/{courseId}/publication")
    public ResponseEntity<OwnedCourse> setOwnedCoursePublication(@PathVariable String courseId,
                                                                 @RequestBody CourseRequest.Publication body,
                                                                 @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return ok(management.setOwnedCoursePublication(CourseSearchRequest.parseCourseId(courseId), body));
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
