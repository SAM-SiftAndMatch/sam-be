package com.sam.be.modules.notification.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface NotificationService {
    void sendInviteNotification(
            UUID freelancerId, UUID jobId, String jobTitle, BigDecimal matchScore);

    void sendPaymentNotification(
            UUID userId,
            UUID contractId,
            UUID paymentId,
            BigDecimal amount,
            String type,
            String message);
}
