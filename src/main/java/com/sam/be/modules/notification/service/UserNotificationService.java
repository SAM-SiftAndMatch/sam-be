package com.sam.be.modules.notification.service;

import com.sam.be.common.response.PageRequests;
import com.sam.be.common.response.PageResponse;
import com.sam.be.modules.notification.dto.NotificationFeedResponse;
import com.sam.be.modules.notification.dto.NotificationMessage;
import com.sam.be.modules.notification.dto.UserNotificationResponse;
import com.sam.be.modules.notification.entity.UserNotification;
import com.sam.be.modules.notification.repository.UserNotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class UserNotificationService {
    private final UserNotificationRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public UserNotificationResponse persistAndPublish(UUID recipientId, NotificationMessage payload) {
        UserNotification notification = repository.save(fromPayload(recipientId, payload));
        UserNotificationResponse response = toResponse(notification);
        publishAfterCommit(recipientId, response);
        return response;
    }

    @Transactional
    public UserNotificationResponse upsertChatMessage(UUID recipientId, UUID actorId, String actorName,
            UUID roomId, UUID jobId, String jobTitle) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO user_notifications
                    (id, recipient_user_id, actor_user_id, actor_name, type, job_id, job_title, room_id,
                     message, created_at, unread_count)
                VALUES (?, ?, ?, ?, 'CHAT_MESSAGE', ?, ?, ?, 'Bạn có tin nhắn mới', ?, 1)
                ON CONFLICT (recipient_user_id, room_id)
                    WHERE type = 'CHAT_MESSAGE' AND read_at IS NULL
                DO UPDATE SET actor_user_id = EXCLUDED.actor_user_id,
                    actor_name = EXCLUDED.actor_name, job_id = EXCLUDED.job_id,
                    job_title = EXCLUDED.job_title, message = EXCLUDED.message,
                    created_at = EXCLUDED.created_at,
                    unread_count = user_notifications.unread_count + 1
                """, UUID.randomUUID(), recipientId, actorId, actorName, jobId, jobTitle, roomId, now);
        UserNotification notification = repository.findFirstByRecipientUserIdAndRoomIdAndTypeAndReadAtIsNull(
                recipientId, roomId, "CHAT_MESSAGE").orElseThrow();
        UserNotificationResponse response = toResponse(notification);
        publishAfterCommit(recipientId, response);
        return response;
    }

    @Transactional(readOnly = true)
    public NotificationFeedResponse getRecent(UUID userId) {
        List<UserNotificationResponse> items = repository
                .findTop20ByRecipientUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(this::toResponse).toList();
        return new NotificationFeedResponse(items, repository.countByRecipientUserIdAndReadAtIsNull(userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserNotificationResponse> getPage(UUID userId, int page, int size) {
        return PageResponse.from(repository.findByRecipientUserId(userId,
                PageRequests.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .map(this::toResponse));
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        repository.markRead(notificationId, userId, LocalDateTime.now());
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllRead(userId, LocalDateTime.now());
    }

    @Transactional
    public void markRoomMessagesRead(UUID userId, UUID roomId, LocalDateTime readAt) {
        repository.markRoomMessagesRead(userId, roomId, readAt);
    }

    private UserNotification fromPayload(UUID recipientId, NotificationMessage payload) {
        return UserNotification.builder().id(UUID.randomUUID()).recipientUserId(recipientId)
                .actorUserId(payload.getActorUserId()).actorName(payload.getActorName())
                .type(payload.getType()).jobId(payload.getJobId()).jobTitle(payload.getJobTitle())
                .matchScore(payload.getMatchScore())
                .invitationId(payload.getInvitationId()).roomId(payload.getRoomId())
                .contractId(payload.getContractId()).paymentId(payload.getPaymentId()).amount(payload.getAmount())
                .subscriptionId(payload.getSubscriptionId()).proposalId(payload.getProposalId())
                .recommendationId(payload.getRecommendationId()).message(payload.getMessage()).build();
    }

    private UserNotificationResponse toResponse(UserNotification n) {
        return new UserNotificationResponse(n.getId(), n.getRecipientUserId(), n.getActorUserId(), n.getActorName(),
                n.getType(), n.getJobId(), n.getJobTitle(), n.getMatchScore(), n.getInvitationId(), n.getRoomId(), n.getContractId(),
                n.getPaymentId(), n.getAmount(), n.getSubscriptionId(), n.getProposalId(), n.getRecommendationId(),
                n.getMessage(), n.getCreatedAt(), n.getReadAt(), n.getUnreadCount());
    }

    private void publishAfterCommit(UUID recipientId, UserNotificationResponse response) {
        Runnable publish = () -> messagingTemplate.convertAndSend(
                "/topic/users/" + recipientId + "/notifications", response);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else {
            publish.run();
        }
    }
}
