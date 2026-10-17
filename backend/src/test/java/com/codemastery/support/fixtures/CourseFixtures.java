package com.codemastery.support.fixtures;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.LessonProgress;
import com.codemastery.modules.course.domain.model.MaterialType;
import com.codemastery.modules.course.domain.model.PublishStatus;
import java.util.Map;
import java.util.UUID;

/**
 * Unchecked structural construction over the accepted CO01 records and enums
 * (Issue #30, PR #77): Course, Chapter, Lesson, LearningMaterial, Enrollment and
 * LessonProgress. Callers supply identifiers, references and valid required values;
 * optional prerequisite descriptions, settings, Markdown and resume references
 * remain nullable. Material payload nullability follows the selected variant.
 *
 * <p>Named shortcuts select only their documented codes and flags. Construction
 * does not validate V002 constraints, verify business outcomes or decide adaptive
 * behavior. Dataset-dependent or behavioral consumers require their own approved
 * checkpoints and execution evidence; no persistence or Spring context is needed.
 */
public final class CourseFixtures {

    // Prevent instantiation.
    private CourseFixtures() {}

    // Creates a course with all caller-supplied attributes.
    public static Course course(
            UUID id,
            UUID instructorId,
            String title,
            String description,
            String prerequisiteDescription,
            PublishStatus publicationStatus,
            boolean adaptiveEnabled,
            Map<String, Object> adaptiveSettings) {
        return new Course(
                id,
                instructorId,
                title,
                description,
                prerequisiteDescription,
                publicationStatus,
                adaptiveEnabled,
                adaptiveSettings);
    }

    // Creates a non-adaptive course with null prerequisite description and settings.
    public static Course course(
            UUID id,
            UUID instructorId,
            String title,
            String description,
            PublishStatus publicationStatus) {
        return course(id, instructorId, title, description, null, publicationStatus, false, null);
    }

    // Creates a non-adaptive course with prerequisite description.
    public static Course course(
            UUID id,
            UUID instructorId,
            String title,
            String description,
            String prerequisiteDescription,
            PublishStatus publicationStatus) {
        return course(id, instructorId, title, description, prerequisiteDescription, publicationStatus, false, null);
    }

    // Creates a published non-adaptive course.
    public static Course publishedCourse(
            UUID id,
            UUID instructorId,
            String title,
            String description) {
        return course(id, instructorId, title, description, null, PublishStatus.PUBLISHED, false, null);
    }

    // Creates a hidden non-adaptive course.
    public static Course hiddenCourse(
            UUID id,
            UUID instructorId,
            String title,
            String description) {
        return course(id, instructorId, title, description, null, PublishStatus.HIDDEN, false, null);
    }

    // Creates an adaptive course with caller-supplied settings.
    public static Course adaptiveCourse(
            UUID id,
            UUID instructorId,
            String title,
            String description,
            PublishStatus publicationStatus,
            Map<String, Object> adaptiveSettings) {
        return course(id, instructorId, title, description, null, publicationStatus, true, adaptiveSettings);
    }

    // Returns a builder with the caller's explicit adaptive flag.
    public static CourseBuilder courseBuilder(boolean adaptiveEnabled) {
        return new CourseBuilder(adaptiveEnabled);
    }

    // Builder for Course representations.
    public static final class CourseBuilder {
        private UUID id;
        private UUID instructorId;
        private String title;
        private String description;
        private String prerequisiteDescription;
        private PublishStatus publicationStatus;
        private boolean adaptiveEnabled;
        private Map<String, Object> adaptiveSettings;

        private CourseBuilder(boolean adaptiveEnabled) {
            this.adaptiveEnabled = adaptiveEnabled;
        }

        public CourseBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public CourseBuilder instructorId(UUID instructorId) {
            this.instructorId = instructorId;
            return this;
        }

        public CourseBuilder title(String title) {
            this.title = title;
            return this;
        }

