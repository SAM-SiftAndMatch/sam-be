-- Support room-scoped browsing of sent attachments without scanning pending uploads.
CREATE INDEX idx_chat_attachments_room_message
    ON chat_attachments(room_id, message_id) WHERE message_id IS NOT NULL;
