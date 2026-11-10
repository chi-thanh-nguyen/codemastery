package com.codemastery.modules.adaptive.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "skill_prerequisites")
public class SkillPrerequisiteEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    @Column(name = "prerequisite_skill_id", nullable = false)
    private UUID prerequisiteSkillId;

    protected SkillPrerequisiteEntity() {

    }

    public SkillPrerequisiteEntity(
            UUID id,
            UUID courseId,
            UUID skillId,
            UUID prerequisiteSkillId) {
        this.id = id;
        this.courseId = courseId;
        this.skillId = skillId;
        this.prerequisiteSkillId = prerequisiteSkillId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public UUID getPrerequisiteSkillId() {
        return prerequisiteSkillId;
    }
}