        public CourseBuilder description(String description) {
            this.description = description;
            return this;
        }

        public CourseBuilder prerequisiteDescription(String prerequisiteDescription) {
            this.prerequisiteDescription = prerequisiteDescription;
            return this;
        }

        public CourseBuilder publicationStatus(PublishStatus publicationStatus) {
            this.publicationStatus = publicationStatus;
            return this;
        }

        public CourseBuilder adaptiveEnabled(boolean adaptiveEnabled) {
            this.adaptiveEnabled = adaptiveEnabled;
            return this;
        }

        public CourseBuilder adaptiveSettings(Map<String, Object> adaptiveSettings) {
            this.adaptiveSettings = adaptiveSettings;
            return this;
        }

        public Course build() {
            return new Course(
                    id,
                    instructorId,
                    title,
                    description,
                    prerequisiteDescription,
                    publicationStatus,
                    adaptiveEnabled,
                    adaptiveSettings);
        }
    }

    // Creates a chapter representation.
    public static Chapter chapter(
            UUID id,
            UUID courseId,
            String title,
            int position) {
        return new Chapter(id, courseId, title, position);
    }

    // Returns a builder with the caller's explicit position.
    public static ChapterBuilder chapterBuilder(int position) {
        return new ChapterBuilder(position);
    }

    // Builder for Chapter representations.
    public static final class ChapterBuilder {
        private UUID id;
        private UUID courseId;
        private String title;
        private int position;

        private ChapterBuilder(int position) {
            this.position = position;
        }

        public ChapterBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public ChapterBuilder courseId(UUID courseId) {
            this.courseId = courseId;
            return this;
        }

        public ChapterBuilder title(String title) {
            this.title = title;
            return this;
        }

        public ChapterBuilder position(int position) {
            this.position = position;
            return this;
        }

        public Chapter build() {
            return new Chapter(id, courseId, title, position);
        }
    }

    // Creates a lesson with all caller-supplied attributes.
    public static Lesson lesson(
            UUID id,
            UUID chapterId,
            String title,
            String contentMarkdown,
            int position,
            PublishStatus publicationStatus) {
        return new Lesson(id, chapterId, title, contentMarkdown, position, publicationStatus);
    }

    // Creates a lesson with null markdown content.
    public static Lesson lesson(
            UUID id,
            UUID chapterId,
            String title,
            int position,
            PublishStatus publicationStatus) {
        return lesson(id, chapterId, title, null, position, publicationStatus);
    }

    // Creates a published lesson with null markdown content.
    public static Lesson publishedLesson(
            UUID id,
            UUID chapterId,
            String title,
            int position) {
        return lesson(id, chapterId, title, null, position, PublishStatus.PUBLISHED);
    }

    // Creates a hidden lesson with null markdown content.
    public static Lesson hiddenLesson(
            UUID id,
            UUID chapterId,
            String title,
            int position) {
        return lesson(id, chapterId, title, null, position, PublishStatus.HIDDEN);
    }

    // Returns a builder with the caller's explicit position.
    public static LessonBuilder lessonBuilder(int position) {
        return new LessonBuilder(position);
    }

    // Builder for Lesson representations.
    public static final class LessonBuilder {
        private UUID id;
        private UUID chapterId;
        private String title;
        private String contentMarkdown;
        private int position;
        private PublishStatus publicationStatus;

        private LessonBuilder(int position) {
            this.position = position;
        }

        public LessonBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public LessonBuilder chapterId(UUID chapterId) {
            this.chapterId = chapterId;
            return this;
        }

        public LessonBuilder title(String title) {
            this.title = title;
            return this;
        }

        public LessonBuilder contentMarkdown(String contentMarkdown) {
            this.contentMarkdown = contentMarkdown;
            return this;
        }

        public LessonBuilder position(int position) {
            this.position = position;
            return this;
        }

        public LessonBuilder publicationStatus(PublishStatus publicationStatus) {
            this.publicationStatus = publicationStatus;
            return this;
        }

