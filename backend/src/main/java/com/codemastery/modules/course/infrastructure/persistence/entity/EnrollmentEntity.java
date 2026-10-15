package com.codemastery.modules.course.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "enrollments", schema = "public")

public class EnrollmentEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "resume_lesson_id", nullable = true)
    private UUID resumeLessonId;

    protected EnrollmentEntity() {}

    public EnrollmentEntity(UUID id, UUID userId, UUID courseId, String status, UUID resumeLessonId) {
        this.id = id;
        this.userId = userId;
        this.courseId = courseId;
        this.status = status;
        this.resumeLessonId = resumeLessonId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getStatus() {
        return status;
    }

    public UUID getResumeLessonId() {
        return resumeLessonId;
    }
}