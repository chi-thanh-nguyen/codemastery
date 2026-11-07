package com.codemastery.modules.assessment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "quizzes")
public class QuizEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID course_id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "time_limit_seconds", nullable = false)
    private Integer time_limit_seconds;

    @Column(name = "publication_status", nullable = false)
    private String publication_status;

    public QuizEntity() {
    }

    public QuizEntity(
            UUID id,
            UUID course_id,
            String title,
            String kind,
            Integer time_limit_seconds,
            String publication_status
    ) {
        this.id = id;
        this.course_id = course_id;
        this.title = title;
        this.kind = kind;
        this.time_limit_seconds = time_limit_seconds;
        this.publication_status = publication_status;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCourseId() {
        return course_id;
    }

    public void setCourseId(UUID courseId) {
        this.course_id = course_id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Integer getTimeLimitSeconds() {
        return time_limit_seconds;
    }

    public void setTimeLimitSeconds(Integer time_limit_seconds) {
        this.time_limit_seconds = time_limit_seconds;
    }

    public String getPublicationStatus() {
        return publication_status;
    }

    public void setPublicationStatus(String publication_status) {
        this.publication_status = publication_status;
    }
}