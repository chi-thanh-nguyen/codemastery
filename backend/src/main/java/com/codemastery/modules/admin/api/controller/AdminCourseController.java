package com.codemastery.modules.admin.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.modules.admin.api.dto.AdminRequests.AdminCourseSearch;
import com.codemastery.modules.admin.api.dto.AdminRequests.CoursePublicationWrite;
import com.codemastery.modules.admin.api.dto.AdminResponses.AdminCourse;
import com.codemastery.modules.admin.api.dto.AdminResponses.AdminCourseChange;
import com.codemastery.modules.admin.application.service.AdminCourseService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * CT12.rest Administrator course operations (ADR012 section 10.3). Administrator only: the role is decided by
 * {@link AdminCourseService}, after malformed path, query and media-type errors (stage 2) and before the body
 * rules (stage 4), as in ADR012 section 12. Responses are {@code no-store}.
 */
@RestController
@RequestMapping("/api/admin/courses")
public class AdminCourseController {

    private final AdminCourseService courses;

    public AdminCourseController(AdminCourseService courses) {
        this.courses = Objects.requireNonNull(courses);
    }

    @GetMapping
    public ResponseEntity<PageResponse<AdminCourse>> list(@RequestParam MultiValueMap<String, String> params) {
        AdminCourseSearch search = AdminCourseSearch.parse(params);
        PageResponse<AdminCourseService.AdminCourseItem> result =
                courses.list(search.query(), search.publicationStatus(), search.page(), search.size());
        return noStore(new PageResponse<>(result.content().stream().map(AdminCourse::from).toList(),
                result.page(), result.size(), result.totalElements()));
    }

    @PutMapping(path = "/{courseId}/publication", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminCourseChange> hide(@PathVariable("courseId") UUID courseId,
                                                  @RequestBody(required = false) String json) {
        courses.requireAdministrator();
        CoursePublicationWrite.parse(json).requireHidden();
        return noStore(AdminCourseChange.from(courses.hideCourse(courseId)));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
