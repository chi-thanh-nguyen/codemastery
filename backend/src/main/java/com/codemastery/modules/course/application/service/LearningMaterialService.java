package com.codemastery.modules.course.application.service;

import com.codemastery.modules.course.api.dto.CourseStructureRequests.MaterialWrite;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.Material;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseContentRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * CO09: Lesson learning materials. Relational text/external_video operations follow CT21.authoring
 * (ADR006 §5) with the CT37.embed canonical URL (ADR006 §6); file materials follow CT09 (ADR010
 * §§4, 5, 7, 11, 14): PDF only, backend-generated keys, store-then-commit, backend-mediated download.
 * Every mutation runs inside {@link CourseManagementService#withCourseConfigurationWrite}; downloads
 * admit through CT03 {@code requireMaterialReadAccess} before any storage call. Method names mirror the
 * OpenAPI operationIds (the {@code b5} batch prefix dropped).
 *
 * <p>The CT09.interface {@code FileStorage} port is delivered by SH05; it is injected once that port exists.
 */
@Service
public class LearningMaterialService {
    private final CourseManagementService management;
    private final CourseAccessService access;
    private final CourseContentRepository content;

    public LearningMaterialService(CourseManagementService management, CourseAccessService access,
                                   CourseContentRepository content) {
        this.management = Objects.requireNonNull(management);
        this.access = Objects.requireNonNull(access);
        this.content = Objects.requireNonNull(content);
    }

    // CT21.authoring relational materials (text or external_video only; file mutations are 400 here).

    public Material createRelationalMaterial(UUID courseId, UUID lessonId, MaterialWrite write) {
        throw new UnsupportedOperationException();
    }

    public Material updateRelationalMaterial(UUID courseId, UUID lessonId, UUID materialId, MaterialWrite write) {
        throw new UnsupportedOperationException();
    }

    public Material setRelationalMaterialPublication(UUID courseId, UUID lessonId, UUID materialId,
                                                     PublishStatus publicationStatus) {
        throw new UnsupportedOperationException();
    }

    // CT09.rest file materials. Multipart parts are read through the supplier only after admission (ADR010 §14 stage 3).

    public Material createFileMaterial(UUID courseId, UUID lessonId, Supplier<FileMaterialUpload> parts) {
        throw new UnsupportedOperationException();
    }

    public Material updateFileMaterialTitle(UUID courseId, UUID lessonId, UUID materialId, String title) {
        throw new UnsupportedOperationException();
    }

    public Material replaceFileMaterialFile(UUID courseId, UUID lessonId, UUID materialId, Supplier<FilePart> part) {
        throw new UnsupportedOperationException();
    }

    public Material setFileMaterialPublication(UUID courseId, UUID lessonId, UUID materialId,
                                               PublishStatus publicationStatus) {
        throw new UnsupportedOperationException();
    }

    /** Read → open → write (ADR010 §11): no transaction or pooled connection is held while the caller copies the body. */
    public MaterialFile downloadMaterialFile(UUID courseId, UUID lessonId, UUID materialId) {
        throw new UnsupportedOperationException();
    }

    /** One raw file part: the client's filename and bytes; the client part Content-Type is ignored (ADR010 §5). */
    public record FilePart(String originalFilename, long size, InputStream content) {}

    /** The {@code title} and {@code file} parts of B5MaterialUpload. */
    public record FileMaterialUpload(String title, FilePart file) {}

    /** A download the caller writes and then closes; the headers derive from the same row read (ADR010 §11). */
    public record MaterialFile(String originalFilename, String mediaType, long contentLength, InputStream content)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            content.close();
        }
    }
}
