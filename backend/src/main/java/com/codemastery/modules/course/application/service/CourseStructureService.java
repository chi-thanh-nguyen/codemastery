package com.codemastery.modules.course.application.service;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.LearnerLesson;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.Material;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedChapter;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedLesson;
import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseContentRepository;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.codemastery.modules.course.application.service.CourseManagementService.markdown;
import static com.codemastery.modules.course.application.service.CourseManagementService.requiredText;

/**
 * CT21.authoring Chapter and Lesson operations (ADR006 §5) and the Learner Lesson read.
 * Every mutation runs inside the ADR005 Course-root guard, which admits the owning Instructor
 * before and after the lock; ancestry and input are then checked against fresh state.
 * Lesson publication changes reconcile joined completion in the same unit (ADR006 §3).
 */
@Service
public class CourseStructureService {
    private final CourseManagementService management;
    private final CourseAccessService access;
    private final CourseContentRepository content;
    private final EnrollmentRepository enrollments;

    public CourseStructureService(CourseManagementService management, CourseAccessService access,
                                  CourseContentRepository content, EnrollmentRepository enrollments) {
        this.management = Objects.requireNonNull(management);
        this.access = Objects.requireNonNull(access);
        this.content = Objects.requireNonNull(content);
        this.enrollments = Objects.requireNonNull(enrollments);
    }

    /** CT03 owner admission plus Chapter ancestry, for callers that reject a body after admission. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void requireOwnedChapter(UUID courseId, UUID chapterId) {
        access.requireCourseOwnership(courseId);
        chapter(courseId, chapterId);
    }

    public OwnedChapter createCourseChapter(UUID courseId, String title) {
        return management.withCourseConfigurationWrite(courseId, () -> {
            Chapter chapter = content.appendChapter(courseId, UUID.randomUUID(), requiredText(title))
                    .orElseThrow(CourseStructureService::positionsExhausted);
            return new OwnedChapter(chapter.id(), chapter.title(), chapter.position(), List.of());
        });
    }

    public OwnedChapter updateCourseChapter(UUID courseId, UUID chapterId, String title) {
        return management.withCourseConfigurationWrite(courseId, () -> {
            chapter(courseId, chapterId);
            content.updateChapterTitle(courseId, chapterId, requiredText(title));
            return ownedChapter(courseId, chapterId);
        });
    }

    public List<UUID> orderCourseChapters(UUID courseId, List<UUID> orderedIds) {
        return management.withCourseConfigurationWrite(courseId, () -> {
            requirePermutation(orderedIds, content.findChaptersByCourseId(courseId).stream().map(Chapter::id).toList());
            content.reorderChapters(courseId, orderedIds);
            return orderedIds;
        });
    }

    /** A new Lesson is hidden and appended after its siblings. */
    public OwnedLesson createCourseLesson(UUID courseId, UUID chapterId, String title, String contentMarkdown) {
        return management.withCourseConfigurationWrite(courseId, () -> {
            chapter(courseId, chapterId);
            Lesson lesson = content.appendLesson(chapterId, UUID.randomUUID(), requiredText(title),
                    markdown(contentMarkdown), PublishStatus.HIDDEN).orElseThrow(CourseStructureService::positionsExhausted);
            return OwnedLesson.of(lesson, List.of());
        });
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OwnedLesson getOwnedLesson(UUID courseId, UUID lessonId) {
        access.requireLessonOwnership(courseId, lessonId);
        return ownedLesson(courseId, lessonId);
    }

    public OwnedLesson updateCourseLesson(UUID courseId, UUID lessonId, String title, String contentMarkdown) {
        return management.withCourseConfigurationWrite(courseId, () -> {
            Lesson lesson = lesson(courseId, lessonId);
            content.updateLesson(lesson.chapterId(), lessonId, requiredText(title), markdown(contentMarkdown));
            return ownedLesson(courseId, lessonId);
        });
    }

    public List<UUID> orderChapterLessons(UUID courseId, UUID chapterId, List<UUID> orderedIds) {
        return management.withCourseConfigurationWrite(courseId, () -> {
            chapter(courseId, chapterId);
            requirePermutation(orderedIds, content.findLessonsByCourseId(courseId).stream()
                    .filter(lesson -> lesson.chapterId().equals(chapterId)).map(Lesson::id).toList());
            content.reorderLessons(chapterId, orderedIds);
            return orderedIds;
        });
    }

    /** Idempotent and without cascade; a real change alters the required set, so completion is reconciled. */
    public OwnedLesson setCourseLessonPublication(UUID courseId, UUID lessonId, PublishStatus publicationStatus) {
        Objects.requireNonNull(publicationStatus, "publicationStatus");
        return management.withCourseConfigurationWrite(courseId, () -> {
            Lesson lesson = lesson(courseId, lessonId);
            if (lesson.publicationStatus() != publicationStatus) {
                content.updateLessonPublicationStatus(lesson.chapterId(), lessonId, publicationStatus);
                enrollments.reconcileCompletion(courseId);
            }
            return ownedLesson(courseId, lessonId);
        });
    }

    /** Published Lesson and its published materials for an admitted Learner; records no visit. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LearnerLesson getLearnerLesson(UUID courseId, UUID lessonId) {
        access.requireLearnerLessonAccess(courseId, lessonId);
        Lesson lesson = lesson(courseId, lessonId);
        return new LearnerLesson(lesson.id(), courseId, lesson.chapterId(), lesson.title(), lesson.contentMarkdown(),
                content.findPublishedMaterialsByLessonId(lessonId).stream().map(Material::of).toList());
    }

    /** Duplicates or a missing sibling are 400; an ID that is not a current sibling is 404. */
    static void requirePermutation(List<UUID> orderedIds, List<UUID> siblings) {
        if (new HashSet<>(orderedIds).size() != orderedIds.size()) throw invalidOrder();
        Set<UUID> current = Set.copyOf(siblings);
        if (!current.containsAll(orderedIds)) throw unavailable();
        if (orderedIds.size() != current.size()) throw invalidOrder();
    }

    private OwnedChapter ownedChapter(UUID courseId, UUID chapterId) {
        return management.ownedCourse(courseId).chapters().stream().filter(chapter -> chapter.id().equals(chapterId))
                .findFirst().orElseThrow(CourseStructureService::unavailable);
    }

    private OwnedLesson ownedLesson(UUID courseId, UUID lessonId) {
        return management.ownedCourse(courseId).chapters().stream().flatMap(chapter -> chapter.lessons().stream())
                .filter(lesson -> lesson.id().equals(lessonId)).findFirst().orElseThrow(CourseStructureService::unavailable);
    }

    private Chapter chapter(UUID courseId, UUID chapterId) {
        return content.findChapter(courseId, chapterId).orElseThrow(CourseStructureService::unavailable);
    }

    private Lesson lesson(UUID courseId, UUID lessonId) {
        return content.findLesson(courseId, lessonId).orElseThrow(CourseStructureService::unavailable);
    }

    private static BusinessRuleException positionsExhausted() {
        return new BusinessRuleException(HttpStatus.CONFLICT, "No representable sibling position remains");
    }

    private static BusinessRuleException invalidOrder() {
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid sibling order");
    }

    private static ResourceNotFoundException unavailable() {
        return new ResourceNotFoundException("Resource not available");
    }
}
