package com.devpulse.prcontext;

import com.devpulse.shared.ai.ActiveModelInfo;
import com.devpulse.shared.ai.LlmCallRepository;
import com.devpulse.shared.github.GitHubClient;
import com.devpulse.shared.github.GitHubCommentResponse;
import com.devpulse.shared.github.GitHubPullRequestFile;
import com.devpulse.shared.linear.LinearClient;
import com.devpulse.shared.webhook.WebhookEvent;
import com.devpulse.shared.webhook.WebhookEventRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * enrich() is @Async in the real class, but instantiating it directly here
 * bypasses the async proxy entirely — runs synchronously, which is exactly
 * what a unit test wants.
 *
 * LinearClient is mocked — these tests verify the enrichment pipeline works
 * with and without Linear ticket context, without making real API calls.
 */
@ExtendWith(MockitoExtension.class)
class PrContextEnricherServiceTest {

    @Mock private GitHubClient gitHubClient;
    @Mock private ChatModel chatModel;
    @Mock private LlmCallRepository llmCallRepository;
    @Mock private PrEnrichmentRepository prEnrichmentRepository;
    @Mock private WebhookEventRepository webhookEventRepository;
    @Mock private LinearClient linearClient;

    @Test
    void enrich_whenAlreadyEnriched_skipsProcessing_neverCallsLlmOrGitHubAgain() {
        PrContextEnricherService service = new PrContextEnricherService(
                gitHubClient, chatModel, new ActiveModelInfo("llama-3.3-70b-versatile"),
                llmCallRepository, prEnrichmentRepository, webhookEventRepository, linearClient);

        UUID webhookEventId = UUID.randomUUID();
        WebhookEvent existingEvent = WebhookEvent.create("GITHUB", "pull_request", "{}");

        when(prEnrichmentRepository.existsByGithubOwnerAndGithubRepoAndPrNumber(
                "sumituppal03", "devpulse", 7)).thenReturn(true);
        when(webhookEventRepository.findById(webhookEventId))
                .thenReturn(Optional.of(existingEvent));

        // branchName is the new 7th parameter added with Linear support
        service.enrich(webhookEventId, "sumituppal03", "devpulse", 7,
                "Some PR", "Some body", "feature/LIN-7-some-pr");

        // Idempotency guarantee: duplicate webhook never calls LLM or GitHub again
        verify(gitHubClient, never()).fetchPullRequestFiles(any(), any(), anyInt());
        verify(chatModel, never()).chat(any(ChatMessage.class), any(ChatMessage.class));

        assertThat(existingEvent.isProcessed()).isTrue();
        assertThat(existingEvent.getErrorMessage()).isNull();
    }

    @Test
    void enrich_withNewPr_fetchesDiff_callsLlm_postsComment_andPersistsEverything() {
        PrContextEnricherService service = new PrContextEnricherService(
                gitHubClient, chatModel, new ActiveModelInfo("llama-3.3-70b-versatile"),
                llmCallRepository, prEnrichmentRepository, webhookEventRepository, linearClient);

        UUID webhookEventId = UUID.randomUUID();
        WebhookEvent existingEvent = WebhookEvent.create("GITHUB", "pull_request", "{}");

        when(prEnrichmentRepository.existsByGithubOwnerAndGithubRepoAndPrNumber(
                "sumituppal03", "devpulse", 9)).thenReturn(false);
        when(gitHubClient.fetchPullRequestFiles("sumituppal03", "devpulse", 9))
                .thenReturn(List.of(new GitHubPullRequestFile(
                        "README.md", "modified", 5, 1, "+added a line")));

        // LinearClient returns empty — tests that enrichment works without a ticket too
        when(linearClient.fetchTicketFromBranchName(any())).thenReturn(Optional.empty());

        ChatResponse fakeResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("This PR updates the README with deployment instructions."))
                .tokenUsage(new TokenUsage(150, 25))
                .build();
        when(chatModel.chat(any(ChatMessage.class), any(ChatMessage.class)))
                .thenReturn(fakeResponse);
        when(gitHubClient.postIssueComment(
                eq("sumituppal03"), eq("devpulse"), eq(9), anyString()))
                .thenReturn(new GitHubCommentResponse(99999L,
                        "https://github.com/x/y/pull/9#comment-99999"));
        when(webhookEventRepository.findById(webhookEventId))
                .thenReturn(Optional.of(existingEvent));

