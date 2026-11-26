package com.codemastery.modules.assessment.api.dto;

import com.codemastery.modules.assessment.application.service.AttemptService.AttemptQuestionView;
import com.codemastery.modules.assessment.application.service.AttemptService.AttemptRead;
import com.codemastery.modules.assessment.application.service.AttemptService.AttemptResult;
import com.codemastery.modules.assessment.application.service.AttemptService.AttemptSummary;
import com.codemastery.modules.assessment.application.service.AttemptService.AttemptView;
import com.codemastery.modules.assessment.application.service.AttemptService.GradedAnswerView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** OpenAPI B3Attempt, B3AttemptSummary, B3AttemptResult and B3AttemptRead DTOs (ADR007 §13). */
public final class AttemptResponses {

    private AttemptResponses() {}

    public sealed interface AttemptReadResponse permits AttemptResponse, AttemptResultResponse {}

    public record AttemptQuestionResponse(UUID questionId, int position, String prompt, String kind,
                                          List<String> options, List<Boolean> selectedOptions) {
        public static AttemptQuestionResponse from(AttemptQuestionView view) {
            return new AttemptQuestionResponse(
                    view.questionId(), view.position(), view.prompt(), view.kind(),
                    view.options(), view.selectedOptions());
        }
    }

    /** B3Attempt: unsubmitted captured attempt view; never exposes keys, correctness or score. */
    public record AttemptResponse(UUID attemptId, UUID quizId, UUID courseId, int attemptNumber,
                                  Instant startedAt, Instant expiresAt, String status,
                                  List<AttemptQuestionResponse> questions) implements AttemptReadResponse {
        public static AttemptResponse from(AttemptView view) {
            return new AttemptResponse(
                    view.attemptId(), view.quizId(), view.courseId(), view.attemptNumber(),
                    view.startedAt(), view.expiresAt(), view.status(),
                    view.questions().stream().map(AttemptQuestionResponse::from).toList());
        }
    }

    public record GradedAnswerResponse(UUID questionId, int position, String prompt, String kind,
                                       List<String> options, List<Boolean> selectedOptions, boolean correct) {
        public static GradedAnswerResponse from(GradedAnswerView view) {
            return new GradedAnswerResponse(
                    view.questionId(), view.position(), view.prompt(), view.kind(),
                    view.options(), view.selectedOptions(), view.correct());
        }
    }

    /** B3AttemptResult: submitted immutable result; never exposes answer keys. */
    public record AttemptResultResponse(UUID attemptId, UUID quizId, UUID courseId, int attemptNumber,
                                        Instant startedAt, Instant expiresAt, String status,
                                        Instant submittedAt, BigDecimal scorePercentage,
                                        List<GradedAnswerResponse> answers) implements AttemptReadResponse {
        public static AttemptResultResponse from(AttemptResult result) {
            return new AttemptResultResponse(
                    result.attemptId(), result.quizId(), result.courseId(), result.attemptNumber(),
                    result.startedAt(), result.expiresAt(), result.status(),
                    result.submittedAt(), result.scorePercentage(),
                    result.answers().stream().map(GradedAnswerResponse::from).toList());
        }
    }

    /** B3AttemptSummary: learner history row. */
    public record AttemptSummaryResponse(UUID attemptId, UUID quizId, UUID courseId, int attemptNumber,
                                         Instant startedAt, Instant expiresAt, String status,
                                         Instant submittedAt, BigDecimal scorePercentage) {
        public static AttemptSummaryResponse from(AttemptSummary summary) {
            return new AttemptSummaryResponse(
                    summary.attemptId(), summary.quizId(), summary.courseId(), summary.attemptNumber(),
                    summary.startedAt(), summary.expiresAt(), summary.status(),
                    summary.submittedAt(), summary.scorePercentage());
        }
    }

    public static AttemptReadResponse fromRead(AttemptRead read) {
        return switch (read) {
            case AttemptView view -> AttemptResponse.from(view);
            case AttemptResult result -> AttemptResultResponse.from(result);
        };
    }
}
