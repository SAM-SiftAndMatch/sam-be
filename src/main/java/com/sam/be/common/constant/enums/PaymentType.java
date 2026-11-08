package com.sam.be.common.constant.enums;

public enum PaymentType {
    // Client nạp 100% giá trị hợp đồng trước khi dự án bắt đầu
    CONTRACT_FUND,
    // Freelancer đặt cọc cam kết 2% giá trị hợp đồng (hoàn trả khi xong, đền client khi bỏ job)
    MAINTENANCE_FUND,
    SECURITY_DEPOSIT
}
