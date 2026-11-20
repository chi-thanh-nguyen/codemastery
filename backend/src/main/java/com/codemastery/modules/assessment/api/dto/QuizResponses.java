package com.codemastery.modules.assessment.api.dto;

import java.util.List;
import java.util.UUID;

public final class QuizResponses {

    private QuizResponses() {}

    public record QuizSummaryResponse(
            UUID id,
            UUID courseId,
            String title,
            String kind,
            int timeLimitSeconds,
            boolean published,
            int questionCount
    ) {}

    public record QuestionDefinition(
            UUID id,
            UUID skillId,
            String prompt,
            String kind,
            String difficulty,
            List<String> options,
            List<Boolean> correctOptions
    ) {}

    public record QuizQuestionItem(
            int position,
            QuestionDefinition question
    ) {}

    public record OwnedQuizResponse(
            UUID id,
            UUID courseId,
            String title,
            String kind,
            int timeLimitSeconds,
            boolean published,
            int questionCount,
            List<QuizQuestionItem> questions
    ) {}
}