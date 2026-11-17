package com.codemastery.modules.course.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

public final class CourseStructureResponses {
    private CourseStructureResponses() {}

    public record OwnedCourseSummary(UUID id, String title, String description, String publicationStatus) {}

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
                              int position, String publicationStatus, List<Object> materials) {
        public OwnedLesson { materials = List.copyOf(materials); }
    }

    public record LearnerLesson(UUID id, UUID courseId, UUID chapterId, String title,
                                @JsonInclude(JsonInclude.Include.ALWAYS) String contentMarkdown,
                                List<Object> materials) {
        public LearnerLesson { materials = List.copyOf(materials); }
    }
}
