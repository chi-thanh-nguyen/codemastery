package com.codemastery.modules.assessment.application.event;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * CT08.evidence ({@code CT08-EVIDENCE-2026-11-09-r1}, ADR007 §12): immutable submitted-quiz
 * evidence published synchronously inside the guarded {@code AttemptService.submit} and
 * {@code AttemptService.finalizeExpired} transaction after answers and terminal fields are
 * flushed (ADR011 §4, ADR012 §9.3).
 */
public record QuizSubmittedEvent(
        UUID attemptId,
        UUID learnerId,
        UUID courseId,
        UUID quizId,
        String quizKind,
        int attemptNumber,
        Instant startedAt,
        Instant expiresAt,
        Instant submittedAt,
        List<AnswerEvidence> answers) {

    private static final Set<String> VALID_QUIZ_KINDS = Set.of("diagnostic", "practice", "test");

    public QuizSubmittedEvent {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(quizId, "quizId");
        Objects.requireNonNull(quizKind, "quizKind");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(answers, "answers");
        if (!VALID_QUIZ_KINDS.contains(quizKind)) {
            throw new IllegalArgumentException("quizKind must be a V004 literal");
        }
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        if (!expiresAt.isAfter(startedAt)) {
            throw new IllegalArgumentException("expiresAt must follow startedAt");
        }
        if (submittedAt.isBefore(startedAt) || submittedAt.isAfter(expiresAt)) {
            throw new IllegalArgumentException("submittedAt must fall within [startedAt, expiresAt]");
        }
        answers = List.copyOf(answers);
        if (answers.isEmpty()) {
            throw new IllegalArgumentException("answers must be nonempty");
        }
        Set<UUID> seenQuestions = new HashSet<>(answers.size());
        int previousPosition = 0;
        for (AnswerEvidence answer : answers) {
            if (!seenQuestions.add(answer.questionId())) {
                throw new IllegalArgumentException("answers must contain distinct questionIds");
            }
            if (answer.position() <= previousPosition) {
                throw new IllegalArgumentException("answers must be ordered by strictly increasing positions");
            }
            previousPosition = answer.position();
        }
    }

    /** Captured per-question grading evidence in ascending captured quiz position order. */
    public record AnswerEvidence(UUID questionId, int position, UUID skillId, boolean correct) {

        public AnswerEvidence {
            Objects.requireNonNull(questionId, "questionId");
            Objects.requireNonNull(skillId, "skillId");
            if (position < 1) {
                throw new IllegalArgumentException("position must be positive");
            }
        }
    }
}
