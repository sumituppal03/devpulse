package com.devpulse.shared.slack;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SlackClient.
 *
 * postMessage() makes a real HTTP call, so we only test the parts
 * we can test without a live Slack webhook — the formatting logic.
 * The HTTP posting itself is tested manually via POST /api/v1/integrations/slack
 * which sends a real test message before saving the webhook URL.
 */
class SlackClientTest {

    private final SlackClient slackClient = new SlackClient();

    @Test
    void formatStandupMessage_includesDeveloperUsernameAndDate() {
        String message = slackClient.formatStandupMessage(
                "sumituppal03", "2026-07-19", "* Fixed the auth bug\n* Added rate limiting");

        assertThat(message).contains("sumituppal03");
        assertThat(message).contains("2026-07-19");
        assertThat(message).contains("Fixed the auth bug");
    }

    @Test
    void formatStandupMessage_includesDevPulsePrefix() {
        String message = slackClient.formatStandupMessage(
                "john", "2026-07-19", "* Did some work");

        assertThat(message).contains("DevPulse");
    }

    @Test
    void postMessage_withBlankWebhookUrl_returnsFalseWithoutThrowing() {
        // Should never throw -- failure is non-fatal by design
        boolean result = slackClient.postMessage("", "test message");
        assertThat(result).isFalse();
    }

    @Test
    void postMessage_withNullWebhookUrl_returnsFalseWithoutThrowing() {
        boolean result = slackClient.postMessage(null, "test message");
        assertThat(result).isFalse();
    }
}
