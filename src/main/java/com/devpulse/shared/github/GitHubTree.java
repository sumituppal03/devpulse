package com.devpulse.shared.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubTree(
        String sha,
        List<GitHubTreeItem> tree,
        boolean truncated
) {}