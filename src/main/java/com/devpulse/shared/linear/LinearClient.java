package com.devpulse.shared.linear;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches ticket context from Linear using their GraphQL API.
 *
 * Previously used a single global LINEAR_API_KEY environment variable,
 * meaning all tenants shared one Linear workspace. This was wrong for
 * a multi-tenant product -- each company has their own Linear workspace.
 *
 * Now each call passes the tenant's own API key (fetched from the
 * integrations table by IntegrationService.getLinearApiKey(tenantId)).
 * Tenants configure their key via POST /api/v1/integrations/linear.
 */
@Component
@Slf4j
public class LinearClient {

    private static final Pattern TICKET_PATTERN =
            Pattern.compile("(?i)([A-Z][A-Z0-9]+-\\d+)");

    /**
     * Extracts a Linear ticket ID from a branch name and fetches its details.
     * Returns empty if no ticket ID found or if the API call fails.
     *
     * apiKey is the tenant's own Linear personal API key -- each tenant
     * provides their own, so this works across different Linear workspaces.
     */
    public Optional<LinearTicket> fetchTicketFromBranchName(String branchName, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        if (branchName == null) return Optional.empty();

        Matcher matcher = TICKET_PATTERN.matcher(branchName);
        if (!matcher.find()) {
            log.debug("No Linear ticket ID found in branch name: {}", branchName);
            return Optional.empty();
        }

        String ticketId = matcher.group(1).toUpperCase();
        return fetchTicket(ticketId, apiKey);
    }

    private Optional<LinearTicket> fetchTicket(String ticketId, String apiKey) {
        String query = """
                {
                  "query": "query { issue(id: \\"%s\\") { id title description state { name } url } }"
                }
                """.formatted(ticketId);

        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl("https://api.linear.app/graphql")
                    .defaultHeader("Authorization", apiKey)
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build();

            LinearResponse response = restClient.post()
                    .body(query)
                    .retrieve()
                    .body(LinearResponse.class);

            if (response != null && response.data() != null
                    && response.data().issue() != null) {
                LinearIssue issue = response.data().issue();
                log.info("Fetched Linear ticket {} - {}", ticketId, issue.title());
                return Optional.of(new LinearTicket(
                        ticketId,
                        issue.title(),
                        issue.description(),
                        issue.state() != null ? issue.state().name() : "Unknown",
                        issue.url()
                ));
            }

            log.warn("Linear ticket {} not found or no access", ticketId);
            return Optional.empty();

        } catch (RestClientException e) {
            log.warn("Failed to fetch Linear ticket {}: {}", ticketId, e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearResponse(LinearData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearData(LinearIssue issue) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearIssue(
            String id, String title, String description,
            LinearState state, String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearState(String name) {}

    public record LinearTicket(
            String id, String title, String description,
            String status, String url) {}
}
