package com.devpulse.shared.github;

public record GitHubCommitResponse(String sha, CommitDetail commit) {
    public record CommitDetail(String message, CommitAuthor author) {}
    public record CommitAuthor(String name, String date) {}
}