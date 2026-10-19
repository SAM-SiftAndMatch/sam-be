CREATE TABLE user_notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_name VARCHAR(255),
    type VARCHAR(64) NOT NULL,
    job_id UUID,
    job_title VARCHAR(500),
    match_score NUMERIC(6, 2),
    invitation_id UUID,
    room_id UUID,
    contract_id UUID,
    payment_id UUID,
    amount NUMERIC(19, 2),
    subscription_id UUID,
    proposal_id UUID,
    recommendation_id UUID,
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    unread_count INTEGER NOT NULL DEFAULT 1 CHECK (unread_count >= 1)
);

CREATE INDEX idx_user_notifications_recipient_created
    ON user_notifications(recipient_user_id, created_at DESC, id DESC);
CREATE INDEX idx_user_notifications_recipient_unread
    ON user_notifications(recipient_user_id, read_at, created_at DESC);
CREATE UNIQUE INDEX uq_user_notifications_unread_chat_room
    ON user_notifications(recipient_user_id, room_id)
    WHERE type = 'CHAT_MESSAGE' AND read_at IS NULL;

ALTER TABLE chat_rooms
    ADD COLUMN client_last_read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN freelancer_last_read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_chat_rooms_client_member ON chat_rooms(client_id);
CREATE INDEX idx_chat_rooms_freelancer_member ON chat_rooms(freelancer_id);
