-- V7__add_code_chunks.sql
--
-- pgvector extension must exist before this migration runs.
-- The extension is created manually on the database before first deploy.
-- This line is a safety net for fresh environments.
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

    -- Stored as TEXT containing the pgvector string "[0.1,0.2,...]".
    -- Hibernate maps this as plain VARCHAR with no special type adapter.
    -- CAST(:embedding AS vector) in native SQL handles the conversion
    -- at query time. This avoids the need for any pgvector JDBC extension.
    embedding        TEXT,

    embedding_model  VARCHAR(100) NOT NULL DEFAULT 'nomic-embed-text:v1.5',
    indexed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Tenant isolation index — every similarity search filters by tenant first
CREATE INDEX idx_code_chunks_tenant_id ON code_chunks(tenant_id);

-- NOTE: The ivfflat similarity index on code_chunks.embedding is NOT created here.
--
-- Reason: ivfflat requires at least `lists` rows of data to build meaningfully.
-- Creating it on an empty table produces a degenerate index that returns the
-- same row for every query regardless of the search vector — confirmed in testing.
--
-- The index is instead rebuilt automatically at the END of each successful
-- indexing run by CodeIndexingService.rebuildVectorIndex(), which calls
-- CodeChunkRepository.rebuildVectorIndex(). This guarantees the index is
-- always built on real data, never on an empty table.