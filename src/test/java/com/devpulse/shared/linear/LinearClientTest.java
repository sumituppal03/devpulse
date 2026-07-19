package com.devpulse.shared.linear;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LinearClient.
 *
 * fetchTicketFromBranchName() with a real API key makes a live GraphQL call,
 * so we test the branch name parsing logic by passing a blank API key
 * (which causes an early empty return before any HTTP call is made).
 * The ticket fetching itself is tested manually via the configured integration.
 */
class LinearClientTest {

    private final LinearClient linearClient = new LinearClient();

    @Test
    void fetchTicketFromBranchName_withBlankApiKey_returnsEmpty() {
        Optional<LinearClient.LinearTicket> result =
                linearClient.fetchTicketFromBranchName("feature/LIN-234-auth", "");

        assertThat(result).isEmpty();
    }

    @Test
    void fetchTicketFromBranchName_withNullBranchName_returnsEmpty() {
        Optional<LinearClient.LinearTicket> result =
                linearClient.fetchTicketFromBranchName(null, "lin_api_somekey");

        assertThat(result).isEmpty();
    }

    @Test
    void fetchTicketFromBranchName_withBranchHavingNoTicketId_returnsEmpty() {
        // Branch name "main" has no ticket ID pattern like LIN-234
        Optional<LinearClient.LinearTicket> result =
                linearClient.fetchTicketFromBranchName("main", "lin_api_somekey");

        assertThat(result).isEmpty();
    }

    @Test
    void fetchTicketFromBranchName_withNullApiKey_returnsEmpty() {
        Optional<LinearClient.LinearTicket> result =
                linearClient.fetchTicketFromBranchName("feature/LIN-99-fix", null);

        assertThat(result).isEmpty();
    }
}
