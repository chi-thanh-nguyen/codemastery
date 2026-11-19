package com.codemastery.modules.assessment.domain.repository;

import com.codemastery.modules.assessment.domain.model.Attempt;
import java.util.Optional;
import java.util.UUID;

/** Assessment-private attempt persistence over V004 (AS05.repo); lifecycle queries belong to CT04. */
public interface AttemptRepository {

    Attempt save(Attempt attempt);

    Optional<Attempt> findById(UUID id);
}
