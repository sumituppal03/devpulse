package com.devpulse.prcontext;

import com.devpulse.shared.ai.ActiveModelInfo;
import com.devpulse.shared.ai.LlmCallRepository;
import com.devpulse.shared.github.GitHubClient;
import com.devpulse.shared.github.GitHubCommentResponse;
import com.devpulse.shared.github.GitHubPullRequestFile;
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
 * (instead of fetching it through Spring's context) bypasses the async proxy
 * entirely — it just runs synchronously, which is exactly what a unit test wants.
 */
@ExtendWith(MockitoExtension.class)
class PrContextEnricherServiceTest {

    @Mock private GitHubClient gitHubClient;
    @Mock private ChatModel chatModel;
    @Mock private LlmCallRepository llmCallRepository;
    @Mock private PrEnrichmentRepository prEnrichmentRepository;
    @Mock private WebhookEventRepository webhookEventRepository;

    @Test
    void enrich_whenAlreadyEnriched_skipsProcessing_neverCallsLlmOrGitHubAgain() {
        PrContextEnricherService service = new PrContextEnricherService(
                gitHubClient, chatModel, new ActiveModelInfo("llama-3.3-70b-versatile"),
                llmCallRepository, prEnrichmentRepository, webhookEventRepository);

        UUID webhookEventId = UUID.randomUUID();
        WebhookEvent existingEvent = WebhookEvent.create("GITHUB", "pull_request", "{}");

        when(prEnrichmentRepository.existsByGithubOwnerAndGithubRepoAndPrNumber("sumituppal03", "devpulse", 7))
                .thenReturn(true);
        when(webhookEventRepository.findById(webhookEventId)).thenReturn(Optional.of(existingEvent));

        service.enrich(webhookEventId, "sumituppal03", "devpulse", 7, "Some PR", "Some body");

        // The idempotency guarantee — proves a duplicate webhook delivery
        // (GitHub retries these) never double-posts or double-calls the LLM.
        verify(gitHubClient, never()).fetchPullRequestFiles(any(), any(), anyInt());
        verify(chatModel, never()).chat(any(ChatMessage.class), any(ChatMessage.class));

        assertThat(existingEvent.isProcessed()).isTrue();
        assertThat(existingEvent.getErrorMessage()).isNull();
    }

    @Test
    void enrich_withNewPr_fetchesDiff_callsLlm_postsComment_andPersistsEverything() {
        PrContextEnricherService service = new PrContextEnricherService(
                gitHubClient, chatModel, new ActiveModelInfo("llama-3.3-70b-versatile"),
                llmCallRepository, prEnrichmentRepository, webhookEventRepository);

        UUID webhookEventId = UUID.randomUUID();
        WebhookEvent existingEvent = WebhookEvent.create("GITHUB", "pull_request", "{}");

        when(prEnrichmentRepository.existsByGithubOwnerAndGithubRepoAndPrNumber("sumituppal03", "devpulse", 9))
                .thenReturn(false);
        when(gitHubClient.fetchPullRequestFiles("sumituppal03", "devpulse", 9))
                .thenReturn(List.of(new GitHubPullRequestFile("README.md", "modified", 5, 1, "+added a line")));

        ChatResponse fakeResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("This PR updates the README with deployment instructions."))
                .tokenUsage(new TokenUsage(150, 25))
                .build();
        when(chatModel.chat(any(ChatMessage.class), any(ChatMessage.class))).thenReturn(fakeResponse);

        when(gitHubClient.postIssueComment(eq("sumituppal03"), eq("devpulse"), eq(9), anyString()))
                .thenReturn(new GitHubCommentResponse(99999L, "https://github.com/x/y/pull/9#comment-99999"));

        when(webhookEventRepository.findById(webhookEventId)).thenReturn(Optional.of(existingEvent));

        service.enrich(webhookEventId, "sumituppal03", "devpulse", 9, "Update README", "Adds deployment docs");

        verify(gitHubClient).postIssueComment(eq("sumituppal03"), eq("devpulse"), eq(9), contains("DevPulse Context"));
        verify(prEnrichmentRepository).save(any(PrEnrichment.class));
        verify(llmCallRepository).save(any());

        assertThat(existingEvent.isProcessed()).isTrue();
        assertThat(existingEvent.getErrorMessage()).isNull();
    }

    @Test
    void enrich_whenGitHubApiFails_marksEventWithErrorMessage_insteadOfCrashingSilently() {
        PrContextEnricherService service = new PrContextEnricherService(
                gitHubClient, chatModel, new ActiveModelInfo("llama-3.3-70b-versatile"),
                llmCallRepository, prEnrichmentRepository, webhookEventRepository);

        UUID webhookEventId = UUID.randomUUID();
        WebhookEvent existingEvent = WebhookEvent.create("GITHUB", "pull_request", "{}");

        when(prEnrichmentRepository.existsByGithubOwnerAndGithubRepoAndPrNumber(any(), any(), anyInt()))
                .thenReturn(false);
        when(gitHubClient.fetchPullRequestFiles(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("GitHub API unavailable"));
        when(webhookEventRepository.findById(webhookEventId)).thenReturn(Optional.of(existingEvent));

        service.enrich(webhookEventId, "sumituppal03", "devpulse", 11, "Broken PR", "body");

        assertThat(existingEvent.isProcessed()).isFalse();
        assertThat(existingEvent.getErrorMessage()).contains("GitHub API unavailable");

        // Confirms the failure happened BEFORE anything was posted or persisted —
        // a partial, half-completed enrichment never gets left behind.
        verify(gitHubClient, never()).postIssueComment(any(), any(), anyInt(), any());
        verify(prEnrichmentRepository, never()).save(any());
    }
}