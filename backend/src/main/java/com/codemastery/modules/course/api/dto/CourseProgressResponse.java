package com.codemastery.modules.course.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CourseProgressResponse(UUID courseId, String title, String membershipStatus,
                                     int completedLessons, int totalLessons, int progressPercentage,
                                     @JsonProperty("isComplete") boolean isComplete,
                                     @JsonInclude(JsonInclude.Include.ALWAYS) UUID nextLessonId,
                                     List<LessonProgressEntry> lessons) {
    public CourseProgressResponse { lessons = List.copyOf(lessons); }

    public record LessonProgressEntry(UUID lessonId, boolean completed, boolean masterySkipped) {}

    public record Summary(UUID courseId, String title, String membershipStatus,
                          int completedLessons, int totalLessons, int progressPercentage,
                          @JsonProperty("isComplete") boolean isComplete,
                          @JsonInclude(JsonInclude.Include.ALWAYS) UUID nextLessonId) {}

    public record Resume(UUID courseId,
                         @JsonInclude(JsonInclude.Include.ALWAYS) UUID resumeLessonId,
                         @JsonInclude(JsonInclude.Include.ALWAYS) UUID nextLessonId) {}

    public record LearnerResume(@JsonInclude(JsonInclude.Include.ALWAYS) Position position) {}

    public record Position(UUID courseId, UUID lessonId, Instant lastVisitedAt) {}

    public record LessonCompletionWrite(Boolean completed) {}

    public record CourseVisitWrite(UUID lessonId) {}
}
