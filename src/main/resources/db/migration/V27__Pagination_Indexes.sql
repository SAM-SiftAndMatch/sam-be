CREATE INDEX IF NOT EXISTS idx_jobs_status_created_id
    ON jobs(status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_jobs_client_created_id
    ON jobs(client_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_proposals_job_created_id
    ON proposals(job_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_proposals_freelancer_created_id
    ON proposals(freelancer_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_chat_rooms_client_updated_id
    ON chat_rooms(client_id, updated_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_chat_rooms_freelancer_updated_id
    ON chat_rooms(freelancer_id, updated_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_user_subscriptions_user_created_id
    ON user_subscriptions(user_id, created_at DESC, id DESC);
