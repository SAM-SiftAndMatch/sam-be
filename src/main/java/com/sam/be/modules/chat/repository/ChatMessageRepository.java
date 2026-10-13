package com.sam.be.modules.chat.repository;

import com.sam.be.modules.chat.entity.ChatMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    // Lấy tin nhắn theo phòng, sắp xếp cũ nhất lên trước (như Messenger)
    @EntityGraph(attributePaths = "sender")
    List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(UUID roomId);

    Optional<ChatMessage> findTopByRoomIdOrderByCreatedAtDesc(UUID roomId);

    @Query(
            "SELECT m FROM ChatMessage m LEFT JOIN FETCH m.sender WHERE m.room.id IN :roomIds "
                    + "AND m.createdAt = (SELECT MAX(latest.createdAt) FROM ChatMessage latest "
                    + "WHERE latest.room.id = m.room.id)")
    List<ChatMessage> findLatestForRooms(@Param("roomIds") List<UUID> roomIds);

    @Query(
            "SELECT m FROM ChatMessage m LEFT JOIN FETCH m.sender WHERE m.room.id = :roomId "
                    + "ORDER BY m.createdAt DESC, m.id DESC")
    List<ChatMessage> findRecentByRoomId(@Param("roomId") UUID roomId, Pageable pageable);
}
