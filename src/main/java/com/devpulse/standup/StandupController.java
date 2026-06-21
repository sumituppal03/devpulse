package com.devpulse.standup;

import com.devpulse.shared.github.GitHubClient;
import com.devpulse.shared.github.GitHubCommitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class StandupController {

    private final GitHubClient gitHubClient;
    private final StandupSummaryService standupSummaryService;

    @GetMapping("/api/v1/standup/generate")
    public StandupResponse generate(
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam String username,
            @RequestParam(required = false) String date) {

        LocalDate targetDate = (date != null) ? LocalDate.parse(date) : LocalDate.now().minusDays(1);
        List<GitHubCommitResponse> todaysCommits = gitHubClient.fetchCommitsForDate(owner, repo, username, targetDate);
        List<GitHubCommitResponse> styleSample = gitHubClient.fetchRecentCommits(owner, repo, username, 20);

        String summary = standupSummaryService.summarize(todaysCommits, styleSample);

        return new StandupResponse(summary, todaysCommits.size(), todaysCommits);
    }
}