package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.infrastructure.persistence.entity.LearningMaterialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningMaterialJpaRepository extends JpaRepository<LearningMaterialEntity, UUID> {

    @Query("select m from LearningMaterialEntity m where m.lesson_id = :lessonId")
    List<LearningMaterialEntity> findByLessonId(@Param("lessonId") UUID lessonId);

    @Query("select m from LearningMaterialEntity m where m.lesson_id = :lessonId and m.publicationStatus = :publicationStatus")
    List<LearningMaterialEntity> findByLessonIdAndPublicationStatus(@Param("lessonId") UUID lessonId,
                                                                    @Param("publicationStatus") String publicationStatus);

    /** Material only when its full material → lesson → chapter → course ancestry matches. */
    @Query("select m from LearningMaterialEntity m "
            + "join LessonEntity l on l.id = m.lesson_id "
            + "join ChapterEntity c on c.id = l.chapterId "
            + "where m.id = :materialId and l.id = :lessonId and c.courseId = :courseId")
    Optional<LearningMaterialEntity> findByIdWithinLesson(@Param("courseId") UUID courseId,
                                                          @Param("lessonId") UUID lessonId,
                                                          @Param("materialId") UUID materialId);
}
