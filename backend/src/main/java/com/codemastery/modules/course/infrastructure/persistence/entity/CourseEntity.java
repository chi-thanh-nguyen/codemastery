package com.codemastery.modules.course.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "courses", schema = "public")
public class CourseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "prerequisite_description", nullable = true, columnDefinition = "text")
    private String prerequisiteDescription;

    @Column(name = "publication_status", nullable = false, columnDefinition = "text")
    private String publicationStatus;

    @Column(name = "adaptive_enabled", nullable = false)
    private boolean adaptiveEnabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "adaptive_settings", nullable = true, columnDefinition = "jsonb")
    private Map<String, Object> adaptiveSettings;

    protected CourseEntity() {}

    public CourseEntity(UUID id,
                        UUID instructorId,
                        String title,
                        String description,
                        String prerequisiteDescription,
                        String publicationStatus,
                        boolean adaptiveEnabled,
                        Map<String, Object> adaptiveSettings) {
        this.id = id;
        this.instructorId = instructorId;
        this.title = title;
        this.description = description;
        this.prerequisiteDescription = prerequisiteDescription;
        this.publicationStatus = publicationStatus;
        this.adaptiveEnabled = adaptiveEnabled;
        this.adaptiveSettings = adaptiveSettings;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInstructorId() {
        return instructorId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getPrerequisiteDescription() {
        return prerequisiteDescription;
    }

    public String getPublicationStatus() {
        return publicationStatus;
    }

    public boolean isAdaptiveEnabled() {
        return adaptiveEnabled;
    }

    public Map<String, Object> getAdaptiveSettings() {
        return adaptiveSettings;
    }
}