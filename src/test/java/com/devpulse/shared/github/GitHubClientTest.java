package com.devpulse.shared.github;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Uses MockRestServiceServer — a fake HTTP server Spring provides for tests.
 * No real network call ever reaches the actual GitHub API; we control exactly
 * what "GitHub" responds with and verify our parsing logic handles it correctly.
 */
class GitHubClientTest {

    @Test
    void fetchCommitsForDate_parsesGitHubJson_intoCommitRecords() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubClient client = new GitHubClient(builder.build());

        String fakeGitHubResponse = """
                [
                  {
                    "sha": "abc123",
                    "commit": {
                      "message": "Fixed the auth bug",
                      "author": { "name": "Sumit", "date": "2026-06-19T10:00:00Z" }
                    }
                  }
                ]
                """;

        server.expect(method(GET))
              .andRespond(withSuccess(fakeGitHubResponse, MediaType.APPLICATION_JSON));

        List<GitHubCommitResponse> result = client.fetchCommitsForDate(
                "sumituppal03", "devpulse", "sumituppal03", LocalDate.of(2026, 6, 19));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sha()).isEqualTo("abc123");
        assertThat(result.get(0).commit().message()).isEqualTo("Fixed the auth bug");

        server.verify();
    }

    @Test
    void fetchCommitsForDate_withNoCommits_returnsEmptyList_notNull() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubClient client = new GitHubClient(builder.build());

        server.expect(method(GET))
              .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<GitHubCommitResponse> result = client.fetchCommitsForDate(
                "sumituppal03", "devpulse", "sumituppal03", LocalDate.of(2026, 6, 19));

        assertThat(result).isEmpty();
        server.verify();
    }
}