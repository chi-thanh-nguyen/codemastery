package com.codemastery.modules.assessment.api.dto;

import java.util.List;
import java.util.UUID;

/** Closed B3AnswersWrite request DTOs shared by answer save and manual submit (ADR007 §13). */
public final class SubmitAttemptRequest {

    private SubmitAttemptRequest() {}

    public record AnswerEntryRequest(UUID questionId, List<Boolean> selectedOptions) {}

    public record AnswersWriteRequest(List<AnswerEntryRequest> answers) {}
}