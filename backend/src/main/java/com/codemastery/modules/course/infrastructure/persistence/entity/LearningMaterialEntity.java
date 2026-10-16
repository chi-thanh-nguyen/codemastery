package com.codemastery.modules.course.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "learning_materials", schema = "public")

public class LearningMaterialEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "lesson_id", nullable = false)
    private UUID lesson_id;

    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "material_type", nullable = false, columnDefinition = "text")
    private String materialType;

    @Column(name = "publication_status", nullable = false, columnDefinition = "text")
    private String publicationStatus;

    @Column(name = "text_content", nullable = true, columnDefinition = "text")
    private String textContent;

    @Column(name = "object_key", nullable = true, columnDefinition = "text")
    private String objectKey;

    @Column(name = "original_filename", nullable = true, columnDefinition = "text")
    private String originalFileName;

    @Column(name = "external_video_url", nullable = true, columnDefinition = "text")
    private String externalVideoUrl;

    protected LearningMaterialEntity() {}

    public LearningMaterialEntity(UUID id,
                                  UUID lesson_id,
                                  String title,
                                  String materialType,
                                  String publicationStatus,
                                  String textContent,
                                  String objectKey,
                                  String originalFileName,
                                  String externalVideoUrl) {
        this.id = id;
        this.lesson_id = lesson_id;
        this.title = title;
        this.materialType = materialType;
        this.publicationStatus = publicationStatus;
        this.textContent = textContent;
        this.objectKey = objectKey;
        this.originalFileName = originalFileName;
        this.externalVideoUrl = externalVideoUrl;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLesson_id() {
        return lesson_id;
    }

    public String getTitle() {
        return title;
    }

    public String getMaterialType() {
        return materialType;
    }

    public String getPublicationStatus() {
        return publicationStatus;
    }

    public String getTextContent() {
        return textContent;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getExternalVideoUrl() {
        return externalVideoUrl;
    }
}