package com.sam.be.modules.notification.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserNotificationResponse(
        UUID id,
        UUID recipientUserId,
        UUID actorUserId,
        String actorName,
        String type,
        UUID jobId,
        String jobTitle,
        BigDecimal matchScore,
        UUID invitationId,
        UUID roomId,
        UUID contractId,
        UUID paymentId,
        BigDecimal amount,
        UUID subscriptionId,
        UUID proposalId,
        UUID recommendationId,
        String message,
        LocalDateTime createdAt,
        LocalDateTime readAt,
        LocalDateTime resolvedAt,
        int unreadCount) {}
