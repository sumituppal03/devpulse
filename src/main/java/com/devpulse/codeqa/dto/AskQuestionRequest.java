package com.devpulse.codeqa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AskQuestionRequest(
        @NotBlank UUID repositoryId,
        @NotBlank @Size(max = 500) String question
) {}