package com.devpulse.standup;

public record StandupGenerationResult(
        String summary,
        String modelName,
        Integer promptTokens,
        Integer completionTokens,
        long latencyMs
) {}