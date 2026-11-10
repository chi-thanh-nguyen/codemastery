package com.codemastery.modules.adaptive.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "skills")
public class SkillEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    protected SkillEntity() {

    }

    public SkillEntity(
            UUID id,
            UUID courseId,
            String name,
            String description) {
        this.id = id;
        this.courseId = courseId;
        this.name = name;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

}