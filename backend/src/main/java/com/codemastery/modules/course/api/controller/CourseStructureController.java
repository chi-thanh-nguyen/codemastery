package com.codemastery.modules.course.api.controller;

import com.codemastery.modules.course.api.dto.CourseRequest;
import com.codemastery.modules.course.api.dto.CourseSearchRequest;
import com.codemastery.modules.course.api.dto.CourseStructureRequests;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.LearnerLesson;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedChapter;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedLesson;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.SiblingOrder;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.CourseStructureService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.codemastery.modules.course.api.dto.CourseSearchRequest.parseCourseId;

/** CT21.authoring Chapter/Lesson operations and the Learner Lesson read, with the precedence of {@link CourseManagementController}. */
@RestController
public class CourseStructureController {
    private static final String COURSE = "/api/instructor/courses/{courseId}";

    private final CourseStructureService structure;
    private final CourseAccessService access;

    public CourseStructureController(CourseStructureService structure, CourseAccessService access) {
        this.structure = structure;
        this.access = access;
    }

    @PostMapping(path = COURSE + "/chapters", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OwnedChapter> createCourseChapter(@PathVariable String courseId,
                                                            @RequestParam MultiValueMap<String, String> params,
                                                            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                            @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        UUID course = parseCourseId(courseId);
        var title = CourseStructureRequests.chapterTitle(body);
        if (title.isEmpty()) {
            access.requireCourseOwnership(course);
            throw CourseRequest.invalidBody();
        }
        return created(structure.createCourseChapter(course, title.get()));
    }

    @PutMapping(path = COURSE + "/chapters/order", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SiblingOrder> orderCourseChapters(@PathVariable String courseId,
                                                            @RequestParam MultiValueMap<String, String> params,
                                                            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                            @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        UUID course = parseCourseId(courseId);
        var order = CourseStructureRequests.siblingOrder(body);
        if (order.isEmpty()) {
            access.requireCourseOwnership(course);
            throw CourseRequest.invalidBody();
        }
        return ok(new SiblingOrder(structure.orderCourseChapters(course, order.get())));
    }

    @PutMapping(path = COURSE + "/chapters/{chapterId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OwnedChapter> updateCourseChapter(@PathVariable String courseId, @PathVariable String chapterId,
                                                            @RequestParam MultiValueMap<String, String> params,
                                                            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                            @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        UUID course = parseCourseId(courseId);
        UUID chapter = parseCourseId(chapterId);
        var title = CourseStructureRequests.chapterTitle(body);
        if (title.isEmpty()) {
            structure.requireOwnedChapter(course, chapter);
            throw CourseRequest.invalidBody();
        }
        return ok(structure.updateCourseChapter(course, chapter, title.get()));
    }

    @PostMapping(path = COURSE + "/chapters/{chapterId}/lessons", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OwnedLesson> createCourseLesson(@PathVariable String courseId, @PathVariable String chapterId,
                                                          @RequestParam MultiValueMap<String, String> params,
                                                          @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                          @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        UUID course = parseCourseId(courseId);
        UUID chapter = parseCourseId(chapterId);
        var write = CourseStructureRequests.lessonWrite(body);
        if (write.isEmpty()) {
            structure.requireOwnedChapter(course, chapter);
            throw CourseRequest.invalidBody();
        }
        return created(structure.createCourseLesson(course, chapter, write.get().title(), write.get().contentMarkdown()));
    }

    @PutMapping(path = COURSE + "/chapters/{chapterId}/lessons/order", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SiblingOrder> orderChapterLessons(@PathVariable String courseId, @PathVariable String chapterId,
                                                            @RequestParam MultiValueMap<String, String> params,
                                                            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                            @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        UUID course = parseCourseId(courseId);
        UUID chapter = parseCourseId(chapterId);
        var order = CourseStructureRequests.siblingOrder(body);
        if (order.isEmpty()) {
            structure.requireOwnedChapter(course, chapter);
            throw CourseRequest.invalidBody();
        }
        return ok(new SiblingOrder(structure.orderChapterLessons(course, chapter, order.get())));
    }

    @GetMapping(COURSE + "/lessons/{lessonId}")
    public ResponseEntity<OwnedLesson> getOwnedLesson(@PathVariable String courseId, @PathVariable String lessonId,
                                                      @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return ok(structure.getOwnedLesson(parseCourseId(courseId), parseCourseId(lessonId)));
    }

    @PutMapping(path = COURSE + "/lessons/{lessonId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OwnedLesson> updateCourseLesson(@PathVariable String courseId, @PathVariable String lessonId,
                                                          @RequestParam MultiValueMap<String, String> params,
                                                          @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                          @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        UUID course = parseCourseId(courseId);
        UUID lesson = parseCourseId(lessonId);
        var write = CourseStructureRequests.lessonWrite(body);
        if (write.isEmpty()) {
            access.requireLessonOwnership(course, lesson);
            throw CourseRequest.invalidBody();
        }
        return ok(structure.updateCourseLesson(course, lesson, write.get().title(), write.get().contentMarkdown()));
    }

    @PutMapping(path = COURSE + "/lessons/{lessonId}/publication", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OwnedLesson> setCourseLessonPublication(@PathVariable String courseId, @PathVariable String lessonId,
                                                                  @RequestParam MultiValueMap<String, String> params,
                                                                  @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                                  @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        UUID course = parseCourseId(courseId);
        UUID lesson = parseCourseId(lessonId);
        var status = CourseRequest.publication(body);
        if (status.isEmpty()) {
            access.requireLessonOwnership(course, lesson);
            throw CourseRequest.invalidBody();
        }
        return ok(structure.setCourseLessonPublication(course, lesson, status.get()));
    }

    @GetMapping("/api/courses/{courseId}/lessons/{lessonId}")
    public ResponseEntity<LearnerLesson> getLearnerLesson(@PathVariable String courseId, @PathVariable String lessonId,
                                                          @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return ok(structure.getLearnerLesson(parseCourseId(courseId), parseCourseId(lessonId)));
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static <T> ResponseEntity<T> created(T body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body);
    }
}
