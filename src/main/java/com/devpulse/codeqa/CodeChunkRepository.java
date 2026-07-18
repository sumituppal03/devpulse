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
     * Top-k cosine similarity search against the tenant's indexed chunks.
     *
     * tenantId  — String so PostgreSQL can CAST to uuid in native SQL.
     *             Java UUID objects aren't auto-converted in @Query nativeQuery.
     * embedding — pgvector string "[0.1,0.2,...]", CAST to vector for <=> operator.
     * topK      — maximum rows to return.
     *
     * tenant_id filter runs first — no tenant can ever see another's chunks.
     */
    @Query(value = """
            SELECT * FROM code_chunks
            WHERE tenant_id = CAST(:tenantId AS uuid)
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<CodeChunk> findTopKSimilar(
            @Param("tenantId") String tenantId,
            @Param("embedding") String embedding,
            @Param("topK") int topK
    );

    /**
     * Rebuilds the ivfflat vector index after data has been loaded.
     *
     * This MUST run after chunks are saved — building ivfflat on an empty
     * table produces a degenerate index that always returns the same row
     * regardless of the query vector. Called automatically by
     * CodeIndexingService.rebuildVectorIndex() at the end of each successful
     * indexing run.
     *
     * Uses two separate statements because JPA @Query with nativeQuery
     * doesn't support semicolon-separated multi-statement execution on
     * all drivers. The DROP and CREATE are called separately from the service.
     */
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
