package com.codemastery.modules.adaptive.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "lesson_skills")
public class LessonSkillEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    protected LessonSkillEntity() {

    }

    public LessonSkillEntity(
            UUID id,
            UUID lessonId,
            UUID skillId) {
        this.id = id;
        this.lessonId = lessonId;
        this.skillId = skillId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public UUID getSkillId() {
        return skillId;
    }

}