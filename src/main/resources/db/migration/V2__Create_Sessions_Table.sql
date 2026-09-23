CREATE TABLE IF NOT EXISTS sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token   VARCHAR(500) NOT NULL,
    user_agent      VARCHAR(500),
    ip_address      VARCHAR(50),
    is_revoked      BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at      TIMESTAMP(6) NOT NULL,
    created_at      TIMESTAMP(6) DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_refresh_token ON sessions(refresh_token);
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at);
