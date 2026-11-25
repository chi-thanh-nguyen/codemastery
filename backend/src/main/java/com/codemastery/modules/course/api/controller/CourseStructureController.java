package com.codemastery.modules.course.api.controller;

import com.codemastery.modules.course.api.dto.CourseRequest;
import com.codemastery.modules.course.api.dto.CourseSearchRequest;
import com.codemastery.modules.course.api.dto.CourseStructureRequests;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.LearnerLesson;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.Material;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedChapter;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedLesson;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.SiblingOrder;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.CourseStructureService;
import com.codemastery.modules.course.application.service.LearningMaterialService;
import com.codemastery.modules.course.application.service.LearningMaterialService.FileMaterialUpload;
import com.codemastery.modules.course.application.service.LearningMaterialService.FilePart;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

import static com.codemastery.modules.course.api.dto.CourseSearchRequest.parseCourseId;

/** CT21.authoring Chapter/Lesson operations, CO09 Lesson materials and the Learner Lesson read, with the precedence of {@link CourseManagementController}. */
@RestController
public class CourseStructureController {
    private static final String COURSE = "/api/instructor/courses/{courseId}";

    private final CourseStructureService structure;
    private final CourseAccessService access;
    private final LearningMaterialService materials;

    public CourseStructureController(CourseStructureService structure, CourseAccessService access,
                                     LearningMaterialService materials) {
        this.structure = structure;
        this.access = access;
        this.materials = materials;
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

    // CO09 relational materials (CT21.authoring, ADR006 §5): text or external_video only.

    @PostMapping(path = COURSE + "/lessons/{lessonId}/materials", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Material> createRelationalMaterial(@PathVariable String courseId, @PathVariable String lessonId,
                                                             @RequestParam MultiValueMap<String, String> params,
                                                             @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                             @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        UUID course = parseCourseId(courseId);
        UUID lesson = parseCourseId(lessonId);
        var write = CourseStructureRequests.relationalMaterialWrite(body);
        if (write.isEmpty()) {
            access.requireLessonOwnership(course, lesson);
            throw CourseRequest.invalidBody();
        }
        return created(materials.createRelationalMaterial(course, lesson, write.get()));
    }

    @PutMapping(path = COURSE + "/lessons/{lessonId}/materials/{materialId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Material> updateRelationalMaterial(@PathVariable String courseId, @PathVariable String lessonId,
                                                             @PathVariable String materialId,
                                                             @RequestParam MultiValueMap<String, String> params,
                                                             @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                             @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        UUID course = parseCourseId(courseId);
        UUID lesson = parseCourseId(lessonId);
        UUID material = parseCourseId(materialId);
        var write = CourseStructureRequests.relationalMaterialWrite(body);
        if (write.isEmpty()) {
            access.requireLessonOwnership(course, lesson);
            throw CourseRequest.invalidBody();
        }
        return ok(materials.updateRelationalMaterial(course, lesson, material, write.get()));
    }

    @PutMapping(path = COURSE + "/lessons/{lessonId}/materials/{materialId}/publication", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Material> setRelationalMaterialPublication(@PathVariable String courseId, @PathVariable String lessonId,
                                                                     @PathVariable String materialId,
                                                                     @RequestParam MultiValueMap<String, String> params,
                                                                     @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                                     @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        UUID course = parseCourseId(courseId);
        UUID lesson = parseCourseId(lessonId);
        UUID material = parseCourseId(materialId);
        var status = CourseRequest.publication(body);
        if (status.isEmpty()) {
            access.requireLessonOwnership(course, lesson);
            throw CourseRequest.invalidBody();
        }
        return ok(materials.setRelationalMaterialPublication(course, lesson, material, status.get()));
    }

    // CO09 file materials (CT09.rest, ADR010 §11). Multipart operations take the lazily resolved request and never
    // touch getParameter*/getPart* (so no @RequestParam/@RequestPart) before CT03 admission inside the service.

    @PostMapping(path = COURSE + "/lessons/{lessonId}/file-materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Material> createFileMaterial(@PathVariable String courseId, @PathVariable String lessonId,
                                                       MultipartHttpServletRequest request) {
        UUID course = parseCourseId(courseId);
        UUID lesson = parseCourseId(lessonId);
        return created(materials.createFileMaterial(course, lesson, () -> fileMaterialUpload(request)));
    }

    @PutMapping(path = COURSE + "/lessons/{lessonId}/file-materials/{materialId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Material> updateFileMaterialTitle(@PathVariable String courseId, @PathVariable String lessonId,
                                                            @PathVariable String materialId,
                                                            @RequestParam MultiValueMap<String, String> params,
                                                            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                            @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        UUID course = parseCourseId(courseId);
        UUID lesson = parseCourseId(lessonId);
        UUID material = parseCourseId(materialId);
        var title = CourseStructureRequests.fileMaterialTitle(body);
        if (title.isEmpty()) {
            access.requireLessonOwnership(course, lesson);
            throw CourseRequest.invalidBody();
        }
        return ok(materials.updateFileMaterialTitle(course, lesson, material, title.get()));
    }

    @PutMapping(path = COURSE + "/lessons/{lessonId}/file-materials/{materialId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Material> replaceFileMaterialFile(@PathVariable String courseId, @PathVariable String lessonId,
                                                            @PathVariable String materialId,
                                                            MultipartHttpServletRequest request) {
        UUID course = parseCourseId(courseId);
        UUID lesson = parseCourseId(lessonId);
        UUID material = parseCourseId(materialId);
        return ok(materials.replaceFileMaterialFile(course, lesson, material, () -> filePart(request)));
    }

    @PutMapping(path = COURSE + "/lessons/{lessonId}/file-materials/{materialId}/publication", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Material> setFileMaterialPublication(@PathVariable String courseId, @PathVariable String lessonId,
                                                               @PathVariable String materialId,
                                                               @RequestParam MultiValueMap<String, String> params,
                                                               @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                               @RequestBody(required = false) String body) throws HttpMediaTypeNotSupportedException {
        CourseRequest.requireJson(contentType);
        CourseSearchRequest.noQuery(params);
        UUID course = parseCourseId(courseId);
        UUID lesson = parseCourseId(lessonId);
        UUID material = parseCourseId(materialId);
        var status = CourseRequest.publication(body);
        if (status.isEmpty()) {
            access.requireLessonOwnership(course, lesson);
            throw CourseRequest.invalidBody();
        }
        return ok(materials.setFileMaterialPublication(course, lesson, material, status.get()));
    }

    /** Attachment download for an admitted Learner or owner preview; headers per ADR010 §11 (no range, no-store, nosniff). */
    @GetMapping("/api/courses/{courseId}/lessons/{lessonId}/materials/{materialId}/file")
    public ResponseEntity<StreamingResponseBody> downloadMaterialFile(@PathVariable String courseId, @PathVariable String lessonId,
                                                                      @PathVariable String materialId,
                                                                      @RequestParam MultiValueMap<String, String> params) {
        throw new UnsupportedOperationException();
    }

    /** Reads exactly the {@code title} and {@code file} parts; called by the service only after admission. */
    private static FileMaterialUpload fileMaterialUpload(MultipartHttpServletRequest request) {
        throw new UnsupportedOperationException();
    }

    /** Reads exactly the {@code file} part; called by the service only after admission. */
    private static FilePart filePart(MultipartHttpServletRequest request) {
        throw new UnsupportedOperationException();
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static <T> ResponseEntity<T> created(T body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body);
    }
}
