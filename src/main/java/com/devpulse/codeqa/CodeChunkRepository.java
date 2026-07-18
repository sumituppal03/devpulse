package com.devpulse.codeqa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CodeChunkRepository extends JpaRepository<CodeChunk, UUID> {

    @Modifying
    @Query("DELETE FROM CodeChunk c WHERE c.repositoryId = :repositoryId")
    void deleteByRepositoryId(@Param("repositoryId") UUID repositoryId);

    /**
     * Counts how many chunks exist for a repo — shown in the dashboard
     * so users know "this repo is indexed with 141 chunks".
     */
    long countByRepositoryId(UUID repositoryId);

    /**
     * Top-k cosine similarity search — scoped to BOTH tenant AND repository.
     *
     * Previously only filtered by tenantId, meaning a question about repo A
     * could return chunks from repo B if the tenant had multiple repos.
     * Now filters by repositoryId too — results always come from the repo you asked about.
     *
     * tenantId  — String so PostgreSQL can CAST to uuid in native SQL
     * repositoryId — String for the same reason
     * embedding — pgvector string "[0.1,0.2,...]", CAST to vector for <=> operator
     * topK      — maximum rows to return
     */
    @Query(value = """
            SELECT * FROM code_chunks
            WHERE tenant_id = CAST(:tenantId AS uuid)
            AND repository_id = CAST(:repositoryId AS uuid)
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<CodeChunk> findTopKSimilar(
            @Param("tenantId") String tenantId,
            @Param("repositoryId") String repositoryId,
            @Param("embedding") String embedding,
            @Param("topK") int topK
    );

    @Modifying
    @Query(value = "DROP INDEX IF EXISTS idx_code_chunks_embedding", nativeQuery = true)
    void dropVectorIndex();

    @Modifying
    @Query(value = """
            CREATE INDEX idx_code_chunks_embedding
            ON code_chunks
            USING ivfflat (embedding vector_cosine_ops)
            WITH (lists = 100)
            """, nativeQuery = true)
    void createVectorIndex();
}
