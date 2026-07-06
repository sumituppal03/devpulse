package com.devpulse.codeqa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AskQuestionRequest(
        @NotNull UUID repositoryId,
        @NotBlank @Size(max = 500) String question
) {}