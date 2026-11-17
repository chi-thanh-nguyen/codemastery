package com.codemastery.modules.course.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.modules.course.api.dto.CourseProgressResponse;
import com.codemastery.modules.course.api.dto.CourseProgressResponse.CourseVisitWrite;
import com.codemastery.modules.course.api.dto.CourseProgressResponse.LearnerResume;
import com.codemastery.modules.course.api.dto.CourseProgressResponse.LessonCompletionWrite;
import com.codemastery.modules.course.api.dto.CourseProgressResponse.Resume;
import com.codemastery.modules.course.api.dto.CourseProgressResponse.Summary;
import com.codemastery.modules.course.api.dto.CourseSearchRequest;
import com.codemastery.modules.course.application.service.EnrollmentService;
import com.codemastery.modules.course.application.service.LessonProgressService;
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
    private final LessonProgressService progress;
    public EnrollmentController(EnrollmentService enrollments, LessonProgressService progress) {
        this.enrollments = enrollments; this.progress = progress;
    }

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
    @GetMapping("/api/courses/{courseId}/progress")
    public ResponseEntity<CourseProgressResponse> progress(@PathVariable String courseId, @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return response(progress.progress(CourseSearchRequest.parseCourseId(courseId)));
    }
    @PutMapping("/api/courses/{courseId}/lessons/{lessonId}/completion")
    public ResponseEntity<CourseProgressResponse> complete(@PathVariable String courseId, @PathVariable String lessonId,
            @RequestBody LessonCompletionWrite body, @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return response(progress.complete(CourseSearchRequest.parseCourseId(courseId), CourseSearchRequest.parseCourseId(lessonId)));
    }
    @GetMapping("/api/me/course-progress")
    public ResponseEntity<PageResponse<Summary>> myProgress(@RequestParam MultiValueMap<String, String> params) {
        if (params.containsKey("status")) CourseSearchRequest.noQuery(params);
        var request = CourseSearchRequest.parse(params, true);
        return response(progress.myCourseProgress(request.page(), request.size()));
    }
    @GetMapping("/api/courses/{courseId}/resume")
    public ResponseEntity<Resume> resume(@PathVariable String courseId, @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return response(progress.resume(CourseSearchRequest.parseCourseId(courseId)));
    }
    @PutMapping("/api/courses/{courseId}/resume")
    public ResponseEntity<Resume> visit(@PathVariable String courseId, @RequestBody CourseVisitWrite body,
            @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return response(progress.visit(CourseSearchRequest.parseCourseId(courseId), body.lessonId()));
    }
    @GetMapping("/api/me/resume")
    public ResponseEntity<LearnerResume> myResume(@RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return response(progress.myResume());
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
