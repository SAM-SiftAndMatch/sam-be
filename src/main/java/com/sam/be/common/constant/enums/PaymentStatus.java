package com.sam.be.common.constant.enums;

public enum PaymentStatus {
    PENDING,
    HELD_IN_ESCROW,
    RELEASED,
    SETTLED,
    REFUNDED,
    // Đơn thanh toán đã hết hạn
    EXPIRED
}
