package com.codemastery.modules.assessment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "submissions")
public class SubmissionEntity{

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignment_id;

    @Column(name = "user_id", nullable = false)
    private UUID user_id;

    @Column(name = "object_key", nullable = false)
    private String object_key;

    @Column(name = "original_filename", nullable = false)
    private String original_filename;

    @Column(name = "submitted_at", nullable = false)
    private Instant submitted_at;

    @Column(name = "grade_percentage", precision = 5, scale = 2)
    private BigDecimal grade_percentage;

    @Column(name = "feedback")
    private String feedback;

    @Column(name = "graded_at")
    private Instant graded_at;

    public SubmissionEntity(){
    }

    public SubmissionEntity(
        UUID id,
        UUID assignment_id,
        UUID user_id,
        String object_key,
        String original_filename,
        Instant submitted_at,
        BigDecimal grade_percentage,
        String feedback,
        Instant graded_at
    ) {
        this.id = id;
        this.assignment_id = assignment_id;
        this.user_id = user_id;
        this.object_key = object_key;
        this.original_filename = original_filename;
        this.submitted_at = submitted_at;
        this.grade_percentage = grade_percentage;
        this.feedback = feedback;
        this.graded_at = graded_at;
    }

    public UUID getId(){
        return id;
    }

    public void setId(UUID id){
        this.id = id;
    }

    public UUID getAssignmentId(){
        return assignment_id;
    }

    public void setAssignmentId(UUID assignment_id){
        this.assignment_id = assignment_id;
    }

    public UUID getUserId(){
        return user_id;
    }

    public void setUserId(UUID user_id){
        this.user_id = user_id;
    }

    public String getObjectKey(){
        return object_key;
    }

    public void setObjectKey(String object_key){
        this.object_key = object_key;
    }

    public String getOriginalFilename(){
        return original_filename;
    }

    public void setOriginalFilename(String original_filename){
        this.original_filename = original_filename;
    }

    public Instant getSubmittedAt(){
        return submitted_at;
    }

    public void setSubmittedAt(Instant submitted_at){
        this.submitted_at = submitted_at;
    }

    public BigDecimal getGradePercentage(){
        return grade_percentage;
    }

    public void setGradePercentage(BigDecimal grade_percentage){
        this.grade_percentage = grade_percentage;
    }

    public String getFeedback(){
        return feedback;
    }

    public void setFeedback(String feedback){
        this.feedback = feedback;
    }

    public Instant getGradedAt(){
        return graded_at;
    }

    public void setGradedAt(Instant graded_at){
        this.graded_at = graded_at;
    }
}