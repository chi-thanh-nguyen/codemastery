package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.infrastructure.persistence.entity.AttemptEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptJpaRepository extends JpaRepository<AttemptEntity, UUID> {
}
