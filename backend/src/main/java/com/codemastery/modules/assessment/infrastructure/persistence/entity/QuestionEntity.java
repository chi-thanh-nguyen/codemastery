package com.codemastery.modules.assessment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "questions")
public class QuestionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "skill_id", nullable = false)
    private UUID skill_id;

    @Column(name = "prompt", nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "difficulty", nullable = false)
    private String difficulty;

    @Column(name = "options", columnDefinition = "text[]", nullable = false)
    private String[] options;

    @Column(name = "correct_options", columnDefinition = "boolean[]", nullable = false)
    private Boolean[] correct_options;

    public QuestionEntity() {
    }

    public QuestionEntity(
            UUID id,
            UUID skill_id,
            String prompt,
            String kind,
            String difficulty,
            String[] options,
            Boolean[] correct_options
    ) {
        this.id = id;
        this.skill_id = skill_id;
        this.prompt = prompt;
        this.kind = kind;
        this.difficulty = difficulty;
        this.options = options;
        this.correct_options = correct_options;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSkillId() {
        return skill_id;
    }

    public void setSkillId(UUID skill_id) {
        this.skill_id = skill_id;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String[] getOptions() {
        return options;
    }

    public void setOptions(String[] options) {
        this.options = options;
    }

    public Boolean[] getCorrectOptions() {
        return correct_options;
    }

    public void setCorrectOptions(Boolean[] correct_options) {
        this.correct_options = correct_options;
    }
}