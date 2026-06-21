package com.devpulse.standup;

import com.devpulse.developer.Developer;
import com.devpulse.developer.DeveloperService;
import com.devpulse.shared.ai.LlmCall;
import com.devpulse.shared.ai.LlmCallRepository;
import com.devpulse.shared.github.GitHubClient;
import com.devpulse.shared.github.GitHubCommitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
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
    private final StandupRepository standupRepository;
    private final LlmCallRepository llmCallRepository;

    @GetMapping("/api/v1/standup/generate")
    @Transactional
    public StandupResponse generate(
            @RequestParam UUID developerId,
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam(required = false) String date) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Developer developer = developerService.getOwnedByTenant(developerId, tenantId);

        LocalDate targetDate = (date != null) ? LocalDate.parse(date) : LocalDate.now().minusDays(1);
        List<GitHubCommitResponse> todaysCommits =
                gitHubClient.fetchCommitsForDate(owner, repo, developer.getGithubUsername(), targetDate);
        List<GitHubCommitResponse> styleSample =
                gitHubClient.fetchRecentCommits(owner, repo, developer.getGithubUsername(), 20);

        StandupGenerationResult result = standupSummaryService.summarize(todaysCommits, styleSample);

        // Audit log — every LLM call recorded, regardless of which feature triggered it
        LlmCall llmCall = LlmCall.create(
                tenantId, developerId, "STANDUP", result.modelName(),
                result.promptTokens(), result.completionTokens(), result.latencyMs()
        );
        llmCallRepository.save(llmCall);

        // Upsert: regenerating for the same day updates the existing draft instead of duplicating
        Standup standup = standupRepository.findByDeveloperIdAndStandupDate(developerId, targetDate)
                .map(existing -> {
                    existing.updateContent(result.summary(), todaysCommits.size());
                    return existing;
                })
                .orElseGet(() -> Standup.create(tenantId, developerId, targetDate, result.summary(), todaysCommits.size()));
        standupRepository.save(standup);

        return new StandupResponse(result.summary(), todaysCommits.size(), todaysCommits);
    }
}