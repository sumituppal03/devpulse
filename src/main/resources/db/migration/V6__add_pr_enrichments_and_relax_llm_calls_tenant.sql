CREATE TABLE pr_enrichments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    github_owner      VARCHAR(255) NOT NULL,
    github_repo       VARCHAR(255) NOT NULL,
    pr_number         INTEGER NOT NULL,
    context_comment   TEXT NOT NULL,
    github_comment_id BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(github_owner, github_repo, pr_number)
);

-- PR enrichment is webhook-triggered, per-repository, with no tenant context
-- at all — there is no Bearer token, no authenticated caller. Forcing a fake
-- tenant_id here would be dishonest audit data. A real repository-to-tenant
-- mapping is documented as a future improvement, not built yet.
ALTER TABLE llm_calls ALTER COLUMN tenant_id DROP NOT NULL;