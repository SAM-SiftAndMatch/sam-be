package com.sam.be.modules.notification.repository;

import com.sam.be.modules.notification.entity.UserNotification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {
    List<UserNotification> findTop20ByRecipientUserIdOrderByCreatedAtDescIdDesc(UUID recipientUserId);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    Page<UserNotification> findByRecipientUserId(UUID recipientUserId, Pageable pageable);

    Optional<UserNotification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    Optional<UserNotification> findFirstByRecipientUserIdAndRoomIdAndTypeAndReadAtIsNull(
            UUID recipientUserId, UUID roomId, String type);

    @Query("SELECT COUNT(n) FROM UserNotification n WHERE n.recipientUserId = :userId AND n.readAt IS NULL AND n.type = 'CHAT_MESSAGE'")
    long countUnreadChatNotifications(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE UserNotification n SET n.readAt = :readAt WHERE n.id = :id AND n.recipientUserId = :userId AND n.readAt IS NULL")
    int markRead(@Param("id") UUID id, @Param("userId") UUID userId, @Param("readAt") LocalDateTime readAt);

    @Modifying
    @Query("UPDATE UserNotification n SET n.readAt = :readAt WHERE n.recipientUserId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") LocalDateTime readAt);

    @Modifying
    @Query("UPDATE UserNotification n SET n.readAt = :readAt WHERE n.recipientUserId = :userId AND n.roomId = :roomId AND n.type = 'CHAT_MESSAGE' AND n.readAt IS NULL")
    int markRoomMessagesRead(@Param("userId") UUID userId, @Param("roomId") UUID roomId,
            @Param("readAt") LocalDateTime readAt);
}
