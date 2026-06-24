package com.devpulse.shared.github;

public record GitHubPullRequestFile(
        String filename,
        String status,
        int additions,
        int deletions,
        String patch
) {}