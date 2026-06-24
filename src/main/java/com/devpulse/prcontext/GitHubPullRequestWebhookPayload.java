package com.devpulse.prcontext;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitHub's real pull_request webhook payload has dozens of fields.
 * @JsonIgnoreProperties(ignoreUnknown = true) means we only care about
 * the few we actually use — everything else is silently ignored rather
 * than throwing a parsing error.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestWebhookPayload(
        String action,
        @JsonProperty("pull_request") PullRequest pullRequest,
        Repository repository
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequest(int number, String title, String body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(String name, Owner owner) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(String login) {}
}