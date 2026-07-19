package com.devpulse.prcontext;

import com.devpulse.integrations.IntegrationService;
import com.devpulse.shared.ai.ActiveModelInfo;
import com.devpulse.shared.ai.LlmCall;
import com.devpulse.shared.ai.LlmCallRepository;
import com.devpulse.shared.github.GitHubClient;
import com.devpulse.shared.github.GitHubCommentResponse;
import com.devpulse.shared.github.GitHubPullRequestFile;
import com.devpulse.shared.linear.LinearClient;
import com.devpulse.shared.webhook.WebhookEventRepository;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrContextEnricherService {

    private static final int MAX_FILES_IN_PROMPT = 5;
    private static final int MAX_PATCH_CHARS_PER_FILE = 800;

    private final GitHubClient gitHubClient;
    private final ChatModel chatModel;
    private final ActiveModelInfo activeModelInfo;
    private final LlmCallRepository llmCallRepository;
    private final PrEnrichmentRepository prEnrichmentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final LinearClient linearClient;
    private final IntegrationService integrationService;

    @Async
    @Transactional
    public void enrich(UUID webhookEventId, String owner, String repo,
                       int prNumber, String prTitle, String prBody,
                       String branchName) {
        try {
            if (prEnrichmentRepository.existsByGithubOwnerAndGithubRepoAndPrNumber(
                    owner, repo, prNumber)) {
                log.info("PR #{} on {}/{} already enriched - skipping duplicate",
                        prNumber, owner, repo);
                markProcessed(webhookEventId, null);
                return;
            }

            List<GitHubPullRequestFile> files =
                    gitHubClient.fetchPullRequestFiles(owner, repo, prNumber);
            String diffSummary = summarizeDiff(files);

            // NOTE: PR enrichment currently has no tenant context (the webhook
            // is repository-level, not tenant-level). For Linear integration,
            // we look up the integration by matching the repo owner to a tenant.
            // This is a known limitation documented in ARCHITECTURE.md.
            // The correct fix is GitHub App installation model (future work).
            // For now: if no tenant Linear key is found, we proceed without it.
            Optional<LinearClient.LinearTicket> ticket = Optional.empty();
            if (branchName != null) {
                // Try to find a tenant that owns this repo and has Linear configured
                // This is best-effort -- fails gracefully if no tenant match found
                ticket = tryFetchLinearTicket(branchName);
            }

            String comment = generateContextComment(
                    prTitle, prBody, diffSummary, ticket, branchName);
            GitHubCommentResponse posted =
                    gitHubClient.postIssueComment(owner, repo, prNumber, comment);

            prEnrichmentRepository.save(
                    PrEnrichment.create(owner, repo, prNumber, comment, posted.id()));

            log.info("Posted context comment on PR #{} ({}/{}) - Linear ticket: {}",
                    prNumber, owner, repo,
                    ticket.map(LinearClient.LinearTicket::id).orElse("none"));
            markProcessed(webhookEventId, null);

        } catch (Exception e) {
            log.error("Failed to enrich PR #{} on {}/{}", prNumber, owner, repo, e);
            markProcessed(webhookEventId, e.getMessage());
        }
    }

    /**
     * Attempts to fetch a Linear ticket using any tenant's configured Linear key.
     * This is the honest best-effort approach given the current architectural limitation
     * that webhook events have no tenant context. Uses the first configured Linear
     * integration found -- works correctly when there is only one tenant (common for
     * early-stage deployments). Full fix requires GitHub App model.
     */
    private Optional<LinearClient.LinearTicket> tryFetchLinearTicket(String branchName) {
        try {
            List<com.devpulse.integrations.TenantIntegration> linearIntegrations =
                    integrationService.getAllLinearIntegrations();

            for (com.devpulse.integrations.TenantIntegration integration : linearIntegrations) {
                String apiKey = integrationService.extractLinearApiKey(integration);
                if (apiKey != null) {
                    Optional<LinearClient.LinearTicket> ticket =
                            linearClient.fetchTicketFromBranchName(branchName, apiKey);
                    if (ticket.isPresent()) return ticket;
                }
            }
        } catch (Exception e) {
            log.debug("Linear ticket fetch failed gracefully: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private String summarizeDiff(List<GitHubPullRequestFile> files) {
        return files.stream()
                .limit(MAX_FILES_IN_PROMPT)
                .map(f -> {
                    String patch = f.patch() != null
                            ? f.patch().substring(0,
                                    Math.min(f.patch().length(), MAX_PATCH_CHARS_PER_FILE))
                            : "(no patch available)";
                    return "File: %s (%s, +%d/-%d)\n%s".formatted(
                            f.filename(), f.status(), f.additions(), f.deletions(), patch);
                })
                .collect(Collectors.joining("\n\n"));
    }

    private String generateContextComment(String prTitle, String prBody,
                                           String diffSummary,
                                           Optional<LinearClient.LinearTicket> ticket,
                                           String branchName) {
        String ticketContext;
        if (ticket.isPresent()) {
            LinearClient.LinearTicket t = ticket.get();
            ticketContext = """
                    Linear Ticket: %s - %s [%s]
                    Ticket Description: %s
                    """.formatted(
                    t.id(), t.title(), t.status(),
                    t.description() != null && !t.description().isBlank()
                            ? t.description()
                            : "(no description on ticket)"
            );
        } else {
            ticketContext = branchName != null
                    ? "No Linear ticket found in branch: " + branchName
                    : "No Linear ticket available.";
        }

        SystemMessage systemMessage = SystemMessage.from("""
                You are an assistant that adds business context to pull requests for code reviewers.
                Given a PR title, description, Linear ticket (if available), and diff, write a
                SHORT comment (max 4 sentences) covering:
                1. The business reason for this change (use Linear ticket if available)
                2. Key technical decisions visible in the diff
                3. What a reviewer should focus on
                Output ONLY the comment. No preamble, no markdown headers.
                """);

        UserMessage userMessage = UserMessage.from("""
                PR Title: %s
                PR Description: %s
                %s
                Diff (up to 5 files):
                %s
                """.formatted(
                prTitle,
                (prBody == null || prBody.isBlank()) ? "(no description)" : prBody,
                ticketContext,
                diffSummary
        ));

        long start = System.currentTimeMillis();
        ChatResponse response = chatModel.chat(systemMessage, userMessage);
        long latencyMs = System.currentTimeMillis() - start;

        TokenUsage tokenUsage = response.tokenUsage();
        llmCallRepository.save(LlmCall.create(
                null, null, "PR_CONTEXT", activeModelInfo.modelName(),
                tokenUsage != null ? tokenUsage.inputTokenCount() : null,
                tokenUsage != null ? tokenUsage.outputTokenCount() : null,
                latencyMs
        ));

        String ticketRef = ticket
                .map(t -> " - " + t.id() + ": " + t.title())
                .orElse("");
        return "DevPulse Context%s\n\n%s".formatted(ticketRef, response.aiMessage().text());
    }

    private void markProcessed(UUID webhookEventId, String errorMessage) {
        webhookEventRepository.findById(webhookEventId).ifPresent(event -> {
            event.setProcessed(errorMessage == null);
            event.setErrorMessage(errorMessage);
            webhookEventRepository.save(event);
        });
    }
}
