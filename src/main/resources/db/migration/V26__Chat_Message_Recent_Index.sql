CREATE INDEX IF NOT EXISTS idx_chat_messages_room_created_id_desc
    ON chat_messages(room_id, created_at DESC, id DESC);
