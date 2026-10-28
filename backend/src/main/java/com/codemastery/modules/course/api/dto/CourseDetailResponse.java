package com.codemastery.modules.course.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

public record CourseDetailResponse(UUID id, String title, String description, UUID instructorId,
                                   @JsonInclude(JsonInclude.Include.ALWAYS) String instructorDisplayName,
                                   @JsonInclude(JsonInclude.Include.ALWAYS) String prerequisiteDescription,
                                   List<Chapter> syllabus) {
    public CourseDetailResponse { syllabus = List.copyOf(syllabus); }

    public record Chapter(UUID id, String title, int position, List<Lesson> lessons) {
        public Chapter { lessons = List.copyOf(lessons); }
    }
    public record Lesson(UUID id, String title, int position) {}
}
