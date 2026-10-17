package com.codemastery.modules.adaptive.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "learner_skill_masteries")
public class LearnerSkillMasteryEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    @Column(name = "state", nullable = false)
    private String state;

    protected LearnerSkillMasteryEntity() {

    }

    public LearnerSkillMasteryEntity(
            UUID id,
            UUID userId,
            UUID skillId,
            String state) {
        this.id = id;
        this.userId = userId;
        this.skillId = skillId;
        this.state = state;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public String getState() {
        return state;
    }

}