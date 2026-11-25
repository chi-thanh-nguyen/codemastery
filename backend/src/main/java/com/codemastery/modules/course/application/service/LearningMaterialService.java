package com.codemastery.modules.course.application.service;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.infrastructure.storage.FileStorage;
import com.codemastery.infrastructure.storage.FileStorage.ObjectNotFoundException;
import com.codemastery.modules.course.api.dto.CourseStructureRequests.MaterialWrite;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.Material;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.MaterialType;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseContentRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static com.codemastery.modules.course.application.service.CourseManagementService.requiredText;

/** CT21.authoring/CT09 consumer. Storage and streaming run outside every database transaction. */
@Service
public class LearningMaterialService {
    private static final Logger LOG = LoggerFactory.getLogger(LearningMaterialService.class);
    private final CourseManagementService management;
    private final CourseAccessService access;
    private final CourseContentRepository content;
    private final FileStorage storage;
    private final EntityManager persistence;
    private final TransactionTemplate read;
    private final TransactionTemplate write;
    private final long uploadLimit;

    public LearningMaterialService(CourseManagementService management, CourseAccessService access,
            CourseContentRepository content, @Lazy FileStorage storage, EntityManager persistence,
            PlatformTransactionManager transactions, Environment environment) {
        this.management = Objects.requireNonNull(management);
        this.access = Objects.requireNonNull(access);
        this.content = Objects.requireNonNull(content);
        this.storage = Objects.requireNonNull(storage);
        this.persistence = Objects.requireNonNull(persistence);
        read = new TransactionTemplate(transactions);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionTemplate.ISOLATION_REPEATABLE_READ);
        write = new TransactionTemplate(transactions);
        write.setIsolationLevel(TransactionTemplate.ISOLATION_READ_COMMITTED);
        long configured;
        try { configured = DataSize.parse(environment.getRequiredProperty("spring.servlet.multipart.max-file-size")).toBytes(); }
        catch (RuntimeException invalid) { throw new IllegalStateException("Invalid MAX_UPLOAD_SIZE"); }
        if (configured < 1 || configured > Integer.MAX_VALUE) throw new IllegalStateException("Invalid MAX_UPLOAD_SIZE");
        uploadLimit = configured;
    }

    public Material createRelationalMaterial(UUID courseId, UUID lessonId, MaterialWrite command) {
        return management.withCourseConfigurationWrite(courseId, () -> {
            access.requireLessonOwnership(courseId, lessonId);
            LearningMaterial row = relational(UUID.randomUUID(), lessonId, PublishStatus.HIDDEN, command);
            content.insertMaterial(row);
            return Material.of(row);
        });
    }

    public Material updateRelationalMaterial(UUID courseId, UUID lessonId, UUID materialId, MaterialWrite command) {
        return management.withCourseConfigurationWrite(courseId, () -> {
            LearningMaterial previous = owned(courseId, lessonId, materialId, false);
            LearningMaterial row = relational(materialId, lessonId, previous.publicationStatus(), command);
            if (row.materialType() != previous.materialType()) throw invalid();
            content.replaceMaterialContent(row);
            return Material.of(row);
        });
    }

    public Material setRelationalMaterialPublication(UUID courseId, UUID lessonId, UUID materialId, PublishStatus status) {
        return publication(courseId, lessonId, materialId, status, false);
    }

    /** Controller admission for B5 JSON bodies, before the body is accessed. Rechecked by each writer. */
    public void requireOwnedFile(UUID courseId, UUID lessonId, UUID materialId) {
        read.executeWithoutResult(status -> owned(courseId, lessonId, materialId, true));
    }

    public Material createFileMaterial(UUID courseId, UUID lessonId, Supplier<FileMaterialUpload> parts) {
        requireOutermost();
        read.executeWithoutResult(status -> access.requireLessonOwnership(courseId, lessonId));
        FileMaterialUpload upload = Objects.requireNonNull(parts.get());
        FilePart part = Objects.requireNonNull(upload.file());
        return storeThenWrite(courseId, lessonId, UUID.randomUUID(), upload.title(), part, false);
    }

    public Material updateFileMaterialTitle(UUID courseId, UUID lessonId, UUID materialId, String title) {
        return management.withCourseConfigurationWrite(courseId, () -> {
            LearningMaterial row = owned(courseId, lessonId, materialId, true);
            LearningMaterial updated = file(row.id(), lessonId, requiredText(title), row.publicationStatus(),
                    row.objectKey(), row.originalFileName());
            content.replaceMaterialContent(updated);
            return Material.of(updated);
        });
    }

    public Material replaceFileMaterialFile(UUID courseId, UUID lessonId, UUID materialId, Supplier<FilePart> parts) {
        requireOutermost();
        requireOwnedFile(courseId, lessonId, materialId);
        return storeThenWrite(courseId, lessonId, materialId, null, Objects.requireNonNull(parts.get()), true);
    }

    public Material setFileMaterialPublication(UUID courseId, UUID lessonId, UUID materialId, PublishStatus status) {
        return publication(courseId, lessonId, materialId, status, true);
    }

    private Material publication(UUID courseId, UUID lessonId, UUID materialId, PublishStatus status, boolean file) {
        return management.withCourseConfigurationWrite(courseId, () -> {
            LearningMaterial row = owned(courseId, lessonId, materialId, file);
            Objects.requireNonNull(status);
            if (row.publicationStatus() != status) content.updateMaterialPublicationStatus(lessonId, materialId, status);
            return new Material(row.id(), row.title(), status.getStatus(), row.materialType().getType(),
                    row.textContent(), row.externalVideoUrl());
        });
    }

    private Material storeThenWrite(UUID courseId, UUID lessonId, UUID materialId, String title, FilePart part, boolean replace) {
        String fresh = "materials/" + UUID.randomUUID();
        Outcome outcome = new Outcome();
        String[] previousKey = {null};
        boolean storageAttempted = false;
        try {
            String admittedTitle;
            try (InputStream input = Objects.requireNonNull(part.content())) {
                admittedTitle = replace ? null : requiredText(title);
                validateFilename(part.originalFilename());
                if (part.size() > uploadLimit) throw new org.springframework.web.multipart.MaxUploadSizeExceededException(uploadLimit);
                if (part.size() < 1) throw invalid();
                BufferedInputStream buffered = new BufferedInputStream(input);
                buffered.mark(5);
                if (!java.util.Arrays.equals(buffered.readNBytes(5), "%PDF-".getBytes(StandardCharsets.US_ASCII))) throw invalid();
                buffered.reset();
                storageAttempted = true;
                storage.store(fresh, buffered, part.size(), "application/pdf");
            }
            Material result = write.execute(transaction -> {
                TransactionSynchronizationManager.registerSynchronization(outcome);
                return management.withCourseConfigurationWrite(courseId, () -> {
                    access.requireLessonOwnership(courseId, lessonId);
                    LearningMaterial old = replace ? owned(courseId, lessonId, materialId, true) : null;
                    if (old != null) { validateKey(old.objectKey()); previousKey[0] = old.objectKey(); }
                    LearningMaterial row = file(materialId, lessonId, replace ? old.title() : admittedTitle,
                            replace ? old.publicationStatus() : PublishStatus.HIDDEN, fresh, part.originalFilename());
                    if (replace) content.replaceMaterialContent(row); else content.insertMaterial(row);
                    persistence.flush();
                    return Material.of(row);
                });
            });
            if (!outcome.committed) throw failure();
            if (previousKey[0] != null) cleanup(previousKey[0], "superseded", materialId);
            return result;
        } catch (RuntimeException | IOException problem) {
            if (outcome.committed) {
                if (previousKey[0] != null) cleanup(previousKey[0], "superseded", materialId);
                throw failure();
            }
            if (outcome.commitPhase) {
                LOG.error("Material operation outcome unknown operation={} rowId={} correlationId={}",
                        replace ? "replace-file" : "create-file", materialId, MDC.get("correlationId"));
                throw failure();
            }
            if (storageAttempted) cleanup(fresh, "unreferenced", materialId);
            if (problem instanceof RuntimeException runtime) throw runtime;
            throw failure();
        }
    }

    /** A rollback reported after beforeCommit is deliberately UNKNOWN, never proof of non-commit. */
    private static final class Outcome implements TransactionSynchronization {
        boolean commitPhase;
        boolean committed;
        @Override public void beforeCommit(boolean readOnly) { commitPhase = true; }
        @Override public void afterCompletion(int status) { committed = status == STATUS_COMMITTED; }
    }

    public MaterialFile downloadMaterialFile(UUID courseId, UUID lessonId, UUID materialId) {
        requireOutermost();
        LearningMaterial row = downloadRow(courseId, lessonId, materialId);
        FileStorage.StoredObject opened;
        try { opened = storage.open(row.objectKey()); }
        catch (ObjectNotFoundException missing) {
            LearningMaterial current = downloadRow(courseId, lessonId, materialId);
            if (current.objectKey().equals(row.objectKey())) throw failure();
            LOG.info("Material download concurrent replacement rowId={}", materialId);
            row = current;
            opened = storage.open(row.objectKey());
        }
        try {
            if (opened.contentLength() < 1 || !"application/pdf".equals(opened.mediaType())) throw failure();
            return new MaterialFile(row.originalFileName(), "application/pdf", opened.contentLength(), opened.content());
        } catch (RuntimeException problem) {
            try { opened.close(); } catch (IOException close) { problem.addSuppressed(close); }
            throw problem;
        }
    }

    private LearningMaterial downloadRow(UUID courseId, UUID lessonId, UUID materialId) {
        return read.execute(status -> {
            access.requireMaterialReadAccess(courseId, lessonId, materialId);
            LearningMaterial row = content.findMaterial(lessonId, materialId).orElseThrow(LearningMaterialService::unavailable);
            if (row.materialType() != MaterialType.FILE) throw unavailable();
            validateKey(row.objectKey());
            try { validateFilename(row.originalFileName()); } catch (BusinessRuleException invalid) { throw failure(); }
            return row;
        });
    }

    private LearningMaterial owned(UUID courseId, UUID lessonId, UUID materialId, boolean file) {
        access.requireLessonOwnership(courseId, lessonId);
        LearningMaterial row = content.findMaterial(lessonId, materialId).orElseThrow(LearningMaterialService::unavailable);
        if ((row.materialType() == MaterialType.FILE) != file) {
            if (file) throw unavailable();
            throw invalid();
        }
        return row;
    }

    private static LearningMaterial relational(UUID id, UUID lesson, PublishStatus status, MaterialWrite command) {
        if (command == null) throw invalid();
        String title = requiredText(command.title());
        if (command.materialType() == MaterialType.TEXT && command.externalVideoUrl() == null && command.textContent() != null) {
            String text = CourseManagementService.markdown(command.textContent());
            return new LearningMaterial(id, lesson, title, MaterialType.TEXT, status, text, null, null, null);
        }
        if (command.materialType() == MaterialType.EXTERNAL_VIDEO && command.textContent() == null) {
            String url = canonicalVideo(command.externalVideoUrl());
            return new LearningMaterial(id, lesson, title, MaterialType.EXTERNAL_VIDEO, status, null, null, null, url);
        }
        throw invalid();
    }

    static String canonicalVideo(String input) {
        if (input == null) throw invalid();
        // CT37 permits only outer ASCII spaces, not URI normalization or Unicode whitespace.
        String url = input.replaceAll("^ +| +$", "");
        try {
            URI parsed = URI.create(url);
            if (!url.matches("https://www\\.youtube-nocookie\\.com/embed/[A-Za-z0-9_-]+")
                    || !parsed.toASCIIString().equals(url)) throw invalid();
        } catch (IllegalArgumentException malformed) { throw invalid(); }
        return url;
    }

    private static LearningMaterial file(UUID id, UUID lesson, String title, PublishStatus status, String key, String filename) {
        return new LearningMaterial(id, lesson, title, MaterialType.FILE, status, null, key, filename, null);
    }

    static void validateFilename(String filename) {
        if (filename == null || !CourseManagementService.isStorable(filename) || filename.isBlank()
                || filename.getBytes(StandardCharsets.UTF_8).length > 255
                || filename.codePoints().anyMatch(c -> c < 32 || c == 127 || c == '/' || c == '\\')
                || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) throw invalid();
    }

    private static void validateKey(String key) {
        if (key == null || !key.matches("materials/[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) throw failure();
    }

    private static void requireOutermost() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) throw failure();
    }

    private void cleanup(String key, String operation, UUID rowId) {
        try { storage.delete(key); }
        catch (RuntimeException ignored) { LOG.warn("Material cleanup failed operation={} rowId={} correlationId={}", operation, rowId, MDC.get("correlationId")); }
    }

    private static BusinessRuleException invalid() { return new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid material request"); }
    private static IllegalStateException failure() { return new IllegalStateException("Material operation failed"); }
    private static ResourceNotFoundException unavailable() { return new ResourceNotFoundException("Resource not available"); }

    public record FilePart(String originalFilename, long size, InputStream content) {}
    public record FileMaterialUpload(String title, FilePart file) {}
    public record MaterialFile(String originalFilename, String mediaType, long contentLength, InputStream content) implements AutoCloseable {
        @Override public void close() throws IOException { content.close(); }
    }
}
