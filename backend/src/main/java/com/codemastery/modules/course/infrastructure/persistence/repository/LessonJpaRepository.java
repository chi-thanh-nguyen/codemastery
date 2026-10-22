package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.infrastructure.persistence.entity.LessonEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LessonJpaRepository extends Repository<LessonEntity, UUID> {

    /** Published lessons of one course in canonical order, resolved in a single query. */
    @Query("select l from LessonEntity l join ChapterEntity c on c.id = l.chapterId "
            + "where c.courseId = :courseId and l.publicationStatus = 'published' "
            + "order by c.position asc, l.position asc")
    List<LessonEntity> findPublishedByCourseIdInCanonicalOrder(@Param("courseId") UUID courseId);
}
