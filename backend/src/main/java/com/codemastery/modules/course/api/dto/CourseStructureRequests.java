package com.codemastery.modules.course.api.dto;

import java.util.List;
import java.util.UUID;

public final class CourseStructureRequests {
    private CourseStructureRequests() {}

    public record ChapterWrite(String title) {}
    public record LessonWrite(String title, String contentMarkdown) {}
    public record SiblingOrder(List<UUID> orderedIds) {
        public SiblingOrder { orderedIds = orderedIds == null ? null : List.copyOf(orderedIds); }
    }
}
