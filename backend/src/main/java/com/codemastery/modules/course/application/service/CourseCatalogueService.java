gipackage com.codemastery.modules.course.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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
        this(
                courseRepository,
                courseContentRepository,
                new CourseAccessService(courseRepository, courseContentRepository)
        );
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

    /**
     * Lists published courses using the approved pagination contract.
     */
    public PageResponse<CourseSummaryResponse> listPublishedCourses(CourseSearchRequest request) {
        CourseSearchRequest effectiveRequest = request != null ? request : CourseSearchRequest.defaults();
        Pageable pageable = effectiveRequest.toValidatedPageable();

        Page<Course> publishedPage = courseRepository.findPublishedCourses(pageable);
        List<CourseSummaryResponse> summaries = publishedPage.getContent().stream()
                .filter(course -> course.publicationStatus() == PublishStatus.PUBLISHED)
                .map(CourseSummaryResponse::from)
                .toList();

        return PageResponse.of(
                summaries,
                effectiveRequest.effectivePage(),
                effectiveRequest.effectiveSize(),
                publishedPage.getTotalElements()
        );
    }

    /**
     * Convenience overload for listing published courses by raw page and size.
     */
    public PageResponse<CourseSummaryResponse> listPublishedCourses(Integer page, Integer size) {
        return listPublishedCourses(CourseSearchRequest.of(page, size));
    }

    public CourseDetailResponse getPublishedCourseDetail(UUID courseId) {
        if (courseId == null) {
            throw new BusinessRuleException("courseId must not be null");
        }

        Course course = courseAccessService.requirePublishedCourse(courseId);

        List<Chapter> chapters = courseContentRepository.findChaptersByCourseId(course.id());
        List<Lesson> publishedLessons = courseContentRepository.findPublishedLessonsByCourseId(course.id());

        Map<UUID, List<Lesson>> publishedLessonsByChapter = new HashMap<>();
        for (Lesson lesson : publishedLessons) {
            if (lesson != null && lesson.publicationStatus() == PublishStatus.PUBLISHED) {
                publishedLessonsByChapter
                        .computeIfAbsent(lesson.chapterId(), ignored -> new ArrayList<>())
                        .add(lesson);
            }
        }

        List<SyllabusChapter> syllabus = chapters.stream()
                .filter(Objects::nonNull)
                .sorted(CHAPTER_ORDER)
                .map(chapter -> {
                    List<SyllabusLesson> chapterLessons = publishedLessonsByChapter
                            .getOrDefault(chapter.id(), List.of())
                            .stream()
                            .sorted(LESSON_ORDER)
                            .map(SyllabusLesson::from)
                            .toList();
                    return new SyllabusChapter(
                            chapter.id(),
                            chapter.title(),
                            chapter.position(),
                            chapterLessons
                    );
                })
                .filter(chapter -> !chapter.lessons().isEmpty())
                .toList();

        return new CourseDetailResponse(
                course.id(),
                course.title(),
                course.description(),
                course.instructorId(),
                course.prerequisiteDescription(),
                syllabus
        );
    }

    /**
     * Alias matching OpenAPI operationId: getPublishedCourse.
     */
    public CourseDetailResponse getPublishedCourse(UUID courseId) {
        return getPublishedCourseDetail(courseId);
    }
}
