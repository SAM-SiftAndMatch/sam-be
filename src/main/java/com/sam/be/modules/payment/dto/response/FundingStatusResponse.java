package com.sam.be.modules.payment.dto.response;

import com.sam.be.common.constant.enums.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Toàn cảnh "nạp tiền khởi động" của một hợp đồng: client nạp 100%, freelancer cọc 2%. Đủ cả
 * hai hệ thống mới cho dự án chạy (job AWAITING_PAYMENT -> IN_PROGRESS). Cuối dự án freelancer
 * nhận 90%, sàn giữ 10%.
 */
@Data
@Builder
public class FundingStatusResponse {
    private UUID contractId;
    private UUID jobId;
    private String jobStatus;
    private BigDecimal agreedAmount;

    // Số tiền mỗi bên phải chuyển (hiển thị cho 2 bên cùng thấy trước khi bấm VNPay)
    private BigDecimal clientAmount;
    private BigDecimal depositAmount;

    // Dự kiến cuối dự án (để minh bạch từ đầu)
    private BigDecimal freelancerPayout;
    private BigDecimal platformFee;

    private PaymentStatus fundStatus;
    private PaymentStatus depositStatus;
    private boolean fundPaid;
    private boolean depositPaid;
    private boolean allPaid;
}
