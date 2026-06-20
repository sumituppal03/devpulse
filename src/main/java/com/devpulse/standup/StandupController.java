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

    /**
     * Skeleton version — returns raw commits, no AI summary yet.
     * That comes next, once we've proven this connection actually works.
     */
    @GetMapping("/api/v1/standup/generate")
    public List<GitHubCommitResponse> generate(
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam String username,
            @RequestParam(required = false) String date) {

        LocalDate targetDate = (date != null) ? LocalDate.parse(date) : LocalDate.now().minusDays(1);
        return gitHubClient.fetchCommitsForDate(owner, repo, username, targetDate);
    }
}