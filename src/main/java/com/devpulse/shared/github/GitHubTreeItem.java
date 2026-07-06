package com.devpulse.shared.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubTreeItem(
        String path,
        String type,
        String sha,
        String url
) {}