        service.enrich(webhookEventId, "sumituppal03", "devpulse", 9,
                "Update README", "Adds deployment docs", "main");

        verify(gitHubClient).postIssueComment(
                eq("sumituppal03"), eq("devpulse"), eq(9), contains("DevPulse Context"));
        verify(prEnrichmentRepository).save(any(PrEnrichment.class));
        verify(llmCallRepository).save(any());

        assertThat(existingEvent.isProcessed()).isTrue();
        assertThat(existingEvent.getErrorMessage()).isNull();
    }

    @Test
    void enrich_withLinearTicket_includesTicketContextInComment() {
        PrContextEnricherService service = new PrContextEnricherService(
                gitHubClient, chatModel, new ActiveModelInfo("llama-3.3-70b-versatile"),
                llmCallRepository, prEnrichmentRepository, webhookEventRepository, linearClient);

        UUID webhookEventId = UUID.randomUUID();
        WebhookEvent existingEvent = WebhookEvent.create("GITHUB", "pull_request", "{}");

        when(prEnrichmentRepository.existsByGithubOwnerAndGithubRepoAndPrNumber(
                "sumituppal03", "devpulse", 12)).thenReturn(false);
        when(gitHubClient.fetchPullRequestFiles("sumituppal03", "devpulse", 12))
                .thenReturn(List.of(new GitHubPullRequestFile(
                        "AuthService.java", "modified", 10, 2, "+new auth logic")));

        // LinearClient returns a real ticket this time
        LinearClient.LinearTicket ticket = new LinearClient.LinearTicket(
                "LIN-99", "Add JWT refresh token support",
                "Users are getting logged out too frequently. Need refresh tokens.",
                "In Progress", "https://linear.app/team/issue/LIN-99");
        when(linearClient.fetchTicketFromBranchName("feature/LIN-99-jwt-refresh"))
                .thenReturn(Optional.of(ticket));

        ChatResponse fakeResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("Implements JWT refresh tokens as described in LIN-99."))
                .tokenUsage(new TokenUsage(200, 30))
                .build();
        when(chatModel.chat(any(ChatMessage.class), any(ChatMessage.class)))
                .thenReturn(fakeResponse);
        when(gitHubClient.postIssueComment(
                eq("sumituppal03"), eq("devpulse"), eq(12), anyString()))
                .thenReturn(new GitHubCommentResponse(88888L,
                        "https://github.com/x/y/pull/12#comment-88888"));
        when(webhookEventRepository.findById(webhookEventId))
                .thenReturn(Optional.of(existingEvent));

        service.enrich(webhookEventId, "sumituppal03", "devpulse", 12,
                "Add JWT refresh tokens", "Implements refresh token flow",
                "feature/LIN-99-jwt-refresh");

        // The comment should reference the Linear ticket ID
        verify(gitHubClient).postIssueComment(
                eq("sumituppal03"), eq("devpulse"), eq(12), contains("LIN-99"));
        assertThat(existingEvent.isProcessed()).isTrue();
    }

    @Test
    void enrich_whenGitHubApiFails_marksEventWithErrorMessage_notSilentlyLost() {
        PrContextEnricherService service = new PrContextEnricherService(
                gitHubClient, chatModel, new ActiveModelInfo("llama-3.3-70b-versatile"),
                llmCallRepository, prEnrichmentRepository, webhookEventRepository, linearClient);

        UUID webhookEventId = UUID.randomUUID();
        WebhookEvent existingEvent = WebhookEvent.create("GITHUB", "pull_request", "{}");

        when(prEnrichmentRepository.existsByGithubOwnerAndGithubRepoAndPrNumber(
                any(), any(), anyInt())).thenReturn(false);
        when(gitHubClient.fetchPullRequestFiles(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("GitHub API unavailable"));
        when(webhookEventRepository.findById(webhookEventId))
                .thenReturn(Optional.of(existingEvent));

        service.enrich(webhookEventId, "sumituppal03", "devpulse", 11,
                "Broken PR", "body", "feature/LIN-11-broken");

        assertThat(existingEvent.isProcessed()).isFalse();
        assertThat(existingEvent.getErrorMessage()).contains("GitHub API unavailable");

        // Nothing partial was persisted — failure is clean
        verify(gitHubClient, never()).postIssueComment(any(), any(), anyInt(), any());
        verify(prEnrichmentRepository, never()).save(any());
    }
}
