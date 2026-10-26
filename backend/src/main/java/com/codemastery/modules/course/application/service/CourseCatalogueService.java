package com.codemastery.modules.course.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.course.api.dto.CourseDetailResponse;
import com.codemastery.modules.course.api.dto.CourseDetailResponse.SyllabusChapter;
import com.codemastery.modules.course.api.dto.CourseDetailResponse.SyllabusLesson;
import com.codemastery.modules.course.api.dto.CourseSearchRequest;
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
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional(readOnly = true)
public class CourseCatalogueService {

    private static final Comparator<Chapter> CHAPTER_ORDER =
            Comparator.comparingInt(Chapter::position)
                    .thenComparing(Chapter::id, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<Lesson> LESSON_ORDER =
            Comparator.comparingInt(Lesson::position)
                    .thenComparing(Lesson::id, Comparator.nullsLast(Comparator.naturalOrder()));

    private final CourseRepository courseRepository;
    private final CourseContentRepository courseContentRepository;
    private final CourseAccessService courseAccessService;

    public CourseCatalogueService(
            CourseRepository courseRepository,
            CourseContentRepository courseContentRepository) {
        this(courseRepository, courseContentRepository,
                new CourseAccessService(courseRepository, courseContentRepository));
    }

    @Autowired
    public CourseCatalogueService(
            CourseRepository courseRepository,
            CourseContentRepository courseContentRepository,
            CourseAccessService courseAccessService) {
        this.courseRepository = Objects.requireNonNull(courseRepository, "courseRepository must not be null");
        this.courseContentRepository = Objects.requireNonNull(
                courseContentRepository, "courseContentRepository must not be null");
        this.courseAccessService = Objects.requireNonNull(
                courseAccessService, "courseAccessService must not be null");
    }

    // =========================================================================
    //  Catalogue listing
    // =========================================================================

    public Page<CourseSummaryResponse> searchPublishedCourses(CourseSearchRequest request, Pageable pageable) {
        Pageable effectivePageable = resolvePageable(request, pageable);
        Page<Course> publishedPage = courseRepository.findPublishedCourses(effectivePageable);
        return publishedPage.map(course -> CourseSummaryResponse.from(requirePublishedRow(course)));
    }

    public PageResponse<CourseSummaryResponse> listPublishedCourses(CourseSearchRequest request) {
        return PageResponse.from(searchPublishedCourses(request, null));
    }

    /* Convenience overload using raw {@code page} and {@code size}. */
    public PageResponse<CourseSummaryResponse> listPublishedCourses(Integer page, Integer size) {
        return listPublishedCourses(CourseSearchRequest.of(page, size));
    }

    // =========================================================================
    //  Course detail
    // =========================================================================

    public CourseDetailResponse getPublishedCourseDetail(UUID courseId) {
        if (courseId == null) {
            throw new BusinessRuleException("courseId must not be null");
        }
        Course course = courseAccessService.requirePublishedCourse(courseId);
        return CourseDetailResponse.from(course, assemblePublicSyllabus(course.id()));
    }

    /** Alias matching OpenAPI {@code operationId: getPublishedCourse}. */
    public CourseDetailResponse getPublishedCourse(UUID courseId) {
        return getPublishedCourseDetail(courseId);
    }

    // =========================================================================
    //  Internal helpers
    // =========================================================================

    private static Pageable resolvePageable(CourseSearchRequest request, Pageable pageable) {
        CourseSearchRequest effectiveRequest = request != null ? request : CourseSearchRequest.defaults();
        if (pageable == null || pageable.isUnpaged()) {
            return effectiveRequest.toValidatedPageable();
        }
        if (pageable.getSort().isSorted()) {
            throw new BusinessRuleException("Sorting is not supported by the published catalogue");
        }
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        PageResponse.validateRequest(page, size);
        if ((effectiveRequest.page() != null && effectiveRequest.page() != page)
                || (effectiveRequest.size() != null && effectiveRequest.size() != size)) {
            throw new BusinessRuleException("Conflicting paging parameters");
        }
        return PageRequest.of(page, size);
    }

    /* Fail closed if a non-published row ever reaches the public mapping. */
    private static Course requirePublishedRow(Course course) {
        if (course == null || course.publicationStatus() != PublishStatus.PUBLISHED) {
            throw new IllegalStateException("Published catalogue query returned a non-published course");
        }
        return course;
    }

    private List<SyllabusChapter> assemblePublicSyllabus(UUID courseId) {
        List<Chapter> chapters = courseContentRepository.findChaptersByCourseId(courseId);
        List<Lesson> publishedLessons = courseContentRepository.findPublishedLessonsByCourseId(courseId);

        Map<UUID, List<Lesson>> lessonsByChapter = new LinkedHashMap<>();
        for (Lesson lesson : publishedLessons) {
            if (lesson != null && lesson.publicationStatus() == PublishStatus.PUBLISHED) {
                lessonsByChapter.computeIfAbsent(lesson.chapterId(), ignored -> new ArrayList<>()).add(lesson);
            }
        }

        return chapters.stream()
                .filter(Objects::nonNull)
                .sorted(CHAPTER_ORDER)
                .map(chapter -> SyllabusChapter.from(chapter,
                        lessonsByChapter.getOrDefault(chapter.id(), List.of()).stream()
                                .sorted(LESSON_ORDER)
                                .map(SyllabusLesson::from)
                                .toList()))
                .filter(chapter -> !chapter.lessons().isEmpty())
                .toList();
    }
}
