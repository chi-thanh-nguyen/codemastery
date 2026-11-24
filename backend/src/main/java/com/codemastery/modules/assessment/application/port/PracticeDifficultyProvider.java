package com.codemastery.modules.assessment.application.port;

import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import java.util.UUID;

/**
 * Assessment-owned practice difficulty port ({@code CT07-DIFFICULTY-2026-11-12-r1}, ADR011 §8).
 *
 * <p>Under Leader Decision E02 (ADR013 §§19.1–19.2), this port remains published with no
 * production caller.
 */
public interface PracticeDifficultyProvider {
    DifficultyLevel targetDifficulty(UUID courseId, UUID skillId);
}
