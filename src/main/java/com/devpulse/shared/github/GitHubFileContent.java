package com.devpulse.shared.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubFileContent(
        String path,
        String type,
        String content,
        String encoding,
        String sha
) {}