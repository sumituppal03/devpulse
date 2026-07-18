package com.devpulse.prcontext;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestWebhookPayload(
        String action,
        @JsonProperty("pull_request") PullRequest pullRequest,
        Repository repository
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequest(
            int number,
            String title,
            String body,
            @JsonProperty("head") Head head
    ) {}

    /**
     * The head object contains the branch name — this is what we parse for
     * a Linear ticket ID. E.g. "feature/LIN-234-auth-refactor" → "LIN-234"
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Head(String ref) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(String name, Owner owner) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(String login) {}
}
