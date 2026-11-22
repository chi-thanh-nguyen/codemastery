package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuestionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionJpaRepository extends JpaRepository<QuestionEntity, UUID> {

    @Query("SELECT q FROM QuestionEntity q WHERE q.id = :id AND q.skill_id IN :skillIds")
    Optional<QuestionEntity> findInSkills(@Param("id") UUID id, @Param("skillIds") Collection<UUID> skillIds);

    /**
     * Native {@code FOR UPDATE}: Hibernate renders PESSIMISTIC_WRITE on PostgreSQL as
     * {@code FOR NO KEY UPDATE}, which does not conflict with the FOR KEY SHARE lock taken by a
     * new foreign-key reference and would therefore not serialize reference insertion.
     */
    @Query(value = "SELECT * FROM public.questions WHERE id = :id AND skill_id IN (:skillIds) FOR UPDATE",
            nativeQuery = true)
    Optional<QuestionEntity> lockInSkills(@Param("id") UUID id, @Param("skillIds") Collection<UUID> skillIds);

    @Query("SELECT COUNT(q) FROM QuestionEntity q WHERE q.skill_id IN :skillIds")
    long countInSkills(@Param("skillIds") Collection<UUID> skillIds);

    /** PostgreSQL uuid order equals the lowercase canonical text order; never sort UUIDs in Java. */
    @Query("SELECT q FROM QuestionEntity q WHERE q.skill_id IN :skillIds ORDER BY q.id ASC")
    List<QuestionEntity> findPageInSkills(@Param("skillIds") Collection<UUID> skillIds, Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(qq) > 0 THEN true ELSE false END FROM QuizQuestionEntity qq WHERE qq.question_id = :questionId")
    boolean isReferencedByQuizQuestion(@Param("questionId") UUID questionId);

    @Query("SELECT CASE WHEN COUNT(aa) > 0 THEN true ELSE false END FROM AttemptAnswerEntity aa WHERE aa.question_id = :questionId")
    boolean isReferencedByAttemptAnswer(@Param("questionId") UUID questionId);
}
