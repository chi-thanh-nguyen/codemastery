package com.codemastery.modules.course.api.dto;

import java.util.List;
import java.util.UUID;

public record CourseDetailResponse(UUID id, String title, String description, UUID instructorId,
                                   String instructorDisplayName, String prerequisiteDescription,
                                   List<Chapter> syllabus) {
    public record Chapter(UUID id, String title, int position, List<Lesson> lessons) {}
    public record Lesson(UUID id, String title, int position) {}
}
