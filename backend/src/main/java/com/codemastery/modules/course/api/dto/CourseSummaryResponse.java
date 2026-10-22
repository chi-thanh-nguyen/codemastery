package com.codemastery.modules.course.api.dto;

import com.codemastery.modules.course.domain.model.Course;
import java.util.Objects;
import java.util.UUID;

/**
 * Public published-course summary item for GET /api/courses.
 */
public record CourseSummaryResponse(
        UUID id,
        String title,
        String description,
        UUID instructorId
) {

    public CourseSummaryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(instructorId, "instructorId must not be null");
    }

    public static CourseSummaryResponse from(Course course) {
        Objects.requireNonNull(course, "course must not be null");
        return new CourseSummaryResponse(
                course.id(),
                course.title(),
                course.description(),
                course.instructorId()
        );
    }
}
