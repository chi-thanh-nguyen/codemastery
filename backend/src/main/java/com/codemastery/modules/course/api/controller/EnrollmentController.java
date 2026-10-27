package com.codemastery.modules.course.api.controller;

import com.codemastery.modules.course.api.dto.CourseSearchRequest;
import com.codemastery.modules.course.application.service.EnrollmentService;
import com.codemastery.modules.course.application.service.EnrollmentService.Membership;
import com.codemastery.modules.course.application.service.EnrollmentService.MembershipPage;
import org.springframework.http.CacheControl;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class EnrollmentController {
    private final EnrollmentService enrollments;
    public EnrollmentController(EnrollmentService enrollments) { this.enrollments = enrollments; }

    @GetMapping("/api/courses/{courseId}/membership")
    public ResponseEntity<Membership> membership(@PathVariable String courseId, @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return response(enrollments.membership(CourseSearchRequest.parseCourseId(courseId)));
    }
    @PutMapping("/api/courses/{courseId}/membership")
    public ResponseEntity<Membership> enroll(@PathVariable String courseId, @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return response(enrollments.enroll(CourseSearchRequest.parseCourseId(courseId)));
    }
    @DeleteMapping("/api/courses/{courseId}/membership")
    public ResponseEntity<Membership> leave(@PathVariable String courseId, @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return response(enrollments.leave(CourseSearchRequest.parseCourseId(courseId)));
    }
    @GetMapping("/api/me/courses")
    public ResponseEntity<MembershipPage> mine(@RequestParam MultiValueMap<String, String> params) {
        var request = CourseSearchRequest.parse(params, true);
        return response(enrollments.myCourses(request.page(), request.size(), request.filter()));
    }
    /** Course owns cache policy for its private responses, including MVC error advice. */
    @ControllerAdvice
    public static class MembershipCacheAdvice implements ResponseBodyAdvice<Object> {
        @Override public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) { return true; }
        @Override public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request, ServerHttpResponse response) {
            String path = request.getURI().getPath();
            if ("/api/me/courses".equals(path) || path.matches("/api/courses/[^/]+/membership")) response.getHeaders().setCacheControl("no-store");
            return body;
        }
    }
    private static <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
