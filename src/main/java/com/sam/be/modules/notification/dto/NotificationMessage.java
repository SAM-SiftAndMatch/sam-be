package com.sam.be.modules.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationMessage {
    private String type; // Loại thông báo (VD: 1_TOUCH_INVITE)
    private UUID jobId;
    private String jobTitle;
    private BigDecimal matchScore;
    private String message;
    private LocalDateTime timestamp;
}