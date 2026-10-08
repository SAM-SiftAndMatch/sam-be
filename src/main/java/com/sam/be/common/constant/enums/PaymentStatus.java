package com.sam.be.common.constant.enums;

public enum PaymentStatus {
    PENDING,
    HELD_IN_ESCROW,
    RELEASED,
    REFUNDED,
    // Đơn dở bị thay bằng đơn mới (VNPay không cho dùng lại mã cũ)
    EXPIRED
}
