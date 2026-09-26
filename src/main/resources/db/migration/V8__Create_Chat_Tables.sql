-- Tạo bảng Phòng Chat (1 Job có thể có nhiều phòng chat, nhưng giữa 1 Job và 1 Dev chỉ có 1 phòng)
CREATE TABLE IF NOT EXISTS chat_rooms (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    client_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    freelancer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP(6) DEFAULT NOW(),
    updated_at TIMESTAMP(6) DEFAULT NOW(),
    UNIQUE (job_id, freelancer_id) -- Đảm bảo không tạo đúp phòng cho cùng 1 Dev trên 1 Job
    );

-- Tạo bảng Lưu Lịch Sử Tin Nhắn
CREATE TABLE IF NOT EXISTS chat_messages (
                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) DEFAULT NOW()
    );

-- Đánh Index để sau này query lịch sử tin nhắn cho nhanh
CREATE INDEX IF NOT EXISTS idx_chat_messages_room_id ON chat_messages(room_id);