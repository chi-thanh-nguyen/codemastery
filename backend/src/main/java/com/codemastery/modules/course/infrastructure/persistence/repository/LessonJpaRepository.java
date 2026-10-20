package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.infrastructure.persistence.entity.LessonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LessonJpaRepository extends JpaRepository<LessonEntity, UUID> {

    List<LessonEntity> findByChapterIdAndPublicationStatusOrderByPositionAsc(UUID chapterId, String publicationStatus);

    List<LessonEntity> findByChapterIdInAndPublicationStatusOrderByPositionAsc(Collection<UUID> chapterIds, String publicationStatus);

    Optional<LessonEntity> findByIdAndPublicationStatus(UUID id, String publicationStatus);
}