        public Lesson build() {
            return new Lesson(id, chapterId, title, contentMarkdown, position, publicationStatus);
        }
    }

    // Creates a learning material with all caller-supplied attributes.
    public static LearningMaterial material(
            UUID id,
            UUID lessonId,
            String title,
            MaterialType materialType,
            PublishStatus publicationStatus,
            String textContent,
            String objectKey,
            String originalFileName,
            String externalVideoUrl) {
        return new LearningMaterial(
                id,
                lessonId,
                title,
                materialType,
                publicationStatus,
                textContent,
                objectKey,
                originalFileName,
                externalVideoUrl);
    }

    // Creates a text-based learning material.
    public static LearningMaterial textMaterial(
            UUID id,
            UUID lessonId,
            String title,
            PublishStatus publicationStatus,
            String textContent) {
        return material(id, lessonId, title, MaterialType.TEXT, publicationStatus, textContent, null, null, null);
    }

    // Creates a file-based learning material.
    public static LearningMaterial fileMaterial(
            UUID id,
            UUID lessonId,
            String title,
            PublishStatus publicationStatus,
            String objectKey,
            String originalFileName) {
        return material(id, lessonId, title, MaterialType.FILE, publicationStatus, null, objectKey, originalFileName, null);
    }

    // Creates an external video learning material.
    public static LearningMaterial externalVideoMaterial(
            UUID id,
            UUID lessonId,
            String title,
            PublishStatus publicationStatus,
            String externalVideoUrl) {
        return material(id, lessonId, title, MaterialType.EXTERNAL_VIDEO, publicationStatus, null, null, null, externalVideoUrl);
    }

    // Returns a builder for LearningMaterial representations.
    public static LearningMaterialBuilder materialBuilder() {
        return new LearningMaterialBuilder();
    }

    // Builder for LearningMaterial representations.
    public static final class LearningMaterialBuilder {
        private UUID id;
        private UUID lessonId;
        private String title;
        private MaterialType materialType;
        private PublishStatus publicationStatus;
        private String textContent;
        private String objectKey;
        private String originalFileName;
        private String externalVideoUrl;

        public LearningMaterialBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public LearningMaterialBuilder lessonId(UUID lessonId) {
            this.lessonId = lessonId;
            return this;
        }

        public LearningMaterialBuilder title(String title) {
            this.title = title;
            return this;
        }

        public LearningMaterialBuilder materialType(MaterialType materialType) {
            this.materialType = materialType;
            return this;
        }

        public LearningMaterialBuilder publicationStatus(PublishStatus publicationStatus) {
            this.publicationStatus = publicationStatus;
            return this;
        }

        public LearningMaterialBuilder textContent(String textContent) {
            this.textContent = textContent;
            return this;
        }

        public LearningMaterialBuilder objectKey(String objectKey) {
            this.objectKey = objectKey;
            return this;
        }

        public LearningMaterialBuilder originalFileName(String originalFileName) {
            this.originalFileName = originalFileName;
            return this;
        }

        public LearningMaterialBuilder externalVideoUrl(String externalVideoUrl) {
            this.externalVideoUrl = externalVideoUrl;
            return this;
        }

        public LearningMaterial build() {
            return new LearningMaterial(
                    id,
                    lessonId,
                    title,
                    materialType,
                    publicationStatus,
                    textContent,
                    objectKey,
                    originalFileName,
                    externalVideoUrl);
        }
    }

    // Creates an enrollment with all caller-supplied attributes.
    public static Enrollment enrollment(
            UUID id,
            UUID userId,
            UUID courseId,
            EnrollmentStatus status,
            UUID resumeLessonId) {
        return new Enrollment(id, userId, courseId, status, resumeLessonId);
    }

    // Creates an enrollment with null resume lesson reference.
    public static Enrollment enrollment(
            UUID id,
            UUID userId,
            UUID courseId,
            EnrollmentStatus status) {
        return enrollment(id, userId, courseId, status, null);
    }

