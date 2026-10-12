ALTER TABLE payments ADD COLUMN IF NOT EXISTS request_fingerprint VARCHAR(64);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id UUID PRIMARY KEY,
    entry_group_id UUID NOT NULL,
    payment_id UUID NOT NULL REFERENCES payments(id),
    entry_key VARCHAR(120) NOT NULL UNIQUE,
    account_code VARCHAR(60) NOT NULL,
    direction VARCHAR(8) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ledger_payment ON ledger_entries(payment_id);
CREATE INDEX IF NOT EXISTS idx_ledger_created_at ON ledger_entries(created_at);

CREATE TABLE IF NOT EXISTS webhook_endpoints (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    target_url VARCHAR(2048) NOT NULL,
    signing_secret VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_webhook_user ON webhook_endpoints(user_id, active);

CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id UUID PRIMARY KEY,
    endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id),
    event_type VARCHAR(100) NOT NULL,
    payment_id UUID NOT NULL REFERENCES payments(id),
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    response_status INTEGER,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_webhook_delivery_due ON webhook_deliveries(status, next_attempt_at);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    topic VARCHAR(120) NOT NULL,
    message_key VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    last_error VARCHAR(1000)
);
CREATE INDEX IF NOT EXISTS idx_outbox_due ON outbox_events(published_at, next_attempt_at);

CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY,
    actor VARCHAR(50) NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(60) NOT NULL,
    resource_id VARCHAR(100),
    details VARCHAR(1000),
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_audit_actor_created ON audit_events(actor, created_at);
