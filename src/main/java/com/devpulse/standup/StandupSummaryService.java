package com.devpulse.standup;

import com.devpulse.shared.github.GitHubCommitResponse;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StandupSummaryService {

    private final ChatModel chatModel;

    public String summarize(List<GitHubCommitResponse> todaysCommits, List<GitHubCommitResponse> styleSampleCommits) {
        if (todaysCommits.isEmpty()) {
            return "No commits found for this date.";
        }

        String todaysCommitList = todaysCommits.stream()
                .map(c -> "- " + c.commit().message())
                .collect(Collectors.joining("\n"));

        String styleSample = styleSampleCommits.stream()
                .map(c -> "- " + c.commit().message())
                .limit(15)
                .collect(Collectors.joining("\n"));

        SystemMessage systemMessage = SystemMessage.from("""
                You write daily standup updates for developers based on their GitHub commits.
                Output ONLY 3 bullet points — no introduction, no explanation, no closing remarks,
                nothing before or after the bullets.
                Each bullet starts with a past-tense verb.
                Never invent work that isn't shown in the commits provided.
                Never mention commit hashes.
                Match the vocabulary and tone of the style sample provided.
                """);

        UserMessage userMessage = UserMessage.from("""
                Style sample (this developer's past commit messages):
                %s

                Today's commits to summarize:
                %s
                """.formatted(styleSample, todaysCommitList));

        ChatResponse response = chatModel.chat(systemMessage, userMessage);
        return response.aiMessage().text();
    }
}