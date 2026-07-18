package com.devpulse.codeqa;

import com.devpulse.codeqa.dto.AskQuestionRequest;
import com.devpulse.codeqa.dto.CodeQaResponse;
import com.devpulse.codeqa.dto.RegisterRepositoryRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CodeQaController {

    private final RepositoryJpaRepository repositoryJpaRepository;
    private final CodeChunkRepository codeChunkRepository;
    private final CodeIndexingService codeIndexingService;
    private final CodeQaService codeQaService;

    /**
     * Registers a GitHub repository under the authenticated tenant.
     * After registration, trigger indexing to make it searchable.
     */
    @PostMapping("/repos")
    public ResponseEntity<Map<String, Object>> registerRepository(
            @Valid @RequestBody RegisterRepositoryRequest request) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        Repository repo = Repository.create(
                tenantId,
                request.githubOwner(),
                request.githubRepo(),
                request.defaultBranch()
        );
        Repository saved = repositoryJpaRepository.save(repo);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "repositoryId", saved.getId(),
                "githubOwner", saved.getGithubOwner(),
                "githubRepo", saved.getGithubRepo(),
                "defaultBranch", saved.getDefaultBranch(),
                "status", "PENDING",
                "message", "Repository registered. Trigger indexing at POST /api/v1/repos/" + saved.getId() + "/index"
        ));
    }

    /**
     * Lists all repositories registered by the authenticated tenant.
     * Frontend dashboard uses this to show the repo list with index status.
     */
    @GetMapping("/repos")
    public ResponseEntity<List<Map<String, Object>>> listRepositories() {

        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        List<Map<String, Object>> repos = repositoryJpaRepository
                .findByTenantId(tenantId)
                .stream()
                .map(repo -> {
                    long chunkCount = codeChunkRepository.countByRepositoryId(repo.getId());
                    return Map.<String, Object>of(
                            "repositoryId", repo.getId(),
                            "githubOwner", repo.getGithubOwner(),
                            "githubRepo", repo.getGithubRepo(),
                            "defaultBranch", repo.getDefaultBranch(),
                            "indexStatus", repo.getIndexStatus(),
                            "chunkCount", chunkCount,
                            "lastIndexedAt", repo.getLastIndexedAt() != null
                                    ? repo.getLastIndexedAt().toString() : "never"
                    );
                })
                .toList();

        return ResponseEntity.ok(repos);
    }

    /**
     * Triggers background indexing for a repository.
     * Returns immediately — check status via GET /repos/{id}/index/status.
     */
    @PostMapping("/repos/{repositoryId}/index")
    public ResponseEntity<Map<String, String>> triggerIndex(
            @PathVariable UUID repositoryId) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        Repository repo = repositoryJpaRepository
                .findByTenantIdAndId(tenantId, repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        codeIndexingService.indexRepository(repo);

        return ResponseEntity.accepted().body(Map.of(
                "status", "INDEXING",
                "message", "Indexing started in the background. Check status at GET /api/v1/repos/" + repositoryId + "/index/status"
        ));
    }

    /**
     * Returns the current index status and chunk count for a repository.
     */
    @GetMapping("/repos/{repositoryId}/index/status")
    public ResponseEntity<Map<String, Object>> indexStatus(
            @PathVariable UUID repositoryId) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        Repository repo = repositoryJpaRepository
                .findByTenantIdAndId(tenantId, repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        long chunkCount = codeChunkRepository.countByRepositoryId(repositoryId);

        return ResponseEntity.ok(Map.of(
                "repositoryId", repo.getId(),
                "githubOwner", repo.getGithubOwner(),
                "githubRepo", repo.getGithubRepo(),
                "indexStatus", repo.getIndexStatus(),
                "chunkCount", chunkCount,
                "lastIndexedAt", repo.getLastIndexedAt() != null
                        ? repo.getLastIndexedAt().toString() : "never"
        ));
    }

    /**
     * Removes a repository and all its indexed chunks.
     * Used when a company removes a repo from DevPulse.
     */
    @DeleteMapping("/repos/{repositoryId}")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteRepository(
            @PathVariable UUID repositoryId) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        Repository repo = repositoryJpaRepository
                .findByTenantIdAndId(tenantId, repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        // Delete chunks first (foreign key), then the repository record
        codeChunkRepository.deleteByRepositoryId(repositoryId);
        repositoryJpaRepository.delete(repo);

        return ResponseEntity.ok(Map.of(
                "message", "Repository and all indexed chunks deleted successfully"
        ));
    }

    /**
     * Answers a natural-language question about a specific repository.
     *
     * The repositoryId is now correctly passed to the similarity search —
     * previously it was validated but then ignored, meaning results could
     * come from the wrong repo if a tenant had multiple repos registered.
     */
    @PostMapping("/codeqa/ask")
    public ResponseEntity<CodeQaResponse> ask(
            @Valid @RequestBody AskQuestionRequest request) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        // Verify the repo belongs to this tenant before doing any AI work
        repositoryJpaRepository
                .findByTenantIdAndId(tenantId, request.repositoryId())
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        // Pass repositoryId to the service so search is scoped correctly
        CodeQaResponse response = codeQaService.ask(
                tenantId,
                request.repositoryId(),
                request.question()
        );

        return ResponseEntity.ok(response);
    }
}
