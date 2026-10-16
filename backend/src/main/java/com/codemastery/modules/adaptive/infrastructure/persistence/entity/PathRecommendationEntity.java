package com.codemastery.modules.adaptive.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "path_recommendations")
public class PathRecommendationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "recommendation_type", nullable = false)
    private String recommendationType;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "recommended_at", nullable = false)
    private Instant recommendedAt;

    protected PathRecommendationEntity() {
        // Required by JPA.
    }

    public PathRecommendationEntity(
            UUID id,
            UUID enrollmentId,
            UUID lessonId,
            String recommendationType,
            String reason,
            Instant recommendedAt) {
        this.id = id;
        this.enrollmentId = enrollmentId;
        this.lessonId = lessonId;
        this.recommendationType = recommendationType;
        this.reason = reason;
        this.recommendedAt = recommendedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEnrollmentId() {
        return enrollmentId;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public String getRecommendationType() {
        return recommendationType;
    }

    public String getReason() {
        return reason;
    }

    public Instant getRecommendedAt() {
        return recommendedAt;
    }
}