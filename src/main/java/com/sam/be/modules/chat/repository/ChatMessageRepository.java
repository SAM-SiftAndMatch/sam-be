package com.sam.be.modules.chat.repository;

import com.sam.be.modules.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    // Lấy tin nhắn theo phòng, sắp xếp cũ nhất lên trước (như Messenger)
    List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(UUID roomId);
}