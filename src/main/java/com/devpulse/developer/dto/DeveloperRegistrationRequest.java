package com.devpulse.developer.dto;

import jakarta.validation.constraints.NotBlank;

public record DeveloperRegistrationRequest(
        @NotBlank(message = "GitHub username is required")
        String githubUsername,
        String timezone
) {}