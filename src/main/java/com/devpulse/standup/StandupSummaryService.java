package com.devpulse.standup;

import com.devpulse.shared.github.GitHubCommitResponse;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StandupSummaryService {

    private final ChatModel chatModel;

    /**
     * Turns raw commits into a short standup summary. If there are no
     * commits, we say so directly instead of asking the LLM to invent
     * activity — the same no-hallucination guardrail from the original plan.
     */
    public String summarize(List<GitHubCommitResponse> commits) {
        if (commits.isEmpty()) {
            return "No commits found for this date.";
        }

        String commitList = commits.stream()
                .map(c -> "- " + c.commit().message())
                .collect(Collectors.joining("\n"));

        String prompt = """
                You are writing a daily standup update for a developer based on
                their actual GitHub commits. Write exactly 3 short bullet points
                summarizing what they worked on.

                Rules:
                - Each bullet starts with a past-tense verb (Fixed, Added, Updated, Refactored...)
                - Be specific about what changed — do not be vague
                - Do NOT invent any work that is not reflected in the commits below
                - Do NOT mention commit hashes

                Commits:
                %s
                """.formatted(commitList);

        return chatModel.chat(prompt);
    }
}