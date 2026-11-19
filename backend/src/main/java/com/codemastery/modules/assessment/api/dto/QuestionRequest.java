package com.codemastery.modules.assessment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record QuestionRequest(
        @NotNull UUID skillId,
        @NotBlank String prompt,
        @NotBlank String kind,
        @NotBlank String difficulty,
        @NotNull @Size(min = 2) List<String> options,
        @NotNull @Size(min = 2) List<Boolean> correctOptions
) {}