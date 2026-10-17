package com.codemastery.modules.course.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "lessons", schema = "public")

public class LessonEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;

    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "content_markdown", nullable = true, columnDefinition = "text")
    private String contentMarkdown;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "publication_status", nullable = false, columnDefinition = "text")
    private String publicationStatus;

    protected LessonEntity() {}

    public LessonEntity(UUID id,
                        UUID chapterId,
                        String title,
                        String contentMarkdown,
                        int position,
                        String publicationStatus) {
        this.id = id;
        this.chapterId = chapterId;
        this.title = title;
        this.contentMarkdown = contentMarkdown;
        this.position = position;
        this.publicationStatus = publicationStatus;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChapterId() {
        return chapterId;
    }

    public String getTitle() {
        return title;
    }

    public String getContentMarkdown() {
        return contentMarkdown;
    }

    public int getPosition() {
        return position;
    }

    public String getPublicationStatus() {
        return publicationStatus;
    }
}