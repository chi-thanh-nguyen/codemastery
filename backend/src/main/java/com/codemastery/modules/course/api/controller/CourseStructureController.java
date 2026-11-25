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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.springframework.web.multipart.MultipartException;

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
        noMaterialQuery(request);
        UUID course = parseCourseId(courseId);
        UUID lesson = parseCourseId(lessonId);
        return created(materials.createFileMaterial(course, lesson, () -> fileMaterialUpload(request)));
    }

    @PutMapping(path = COURSE + "/lessons/{lessonId}/file-materials/{materialId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Material> updateFileMaterialTitle(@PathVariable String courseId, @PathVariable String lessonId,
            @PathVariable String materialId, HttpServletRequest request) throws HttpMediaTypeNotSupportedException, IOException {
        CourseRequest.requireJson(request.getContentType());
        noMaterialQuery(request);
        UUID course = parseCourseId(courseId), lesson = parseCourseId(lessonId), material = parseCourseId(materialId);
        materials.requireOwnedFile(course, lesson, material);
        String title = CourseStructureRequests.fileMaterialTitle(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .orElseThrow(CourseRequest::invalidBody);
        return ok(materials.updateFileMaterialTitle(course, lesson, material, title));
    }

    @PutMapping(path = COURSE + "/lessons/{lessonId}/file-materials/{materialId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Material> replaceFileMaterialFile(@PathVariable String courseId, @PathVariable String lessonId,
                                                            @PathVariable String materialId,
                                                            MultipartHttpServletRequest request) {
        noMaterialQuery(request);
        UUID course = parseCourseId(courseId);
        UUID lesson = parseCourseId(lessonId);
        UUID material = parseCourseId(materialId);
        return ok(materials.replaceFileMaterialFile(course, lesson, material, () -> filePart(request)));
    }

    @PutMapping(path = COURSE + "/lessons/{lessonId}/file-materials/{materialId}/publication", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Material> setFileMaterialPublication(@PathVariable String courseId, @PathVariable String lessonId,
            @PathVariable String materialId, HttpServletRequest request) throws HttpMediaTypeNotSupportedException, IOException {
        CourseRequest.requireJson(request.getContentType());
        noMaterialQuery(request);
        UUID course = parseCourseId(courseId), lesson = parseCourseId(lessonId), material = parseCourseId(materialId);
        materials.requireOwnedFile(course, lesson, material);
        var status = CourseRequest.publication(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .orElseThrow(CourseRequest::invalidBody);
        return ok(materials.setFileMaterialPublication(course, lesson, material, status));
    }

    /** Attachment download for an admitted Learner or owner preview; headers per ADR010 §11 (no range, no-store, nosniff). */
    @GetMapping("/api/courses/{courseId}/lessons/{lessonId}/materials/{materialId}/file")
    public void downloadMaterialFile(@PathVariable String courseId, @PathVariable String lessonId,
            @PathVariable String materialId, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        noMaterialQuery(request);
        // Synchronous ownership encloses header preparation, copy, disconnect and close. An async
        // StreamingResponseBody would leave an opened object ownerless if dispatch never began.
        try (var file = materials.downloadMaterialFile(parseCourseId(courseId), parseCourseId(lessonId), parseCourseId(materialId))) {
            response.setContentType(file.mediaType());
            response.setContentLengthLong(file.contentLength());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition(file.originalFilename()));
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setStatus(200);
            file.content().transferTo(response.getOutputStream());
        } catch (IOException | RuntimeException failure) {
            if (!response.isCommitted()) response.reset();
            throw failure;
        }
    }

    private static String disposition(String filename) {
        StringBuilder ascii = new StringBuilder();
        filename.codePoints().forEach(c -> ascii.append(c < 128 && c != '"' && c != '\\' ? (char)c : '_'));
        StringBuilder encoded = new StringBuilder();
        for (byte b : filename.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 255;
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || "!#$&+-.^_`|~".indexOf(c) >= 0) encoded.append((char)c);
            else encoded.append("%%%02X".formatted(c));
        }
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private static void noMaterialQuery(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) throw CourseRequest.invalidBody();
    }

    /** Servlet Parts retain raw filenames and multiplicity; no Spring filename normalization. */
    private static List<Part> parts(MultipartHttpServletRequest request, Set<String> names) {
        try {
            List<Part> parts = List.copyOf(request.getParts());
            if (parts.size() != names.size() || !parts.stream().map(Part::getName).collect(java.util.stream.Collectors.toSet()).equals(names)) {
                throw CourseRequest.invalidBody();
            }
            return parts;
        } catch (org.springframework.web.multipart.MaxUploadSizeExceededException tooLarge) { throw tooLarge; }
        catch (IllegalStateException tooLarge) { throw new org.springframework.web.multipart.MaxUploadSizeExceededException(-1); }
        catch (jakarta.servlet.ServletException | IOException | MultipartException malformed) { throw CourseRequest.invalidBody(); }
    }

    private static FileMaterialUpload fileMaterialUpload(MultipartHttpServletRequest request) {
        List<Part> parts = parts(request, Set.of("title", "file"));
        Part title = parts.stream().filter(p -> p.getName().equals("title")).findFirst().orElseThrow();
        Part file = parts.stream().filter(p -> p.getName().equals("file")).findFirst().orElseThrow();
        if (title.getSubmittedFileName() != null || file.getSubmittedFileName() == null) throw CourseRequest.invalidBody();
        String text;
        try (var input = title.getInputStream()) {
            var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            text = decoder.decode(java.nio.ByteBuffer.wrap(input.readAllBytes())).toString();
        } catch (IOException malformed) { throw CourseRequest.invalidBody(); }
        // Complete title closure before acquiring the stream that ownership transfers to the service.
        try { return new FileMaterialUpload(text, new FilePart(file.getSubmittedFileName(), file.getSize(), file.getInputStream())); }
        catch (IOException malformed) { throw CourseRequest.invalidBody(); }
    }

    private static FilePart filePart(MultipartHttpServletRequest request) {
        Part file = parts(request, Set.of("file")).getFirst();
        if (file.getSubmittedFileName() == null) throw CourseRequest.invalidBody();
        try { return new FilePart(file.getSubmittedFileName(), file.getSize(), file.getInputStream()); }
        catch (IOException malformed) { throw CourseRequest.invalidBody(); }
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static <T> ResponseEntity<T> created(T body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body);
    }
}
