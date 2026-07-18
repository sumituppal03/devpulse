package com.devpulse.shared.linear;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches ticket context from Linear using their GraphQL API.
 *
 * How it fits into PR enrichment:
 * When a PR is opened with a branch like "feature/LIN-234-auth-refactor",
 * we extract "LIN-234", call this client to get the ticket's title and
 * description, and include that business context in the AI-generated
 * PR comment — so reviewers know WHY the change was made, not just WHAT changed.
 *
 * Linear's API is free for all workspaces. The API key is a personal API key
 * from Linear Settings → API → Personal API keys.
 * Set LINEAR_API_KEY environment variable — empty means Linear integration is
 * disabled but the rest of the PR enrichment still works.
 */
@Component
@Slf4j
public class LinearClient {

    private static final Pattern TICKET_PATTERN =
            Pattern.compile("(?i)([A-Z][A-Z0-9]+-\\d+)");

    private final RestClient restClient;
    private final boolean enabled;

    public LinearClient(@Value("${linear.api-key:}") String apiKey) {
        this.enabled = apiKey != null && !apiKey.isBlank();
        if (this.enabled) {
            this.restClient = RestClient.builder()
                    .baseUrl("https://api.linear.app/graphql")
                    .defaultHeader("Authorization", apiKey)
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build();
            log.info("Linear integration enabled");
        } else {
            this.restClient = null;
            log.info("Linear integration disabled — set LINEAR_API_KEY to enable ticket context in PR comments");
        }
    }

    /**
     * Extracts a Linear ticket ID from a branch name and fetches its details.
     * Returns empty if Linear is not configured or no ticket ID found.
     *
     * Examples of branch names that work:
     *   feature/LIN-234-auth-refactor   → LIN-234
     *   fix/ENG-89-fix-the-thing         → ENG-89
     *   LIN-100-new-feature              → LIN-100
     */
    public Optional<LinearTicket> fetchTicketFromBranchName(String branchName) {
        if (!enabled || branchName == null) return Optional.empty();

        Matcher matcher = TICKET_PATTERN.matcher(branchName);
        if (!matcher.find()) {
            log.debug("No Linear ticket ID found in branch name: {}", branchName);
            return Optional.empty();
        }

        String ticketId = matcher.group(1).toUpperCase();
        return fetchTicket(ticketId);
    }

    private Optional<LinearTicket> fetchTicket(String ticketId) {
        String query = """
                {
                  "query": "query { issue(id: \\"%s\\") { id title description state { name } url } }"
                }
                """.formatted(ticketId);

        try {
            LinearResponse response = restClient.post()
                    .body(query)
                    .retrieve()
                    .body(LinearResponse.class);

            if (response != null && response.data() != null && response.data().issue() != null) {
                LinearIssue issue = response.data().issue();
                log.info("Fetched Linear ticket {} — {}", ticketId, issue.title());
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

    // --- Response shape DTOs ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearResponse(LinearData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearData(LinearIssue issue) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearIssue(String id, String title, String description, LinearState state, String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinearState(String name) {}

    /** The clean result returned to calling code — hides internal API shape. */
    public record LinearTicket(
            String id,
            String title,
            String description,
            String status,
            String url
    ) {}
}
