CREATE TABLE platform_feedback (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    request_key UUID NOT NULL,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(2000) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'IN_PROGRESS', 'RESOLVED')),
    admin_note VARCHAR(2000) NOT NULL DEFAULT '',
    reviewed_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_platform_feedback_request UNIQUE (user_id, request_key)
);

CREATE INDEX idx_platform_feedback_created ON platform_feedback(created_at DESC, id DESC);
CREATE INDEX idx_platform_feedback_user ON platform_feedback(user_id, created_at DESC, id DESC);
CREATE INDEX idx_platform_feedback_status ON platform_feedback(status, created_at DESC, id DESC);
CREATE INDEX idx_platform_feedback_rating ON platform_feedback(rating, created_at DESC, id DESC);
