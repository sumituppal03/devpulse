package com.devpulse.standup;

import com.devpulse.codeqa.Repository;
import com.devpulse.codeqa.RepositoryJpaRepository;
import com.devpulse.developer.Developer;
import com.devpulse.developer.DeveloperService;
import com.devpulse.shared.ai.LlmCall;
import com.devpulse.shared.ai.LlmCallRepository;
import com.devpulse.shared.github.GitHubClient;
import com.devpulse.shared.github.GitHubCommitResponse;
import com.devpulse.shared.ratelimit.RateLimitExceededException;
import com.devpulse.shared.ratelimit.RateLimiterService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
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
public class StandupController {

    private final GitHubClient gitHubClient;
    private final StandupSummaryService standupSummaryService;
    private final DeveloperService developerService;
    private final StandupRepository standupRepository;
    private final LlmCallRepository llmCallRepository;
    private final RateLimiterService rateLimiterService;
    private final RepositoryJpaRepository repositoryJpaRepository;

    /**
     * Generates an AI standup for a developer.
     *
     * No owner/repo parameters — the system automatically fetches commits
     * from ALL repos registered by the tenant. This is the correct design:
     * a developer may commit to multiple repos in one day, and the standup
     * should cover all of them without the caller knowing which repos exist.
     */
    @GetMapping("/generate")
    @Transactional
    public ResponseEntity<StandupResponse> generate(
            @RequestParam UUID developerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!rateLimiterService.isAllowed(tenantId)) {
            throw new RateLimitExceededException("Rate limit exceeded: max 10 standup requests per minute.");
        }

        Developer developer = developerService.getOwnedByTenant(developerId, tenantId);
        LocalDate targetDate = (date != null) ? date : LocalDate.now().minusDays(1);

        // Fetch ALL repos belonging to this tenant
        List<Repository> tenantRepos = repositoryJpaRepository.findByTenantId(tenantId);

        List<GitHubCommitResponse> todaysCommits;
        List<GitHubCommitResponse> styleSample;

        if (tenantRepos.isEmpty()) {
            // Fallback: no repos registered yet — return a helpful message
            return ResponseEntity.ok(new StandupResponse(
                    "No repositories registered yet. Register your company's GitHub repos at POST /api/v1/repos first.",
                    0, List.of()
            ));
        }

        // Fetch commits across ALL tenant repos for this developer
        todaysCommits = gitHubClient.fetchCommitsAcrossRepos(tenantRepos, developer.getGithubUsername(), targetDate);
        styleSample = gitHubClient.fetchRecentCommitsAcrossRepos(tenantRepos, developer.getGithubUsername(), 20);

        StandupGenerationResult result = standupSummaryService.summarize(todaysCommits, styleSample);

        // Audit log
        LlmCall llmCall = LlmCall.create(
                tenantId, developerId, "STANDUP", result.modelName(),
                result.promptTokens(), result.completionTokens(), result.latencyMs()
        );
        llmCallRepository.save(llmCall);

        // Upsert — regenerating for the same day updates rather than duplicates
        Standup standup = standupRepository.findByDeveloperIdAndStandupDate(developerId, targetDate)
                .map(existing -> {
                    existing.updateContent(result.summary(), todaysCommits.size());
                    return existing;
                })
                .orElseGet(() -> Standup.create(tenantId, developerId, targetDate, result.summary(), todaysCommits.size()));
        standupRepository.save(standup);

        return ResponseEntity.ok(new StandupResponse(result.summary(), todaysCommits.size(), todaysCommits));
    }

    /**
     * Developer reviews and finalizes their AI-generated standup.
     * Saves the edited version and tracks how much was changed (edit distance)
     * — this is the real product quality metric: if developers edit <20% of the
     * text, the AI is genuinely useful. If they edit >80%, the prompts need work.
     */
    @PutMapping("/{standupId}/finalize")
    @Transactional
    public ResponseEntity<Map<String, Object>> finalize(
            @PathVariable UUID standupId,
            @RequestBody Map<String, @NotBlank String> body) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String finalContent = body.get("content");

        if (finalContent == null || finalContent.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
        }

        Standup standup = standupRepository.findById(standupId)
                .orElseThrow(() -> new IllegalArgumentException("Standup not found"));

        // Security check — only the owning tenant can finalize
        if (!standup.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Standup not found");
        }

        int editDistance = computeLevenshteinApprox(standup.getGeneratedContent(), finalContent);
        standup.setFinalContent(finalContent);
        standup.setEditDistance(editDistance);
        standupRepository.save(standup);

        double editPercent = standup.getGeneratedContent().isEmpty() ? 0 :
                (editDistance * 100.0) / standup.getGeneratedContent().length();

        return ResponseEntity.ok(Map.of(
                "standupId", standupId,
                "editDistance", editDistance,
                "editPercent", Math.round(editPercent),
                "message", editPercent < 20
                        ? "Great — the AI nailed it (under 20% edited)"
                        : editPercent < 50
                        ? "Minor edits — AI was mostly correct"
                        : "Significant edits — feedback noted for improvement"
        ));
    }

    /**
     * Returns standup history for a developer.
     * Default: last 7 days. Frontend dashboard calls this to show the history panel.
     */
    @GetMapping("/history")
    public ResponseEntity<List<StandupHistoryResponse>> history(
            @RequestParam UUID developerId,
            @RequestParam(defaultValue = "7") int days) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        developerService.getOwnedByTenant(developerId, tenantId);

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);

        List<Standup> standups = standupRepository
                .findByDeveloperIdAndStandupDateBetweenOrderByStandupDateDesc(developerId, from, to);

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

    /**
     * Approximate edit distance using character-level diff.
     * Not true Levenshtein (too expensive for long strings) but gives
     * a meaningful percentage for the quality metric we care about.
     */
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
