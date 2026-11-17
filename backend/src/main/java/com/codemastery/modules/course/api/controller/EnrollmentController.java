package com.codemastery.modules.course.api.controller;

import com.codemastery.modules.course.api.dto.CourseProgressResponse;
import com.codemastery.modules.course.api.dto.CourseProgressResponse.CourseProgress;
import com.codemastery.modules.course.api.dto.CourseProgressResponse.CourseProgressPage;
import com.codemastery.modules.course.api.dto.CourseProgressResponse.CourseResume;
import com.codemastery.modules.course.api.dto.CourseProgressResponse.LearnerResume;
import com.codemastery.modules.course.api.dto.CourseSearchRequest;
import com.codemastery.modules.course.application.service.CourseAccessService;
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
    private final CourseAccessService access;
    public EnrollmentController(EnrollmentService enrollments, LessonProgressService progress, CourseAccessService access) {
        this.enrollments = enrollments; this.progress = progress; this.access = access;
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

    // CT22.progress / CT22.resume (ADR006 §§3, 4, 7.2). Order: 401/role (security chain),
    // 415 media type, 400 path/query, CT03 403/404, then 400 body; no write before admission.
    @GetMapping("/api/courses/{courseId}/progress")
    public ResponseEntity<CourseProgress> progress(@PathVariable String courseId, @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return response(CourseProgress.from(progress.progress(CourseSearchRequest.parseCourseId(courseId))));
    }
    @PutMapping(path = "/api/courses/{courseId}/lessons/{lessonId}/completion", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CourseProgress> complete(@PathVariable String courseId, @PathVariable String lessonId,
            @RequestParam MultiValueMap<String, String> params, @RequestBody(required = false) String body) {
        CourseSearchRequest.noQuery(params);
        var course = CourseSearchRequest.parseCourseId(courseId);
        var lesson = CourseSearchRequest.parseCourseId(lessonId);
        if (!CourseProgressResponse.isCompletionWrite(body)) {
            access.requireLearnerLessonAccess(course, lesson);
            throw CourseProgressResponse.invalidBody();
        }
        return response(CourseProgress.from(progress.complete(course, lesson)));
    }
    @GetMapping("/api/me/course-progress")
    public ResponseEntity<CourseProgressPage> myCourseProgress(@RequestParam MultiValueMap<String, String> params) {
        var request = CourseSearchRequest.parsePage(params);
        return response(CourseProgressPage.from(progress.myCourseProgress(request.page(), request.size())));
    }
    @GetMapping("/api/courses/{courseId}/resume")
    public ResponseEntity<CourseResume> resume(@PathVariable String courseId, @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return response(CourseResume.from(progress.resume(CourseSearchRequest.parseCourseId(courseId))));
    }
    @PutMapping(path = "/api/courses/{courseId}/resume", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CourseResume> visit(@PathVariable String courseId, @RequestParam MultiValueMap<String, String> params,
            @RequestBody(required = false) String body) {
        CourseSearchRequest.noQuery(params);
        var course = CourseSearchRequest.parseCourseId(courseId);
        var lesson = CourseProgressResponse.visitLessonId(body);
        if (lesson.isEmpty()) {
            access.requireLearnerCourseAccess(course);
            throw CourseProgressResponse.invalidBody();
        }
        return response(CourseResume.from(progress.visit(course, lesson.get())));
    }
    @GetMapping("/api/me/resume")
    public ResponseEntity<LearnerResume> myResume(@RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return response(LearnerResume.from(progress.myResume()));
    }
    /** Course owns cache policy for its private responses, including MVC error advice. */
    @ControllerAdvice
    public static class MembershipCacheAdvice implements ResponseBodyAdvice<Object> {
        private static final java.util.Set<String> PRIVATE_PATHS = java.util.Set.of("/api/me/courses", "/api/me/course-progress", "/api/me/resume");
        private static final java.util.regex.Pattern PRIVATE_COURSE_PATH =
                java.util.regex.Pattern.compile("/api/courses/[^/]+/(membership|progress|resume|lessons/[^/]+/completion)");
        @Override public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) { return true; }
        @Override public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request, ServerHttpResponse response) {
            String path = request.getURI().getPath();
            if (PRIVATE_PATHS.contains(path) || PRIVATE_COURSE_PATH.matcher(path).matches()) response.getHeaders().setCacheControl("no-store");
            return body;
        }
    }
    private static <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
