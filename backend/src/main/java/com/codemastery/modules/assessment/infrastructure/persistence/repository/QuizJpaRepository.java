package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.infrastructure.persistence.entity.QuizEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizJpaRepository extends JpaRepository<QuizEntity, UUID> {
}
