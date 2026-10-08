package com.sam.be.modules.payment.service;

import com.sam.be.modules.ai.dto.response.AiAmountVerification;
import com.sam.be.modules.payment.dto.request.ConfirmFundingRequest;
import com.sam.be.modules.payment.dto.request.CreateEscrowRequest;
import com.sam.be.modules.payment.dto.response.FundingStatusResponse;
import com.sam.be.modules.payment.dto.response.PaymentResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PaymentService {
    // Client nạp 100% giá trị hợp đồng (1 lần duy nhất cho mỗi hợp đồng)
    PaymentResponse createContractFund(UUID userId, CreateEscrowRequest request);

    // Freelancer đặt cọc cam kết 2% giá trị hợp đồng (1 lần duy nhất)
    PaymentResponse createSecurityDeposit(UUID userId, CreateEscrowRequest request);

    // AI đọc văn bản hợp đồng, đối chiếu với giá thỏa thuận (chặn nạp tiền nếu lệch)
    AiAmountVerification verifyContractAmount(UUID userId, UUID contractId);

    // Bảng tiền nạp khởi động cho cả 2 bên cùng thấy
    FundingStatusResponse getFundingStatus(UUID userId, UUID contractId);

    // FE tự báo đã chuyển sau redirect VNPay (giống confirm-payment của mua gói).
    // Chỉ ghi nhận khi đúng người trả + đúng txnRef/amount + đang PENDING.
    PaymentResponse confirmFunding(UUID userId, UUID paymentId, ConfirmFundingRequest request);

    Map<String, String> handleVnpayIpn(Map<String, String> params);

    PaymentResponse releaseEscrow(UUID userId, UUID paymentId);

    List<PaymentResponse> getByContract(UUID userId, UUID contractId);
}
