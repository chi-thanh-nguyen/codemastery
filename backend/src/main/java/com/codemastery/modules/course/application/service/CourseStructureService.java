package com.codemastery.modules.course.application.service;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.auth.application.service.UserAccountService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * It also produces the CT40 structural projection {@link #readStructure} (ADR005 §3.1).
 */
@Service
public class CourseStructureService {
    private final CourseManagementService management;
    private final CourseAccessService access;
    private final CourseContentRepository content;
    private final EnrollmentRepository enrollments;
    private final UserAccountService identities;

    public CourseStructureService(CourseManagementService management, CourseAccessService access,
                                  CourseContentRepository content, EnrollmentRepository enrollments,
                                  UserAccountService identities) {
        this.management = Objects.requireNonNull(management);
        this.access = Objects.requireNonNull(access);
        this.content = Objects.requireNonNull(content);
        this.enrollments = Objects.requireNonNull(enrollments);
        this.identities = Objects.requireNonNull(identities);
    }

    /**
     * CT40-LESSON-READ-2026-11-08-r1 (ADR005 §3.1): canonical Lesson identities and order for approved
     * structural consumers. The trusted CT01 role selects the CT03 assertion before any resource lookup:
     * an Instructor must own the Course and sees every Lesson; a Learner must be admitted and sees only
     * published Lessons; any other role is 403. Rows are sorted by Chapter then Lesson position, stored
     * gaps retained; an empty Course is an empty list. Every row must belong to a Chapter of the requested
     * Course, otherwise the read fails (safe 500) instead of returning a partial or foreign list.
     *
     * <p>Admission and both structure reads run in one REPEATABLE READ snapshot, so a concurrent
     * publication, reorder or append is seen entirely or not at all. A caller that joins this read from
     * its own transaction supplies that transaction's isolation instead; it must hold the Course root or
     * read at REPEATABLE READ itself to keep the same guarantee.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StructureSnapshot readStructure(UUID courseId) {
        if (courseId == null) throw new IllegalArgumentException("Identifier must not be null");
        boolean owner = switch (identities.requireCurrentIdentity().role()) {
            case INSTRUCTOR -> {
                access.requireCourseOwnership(courseId);
                yield true;
            }
            case LEARNER -> {
                access.requireLearnerCourseAccess(courseId);
                yield false;
            }
            case ADMINISTRATOR -> throw new AccessDeniedException("Access denied");
        };
        Map<UUID, Chapter> chapters = new HashMap<>();
        for (Chapter chapter : rows(content.findChaptersByCourseId(courseId))) {
            if (chapter == null || chapter.id() == null || !courseId.equals(chapter.courseId()) || chapter.position() < 1
                    || chapters.put(chapter.id(), chapter) != null) {
                throw inconsistentStructure();
            }
        }
        List<LessonReference> visible = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Lesson lesson : rows(content.findLessonsByCourseId(courseId))) {
            Chapter chapter = lesson == null || lesson.chapterId() == null ? null : chapters.get(lesson.chapterId());
            if (chapter == null || lesson.id() == null || lesson.position() < 1 || lesson.publicationStatus() == null
                    || !seen.add(lesson.id())) {
                throw inconsistentStructure();
            }
            boolean published = lesson.publicationStatus() == PublishStatus.PUBLISHED;
            if (owner || published) {
                visible.add(new LessonReference(lesson.id(), chapter.id(), chapter.position(), lesson.position(), published));
            }
        }
        visible.sort(Comparator.comparingInt(LessonReference::chapterPosition)
                .thenComparingInt(LessonReference::lessonPosition));
        return new StructureSnapshot(courseId, visible);
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

    private static <T> List<T> rows(List<T> rows) {
        if (rows == null) throw inconsistentStructure();
        return rows;
    }

    private static IllegalStateException inconsistentStructure() {
        return new IllegalStateException("Inconsistent Course structure");
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

    /** ADR005 §3.1: the Course and its visible Lessons in canonical order; identities and order only. */
    public record StructureSnapshot(UUID courseId, List<LessonReference> lessons) {
        public StructureSnapshot {
            Objects.requireNonNull(courseId, "courseId");
            lessons = List.copyOf(lessons);
        }
    }

    /** ADR005 §3.1: one visible Lesson, its parent Chapter and their stored positions. */
    public record LessonReference(UUID lessonId, UUID chapterId, int chapterPosition, int lessonPosition,
                                  boolean published) {
        public LessonReference {
            Objects.requireNonNull(lessonId, "lessonId");
            Objects.requireNonNull(chapterId, "chapterId");
        }
    }
}
