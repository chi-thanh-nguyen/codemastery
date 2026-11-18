package com.codemastery.modules.course.api.dto;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.course.application.service.LessonProgressService;
import org.springframework.http.HttpStatus;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** CT22.progress/resume REST representations (OpenAPI CourseProgress, CourseResume, LearnerResume). */
public final class CourseProgressResponse {
    private static final JsonMapper STRICT_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private CourseProgressResponse() {}

    public record LessonProgressEntry(UUID lessonId, boolean completed, boolean masterySkipped) {}

    public record CourseProgress(UUID courseId, String title, String membershipStatus, long completedLessons,
            long totalLessons, int progressPercentage, boolean isComplete, UUID nextLessonId,
            List<LessonProgressEntry> lessons) {
        public static CourseProgress from(LessonProgressService.CourseProgress result) {
            return new CourseProgress(result.courseId(), result.title(), result.membershipStatus(), result.completedLessons(),
                    result.totalLessons(), result.progressPercentage(), result.isComplete(), result.nextLessonId(),
                    result.lessons().stream().map(lesson ->
                            new LessonProgressEntry(lesson.lessonId(), lesson.completed(), lesson.masterySkipped())).toList());
        }
    }

    public record CourseProgressSummary(UUID courseId, String title, String membershipStatus, long completedLessons,
            long totalLessons, int progressPercentage, boolean isComplete, UUID nextLessonId) {}

    public record CourseProgressPage(List<CourseProgressSummary> content, int page, int size, long totalElements, int totalPages) {
        public static CourseProgressPage from(LessonProgressService.CourseProgressPage result) {
            return new CourseProgressPage(result.content().stream().map(row -> new CourseProgressSummary(row.courseId(),
                    row.title(), row.membershipStatus(), row.completedLessons(), row.totalLessons(), row.progressPercentage(),
                    row.isComplete(), row.nextLessonId())).toList(),
                    result.page(), result.size(), result.totalElements(), result.totalPages());
        }
    }

    public record CourseResume(UUID courseId, UUID resumeLessonId, UUID nextLessonId) {
        public static CourseResume from(LessonProgressService.CourseResume result) {
            return new CourseResume(result.courseId(), result.resumeLessonId(), result.nextLessonId());
        }
    }

    public record LearnerResumePosition(UUID courseId, UUID lessonId, Instant lastVisitedAt) {}

    public record LearnerResume(LearnerResumePosition position) {
        public static LearnerResume from(LessonProgressService.LearnerResume result) {
            var position = result.position();
            return new LearnerResume(position == null ? null
                    : new LearnerResumePosition(position.courseId(), position.lessonId(), position.lastVisitedAt()));
        }
    }

    /**
     * Strict LessonCompletionWrite: exactly {@code {"completed":true}}. False when malformed,
     * unknown, duplicate, non-boolean or false, so the controller can apply CT03 admission
     * precedence before the 400.
     */
    public static boolean isCompletionWrite(String json) {
        return object(json).filter(input -> input.size() == 1 && input.has("completed"))
                .map(input -> input.get("completed")).filter(JsonNode::isBoolean).map(JsonNode::booleanValue).orElse(false);
    }

    /** Strict CourseVisitWrite: exactly {@code {"lessonId":"<canonical UUID>"}}; empty otherwise. */
    public static Optional<UUID> visitLessonId(String json) {
        return object(json).filter(input -> input.size() == 1 && input.has("lessonId"))
                .map(input -> input.get("lessonId")).filter(JsonNode::isString).flatMap(value -> {
                    try { return Optional.of(CourseSearchRequest.parseCourseId(value.asString())); }
                    catch (BusinessRuleException invalid) { return Optional.empty(); }
                });
    }

    public static BusinessRuleException invalidBody() {
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid progress request body");
    }

    private static Optional<JsonNode> object(String json) {
        if (json == null || json.isBlank()) return Optional.empty();
        try {
            JsonNode input = STRICT_JSON.readTree(json);
            return input != null && input.isObject() ? Optional.of(input) : Optional.empty();
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }
}
