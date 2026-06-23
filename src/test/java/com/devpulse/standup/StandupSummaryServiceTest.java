package com.devpulse.standup;

import com.devpulse.shared.ai.ActiveModelInfo;
import com.devpulse.shared.github.GitHubCommitResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StandupSummaryServiceTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void summarize_withNoCommits_returnsNoActivityMessage_withoutCallingTheLlm() {
        StandupSummaryService service = new StandupSummaryService(chatModel, new ActiveModelInfo("llama3.2"));

        StandupGenerationResult result = service.summarize(List.of(), List.of());

        assertThat(result.summary()).isEqualTo("No commits found for this date.");

        // The most important line in this test: proves the no-hallucination
        // guardrail isn't just a comment in the code — the LLM genuinely
        // never gets called when there's nothing real to summarize.
        verify(chatModel, never()).chat(any(ChatMessage.class), any(ChatMessage.class));
    }

    @Test
    void summarize_withRealCommits_returnsAiSummary_andCapturesAccurateMetadata() {
        StandupSummaryService service =
                new StandupSummaryService(chatModel, new ActiveModelInfo("llama-3.3-70b-versatile"));

        ChatResponse fakeResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("* Fixed the auth bug\n* Added the new migration\n* Updated tests"))
                .tokenUsage(new TokenUsage(120, 30))
                .build();

        when(chatModel.chat(any(ChatMessage.class), any(ChatMessage.class))).thenReturn(fakeResponse);

        GitHubCommitResponse commit = new GitHubCommitResponse(
                "abc123",
                new GitHubCommitResponse.CommitDetail(
                        "Fixed the auth bug",
                        new GitHubCommitResponse.CommitAuthor("Sumit", "2026-06-19T10:00:00Z")
                )
        );

        StandupGenerationResult result = service.summarize(List.of(commit), List.of(commit));

        assertThat(result.summary()).contains("Fixed the auth bug");
        assertThat(result.modelName()).isEqualTo("llama-3.3-70b-versatile");
        assertThat(result.promptTokens()).isEqualTo(120);
        assertThat(result.completionTokens()).isEqualTo(30);
    }
}