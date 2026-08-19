CREATE TABLE oauth_transactions (
    id UUID PRIMARY KEY,
    state_hash VARCHAR(64) NOT NULL UNIQUE,
    app_state VARCHAR(128) NOT NULL,
    code_challenge VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    user_id UUID REFERENCES users(id),
    completion_code_hash VARCHAR(64) UNIQUE,
    state_expires_at TIMESTAMPTZ NOT NULL,
    code_expires_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_oauth_transactions_status ON oauth_transactions(status);
CREATE INDEX idx_oauth_transactions_state_expires ON oauth_transactions(state_expires_at);
CREATE INDEX idx_oauth_transactions_code_expires ON oauth_transactions(code_expires_at);
