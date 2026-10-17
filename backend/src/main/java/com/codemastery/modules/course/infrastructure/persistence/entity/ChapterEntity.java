package com.codemastery.modules.course.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;


@Entity
@Table(name = "chapters", schema = "public")
public class ChapterEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;
    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;
    @Column(name = "position", nullable = false)
    private int position;

    protected ChapterEntity() {}

    public ChapterEntity(UUID id,
                         UUID courseId,
                         String title,
                         int position) {
        this.id = id;
        this.courseId = courseId;
        this.title = title;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getTitle() {
        return title;
    }

    public int getPosition() {
        return position;
    }
}
