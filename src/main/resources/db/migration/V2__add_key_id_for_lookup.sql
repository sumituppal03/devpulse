-- The old test data used a key format incompatible with the new lookup design.
-- Clearing it is safe — it was only ever a learning/test row.
DELETE FROM tenants;

ALTER TABLE tenants
    ADD COLUMN key_id VARCHAR(32) NOT NULL;

ALTER TABLE tenants
    ADD CONSTRAINT uq_tenants_key_id UNIQUE (key_id);

CREATE INDEX idx_tenants_key_id ON tenants(key_id);