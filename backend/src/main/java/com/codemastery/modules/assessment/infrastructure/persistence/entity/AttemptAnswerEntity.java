package com.codemastery.modules.assessment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "attempt_answers")
public class AttemptAnswerEntity{

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attempt_id;

    @Column(name = "question_id", nullable = false)
    private UUID question_id;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "selected_options", columnDefinition = "boolean[]", nullable = false)
    private Boolean[] selected_options;

    @Column(name = "is_correct")
    private Boolean is_correct;

    public AttemptAnswerEntity(){
    }

    public AttemptAnswerEntity(
        UUID id,
        UUID attempt_id,
        UUID question_id,
        Integer position,
        Boolean[] selected_options,
        Boolean is_correct
    ) {
        this.id = id;
        this.attempt_id = attempt_id;
        this.question_id = question_id;
        this.position = position;
        this.selected_options = selected_options;
        this.is_correct = is_correct;
    }

    public UUID getId(){
        return id;
    }

    public void setId(UUID id){
        this.id = id;
    }

    public UUID getAttemptId(){
        return attempt_id;
    }

    public void setAttemptId(UUID attempt_id){
        this.attempt_id = attempt_id;
    }

    public UUID getQuestionId(){
        return question_id;
    }

    public void setQuestionId(UUID question_id){
        this.question_id = question_id;
    }

    public Integer getPosition(){
        return position;
    }

    public void setPosition(Integer position){
        this.position = position;
    }

    public Boolean[] getSelectedOptions(){
        return selected_options;
    }

    public void setSelectedOptions(Boolean[] selected_options){
        this.selected_options = selected_options;
    }

    public Boolean getIsCorrect(){
        return is_correct;
    }

    public void setIsCorrect(Boolean is_correct){
        this.is_correct = is_correct;
    }
}