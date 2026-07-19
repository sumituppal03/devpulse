CREATE TABLE integrations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    integration_type VARCHAR(50) NOT NULL,
    config           TEXT NOT NULL,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, integration_type)
);

CREATE INDEX idx_integrations_tenant_id ON integrations(tenant_id);