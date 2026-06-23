package com.devpulse.shared.github;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class GitHubClient {

    private final RestClient restClient;

    public GitHubClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<GitHubCommitResponse> fetchCommitsForDate(
            String owner, String repo, String username, LocalDate date) {

        String since = date.atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        String until = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString();

        GitHubCommitResponse[] response = restClient.get()
                .uri("/repos/{owner}/{repo}/commits?author={username}&since={since}&until={until}",
                        owner, repo, username, since, until)
                .retrieve()
                .body(GitHubCommitResponse[].class);

        return response != null ? List.of(response) : List.of();
    }

    public List<GitHubCommitResponse> fetchRecentCommits(String owner, String repo, String username, int limit) {
        GitHubCommitResponse[] response = restClient.get()
                .uri("/repos/{owner}/{repo}/commits?author={username}&per_page={limit}",
                        owner, repo, username, limit)
                .retrieve()
                .body(GitHubCommitResponse[].class);

        return response != null ? List.of(response) : List.of();
    }
}