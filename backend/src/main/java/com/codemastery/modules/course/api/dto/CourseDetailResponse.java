package com.codemastery.modules.course.api.dto;

import com.codemastery.modules.course.domain.model.Lesson;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


public record CourseDetailResponse(
        UUID id,
        String title,
        String description,
        UUID instructorId,
        String prerequisiteDescription,
        List<SyllabusChapter> syllabus
) {

    public CourseDetailResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(instructorId, "instructorId must not be null");
        syllabus = syllabus == null ? List.of() : List.copyOf(syllabus);
    }

    /**
     * Public syllabus chapter entry containing at least one published lesson heading.
     */
    public record SyllabusChapter(
            UUID id,
            String title,
            int position,
            List<SyllabusLesson> lessons
    ) {
        public SyllabusChapter {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(title, "title must not be null");
            lessons = lessons == null ? List.of() : List.copyOf(lessons);
        }
    }

    /**
     * Public syllabus lesson heading (excluding lesson markdown body and materials).
     */
    public record SyllabusLesson(
            UUID id,
            String title,
            int position
    ) {
        public SyllabusLesson {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(title, "title must not be null");
        }

        public static SyllabusLesson from(Lesson lesson) {
            Objects.requireNonNull(lesson, "lesson must not be null");
            return new SyllabusLesson(lesson.id(), lesson.title(), lesson.position());
        }
    }
}
