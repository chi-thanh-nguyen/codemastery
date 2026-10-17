package com.codemastery.modules.course.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "lesson_progress", schema = "public")

public class LessonProgressEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "mastery_skipped", nullable = false)
    private boolean masterySkipped;

    protected LessonProgressEntity() {}

    public LessonProgressEntity(UUID id,
                                UUID enrollmentId,
                                UUID lessonId,
                                boolean completed,
                                boolean masterySkipped) {
        this.id = id;
        this.enrollmentId = enrollmentId;
        this.lessonId = lessonId;
        this.completed = completed;
        this.masterySkipped = masterySkipped;
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

    public boolean isCompleted() {
        return completed;
    }

    public boolean isMasterySkipped() {
        return masterySkipped;
    }
}