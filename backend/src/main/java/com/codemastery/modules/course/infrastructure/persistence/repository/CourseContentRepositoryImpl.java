package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.domain.model.Chapter;
import com.codemastery.modules.course.domain.model.LearningMaterial;
import com.codemastery.modules.course.domain.model.Lesson;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseContentRepository;
import com.codemastery.modules.course.infrastructure.persistence.entity.ChapterEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LearningMaterialEntity;
import com.codemastery.modules.course.infrastructure.persistence.entity.LessonEntity;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseContentMapper;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Repository
public class CourseContentRepositoryImpl implements CourseContentRepository {

    private static final String LESSON_COLUMNS =
            "l.id, l.chapter_id, l.title, l.content_markdown, l.position, l.publication_status";
    private static final String MATERIAL_COLUMNS = "m.id, m.lesson_id, m.title, m.material_type, m.publication_status, "
            + "m.text_content, m.object_key, m.original_filename, m.external_video_url";

    private final ChapterJpaRepository chapterJpaRepository;
    private final LessonJpaRepository lessonJpaRepository;
    private final CourseContentMapper courseContentMapper;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final TransactionTemplate callerTransaction;

    public CourseContentRepositoryImpl(
            ChapterJpaRepository chapterJpaRepository,
            LessonJpaRepository lessonJpaRepository,
            CourseContentMapper courseContentMapper,
            JdbcTemplate jdbc,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager) {
        this.chapterJpaRepository = chapterJpaRepository;
        this.lessonJpaRepository = lessonJpaRepository;
        this.courseContentMapper = courseContentMapper;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.callerTransaction = CourseRepositoryImpl.callerTransaction(transactionManager);
    }

    /** See {@link CourseRepositoryImpl#directWrite}: rejects pending JPA changes, dooms the unit on failure. */
    private <T> T directWrite(Class<?> changedType, Collection<?> changedIds, Supplier<T> write) {
        return CourseRepositoryImpl.directWrite(callerTransaction, entityManager, changedType, changedIds, write);
    }

    @Override
    public List<Chapter> findChaptersByCourseId(UUID courseId) {
        if (courseId == null) {
            return List.of();
        }
        return chapterJpaRepository.findByCourseIdOrderByPositionAsc(courseId).stream()
                .map(courseContentMapper::toDomain)
                .toList();
    }

