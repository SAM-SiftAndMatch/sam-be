ALTER TABLE chat_messages ADD COLUMN client_message_id UUID;
CREATE UNIQUE INDEX uq_chat_message_client_id
    ON chat_messages(room_id, sender_id, client_message_id) WHERE client_message_id IS NOT NULL;

CREATE TABLE chat_attachments (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms(id),
    owner_id UUID NOT NULL REFERENCES users(id),
    message_id UUID REFERENCES chat_messages(id),
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    storage_url TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_chat_attachments_message ON chat_attachments(message_id);
CREATE INDEX idx_chat_attachments_pending ON chat_attachments(owner_id, created_at) WHERE message_id IS NULL;
