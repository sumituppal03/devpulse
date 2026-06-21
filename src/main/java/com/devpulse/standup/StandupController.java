package com.devpulse.standup;

import com.devpulse.developer.Developer;
import com.devpulse.developer.DeveloperService;
import com.devpulse.shared.github.GitHubClient;
import com.devpulse.shared.github.GitHubCommitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class StandupController {

    private final GitHubClient gitHubClient;
    private final StandupSummaryService standupSummaryService;
    private final DeveloperService developerService;

    @GetMapping("/api/v1/standup/generate")
    public StandupResponse generate(
            @RequestParam UUID developerId,
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam(required = false) String date) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // The actual security boundary: throws → 404 if this developer
        // doesn't exist OR belongs to a different tenant.
        Developer developer = developerService.getOwnedByTenant(developerId, tenantId);

        LocalDate targetDate = (date != null) ? LocalDate.parse(date) : LocalDate.now().minusDays(1);
        List<GitHubCommitResponse> todaysCommits =
                gitHubClient.fetchCommitsForDate(owner, repo, developer.getGithubUsername(), targetDate);
        List<GitHubCommitResponse> styleSample =
                gitHubClient.fetchRecentCommits(owner, repo, developer.getGithubUsername(), 20);

        String summary = standupSummaryService.summarize(todaysCommits, styleSample);

        return new StandupResponse(summary, todaysCommits.size(), todaysCommits);
    }
}