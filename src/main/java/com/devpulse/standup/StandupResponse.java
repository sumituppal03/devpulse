package com.devpulse.standup;

import com.devpulse.shared.github.GitHubCommitResponse;

import java.util.List;

public record StandupResponse(
        String summary,
        int commitCount,
        List<GitHubCommitResponse> commits
) {}