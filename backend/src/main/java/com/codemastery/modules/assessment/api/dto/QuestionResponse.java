package com.codemastery.modules.assessment.api.dto;

import com.codemastery.modules.assessment.domain.model.Question;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record QuestionResponse(
        UUID id,
        UUID skillId,
        String prompt,
        String kind,
        String difficulty,
        List<String> options,
        List<Boolean> correctOptions
) {
    public static QuestionResponse from(Question question) {
        return new QuestionResponse(
                question.id(),
                question.skill_id(),
                question.prompt(),
                question.kind(),
                question.difficulty(),
                question.options() == null ? List.of() : Arrays.asList(question.options()),
                question.correct_options() == null ? List.of() : Arrays.asList(question.correct_options())
        );
    }
}