package com.codemastery.modules.assessment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "attempts")
public class AttemptEntity{

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID user_id;

    @Column(name = "quiz_id", nullable = false)
    private UUID quiz_id;

    @Column(name = "attempt_number", nullable = false)
    private Integer attempt_number;

    @Column(name = "started_at", nullable = false)
    private Instant started_at;

    @Column(name = "expires_at", nullable = false)
    private Instant expires_at;

    @Column(name = "submitted_at")
    private Instant submitted_at;

    @Column(name = "score_percentage", precision = 5, scale = 2)
    private BigDecimal score_percentage;

    public AttemptEntity(){
    }

    public AttemptEntity(
        UUID id,
        UUID quiz_id,
        UUID user_id,
        Integer attempt_number,
        Instant started_at,
        Instant expires_at,
        Instant submitted_at,
        BigDecimal score_percentage
    ) {
        this.id = id;
        this.user_id = user_id;
        this.quiz_id = quiz_id;
        this.attempt_number = attempt_number;
        this.started_at = started_at;
        this.expires_at = expires_at;
        this.submitted_at = submitted_at;
        this.score_percentage = score_percentage;
    }

    public UUID getId(){
        return id;
    }

    public void setId(UUID id){
        this.id = id;
    }

    public UUID getUserId() {
        return user_id;
    }

    public void setUserId(UUID user_id) {
        this.user_id = user_id;
    }

    public UUID getQuizId(){
        return quiz_id;
    }

    public void setQuizId(UUID quiz_id){
        this.quiz_id = quiz_id;
    }

    public Integer getAttemptNumber(){
        return attempt_number;
    } 

    public void setAttemptNumber(Integer attempt_number){
        this.attempt_number = attempt_number;
    }

    public Instant getStartedAt(){
        return started_at;
    }

    public void setStartedAt(Instant started_at){
        this.started_at = started_at;
    }

    public Instant getExpiresAt(){
        return expires_at;
    }

    public void setExpiresAt(Instant expires_at){
        this.expires_at = expires_at;
    }

    public Instant getSubmittedAt(){
        return submitted_at;
    }

    public void setSubmittedAt(Instant submitted_at){
        this.submitted_at = submitted_at;
    }

    public BigDecimal getScorePercentage(){
        return score_percentage;
    }

    public void setScorePercentage(BigDecimal score_percentage){
        this.score_percentage = score_percentage;
    }
}