package com.devpulse.shared.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Lives in `shared` because future features (PR context enrichment, codebase
 * indexing) will reuse this same GitHub connection — it's not specific to
 * the standup feature.
 */
@Component
public class GitHubClient {

    private final RestClient restClient;

    public GitHubClient(@Value("${github.token}") String githubToken) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + githubToken)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    /**
     * Fetches commits authored by `username` in `owner/repo`, for the given
     * calendar date (midnight to midnight, UTC). Never throws on "no commits
     * found" — returns an empty list, so callers can tell "no activity"
     * apart from "something actually broke."
     */
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
}