    // Creates an active enrollment with null resume lesson reference.
    public static Enrollment activeEnrollment(
            UUID id,
            UUID userId,
            UUID courseId) {
        return enrollment(id, userId, courseId, EnrollmentStatus.ACTIVE, null);
    }

    // Creates a completed enrollment with null resume lesson reference.
    public static Enrollment completedEnrollment(
            UUID id,
            UUID userId,
            UUID courseId) {
        return enrollment(id, userId, courseId, EnrollmentStatus.COMPLETED, null);
    }

    // Returns a builder for Enrollment representations.
    public static EnrollmentBuilder enrollmentBuilder() {
        return new EnrollmentBuilder();
    }

    // Builder for Enrollment representations.
    public static final class EnrollmentBuilder {
        private UUID id;
        private UUID userId;
        private UUID courseId;
        private EnrollmentStatus status;
        private UUID resumeLessonId;

        public EnrollmentBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public EnrollmentBuilder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public EnrollmentBuilder courseId(UUID courseId) {
            this.courseId = courseId;
            return this;
        }

        public EnrollmentBuilder status(EnrollmentStatus status) {
            this.status = status;
            return this;
        }

        public EnrollmentBuilder resumeLessonId(UUID resumeLessonId) {
            this.resumeLessonId = resumeLessonId;
            return this;
        }

        public Enrollment build() {
            return new Enrollment(id, userId, courseId, status, resumeLessonId);
        }
    }

    // Creates a lesson progress with all caller-supplied attributes.
    public static LessonProgress lessonProgress(
            UUID id,
            UUID enrollmentId,
            UUID lessonId,
            boolean completed,
            boolean masterySkipped) {
        return new LessonProgress(id, enrollmentId, lessonId, completed, masterySkipped);
    }

    // Creates a completed lesson progress representation.
    public static LessonProgress completedProgress(
            UUID id,
            UUID enrollmentId,
            UUID lessonId) {
        return lessonProgress(id, enrollmentId, lessonId, true, false);
    }

    // Creates a skipped lesson progress representation satisfying V002 constraint.
    public static LessonProgress skippedProgress(
            UUID id,
            UUID enrollmentId,
            UUID lessonId) {
        return lessonProgress(id, enrollmentId, lessonId, true, true);
    }

    // Creates an in-progress lesson progress representation.
    public static LessonProgress inProgress(
            UUID id,
            UUID enrollmentId,
            UUID lessonId) {
        return lessonProgress(id, enrollmentId, lessonId, false, false);
    }

    // Returns a builder with the caller's explicit completion and skip flags.
    public static LessonProgressBuilder lessonProgressBuilder(boolean completed, boolean masterySkipped) {
        return new LessonProgressBuilder(completed, masterySkipped);
    }

    // Builder for LessonProgress representations.
    public static final class LessonProgressBuilder {
        private UUID id;
        private UUID enrollmentId;
        private UUID lessonId;
        private boolean completed;
        private boolean masterySkipped;

        private LessonProgressBuilder(boolean completed, boolean masterySkipped) {
            this.completed = completed;
            this.masterySkipped = masterySkipped;
        }

        public LessonProgressBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public LessonProgressBuilder enrollmentId(UUID enrollmentId) {
            this.enrollmentId = enrollmentId;
            return this;
        }

        public LessonProgressBuilder lessonId(UUID lessonId) {
            this.lessonId = lessonId;
            return this;
        }

        public LessonProgressBuilder completed(boolean completed) {
            this.completed = completed;
            return this;
        }

        public LessonProgressBuilder masterySkipped(boolean masterySkipped) {
            this.masterySkipped = masterySkipped;
            return this;
        }

        public LessonProgress build() {
            return new LessonProgress(id, enrollmentId, lessonId, completed, masterySkipped);
        }
    }
}
