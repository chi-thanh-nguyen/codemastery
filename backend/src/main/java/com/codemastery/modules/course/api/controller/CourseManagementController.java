package com.codemastery.modules.course.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.modules.course.api.dto.CourseRequest;
import com.codemastery.modules.course.api.dto.CourseSearchRequest;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedCourse;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedCourseSummary;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.CourseManagementService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * CT21.authoring Course operations (ADR006 §5). Order: 401/role (security chain), 415 media type,
 * 400 path/query, CT03 403/404, then 400 body; no write before admission. Responses are no-store.
 */
@RestController
@RequestMapping("/api/instructor/courses")
public class CourseManagementController {
    private final CourseManagementService management;
    private final CourseAccessService access;

    public CourseManagementController(CourseManagementService management, CourseAccessService access) {
        this.management = management;
        this.access = access;
    }

    @GetMapping
    public ResponseEntity<PageResponse<OwnedCourseSummary>> listOwnedCourses(@RequestParam MultiValueMap<String, String> params) {
        var request = CourseSearchRequest.parsePage(params);
        return ok(management.listOwnedCourses(request.page(), request.size()));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OwnedCourse> createOwnedCourse(@RequestParam MultiValueMap<String, String> params,
                                                         @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                         @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        var write = CourseRequest.courseWrite(body);
        if (write.isEmpty()) {
            management.requireActiveInstructor();
            throw CourseRequest.invalidBody();
        }
        var course = write.get();
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(
                management.createOwnedCourse(course.title(), course.description(), course.prerequisiteDescription()));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<OwnedCourse> getOwnedCourse(@PathVariable String courseId,
                                                      @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return ok(management.getOwnedCourse(CourseSearchRequest.parseCourseId(courseId)));
    }

    @PutMapping(path = "/{courseId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OwnedCourse> updateOwnedCourse(@PathVariable String courseId,
                                                         @RequestParam MultiValueMap<String, String> params,
                                                         @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                         @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        UUID course = CourseSearchRequest.parseCourseId(courseId);
        var write = CourseRequest.courseWrite(body);
        if (write.isEmpty()) {
            access.requireCourseOwnership(course);
            throw CourseRequest.invalidBody();
        }
        var metadata = write.get();
        return ok(management.updateOwnedCourse(course, metadata.title(), metadata.description(),
                metadata.prerequisiteDescription()));
    }

    @PutMapping(path = "/{courseId}/publication", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OwnedCourse> setOwnedCoursePublication(@PathVariable String courseId,
                                                                 @RequestParam MultiValueMap<String, String> params,
                                                                 @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                                 @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        UUID course = CourseSearchRequest.parseCourseId(courseId);
        var status = CourseRequest.publication(body);
        if (status.isEmpty()) {
            access.requireCourseOwnership(course);
            throw CourseRequest.invalidBody();
        }
        return ok(management.setOwnedCoursePublication(course, status.get()));
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
