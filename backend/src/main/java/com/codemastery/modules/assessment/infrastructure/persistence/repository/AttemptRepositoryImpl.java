package com.codemastery.modules.assessment.infrastructure.persistence.repository;

import com.codemastery.modules.assessment.domain.model.Attempt;
import com.codemastery.modules.assessment.domain.repository.AttemptRepository;
import com.codemastery.modules.assessment.infrastructure.persistence.entity.AttemptEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class AttemptRepositoryImpl implements AttemptRepository {

    private final AttemptJpaRepository jpaRepository;

    public AttemptRepositoryImpl(AttemptJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Attempt save(Attempt attempt) {
        AttemptEntity saved = jpaRepository.save(toEntity(attempt));
        return toDomain(saved);
    }

    @Override
    public Optional<Attempt> findById(UUID id) {
        return jpaRepository.findById(id).map(AttemptRepositoryImpl::toDomain);
    }

    private static AttemptEntity toEntity(Attempt attempt) {
        // The AS01 AttemptEntity constructor takes quiz_id before user_id (unlike the Attempt record).
        return new AttemptEntity(
                attempt.id(),
                attempt.quiz_id(),
                attempt.user_id(),
                attempt.attempt_number(),
                attempt.started_at(),
                attempt.expires_at(),
                attempt.submitted_at(),
                attempt.score_percentage()
        );
    }

    private static Attempt toDomain(AttemptEntity entity) {
        return new Attempt(
                entity.getId(),
                entity.getUserId(),
                entity.getQuizId(),
                entity.getAttemptNumber(),
                entity.getStartedAt(),
                entity.getExpiresAt(),
                entity.getSubmittedAt(),
                entity.getScorePercentage()
        );
    }
}
