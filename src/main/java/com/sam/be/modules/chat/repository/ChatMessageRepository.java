package com.sam.be.modules.chat.repository;

import com.sam.be.modules.chat.entity.ChatMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    // Lấy tin nhắn theo phòng, sắp xếp cũ nhất lên trước (như Messenger)
    List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(UUID roomId);

    Optional<ChatMessage> findTopByRoomIdOrderByCreatedAtDesc(UUID roomId);
}
