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
     * tenantId  — String so PostgreSQL can CAST to uuid in native SQL
     *             (Java UUID objects aren't auto-converted in @Query nativeQuery)
     * embedding — pgvector string "[0.1,0.2,...]", CAST to vector for <=> operator
     * topK      — maximum rows to return
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
}
