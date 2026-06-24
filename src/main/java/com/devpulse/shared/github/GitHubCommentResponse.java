package com.devpulse.shared.github;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubCommentResponse(
        long id,
        @JsonProperty("html_url") String htmlUrl
) {}