    @Override
    public List<Lesson> findPublishedLessonsByCourseId(UUID courseId) {
        if (courseId == null) {
            return List.of();
        }
        return lessonJpaRepository.findPublishedByCourseIdInCanonicalOrder(courseId).stream()
                .map(courseContentMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<UUID> findLessonCourseId(UUID lessonId) {
        return jdbc.query("SELECT ch.course_id FROM public.lessons l JOIN public.chapters ch ON ch.id=l.chapter_id WHERE l.id=?",
                (row, index) -> row.getObject(1, UUID.class), Objects.requireNonNull(lessonId)).stream().findFirst();
    }

    // CO03.content reads. Direct statements never return pre-lock managed entities.

    @Override
    public List<Lesson> findLessonsByCourseId(UUID courseId) {
        Objects.requireNonNull(courseId, "courseId");
        return jdbc.query("SELECT " + LESSON_COLUMNS + " FROM public.lessons l "
                        + "JOIN public.chapters ch ON ch.id = l.chapter_id WHERE ch.course_id = ? "
                        + "ORDER BY ch.position ASC, l.position ASC",
                (row, index) -> courseContentMapper.lessonFromRow(row), courseId);
    }

    @Override
    public Optional<Chapter> findChapter(UUID courseId, UUID chapterId) {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(chapterId, "chapterId");
        return jdbc.query("SELECT id, course_id, title, position FROM public.chapters WHERE id = ? AND course_id = ?",
                (row, index) -> courseContentMapper.chapterFromRow(row), chapterId, courseId).stream().findFirst();
    }

    @Override
    public Optional<Lesson> findLesson(UUID courseId, UUID lessonId) {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(lessonId, "lessonId");
        return jdbc.query("SELECT " + LESSON_COLUMNS + " FROM public.lessons l "
                        + "JOIN public.chapters ch ON ch.id = l.chapter_id WHERE l.id = ? AND ch.course_id = ?",
                (row, index) -> courseContentMapper.lessonFromRow(row), lessonId, courseId).stream().findFirst();
    }

    @Override
    public List<LearningMaterial> findMaterialsByCourseId(UUID courseId) {
        Objects.requireNonNull(courseId, "courseId");
        return jdbc.query("SELECT " + MATERIAL_COLUMNS + " FROM public.learning_materials m "
                        + "JOIN public.lessons l ON l.id = m.lesson_id JOIN public.chapters ch ON ch.id = l.chapter_id "
                        + "WHERE ch.course_id = ? ORDER BY ch.position ASC, l.position ASC, m.id ASC",
                (row, index) -> courseContentMapper.materialFromRow(row), courseId);
    }

    @Override
    public List<LearningMaterial> findPublishedMaterialsByLessonId(UUID lessonId) {
        Objects.requireNonNull(lessonId, "lessonId");
        return jdbc.query("SELECT " + MATERIAL_COLUMNS + " FROM public.learning_materials m "
                        + "WHERE m.lesson_id = ? AND m.publication_status = 'published' ORDER BY m.id ASC",
                (row, index) -> courseContentMapper.materialFromRow(row), lessonId);
    }

    @Override
    public Optional<LearningMaterial> findMaterial(UUID lessonId, UUID materialId) {
        Objects.requireNonNull(lessonId, "lessonId");
        Objects.requireNonNull(materialId, "materialId");
        return jdbc.query("SELECT " + MATERIAL_COLUMNS + " FROM public.learning_materials m "
                        + "WHERE m.id = ? AND m.lesson_id = ?",
                (row, index) -> courseContentMapper.materialFromRow(row), materialId, lessonId).stream().findFirst();
    }

    // CO03.content writes. Concurrent appends/reorders of one Course must be serialized by the
    // caller's Course-root guard; unserialized races fail on V002 uniqueness, never silently.

    @Override
    public Optional<Chapter> appendChapter(UUID courseId, UUID chapterId, String title) {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(chapterId, "chapterId");
        return directWrite(ChapterEntity.class, List.of(), () -> jdbc.query("INSERT INTO public.chapters (id, course_id, title, position) "
                        + "SELECT ?, ?, ?, next_position FROM (SELECT COALESCE(max(position), 0)::bigint + 1 AS next_position "
                        + "FROM public.chapters WHERE course_id = ?) next WHERE next_position <= 2147483647 RETURNING position",
                (row, index) -> row.getInt("position"), chapterId, courseId, title, courseId))
                .stream().findFirst().map(position -> new Chapter(chapterId, courseId, title, position));
    }

    @Override
    public Optional<Lesson> appendLesson(UUID chapterId, UUID lessonId, String title, String contentMarkdown,
                                         PublishStatus publicationStatus) {
        Objects.requireNonNull(chapterId, "chapterId");
        Objects.requireNonNull(lessonId, "lessonId");
        Objects.requireNonNull(publicationStatus, "publicationStatus");
        return directWrite(LessonEntity.class, List.of(), () -> jdbc.query(
                "INSERT INTO public.lessons (id, chapter_id, title, content_markdown, position, publication_status) "
                        + "SELECT ?, ?, ?, ?, next_position, ? FROM (SELECT COALESCE(max(position), 0)::bigint + 1 AS next_position "
                        + "FROM public.lessons WHERE chapter_id = ?) next WHERE next_position <= 2147483647 RETURNING position",
                (row, index) -> row.getInt("position"), lessonId, chapterId, title, contentMarkdown,
                publicationStatus.getStatus(), chapterId))
                .stream().findFirst()
                .map(position -> new Lesson(lessonId, chapterId, title, contentMarkdown, position, publicationStatus));
    }

    @Override
    public void updateChapterTitle(UUID courseId, UUID chapterId, String title) {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(chapterId, "chapterId");
        directWrite(ChapterEntity.class, List.of(chapterId), () -> requireOneRow(jdbc.update(
                "UPDATE public.chapters SET title = ? WHERE id = ? AND course_id = ?", title, chapterId, courseId)));
    }

    @Override
    public void updateLesson(UUID chapterId, UUID lessonId, String title, String contentMarkdown) {
        Objects.requireNonNull(chapterId, "chapterId");
        Objects.requireNonNull(lessonId, "lessonId");
        directWrite(LessonEntity.class, List.of(lessonId), () -> requireOneRow(jdbc.update(
                "UPDATE public.lessons SET title = ?, content_markdown = ? WHERE id = ? AND chapter_id = ?",
                title, contentMarkdown, lessonId, chapterId)));
    }

    @Override
    public void updateLessonPublicationStatus(UUID chapterId, UUID lessonId, PublishStatus publicationStatus) {
        Objects.requireNonNull(chapterId, "chapterId");
        Objects.requireNonNull(lessonId, "lessonId");
        Objects.requireNonNull(publicationStatus, "publicationStatus");
        directWrite(LessonEntity.class, List.of(lessonId), () -> requireOneRow(jdbc.update(
                "UPDATE public.lessons SET publication_status = ? WHERE id = ? AND chapter_id = ?",
                publicationStatus.getStatus(), lessonId, chapterId)));
    }

    @Override
    public void reorderChapters(UUID courseId, List<UUID> orderedChapterIds) {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(orderedChapterIds, "orderedChapterIds");
        directWrite(ChapterEntity.class, orderedChapterIds, () -> reorder("public.uq_chapters_course_position",
                jdbc.queryForList("SELECT id FROM public.chapters WHERE course_id = ?", UUID.class, courseId),
                orderedChapterIds, "UPDATE public.chapters SET position = ? WHERE id = ? AND course_id = ?", courseId));
    }

    @Override
    public void reorderLessons(UUID chapterId, List<UUID> orderedLessonIds) {
        Objects.requireNonNull(chapterId, "chapterId");
        Objects.requireNonNull(orderedLessonIds, "orderedLessonIds");
        directWrite(LessonEntity.class, orderedLessonIds, () -> reorder("public.uq_lessons_chapter_position",
                jdbc.queryForList("SELECT id FROM public.lessons WHERE chapter_id = ?", UUID.class, chapterId),
                orderedLessonIds, "UPDATE public.lessons SET position = ? WHERE id = ? AND chapter_id = ?", chapterId));
    }

    @Override
    public void insertMaterial(LearningMaterial material) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(material.materialType(), "materialType");
        Objects.requireNonNull(material.publicationStatus(), "publicationStatus");
        directWrite(LearningMaterialEntity.class, List.of(), () -> jdbc.update(
                "INSERT INTO public.learning_materials (id, lesson_id, title, material_type, publication_status, "
                        + "text_content, object_key, original_filename, external_video_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                material.id(), material.lessonId(), material.title(), material.materialType().getType(),
                material.publicationStatus().getStatus(), material.textContent(), material.objectKey(),
                material.originalFileName(), material.externalVideoUrl()));
    }

    @Override
    public void replaceMaterialContent(LearningMaterial material) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(material.id(), "id");
        Objects.requireNonNull(material.lessonId(), "lessonId");
        Objects.requireNonNull(material.materialType(), "materialType");
        directWrite(LearningMaterialEntity.class, List.of(material.id()), () -> requireOneRow(jdbc.update(
                "UPDATE public.learning_materials SET title = ?, text_content = ?, object_key = ?, "
                        + "original_filename = ?, external_video_url = ? WHERE id = ? AND lesson_id = ? AND material_type = ?",
                material.title(), material.textContent(), material.objectKey(), material.originalFileName(),
                material.externalVideoUrl(), material.id(), material.lessonId(), material.materialType().getType())));
    }

