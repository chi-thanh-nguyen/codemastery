package com.codemastery.modules.course.infrastructure.persistence.mapper;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.infrastructure.persistence.entity.ChapterEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonEntity;
import org.springframework.stereotype.Component;

@Component
public class CourseContentMapper {

    public ChapterEntity toEntity(Chapter domain) {
        if (domain == null) {
            return null;
        }
        return new ChapterEntity(
                domain.id(),
                domain.courseId(),
                domain.title(),
                domain.position()
        );
    }

    public Chapter toDomain(ChapterEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Chapter(
                entity.getId(),
                entity.getCourseId(),
                entity.getTitle(),
                entity.getPosition()
        );
    }

    public LessonEntity toEntity(Lesson domain) {
        if (domain == null) {
            return null;
        }
        String publicationStatus = domain.publicationStatus() != null
                ? domain.publicationStatus().getStatus()
                : null;
        return new LessonEntity(
                domain.id(),
                domain.chapterId(),
                domain.title(),
                domain.contentMarkdown(),
                domain.position(),
                publicationStatus
        );
    }

    public Lesson toDomain(LessonEntity entity) {
        if (entity == null) {
            return null;
        }
        PublishStatus publicationStatus = toPublishStatus(entity.getPublicationStatus());
        return new Lesson(
                entity.getId(),
                entity.getChapterId(),
                entity.getTitle(),
                entity.getContentMarkdown(),
                entity.getPosition(),
                publicationStatus
        );
    }

    private PublishStatus toPublishStatus(String status) {
        if (status == null) {
            return null;
        }
        for (PublishStatus ps : PublishStatus.values()) {
            if (ps.getStatus().equalsIgnoreCase(status) || ps.name().equalsIgnoreCase(status)) {
                return ps;
            }
        }
        throw new IllegalArgumentException("Unknown publication status: " + status);
    }
}
