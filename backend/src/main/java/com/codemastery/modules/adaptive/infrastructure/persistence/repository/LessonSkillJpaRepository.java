package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.modules.adaptive.infrastructure.persistence.entity.LessonSkillEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonSkillJpaRepository extends Repository<LessonSkillEntity, UUID> {
    List<LessonSkillEntity> findBySkillIdIn(Collection<UUID> skillIds);

    Optional<LessonSkillEntity> findByLessonIdAndSkillId(UUID lessonId, UUID skillId);

    LessonSkillEntity save(LessonSkillEntity entity);

    @Modifying(flushAutomatically = true)
    @Query("delete from LessonSkillEntity m where m.lessonId = :lessonId and m.skillId = :skillId")
    int deletePair(
            @Param("lessonId") UUID lessonId,
            @Param("skillId") UUID skillId);

}
