package com.codemastery.modules.assessment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestionEntity{

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "quiz_id", nullable = false)
    private UUID quiz_id;

    @Column(name = "question_id", nullable = false)
    private UUID question_id;

    @Column(name = "position", nullable = false)
    private Integer position;

    public QuizQuestionEntity(){
    }

    public QuizQuestionEntity(
        UUID id,
        UUID quiz_id,
        UUID question_id,
        Integer position
    ) {
        this.id = id;
        this.quiz_id = quiz_id;
        this.question_id = question_id;
        this.position = position;
    }

    public UUID getId(){
        return id;
    }

    public void setId(UUID id){
        this.id = id;
    }

    public UUID getQuizId(){
        return quiz_id;
    }

    public void setQuizId(UUID quiz_id){
        this.quiz_id = quiz_id;
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
}