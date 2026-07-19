package com.devpulse.standup;

import com.devpulse.codeqa.Repository;
import com.devpulse.codeqa.RepositoryJpaRepository;
import com.devpulse.developer.Developer;
import com.devpulse.developer.DeveloperService;
import com.devpulse.integrations.IntegrationService;
import com.devpulse.shared.ai.LlmCall;
import com.devpulse.shared.ai.LlmCallRepository;
import com.devpulse.shared.github.GitHubClient;
import com.devpulse.shared.github.GitHubCommitResponse;
import com.devpulse.shared.ratelimit.RateLimitExceededException;
import com.devpulse.shared.ratelimit.RateLimiterService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/standup")
@RequiredArgsConstructor
@Slf4j
public class StandupController {

    private final GitHubClient gitHubClient;
    private final StandupSummaryService standupSummaryService;
    private final DeveloperService developerService;
    private final StandupRepository standupRepository;
    private final LlmCallRepository llmCallRepository;
    private final RateLimiterService rateLimiterService;
    private final RepositoryJpaRepository repositoryJpaRepository;
    private final IntegrationService integrationService;

    /**
     * Generates an AI standup for a developer.
     * Automatically fetches commits from ALL repos registered by the tenant.
     * No owner/repo parameters needed -- the system knows which repos belong to this tenant.
     */
    @GetMapping("/generate")
    @Transactional
    public ResponseEntity<StandupResponse> generate(
            @RequestParam UUID developerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        if (!rateLimiterService.isAllowed(tenantId)) {
            throw new RateLimitExceededException("Rate limit exceeded: max 10 standup requests per minute.");
        }

        Developer developer = developerService.getOwnedByTenant(developerId, tenantId);
        LocalDate targetDate = (date != null) ? date : LocalDate.now().minusDays(1);

        List<Repository> tenantRepos = repositoryJpaRepository.findByTenantId(tenantId);

        if (tenantRepos.isEmpty()) {
            return ResponseEntity.ok(new StandupResponse(
                    "No repositories registered yet. Register your company repos at POST /api/v1/repos first.",
                    0, List.of()
            ));
        }

        List<GitHubCommitResponse> todaysCommits =
                gitHubClient.fetchCommitsAcrossRepos(tenantRepos, developer.getGithubUsername(), targetDate);
        List<GitHubCommitResponse> styleSample =
                gitHubClient.fetchRecentCommitsAcrossRepos(tenantRepos, developer.getGithubUsername(), 20);

        StandupGenerationResult result = standupSummaryService.summarize(todaysCommits, styleSample);

        LlmCall llmCall = LlmCall.create(
                tenantId, developerId, "STANDUP", result.modelName(),
                result.promptTokens(), result.completionTokens(), result.latencyMs()
        );
        llmCallRepository.save(llmCall);

        Standup standup = standupRepository.findByDeveloperIdAndStandupDate(developerId, targetDate)
                .map(existing -> {
                    existing.updateContent(result.summary(), todaysCommits.size());
                    return existing;
                })
                .orElseGet(() -> Standup.create(tenantId, developerId, targetDate,
                        result.summary(), todaysCommits.size()));
        standupRepository.save(standup);

        return ResponseEntity.ok(new StandupResponse(
                result.summary(), todaysCommits.size(), todaysCommits));
    }

    /**
     * Developer reviews and finalizes their AI-generated standup.
     * After saving, automatically posts to Slack if configured.
     * Tracks edit distance as a product quality metric.
     */
    @PutMapping("/{standupId}/finalize")
    @Transactional
    public ResponseEntity<Map<String, Object>> finalize(
            @PathVariable UUID standupId,
            @RequestBody Map<String, @NotBlank String> body) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        String finalContent = body.get("content");
        if (finalContent == null || finalContent.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
        }

        Standup standup = standupRepository.findById(standupId)
                .orElseThrow(() -> new IllegalArgumentException("Standup not found"));

        if (!standup.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Standup not found");
        }

        int editDistance = computeLevenshteinApprox(standup.getGeneratedContent(), finalContent);
        standup.setFinalContent(finalContent);
        standup.setEditDistance(editDistance);
        standupRepository.save(standup);

        double editPercent = standup.getGeneratedContent().isEmpty() ? 0 :
                (editDistance * 100.0) / standup.getGeneratedContent().length();

        // Post to Slack automatically after finalize
        // Failure is non-fatal -- standup is saved regardless of Slack outcome
        boolean postedToSlack = false;
        try {
            Developer developer = developerService.getOwnedByTenant(
                    standup.getDeveloperId(), tenantId);
            postedToSlack = integrationService.postStandupToSlack(
                    tenantId,
                    developer.getGithubUsername(),
                    standup.getStandupDate().toString(),
                    finalContent
            );
            if (postedToSlack) {
                log.info("Standup {} posted to Slack for tenant {}", standupId, tenantId);
            }
        } catch (Exception e) {
            log.warn("Slack post failed for standup {} - standup still saved: {}",
                    standupId, e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "standupId", standupId,
                "editDistance", editDistance,
                "editPercent", Math.round(editPercent),
                "postedToSlack", postedToSlack,
                "message", postedToSlack
                        ? "Standup saved and posted to Slack!"
                        : editPercent < 20
                        ? "Standup saved. AI was accurate (under 20% edited)."
                        : "Standup saved."
        ));
    }

    /**
     * Returns standup history for a developer.
     * Default: last 7 days.
     */
    @GetMapping("/history")
    public ResponseEntity<List<StandupHistoryResponse>> history(
            @RequestParam UUID developerId,
            @RequestParam(defaultValue = "7") int days) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        developerService.getOwnedByTenant(developerId, tenantId);

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);

        List<Standup> standups = standupRepository
                .findByDeveloperIdAndStandupDateBetweenOrderByStandupDateDesc(
                        developerId, from, to);

        List<StandupHistoryResponse> response = standups.stream()
                .map(s -> new StandupHistoryResponse(
                        s.getId(),
                        s.getStandupDate(),
                        s.getFinalContent() != null ? s.getFinalContent() : s.getGeneratedContent(),
                        s.getFinalContent() != null,
                        s.getEditDistance(),
                        s.getCommitsUsed()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    private int computeLevenshteinApprox(String original, String edited) {
        int changes = 0;
        int minLen = Math.min(original.length(), edited.length());
        for (int i = 0; i < minLen; i++) {
            if (original.charAt(i) != edited.charAt(i)) changes++;
        }
        changes += Math.abs(original.length() - edited.length());
        return changes;
    }
}
