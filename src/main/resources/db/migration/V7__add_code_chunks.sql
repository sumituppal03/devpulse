-- V7__add_code_chunks.sql
-- pgvector extension must exist before this runs.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE repositories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    github_owner    VARCHAR(255) NOT NULL,
    github_repo     VARCHAR(255) NOT NULL,
    default_branch  VARCHAR(100) NOT NULL DEFAULT 'main',
    index_status    VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    last_indexed_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, github_owner, github_repo)
);

CREATE INDEX idx_repositories_tenant_id ON repositories(tenant_id);

CREATE TABLE code_chunks (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    repository_id    UUID NOT NULL REFERENCES repositories(id),
    source_type      VARCHAR(50) NOT NULL,
    file_path        TEXT NOT NULL,
    class_name       VARCHAR(255),
    method_name      VARCHAR(255),
    heading          VARCHAR(500),
    content          TEXT NOT NULL,
    token_count      INTEGER,
    commit_sha       VARCHAR(40),

    -- Stored as pgvector string "[0.1,0.2,...]" mapped as TEXT by Hibernate.
    -- CAST(:embedding AS vector) in native SQL handles the type conversion at
    -- query time, so no special Hibernate vector type mapping is needed.
    embedding        vector(768),

    embedding_model  VARCHAR(100) NOT NULL DEFAULT 'nomic-embed-text:v1.5',
    indexed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Tenant isolation index — every similarity search filters by tenant first
CREATE INDEX idx_code_chunks_tenant_id ON code_chunks(tenant_id);

-- NOTE: The ivfflat index on code_chunks.embedding is intentionally NOT created
-- here. Building ivfflat on an empty table produces a degenerate index that
-- returns the same row regardless of the query vector.
--
-- The index is created automatically at the end of each successful indexing run
-- by CodeIndexingService.rebuildVectorIndex() — after real data exists.
--
-- If you ever need to rebuild it manually:
--   DROP INDEX IF EXISTS idx_code_chunks_embedding;
--   CREATE INDEX idx_code_chunks_embedding ON code_chunks
--       USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
