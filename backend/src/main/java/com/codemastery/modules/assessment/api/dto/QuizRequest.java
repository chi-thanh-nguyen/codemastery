package com.codemastery.modules.assessment.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public final class QuizRequest {

    private QuizRequest() {}

    public record WriteQuizRequest(
            @NotBlank String title,
            @NotBlank String kind,
            @NotNull @Min(1) Integer timeLimitSeconds,
            @NotNull List<UUID> questionIds
    ) {}

    public record PublicationRequest(
            @NotNull Boolean published
    ) {}
}