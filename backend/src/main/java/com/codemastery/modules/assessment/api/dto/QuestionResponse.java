package com.codemastery.modules.assessment.api.dto;

import com.codemastery.modules.assessment.application.service.QuestionBankService.QuestionView;
import java.util.List;
import java.util.UUID;

/** B3Question: the owner representation, answer key included. Never used by learner endpoints. */
public record QuestionResponse(
        UUID id,
        UUID skillId,
        String prompt,
        String kind,
        String difficulty,
        List<String> options,
        List<Boolean> correctOptions
) {
    public QuestionResponse {
        options = List.copyOf(options);
        correctOptions = List.copyOf(correctOptions);
    }

    public static QuestionResponse from(QuestionView view) {
        return new QuestionResponse(view.id(), view.skillId(), view.prompt(), view.kind(), view.difficulty(),
                view.options(), view.correctOptions());
    }
}
