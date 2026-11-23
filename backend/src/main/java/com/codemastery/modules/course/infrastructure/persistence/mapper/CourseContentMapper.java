package com.codemastery.modules.course.infrastructure.persistence.mapper;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.MaterialType;
import com.codemastery.modules.course.infrastructure.persistence.entity.ChapterEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LearningMaterialEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonEntity;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;


@Component
public class CourseContentMapper {

    public ChapterEntity toEntity(Chapter domain) {
        if (domain == null) {
            return null;
        }
        return new ChapterEntity(domain.id(), domain.courseId(), domain.title(), domain.position());
    }

    public Chapter toDomain(ChapterEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Chapter(entity.getId(), entity.getCourseId(), entity.getTitle(), entity.getPosition());
    }

    public LessonEntity toEntity(Lesson domain) {
        if (domain == null) {
            return null;
        }
        return new LessonEntity(
                domain.id(),
                domain.chapterId(),
                domain.title(),
                domain.contentMarkdown(),
                domain.position(),
                CourseMapper.toCode(domain.publicationStatus()));
    }

    public Lesson toDomain(LessonEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Lesson(
                entity.getId(),
                entity.getChapterId(),
                entity.getTitle(),
                entity.getContentMarkdown(),
                entity.getPosition(),
                CourseMapper.toPublishStatus(entity.getPublicationStatus()));
    }

    public LearningMaterialEntity toEntity(LearningMaterial domain) {
        if (domain == null) {
            return null;
        }
        return new LearningMaterialEntity(
                domain.id(),
                domain.lessonId(),
                domain.title(),
                domain.materialType() == null ? null : domain.materialType().getType(),
                CourseMapper.toCode(domain.publicationStatus()),
                domain.textContent(),
                domain.objectKey(),
                domain.originalFileName(),
                domain.externalVideoUrl());
    }

    public LearningMaterial toDomain(LearningMaterialEntity entity) {
        if (entity == null) {
            return null;
        }
        return new LearningMaterial(
                entity.getId(),
                entity.getLesson_id(),
                entity.getTitle(),
                toMaterialType(entity.getMaterialType()),
                CourseMapper.toPublishStatus(entity.getPublicationStatus()),
                entity.getTextContent(),
                entity.getObjectKey(),
                entity.getOriginalFileName(),
                entity.getExternalVideoUrl());
    }

    /** Maps one public.chapters row (id, course_id, title, position) read by private JDBC persistence. */
    public Chapter chapterFromRow(ResultSet row) throws SQLException {
        return new Chapter(row.getObject("id", UUID.class), row.getObject("course_id", UUID.class),
                row.getString("title"), row.getInt("position"));
    }

    /** Maps one public.lessons row (id, chapter_id, title, content_markdown, position, publication_status). */
    public Lesson lessonFromRow(ResultSet row) throws SQLException {
        return new Lesson(row.getObject("id", UUID.class), row.getObject("chapter_id", UUID.class),
                row.getString("title"), row.getString("content_markdown"), row.getInt("position"),
                CourseMapper.publicationFromCode(row.getString("publication_status")));
    }

    /** Maps one public.learning_materials row; the exclusive payload columns are preserved verbatim. */
    public LearningMaterial materialFromRow(ResultSet row) throws SQLException {
        return new LearningMaterial(row.getObject("id", UUID.class), row.getObject("lesson_id", UUID.class),
                row.getString("title"), toMaterialType(row.getString("material_type")),
                CourseMapper.publicationFromCode(row.getString("publication_status")),
                row.getString("text_content"), row.getString("object_key"),
                row.getString("original_filename"), row.getString("external_video_url"));
    }

    // Accepts only the exact V002 codes ('text', 'file', 'external_video'). */
    private static MaterialType toMaterialType(String code) {
        if (code == null) {
            return null;
        }
        for (MaterialType type : MaterialType.values()) {
            if (type.getType().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown material type code");
    }
}
