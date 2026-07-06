package com.devpulse.codeqa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import com.devpulse.shared.persistence.PgVectorType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "code_chunks")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeChunk {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "file_path", nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "class_name")
    private String className;

    @Column(name = "method_name")
    private String methodName;

    @Column(name = "heading")
    private String heading;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "commit_sha", length = 40)
    private String commitSha;

    /**
     * Stored as pgvector string format "[0.1,0.2,...]".
     * PgVectorType binds this as Types.OTHER (an "unknown"-typed JDBC parameter)
     * so Postgres infers the vector(768) column type at insert time, instead of
     * rejecting a plain VARCHAR bind. See PgVectorType's javadoc for details.
     */
    @Type(PgVectorType.class)
    @Column(name = "embedding", columnDefinition = "vector(768)")
    private String embedding;

    @Column(name = "embedding_model", nullable = false)
    private String embeddingModel = "nomic-embed-text:v1.5";

    @Column(name = "indexed_at", nullable = false, updatable = false)
    private Instant indexedAt;

    public static CodeChunk create(UUID tenantId, UUID repositoryId, String sourceType,
                                    String filePath, String className, String methodName,
                                    String heading, String content, String embedding) {
        CodeChunk chunk = new CodeChunk();
        chunk.tenantId = tenantId;
        chunk.repositoryId = repositoryId;
        chunk.sourceType = sourceType;
        chunk.filePath = filePath;
        chunk.className = className;
        chunk.methodName = methodName;
        chunk.heading = heading;
        chunk.content = content;
        chunk.embedding = embedding;
        chunk.indexedAt = Instant.now();
        return chunk;
    }
}