package com.codemastery.modules.course.api.controller;

import com.codemastery.modules.course.api.dto.CourseRequest;
import com.codemastery.modules.course.api.dto.CourseSearchRequest;
import com.codemastery.modules.course.api.dto.CourseStructureRequests.ChapterWrite;
import com.codemastery.modules.course.api.dto.CourseStructureRequests.LessonWrite;
import com.codemastery.modules.course.api.dto.CourseStructureRequests.SiblingOrder;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.LearnerLesson;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedChapter;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedLesson;
import com.codemastery.modules.course.application.service.CourseStructureService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import static com.codemastery.modules.course.api.dto.CourseSearchRequest.parseCourseId;

@RestController
public class CourseStructureController {
    private final CourseStructureService structure;

    public CourseStructureController(CourseStructureService structure) {
        this.structure = structure;
    }

    @PostMapping("/api/instructor/courses/{courseId}/chapters")
    public ResponseEntity<OwnedChapter> createCourseChapter(@PathVariable String courseId, @RequestBody ChapterWrite body,
                                                            @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return created(structure.createCourseChapter(parseCourseId(courseId), body));
    }

    @PutMapping("/api/instructor/courses/{courseId}/chapters/order")
    public ResponseEntity<SiblingOrder> orderCourseChapters(@PathVariable String courseId, @RequestBody SiblingOrder body,
                                                            @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return ok(structure.orderCourseChapters(parseCourseId(courseId), body));
    }

    @PutMapping("/api/instructor/courses/{courseId}/chapters/{chapterId}")
    public ResponseEntity<OwnedChapter> updateCourseChapter(@PathVariable String courseId, @PathVariable String chapterId,
                                                            @RequestBody ChapterWrite body,
                                                            @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return ok(structure.updateCourseChapter(parseCourseId(courseId), parseCourseId(chapterId), body));
    }

    @PostMapping("/api/instructor/courses/{courseId}/chapters/{chapterId}/lessons")
    public ResponseEntity<OwnedLesson> createCourseLesson(@PathVariable String courseId, @PathVariable String chapterId,
                                                          @RequestBody LessonWrite body,
                                                          @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return created(structure.createCourseLesson(parseCourseId(courseId), parseCourseId(chapterId), body));
    }

    @PutMapping("/api/instructor/courses/{courseId}/chapters/{chapterId}/lessons/order")
    public ResponseEntity<SiblingOrder> orderChapterLessons(@PathVariable String courseId, @PathVariable String chapterId,
                                                            @RequestBody SiblingOrder body,
                                                            @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return ok(structure.orderChapterLessons(parseCourseId(courseId), parseCourseId(chapterId), body));
    }

    @GetMapping("/api/instructor/courses/{courseId}/lessons/{lessonId}")
    public ResponseEntity<OwnedLesson> getOwnedLesson(@PathVariable String courseId, @PathVariable String lessonId,
                                                      @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return ok(structure.getOwnedLesson(parseCourseId(courseId), parseCourseId(lessonId)));
    }

    @PutMapping("/api/instructor/courses/{courseId}/lessons/{lessonId}")
    public ResponseEntity<OwnedLesson> updateCourseLesson(@PathVariable String courseId, @PathVariable String lessonId,
                                                          @RequestBody LessonWrite body,
                                                          @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return ok(structure.updateCourseLesson(parseCourseId(courseId), parseCourseId(lessonId), body));
    }

    @PutMapping("/api/instructor/courses/{courseId}/lessons/{lessonId}/publication")
    public ResponseEntity<OwnedLesson> setCourseLessonPublication(@PathVariable String courseId, @PathVariable String lessonId,
                                                                  @RequestBody CourseRequest.Publication body,
                                                                  @RequestParam MultiValueMap<String, String> params) {
        CourseSearchRequest.noQuery(params);
        return ok(structure.setCourseLessonPublication(parseCourseId(courseId), parseCourseId(lessonId), body));
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
