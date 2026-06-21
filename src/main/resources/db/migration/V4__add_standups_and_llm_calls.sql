CREATE TABLE standups (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    developer_id      UUID NOT NULL REFERENCES developers(id),
    standup_date      DATE NOT NULL,
    generated_content TEXT NOT NULL,
    commits_used      INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(developer_id, standup_date)
);

CREATE INDEX idx_standups_tenant_id ON standups(tenant_id);

-- Every LLM call ever made, for every feature, logged here. This is what
-- lets you answer "what was the model actually asked?" six months from now.
CREATE TABLE llm_calls (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    developer_id        UUID REFERENCES developers(id),
    feature             VARCHAR(50) NOT NULL,
    model_name          VARCHAR(100) NOT NULL,
    prompt_tokens       INTEGER,
    completion_tokens   INTEGER,
    latency_ms          BIGINT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_llm_calls_tenant_id ON llm_calls(tenant_id);