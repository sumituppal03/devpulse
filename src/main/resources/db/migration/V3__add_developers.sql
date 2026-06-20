CREATE TABLE developers (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    github_username  VARCHAR(255) NOT NULL,
    timezone         VARCHAR(100) NOT NULL DEFAULT 'UTC',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, github_username)
);

CREATE INDEX idx_developers_tenant_id ON developers(tenant_id);