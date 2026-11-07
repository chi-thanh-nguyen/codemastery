package com.codemastery.modules.assessment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "assignments")
public class AssignmentEntity{

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID course_id;

    @Column(name = "lesson_id", nullable = false)
    private UUID lesson_id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "instructions", nullable = false)
    private String instructions;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadline_at;

    @Column(name = "publication_status", nullable = false)
    private String publication_status;

    public AssignmentEntity(){
    }

    public AssignmentEntity(
        UUID id,
        UUID course_id,
        UUID lesson_id,
        String title,
        String instructions,
        Instant deadline_at,
        String publication_status
    ) {
        this.id = id;
        this.course_id = course_id;
        this.lesson_id = lesson_id;
        this.title = title;
        this.instructions = instructions;
        this.deadline_at = deadline_at;
        this.publication_status = publication_status;
    }

    public UUID getId(){
        return id;
    }

    public void setId(UUID id){
        this.id = id;
    }

    public UUID getCourseId(){
        return course_id;
    }

    public void setCourseId(UUID course_id){
        this.course_id = course_id;
    }

    public UUID getLessonId(){
        return lesson_id;
    }

    public void setLessonId(UUID lesson_id){
        this.lesson_id = lesson_id;
    }

    public String getTitle(){
        return title;
    }

    public void setTitle(String title){
        this.title = title;
    }

    public String getInstructions(){
        return instructions;
    }

    public void setInstructions(String instructions){
        this.instructions = instructions;
    }

    public Instant getDeadlineAt(){
        return deadline_at;
    }

    public void setDeadlineAt(Instant deadline_at){
        this.deadline_at = deadline_at;
    }

    public String getPublicationStatus(){
        return publication_status;
    }

    public void setPublicationStatus(String publication_status){
        this.publication_status = publication_status;
    }
}