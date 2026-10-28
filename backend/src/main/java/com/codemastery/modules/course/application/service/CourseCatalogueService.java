package com.codemastery.modules.course.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.course.api.dto.CourseDetailResponse;
import com.codemastery.modules.course.api.dto.CourseSummaryResponse;
import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseContentRepository;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CourseCatalogueService {

    private static final Comparator<Chapter> CHAPTER_ORDER =
            Comparator.comparingInt(Chapter::position)
                    .thenComparing(Chapter::id, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<Lesson> LESSON_ORDER =
            Comparator.comparingInt(Lesson::position)
                    .thenComparing(Lesson::id, Comparator.nullsLast(Comparator.naturalOrder()));

    private final CourseRepository courseRepository;
    private final CourseContentRepository courseContentRepository;
    private final UserAccountService identities;

    public CourseCatalogueService(
            CourseRepository courseRepository,
            CourseContentRepository courseContentRepository,
            UserAccountService identities) {
        this.courseRepository = Objects.requireNonNull(courseRepository, "courseRepository must not be null");
        this.courseContentRepository = Objects.requireNonNull(
                courseContentRepository, "courseContentRepository must not be null");
        this.identities = Objects.requireNonNull(identities, "identities must not be null");
    }

    public CourseSummaryResponse.CoursePage list(int page, int size, String query, String prerequisites) {
        var result = courseRepository.searchPublishedCourses(PageRequest.of(page, size), query, prerequisites);
        var selected = result.getContent().stream().map(CourseCatalogueService::requirePublishedRow).toList();
        var names = identities.instructorDisplayNames(
                selected.stream().map(Course::instructorId).collect(Collectors.toSet()));
        var summaries = selected.stream().map(course -> new CourseSummaryResponse(
                course.id(), course.title(), course.description(), course.instructorId(),
                names.get(course.instructorId()))).toList();
        var response = new PageResponse<>(summaries, page, size, result.getTotalElements());
        return new CourseSummaryResponse.CoursePage(response.content(), response.page(), response.size(),
                response.totalElements(), response.totalPages());
    }

    public CourseDetailResponse detail(UUID courseId) {
        Course course = courseRepository.findPublishedById(courseId)
                .filter(row -> row.publicationStatus() == PublishStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not available"));
        var names = identities.instructorDisplayNames(Set.of(course.instructorId()));
        return new CourseDetailResponse(course.id(), course.title(), course.description(), course.instructorId(),
                names.get(course.instructorId()), course.prerequisiteDescription(), assemblePublicSyllabus(course.id()));
    }

    /** Fail closed if a non-published row ever reaches the public mapping. */
    private static Course requirePublishedRow(Course course) {
        if (course == null || course.publicationStatus() != PublishStatus.PUBLISHED) {
            throw new IllegalStateException("Published catalogue query returned a non-published course");
        }
        return course;
    }

    private List<CourseDetailResponse.Chapter> assemblePublicSyllabus(UUID courseId) {
        var chapters = courseContentRepository.findChaptersByCourseId(courseId);
        var publishedLessons = courseContentRepository.findPublishedLessonsByCourseId(courseId);

        Map<UUID, List<Lesson>> lessonsByChapter = new LinkedHashMap<>();
        for (var lesson : publishedLessons) {
            if (lesson != null && lesson.publicationStatus() == PublishStatus.PUBLISHED) {
                lessonsByChapter.computeIfAbsent(lesson.chapterId(), ignored -> new ArrayList<>()).add(lesson);
            }
        }

        return chapters.stream()
                .filter(Objects::nonNull)
                .filter(chapter -> courseId.equals(chapter.courseId()))
                .sorted(CHAPTER_ORDER)
                .map(chapter -> new CourseDetailResponse.Chapter(chapter.id(), chapter.title(), chapter.position(),
                        lessonsByChapter.getOrDefault(chapter.id(), List.of()).stream()
                                .sorted(LESSON_ORDER)
                                .map(lesson -> new CourseDetailResponse.Lesson(lesson.id(), lesson.title(), lesson.position()))
                                .toList()))
                .filter(chapter -> !chapter.lessons().isEmpty())
                .toList();
    }
}
