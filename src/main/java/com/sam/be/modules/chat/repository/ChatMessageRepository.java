package com.sam.be.modules.chat.repository;

import com.sam.be.modules.chat.entity.ChatMessage;
import java.time.LocalDateTime;
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
    @Query(value = "SELECT COUNT(*) FROM ("
            + "SELECT m.id FROM chat_rooms r JOIN chat_messages m ON m.room_id = r.id "
            + "WHERE r.client_id = :userId AND m.sender_id IS NOT NULL AND m.sender_id <> :userId "
            + "AND COALESCE(m.is_system, FALSE) = FALSE AND m.created_at > r.client_last_read_at "
            + "UNION ALL SELECT m.id FROM chat_rooms r JOIN chat_messages m ON m.room_id = r.id "
            + "WHERE r.freelancer_id = :userId AND m.sender_id IS NOT NULL AND m.sender_id <> :userId "
            + "AND COALESCE(m.is_system, FALSE) = FALSE AND m.created_at > r.freelancer_last_read_at"
            + ") unread_messages", nativeQuery = true)
    long countUnreadMessagesForUser(@Param("userId") UUID userId);

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

    @Query(
            "SELECT m FROM ChatMessage m LEFT JOIN FETCH m.sender WHERE m.room.id = :roomId "
                    + "AND (m.createdAt < :beforeAt "
                    + "OR (m.createdAt = :beforeAt AND m.id < :beforeId)) "
                    + "ORDER BY m.createdAt DESC, m.id DESC")
    List<ChatMessage> findHistoryBefore(
            @Param("roomId") UUID roomId,
            @Param("beforeAt") LocalDateTime beforeAt,
            @Param("beforeId") UUID beforeId,
            Pageable pageable);
}
