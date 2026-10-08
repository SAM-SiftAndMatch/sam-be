package com.sam.be.modules.notification.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface NotificationService {
    void sendInviteNotification(
            UUID freelancerId, UUID jobId, String jobTitle, BigDecimal matchScore);

    void sendInviteNotification(
            UUID freelancerId, UUID jobId, String jobTitle, BigDecimal matchScore, UUID recommendationId);

    void sendPaymentNotification(
            UUID userId,
            UUID contractId,
            UUID paymentId,
            BigDecimal amount,
            String type,
            String message);

    void sendSubscriptionNotification(
            UUID userId, UUID subscriptionId, String type, String message);

    void sendDevClaimNotification(
            UUID clientId, UUID jobId, String jobTitle, UUID recommendationId, String devName);

    void sendChatOpenedNotification(
            UUID userId, UUID jobId, String jobTitle, UUID recommendationId, UUID roomId);

    // Luồng proposal phổ thông: client nhận hồ sơ mới, dev nhận lời mời, client nhận phản hồi
    void sendProposalNotification(
            UUID userId,
            UUID jobId,
            String jobTitle,
            UUID proposalId,
            String type,
            String message);
}
