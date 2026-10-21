package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.infrastructure.persistence.entity.LessonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonJpaRepository extends JpaRepository<LessonEntity, UUID> {

    List<LessonEntity> findByChapterIdOrderByPositionAsc(UUID chapterId);

    List<LessonEntity> findByChapterIdAndPublicationStatusOrderByPositionAsc(UUID chapterId, String publicationStatus);

    List<LessonEntity> findByChapterIdInAndPublicationStatusOrderByPositionAsc(Collection<UUID> chapterIds, String publicationStatus);

    Optional<LessonEntity> findByIdAndPublicationStatus(UUID id, String publicationStatus);

    /** Published lessons of one course in canonical order, resolved in a single query. */
    @Query("select l from LessonEntity l join ChapterEntity c on c.id = l.chapterId "
            + "where c.courseId = :courseId and l.publicationStatus = 'published' "
            + "order by c.position asc, l.position asc")
    List<LessonEntity> findPublishedByCourseIdInCanonicalOrder(@Param("courseId") UUID courseId);

    /** Lesson only when its actual chapter belongs to the given course. */
    @Query("select l from LessonEntity l join ChapterEntity c on c.id = l.chapterId "
            + "where l.id = :lessonId and c.courseId = :courseId")
    Optional<LessonEntity> findByIdWithinCourse(@Param("courseId") UUID courseId,
                                                @Param("lessonId") UUID lessonId);

    @Query("select c.courseId from LessonEntity l join ChapterEntity c on c.id = l.chapterId "
            + "where l.id = :lessonId")
    Optional<UUID> findCourseIdByLessonId(@Param("lessonId") UUID lessonId);
}
