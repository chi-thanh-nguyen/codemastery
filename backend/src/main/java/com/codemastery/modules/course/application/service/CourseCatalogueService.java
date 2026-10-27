package com.codemastery.modules.course.application.service;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.course.api.dto.CourseSummaryResponse;
import com.codemastery.modules.course.api.dto.CourseSummaryResponse.CoursePage;
import com.codemastery.modules.course.api.dto.CourseDetailResponse;
import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.domain.repository.CourseContentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CourseCatalogueService {
    private final CourseRepository courses;
    private final CourseContentRepository content;
    private final UserAccountService identities;

    public CourseCatalogueService(CourseRepository courses, CourseContentRepository content, UserAccountService identities) {
        this.courses = courses; this.content = content; this.identities = identities;
    }

    public CoursePage list(int page, int size, String query, String prerequisites) {
        var result = courses.searchPublishedCourses(PageRequest.of(page, size), query, prerequisites);
        var names = identities.instructorDisplayNames(result.stream().map(Course::instructorId).collect(Collectors.toSet()));
        return new CoursePage(result.stream().map(course -> summary(course, names)).toList(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    public CourseDetailResponse detail(UUID id) {
        var course = courses.findPublishedById(id).orElseThrow(() -> new ResourceNotFoundException("Resource not available"));
        var names = identities.instructorDisplayNames(Set.of(course.instructorId()));
        var lessons = content.findPublishedLessonsByCourseId(id);
        var chapters = content.findChaptersByCourseId(id).stream()
                .filter(chapter -> lessons.stream().anyMatch(lesson -> lesson.chapterId().equals(chapter.id())))
                .map(chapter -> new CourseDetailResponse.Chapter(
                chapter.id(), chapter.title(), chapter.position(), lessons.stream()
                .filter(lesson -> lesson.chapterId().equals(chapter.id()))
                .map(lesson -> new CourseDetailResponse.Lesson(lesson.id(), lesson.title(), lesson.position())).toList())).toList();
        return new CourseDetailResponse(course.id(), course.title(), course.description(), course.instructorId(),
                names.get(course.instructorId()), course.prerequisiteDescription(), chapters);
    }

    private static CourseSummaryResponse summary(Course course, Map<UUID, String> names) {
        return new CourseSummaryResponse(course.id(), course.title(), course.description(), course.instructorId(), names.get(course.instructorId()));
    }
}
