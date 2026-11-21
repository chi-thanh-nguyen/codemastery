package com.codemastery.modules.course.api.dto;

import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.MaterialType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/**
 * CT21.authoring responses (OpenAPI OwnedCourse*, OwnedChapter, OwnedLesson, LearnerLesson,
 * RelationalMaterial). Materials expose relational metadata only: never an object key,
 * original filename or delivery URL.
 */
public final class CourseStructureResponses {
    private CourseStructureResponses() {}

    public record OwnedCourseSummary(UUID id, String title, String description, String publicationStatus) {
        public static OwnedCourseSummary of(Course course) {
            return new OwnedCourseSummary(course.id(), course.title(), course.description(),
                    course.publicationStatus().getStatus());
        }
    }

    public record OwnedCourse(UUID id, String title, String description, String publicationStatus,
                              @JsonInclude(JsonInclude.Include.ALWAYS) String prerequisiteDescription,
                              List<OwnedChapter> chapters) {
        public OwnedCourse { chapters = List.copyOf(chapters); }
    }

    public record OwnedChapter(UUID id, String title, int position, List<OwnedLesson> lessons) {
        public OwnedChapter { lessons = List.copyOf(lessons); }
    }

    public record OwnedLesson(UUID id, UUID chapterId, String title,
                              @JsonInclude(JsonInclude.Include.ALWAYS) String contentMarkdown,
                              int position, String publicationStatus, List<Material> materials) {
        public OwnedLesson { materials = List.copyOf(materials); }

        public static OwnedLesson of(Lesson lesson, List<LearningMaterial> materials) {
            return new OwnedLesson(lesson.id(), lesson.chapterId(), lesson.title(), lesson.contentMarkdown(),
                    lesson.position(), lesson.publicationStatus().getStatus(), materials.stream().map(Material::of).toList());
        }
    }

    public record LearnerLesson(UUID id, UUID courseId, UUID chapterId, String title,
                                @JsonInclude(JsonInclude.Include.ALWAYS) String contentMarkdown,
                                List<Material> materials) {
        public LearnerLesson { materials = List.copyOf(materials); }
    }

    public record SiblingOrder(List<UUID> orderedIds) {
        public SiblingOrder { orderedIds = List.copyOf(orderedIds); }
    }

    /** One of TextMaterial, VideoMaterial or FileMaterialMetadata; the type-specific field is omitted otherwise. */
    public record Material(UUID id, String title, String publicationStatus, String materialType,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String textContent,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String externalVideoUrl) {
        public static Material of(LearningMaterial material) {
            MaterialType type = material.materialType();
            return new Material(material.id(), material.title(), material.publicationStatus().getStatus(), type.getType(),
                    type == MaterialType.TEXT ? material.textContent() : null,
                    type == MaterialType.EXTERNAL_VIDEO ? material.externalVideoUrl() : null);
        }
    }
}
