package com.devpulse.codeqa;

import com.devpulse.codeqa.dto.AskQuestionRequest;
import com.devpulse.codeqa.dto.CodeQaResponse;
import com.devpulse.codeqa.dto.RegisterRepositoryRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CodeQaController {

    private final RepositoryJpaRepository repositoryJpaRepository;
    private final CodeIndexingService codeIndexingService;
    private final CodeQaService codeQaService;

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
                "status", "PENDING",
                "message", "Repository registered. Call POST /api/v1/repos/" + saved.getId() + "/index to start indexing."
        ));
    }

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
                "message", "Indexing started in the background. This may take a few minutes."
        ));
    }

    @GetMapping("/repos/{repositoryId}/index/status")
    public ResponseEntity<Map<String, Object>> indexStatus(
            @PathVariable UUID repositoryId) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        Repository repo = repositoryJpaRepository
                .findByTenantIdAndId(tenantId, repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        return ResponseEntity.ok(Map.of(
                "repositoryId", repo.getId(),
                "status", repo.getIndexStatus(),
                "lastIndexedAt", repo.getLastIndexedAt() != null
                        ? repo.getLastIndexedAt().toString() : "never"
        ));
    }

    @PostMapping("/codeqa/ask")
    public ResponseEntity<CodeQaResponse> ask(
            @Valid @RequestBody AskQuestionRequest request) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        repositoryJpaRepository
                .findByTenantIdAndId(tenantId, request.repositoryId())
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        CodeQaResponse response = codeQaService.ask(tenantId, request.question());
        return ResponseEntity.ok(response);
    }
}