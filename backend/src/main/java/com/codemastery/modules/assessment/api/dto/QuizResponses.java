package com.codemastery.modules.assessment.api.dto;

import com.codemastery.modules.assessment.application.service.QuizService.QuizSummary;
import com.codemastery.modules.assessment.application.service.QuizService.QuizView;
import java.util.List;
import java.util.UUID;

/** CT23 quiz representations (OpenAPI B3QuizSummary, B3QuizOwned and its B3Question items). */
public final class QuizResponses {

    private QuizResponses() {}

    /** Learner metadata only: no question content or answer keys. */
    public record QuizSummaryResponse(
            UUID id,
            UUID courseId,
            String title,
            String kind,
            int timeLimitSeconds,
            boolean published,
            int questionCount
    ) {
        public static QuizSummaryResponse from(QuizSummary summary) {
            return new QuizSummaryResponse(summary.id(), summary.courseId(), summary.title(), summary.kind(),
                    summary.timeLimitSeconds(), summary.published(), summary.questionCount());
        }
    }

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

    /** Owner representation, including ordered question definitions and answer keys. */
    public record OwnedQuizResponse(
            UUID id,
            UUID courseId,
            String title,
            String kind,
            int timeLimitSeconds,
            boolean published,
            int questionCount,
            List<QuizQuestionItem> questions
    ) {
        public static OwnedQuizResponse from(QuizView quiz) {
            return new OwnedQuizResponse(quiz.id(), quiz.courseId(), quiz.title(), quiz.kind(),
                    quiz.timeLimitSeconds(), quiz.published(), quiz.questionCount(),
                    quiz.questions().stream().map(item -> new QuizQuestionItem(item.position(),
                            new QuestionDefinition(item.question().id(), item.question().skillId(),
                                    item.question().prompt(), item.question().kind(), item.question().difficulty(),
                                    item.question().options(), item.question().correctOptions()))).toList());
        }
    }
}
