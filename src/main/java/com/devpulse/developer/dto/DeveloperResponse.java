package com.devpulse.developer.dto;

import java.util.UUID;

public record DeveloperResponse(
        UUID developerId,
        String githubUsername,
        String timezone
) {}