CREATE TABLE webhook_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source        VARCHAR(50) NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       TEXT NOT NULL,
    processed     BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    received_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);