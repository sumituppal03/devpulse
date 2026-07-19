package com.devpulse.shared.slack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Posts messages to Slack via Incoming Webhooks.
 *
 * Each tenant configures their own webhook URL via POST /api/v1/integrations/slack.
 * Slack Incoming Webhooks are free, require no OAuth approval process, and
 * can be created by any Slack workspace admin in under 2 minutes.
 *
 * Setup: Slack workspace admin goes to
 * https://api.slack.com/apps -> Create App -> Incoming Webhooks -> Add New Webhook
 * and gets a URL like https://hooks.slack.com/services/T.../B.../xxx
 *
 * That URL is what tenants paste into POST /api/v1/integrations/slack.
 */
@Component
@Slf4j
public class SlackClient {

    private final RestClient restClient;

    public SlackClient() {
        this.restClient = RestClient.builder().build();
    }

    /**
     * Posts a message to a Slack channel via the tenant's configured webhook URL.
     * Returns true if the message was posted successfully, false otherwise.
     * Failure is non-fatal — a Slack posting failure should not break standup generation.
     */
    public boolean postMessage(String webhookUrl, String text) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Slack webhook URL is blank - skipping message post");
            return false;
        }

        try {
            SlackMessage message = new SlackMessage(text);
            String response = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message)
                    .retrieve()
                    .body(String.class);

            boolean success = "ok".equals(response);
            if (success) {
                log.info("Slack message posted successfully");
            } else {
                log.warn("Slack returned unexpected response: {}", response);
            }
            return success;

        } catch (RestClientException e) {
            log.error("Failed to post Slack message: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Formats a standup summary for Slack with proper markdown.
     * Slack uses mrkdwn, not CommonMark -- *bold*, not **bold**.
     */
    public String formatStandupMessage(String developerUsername, String date, String summary) {
        return "*DevPulse Standup* - @%s (%s)\n\n%s".formatted(
                developerUsername, date, summary);
    }

    private record SlackMessage(@JsonProperty("text") String text) {}
}