    @Override
    public void updateMaterialPublicationStatus(UUID lessonId, UUID materialId, PublishStatus publicationStatus) {
        Objects.requireNonNull(lessonId, "lessonId");
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(publicationStatus, "publicationStatus");
        directWrite(LearningMaterialEntity.class, List.of(materialId), () -> requireOneRow(jdbc.update(
                "UPDATE public.learning_materials SET publication_status = ? WHERE id = ? AND lesson_id = ?",
                publicationStatus.getStatus(), materialId, lessonId)));
    }

    /**
     * ADR006 §5: defer the existing sibling-position uniqueness, assign 1..N, then restore
     * immediate checking before returning, so a duplicate fails here rather than at commit.
     */
    private Void reorder(String constraint, List<UUID> current, List<UUID> ordered, String update, UUID parentId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Sibling reordering requires the caller's transaction");
        }
        Objects.requireNonNull(ordered, "ordered");
        if (ordered.stream().anyMatch(Objects::isNull) || ordered.size() != current.size()
                || !new HashSet<>(ordered).equals(new HashSet<>(current))
                || new HashSet<>(ordered).size() != ordered.size()) {
            throw new IllegalArgumentException("The order must name every current sibling exactly once");
        }
        jdbc.execute("SET CONSTRAINTS " + constraint + " DEFERRED");
        for (int index = 0; index < ordered.size(); index++) {
            requireOneRow(jdbc.update(update, index + 1, ordered.get(index), parentId));
        }
        jdbc.execute("SET CONSTRAINTS " + constraint + " IMMEDIATE");
        return null;
    }

    private static Integer requireOneRow(int rows) {
        if (rows != 1) {
            throw new IllegalStateException("Course content write lost its row");
        }
        return rows;
    }
}
