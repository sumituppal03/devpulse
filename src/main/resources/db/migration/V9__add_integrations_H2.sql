CREATE TABLE IF NOT EXISTS integrations (
    id               UUID PRIMARY KEY DEFAULT random_uuid(),
    tenant_id        UUID NOT NULL,
    integration_type VARCHAR(50) NOT NULL,
    config           TEXT NOT NULL,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenant_integration UNIQUE (tenant_id, integration_type)
);
