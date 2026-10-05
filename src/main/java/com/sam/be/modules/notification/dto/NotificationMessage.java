package com.sam.be.modules.notification.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationMessage {
    private String type; // Loại thông báo (VD: 1_TOUCH_INVITE, PAYMENT_ESCROW_HELD)
    private UUID jobId;
    private String jobTitle;
    private BigDecimal matchScore;
    private UUID contractId; // Nullable: chỉ dùng cho sự kiện payment
    private UUID paymentId; // Nullable: chỉ dùng cho sự kiện payment
    private BigDecimal amount; // Nullable: chỉ dùng cho sự kiện payment
    private String message;
    private LocalDateTime timestamp;
}
