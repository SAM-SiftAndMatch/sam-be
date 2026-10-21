CREATE TABLE project_drafts (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES users(id),
    flow_type VARCHAR(16) NOT NULL CHECK (flow_type IN ('AI', 'MANUAL')),
    phase VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'EDITING' CHECK (status IN ('EDITING', 'PUBLISHED', 'DELETED')),
    title VARCHAR(255),
    brief TEXT,
    state_json TEXT NOT NULL DEFAULT '{}',
    srs_content TEXT,
    srs_document_url VARCHAR(500),
    exact_budget_vnd NUMERIC(18, 2),
    duration_months NUMERIC(4, 1),
    published_job_id UUID UNIQUE REFERENCES jobs(id),
    version BIGINT NOT NULL DEFAULT 0,
    edit_revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_project_drafts_client_status_updated
    ON project_drafts(client_id, status, updated_at DESC, id DESC);

CREATE TABLE project_draft_ai_turns (
    id UUID PRIMARY KEY,
    draft_id UUID NOT NULL REFERENCES project_drafts(id),
    client_message_id UUID NOT NULL,
    phase VARCHAR(8) NOT NULL CHECK (phase IN ('BA', 'RISK')),
    user_message TEXT NOT NULL,
    ai_response_json TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_project_draft_turn_request UNIQUE (draft_id, client_message_id)
);

CREATE INDEX idx_project_draft_turns_history
    ON project_draft_ai_turns(draft_id, is_active, created_at, id);
CREATE INDEX idx_project_draft_turns_pending
    ON project_draft_ai_turns(status, updated_at, id)
    WHERE status IN ('PENDING', 'PROCESSING');
