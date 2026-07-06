package com.devpulse.codeqa.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterRepositoryRequest(
        @NotBlank String githubOwner,
        @NotBlank String githubRepo,
        String defaultBranch
) {}