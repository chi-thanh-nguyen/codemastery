package com.codemastery.modules.course.api.dto;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.Lesson;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


public record CourseDetailResponse(
        UUID id,
        String title,
        String description,
        UUID instructorId,
        @JsonInclude(JsonInclude.Include.ALWAYS) String prerequisiteDescription,
        List<SyllabusChapter> syllabus
) {

    public CourseDetailResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(instructorId, "instructorId must not be null");
        syllabus = syllabus == null ? List.of() : List.copyOf(syllabus);
    }

    /* Builds the public detail from a published course and an already filtered, ordered syllabus. */
    public static CourseDetailResponse from(Course course, List<SyllabusChapter> syllabus) {
        Objects.requireNonNull(course, "course must not be null");
        return new CourseDetailResponse(
                course.id(),
                course.title(),
                course.description(),
                course.instructorId(),
                course.prerequisiteDescription(),
                syllabus
        );
    }

    /* Public syllabus chapter; only emitted when it contains at least one published lesson heading. */
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

        public static SyllabusChapter from(Chapter chapter, List<SyllabusLesson> lessons) {
            Objects.requireNonNull(chapter, "chapter must not be null");
            return new SyllabusChapter(chapter.id(), chapter.title(), chapter.position(), lessons);
        }
    }

    /* Public syllabus lesson heading (no markdown body or materials). */
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
