package com.sam.be.modules.payment.service;

import com.sam.be.common.response.PageResponse;
import com.sam.be.modules.ai.dto.response.AiAmountVerification;
import com.sam.be.modules.payment.dto.request.ConfirmFundingRequest;
import com.sam.be.modules.payment.dto.request.CreateEscrowRequest;
import com.sam.be.modules.payment.dto.response.FreelancerEscrowSummaryResponse;
import com.sam.be.modules.payment.dto.response.FundingStatusResponse;
import com.sam.be.modules.payment.dto.response.PaymentResponse;
import java.util.List;
import java.util.UUID;

public interface PaymentService {
    PaymentResponse createMaintenanceFunding(UUID userId, UUID orderId);

    java.util.Map<String, Boolean> handlePay2sIpn(com.fasterxml.jackson.databind.JsonNode payload);

    // Client nạp 100% giá trị hợp đồng (1 lần duy nhất cho mỗi hợp đồng)
    PaymentResponse createContractFund(UUID userId, CreateEscrowRequest request);

    // Freelancer đặt cọc cam kết 2% giá trị hợp đồng (1 lần duy nhất)
    PaymentResponse createSecurityDeposit(UUID userId, CreateEscrowRequest request);

    // AI đọc văn bản hợp đồng, đối chiếu với giá thỏa thuận (chặn nạp tiền nếu lệch)
    AiAmountVerification verifyContractAmount(UUID userId, UUID contractId);

    // Bảng tiền nạp khởi động cho cả 2 bên cùng thấy
    FundingStatusResponse getFundingStatus(UUID userId, UUID contractId);

    // Read-only state; only provider IPN confirms money.
    PaymentResponse confirmFunding(UUID userId, UUID paymentId, ConfirmFundingRequest request);

    String applyVerifiedPay2sPayment(
            UUID paymentId, String order, String transaction, java.math.BigDecimal amount);

    PaymentResponse releaseEscrow(UUID userId, UUID paymentId);

    List<PaymentResponse> getByContract(UUID userId, UUID contractId);

    PageResponse<PaymentResponse> getByContractPage(
            UUID userId, UUID contractId, int page, int size);

    FreelancerEscrowSummaryResponse getFreelancerEscrowSummary(UUID requesterId, UUID freelancerId);
}
