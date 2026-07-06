package com.devpulse.codeqa;

import com.devpulse.shared.github.GitHubClient;
import com.devpulse.shared.github.GitHubFileContent;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeIndexingService {

    private static final int BATCH_SIZE = 10;
    private static final int MAX_CHUNK_CHARS = 1500;
    private static final Set<String> INDEXABLE_EXTENSIONS =
            Set.of(".java", ".kt", ".md", ".yml", ".yaml", ".properties");

    private final GitHubClient gitHubClient;
    private final EmbeddingModel embeddingModel;
    private final CodeChunkRepository codeChunkRepository;
    private final RepositoryJpaRepository repositoryJpaRepository;
    private final PlatformTransactionManager transactionManager;

    // Each unit of work (status flip, each batch save) commits in its own short
    // transaction via this template, instead of wrapping the entire multi-minute
    // GitHub-fetch + embedding job in one @Transactional method. Previously, a
    // failure at the very end (e.g. one bad insert) rolled back everything —
    // including chunks that had already been successfully embedded and "saved"
    // minutes earlier — because nothing actually committed until the whole
    // method returned. Now each batch is durable the moment it's written, and
    // status changes (INDEXING/READY/FAILED) are visible to callers immediately.
    @Async
    public void indexRepository(Repository repository) {
        log.info("Starting indexing for {}/{}", repository.getGithubOwner(), repository.getGithubRepo());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            repository.setIndexStatus("INDEXING");
            repositoryJpaRepository.save(repository);
            codeChunkRepository.deleteByRepositoryId(repository.getId());
        });

        try {
            List<GitHubFileContent> files = gitHubClient.fetchRepositoryFiles(
                    repository.getGithubOwner(),
                    repository.getGithubRepo(),
                    repository.getDefaultBranch()
            );

            log.info("Fetched {} indexable files for {}/{}",
                    files.size(), repository.getGithubOwner(), repository.getGithubRepo());

            List<CodeChunk> batch = new ArrayList<>();
            int totalChunks = 0;

            for (GitHubFileContent file : files) {
                if (file.content() == null || file.content().isBlank()) continue;

                String decoded = decodeBase64Content(file.content());
                if (decoded.isBlank()) continue;

                List<String> chunks = chunkContent(decoded, file.path());

                for (String chunkText : chunks) {
                    try {
                        Embedding embedding = embeddingModel.embed(chunkText).content();
                        String vectorString = toVectorString(embedding.vector());

                        CodeChunk chunk = CodeChunk.create(
                                repository.getTenantId(),
                                repository.getId(),
                                inferSourceType(file.path()),
                                file.path(),
                                null, null, null,
                                chunkText,
                                vectorString
                        );
                        batch.add(chunk);

                        if (batch.size() >= BATCH_SIZE) {
                            List<CodeChunk> toSave = List.copyOf(batch);
                            tx.executeWithoutResult(status -> codeChunkRepository.saveAll(toSave));
                            totalChunks += batch.size();
                            batch.clear();
                            log.info("Saved {} chunks so far for {}/{}",
                                    totalChunks, repository.getGithubOwner(), repository.getGithubRepo());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to embed chunk from {}: {}", file.path(), e.getMessage());
                    }
                }
            }

            if (!batch.isEmpty()) {
                List<CodeChunk> toSave = List.copyOf(batch);
                tx.executeWithoutResult(status -> codeChunkRepository.saveAll(toSave));
                totalChunks += batch.size();
            }

            int finalTotalChunks = totalChunks;
            tx.executeWithoutResult(status -> {
                repository.setIndexStatus("READY");
                repository.setLastIndexedAt(Instant.now());
                repositoryJpaRepository.save(repository);
            });

            log.info("Indexing complete for {}/{} — {} total chunks",
                    repository.getGithubOwner(), repository.getGithubRepo(), finalTotalChunks);

        } catch (Exception e) {
            log.error("Indexing failed for {}/{}", repository.getGithubOwner(), repository.getGithubRepo(), e);
            tx.executeWithoutResult(status -> {
                repository.setIndexStatus("FAILED");
                repositoryJpaRepository.save(repository);
            });
        }
    }

    private String decodeBase64Content(String base64Content) {
        try {
            String cleaned = base64Content.replaceAll("\\s", "");
            return new String(Base64.getDecoder().decode(cleaned));
        } catch (Exception e) {
            log.warn("Failed to decode base64: {}", e.getMessage());
            return "";
        }
    }

    private List<String> chunkContent(String content, String filePath) {
        List<String> chunks = new ArrayList<>();
        String lower = filePath.toLowerCase();

        if (lower.endsWith(".md") || lower.endsWith(".yml")
                || lower.endsWith(".yaml") || lower.endsWith(".properties")) {
            if (content.length() <= MAX_CHUNK_CHARS) {
                chunks.add(content);
            } else {
                for (int i = 0; i < content.length(); i += MAX_CHUNK_CHARS) {
                    chunks.add(content.substring(i, Math.min(i + MAX_CHUNK_CHARS, content.length())));
                }
            }
        } else {
            String[] lines = content.split("\n");
            StringBuilder current = new StringBuilder();
            for (String line : lines) {
                if (current.length() + line.length() > MAX_CHUNK_CHARS && !current.isEmpty()) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder();
                }
                current.append(line).append("\n");
            }
            if (!current.isEmpty()) {
                chunks.add(current.toString().trim());
            }
        }

        return chunks.isEmpty() ? List.of(content) : chunks;
    }

    private String inferSourceType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".kt")) return "kotlin";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "yaml";
        return "config";
    }

    /**
     * Converts float[] to pgvector string format: "[0.12345,-0.67891,...]"
     * Hibernate stores this as TEXT; PostgreSQL casts it to vector at query time.
     */
    private String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}