CREATE TABLE IF NOT EXISTS app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    idempotency_key VARCHAR(100) UNIQUE,
    user_id UUID REFERENCES app_users(id)
);

CREATE INDEX IF NOT EXISTS idx_payments_user_created ON payments(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
