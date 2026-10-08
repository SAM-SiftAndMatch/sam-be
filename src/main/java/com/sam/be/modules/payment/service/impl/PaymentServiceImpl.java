package com.sam.be.modules.payment.service.impl;

import com.sam.be.common.constant.enums.ContractStatus;
import com.sam.be.common.constant.enums.PaymentStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.infrastructure.thirdparty.vnpay.VnpayClient;
import com.sam.be.infrastructure.thirdparty.vnpay.VnpayProperties;
import com.sam.be.infrastructure.thirdparty.vnpay.VnpaySigner;
import com.sam.be.modules.contract.entity.Contract;
import com.sam.be.modules.contract.service.ContractService;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.payment.dto.request.ConfirmFundingRequest;
import com.sam.be.modules.payment.dto.request.CreateEscrowRequest;
import com.sam.be.modules.payment.dto.response.FundingStatusResponse;
import com.sam.be.modules.payment.dto.response.PaymentResponse;
import com.sam.be.modules.payment.entity.Payment;
import com.sam.be.modules.payment.repository.PaymentRepository;
import com.sam.be.modules.payment.service.PaymentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    // Mô hình "nạp đủ mới chạy": client 100%, freelancer cọc 2%, cuối dự án freelancer nhận 90%
    private static final BigDecimal DEPOSIT_RATE = new BigDecimal("0.02");
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.10");

    private final PaymentRepository paymentRepository;

    private final ContractService contractService;

    private final com.sam.be.modules.ai.service.AiService aiService;

    private final VnpayClient vnpayClient;

    private final VnpaySigner vnpaySigner;

    private final VnpayProperties vnpayProperties;

    private final NotificationService notificationService;

    private final com.sam.be.modules.job.repository.JobRepository jobRepository;

    @Override
    @Transactional
    public PaymentResponse createContractFund(UUID userId, CreateEscrowRequest request) {
        Contract contract = contractService.getContractById(request.getContractId());

        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONTRACT_NOT_ACTIVE);
        }
        if (contract.getClient() == null || !contract.getClient().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        // Chốt số tiền bằng AI đọc văn bản: lệch là chặn, bắt sửa hợp đồng trước
        assertAmountVerified(contract);

        String okUrl = resolveReturnUrl(request.getReturnUrl());

        // Đơn dở thì cho hết hạn rồi tạo đơn mới (VNPay không cho dùng lại mã cũ)
        expireStalePendingPayments(
                contract, com.sam.be.common.constant.enums.PaymentType.CONTRACT_FUND);

        BigDecimal amount = contract.getAgreedAmount().setScale(2, RoundingMode.HALF_UP);
        return createVnpayPayment(
                contract,
                com.sam.be.common.constant.enums.PaymentType.CONTRACT_FUND,
                amount,
                "Nop 100% gia tri hop dong " + contract.getId(),
                okUrl);
    }

    @Override
    @Transactional
    public PaymentResponse createSecurityDeposit(UUID userId, CreateEscrowRequest request) {
        Contract contract = contractService.getContractById(request.getContractId());

        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONTRACT_NOT_ACTIVE);
        }
        if (contract.getFreelancer() == null
                || !contract.getFreelancer().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        assertAmountVerified(contract);

        String okUrl = resolveReturnUrl(request.getReturnUrl());

        expireStalePendingPayments(
                contract, com.sam.be.common.constant.enums.PaymentType.SECURITY_DEPOSIT);

        // Cọc cam kết 2%, làm tròn tới đồng
        BigDecimal amount =
                contract
                        .getAgreedAmount()
                        .multiply(DEPOSIT_RATE)
                        .setScale(0, RoundingMode.HALF_UP)
                        .setScale(2);
        return createVnpayPayment(
                contract,
                com.sam.be.common.constant.enums.PaymentType.SECURITY_DEPOSIT,
                amount,
                "Dat coc cam ket 2% hop dong " + contract.getId(),
                okUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public com.sam.be.modules.ai.dto.response.AiAmountVerification verifyContractAmount(
            UUID userId, UUID contractId) {
        Contract contract = contractService.getContractById(contractId);
        boolean isMember =
                (contract.getClient() != null && contract.getClient().getId().equals(userId))
                        || (contract.getFreelancer() != null
                                && contract.getFreelancer().getId().equals(userId));
        if (!isMember) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        return aiService.verifyContractAmount(
                contract.getTermsAndConditions(), contract.getAgreedAmount());
    }

    @Override
    @Transactional(readOnly = true)
    public FundingStatusResponse getFundingStatus(UUID userId, UUID contractId) {
        Contract contract = contractService.getContractById(contractId);
        boolean isClient =
                contract.getClient() != null && contract.getClient().getId().equals(userId);
        boolean isFreelancer =
                contract.getFreelancer() != null && contract.getFreelancer().getId().equals(userId);
        if (!isClient && !isFreelancer) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        BigDecimal agreed = contract.getAgreedAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal deposit =
                agreed.multiply(DEPOSIT_RATE).setScale(0, RoundingMode.HALF_UP).setScale(2);
        BigDecimal fee = agreed.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal payout = agreed.subtract(fee);

        PaymentStatus fundStatus = latestStatus(contractId, com.sam.be.common.constant.enums.PaymentType.CONTRACT_FUND);
        PaymentStatus depositStatus =
                latestStatus(contractId, com.sam.be.common.constant.enums.PaymentType.SECURITY_DEPOSIT);

        boolean fundPaid = fundStatus == PaymentStatus.HELD_IN_ESCROW || fundStatus == PaymentStatus.RELEASED;
        boolean depositPaid =
                depositStatus == PaymentStatus.HELD_IN_ESCROW || depositStatus == PaymentStatus.RELEASED;

        return FundingStatusResponse.builder()
                .contractId(contract.getId())
                .jobId(contract.getJob().getId())
                .jobStatus(contract.getJob().getStatus().name())
                .agreedAmount(agreed)
                .clientAmount(agreed)
                .depositAmount(deposit)
                .freelancerPayout(payout)
                .platformFee(fee)
                .fundStatus(fundStatus)
                .depositStatus(depositStatus)
                .fundPaid(fundPaid)
                .depositPaid(depositPaid)
                .allPaid(fundPaid && depositPaid)
                .build();
    }

    private PaymentStatus latestStatus(
            UUID contractId, com.sam.be.common.constant.enums.PaymentType type) {
        List<Payment> all =
                paymentRepository.findAllByContractIdAndPaymentType(contractId, type);
        // Ưu tiên trạng thái đã trả, rồi tới đơn đang chờ; bỏ qua đơn hết hạn/hoàn
        return all.stream()
                .map(Payment::getStatus)
                .filter(s -> s != PaymentStatus.EXPIRED && s != PaymentStatus.REFUNDED)
                .sorted(
                        (a, b) ->
                                Integer.compare(statusRank(a), statusRank(b)))
                .findFirst()
                .orElse(null);
    }

    private int statusRank(PaymentStatus s) {
        if (s == PaymentStatus.HELD_IN_ESCROW || s == PaymentStatus.RELEASED) return 0;
        if (s == PaymentStatus.PENDING) return 1;
        return 2;
    }

    private String resolveReturnUrl(String returnUrl) {
        if (returnUrl != null && !returnUrl.isBlank()) {
            if (!vnpayProperties.isReturnUrlAllowed(returnUrl)) {
                throw new ApiException(ErrorCode.INVALID_RETURN_URL);
            }
            return returnUrl.trim();
        }
        return vnpayProperties.getDefaultReturnUrl();
    }

    /**
     * Bấm Nạp lần nữa khi đơn cũ còn dở: VNPay không cho dùng lại mã cũ nên đánh dấu đơn PENDING
     * cũ hết hạn rồi tạo đơn mới toanh (mã mới). Chỉ chặn khi đã trả xong.
     */
    private void expireStalePendingPayments(
            Contract contract, com.sam.be.common.constant.enums.PaymentType type) {
        List<Payment> existing =
                paymentRepository.findAllByContractIdAndPaymentType(contract.getId(), type).stream()
                        .filter(
                                p ->
                                        p.getStatus() != PaymentStatus.REFUNDED
                                                && p.getStatus() != PaymentStatus.EXPIRED)
                        .toList();
        boolean paid =
                existing.stream()
                        .anyMatch(
                                p ->
                                        p.getStatus() == PaymentStatus.HELD_IN_ESCROW
                                                || p.getStatus() == PaymentStatus.RELEASED);
        if (paid) {
            throw new ApiException(
                    ErrorCode.ESCROW_PAYMENT_EXISTS, "Khoản này đã được thanh toán rồi.");
        }
        List<Payment> stale =
                existing.stream()
                        .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                        .toList();
        for (Payment p : stale) {
            p.setStatus(PaymentStatus.EXPIRED);
        }
        if (!stale.isEmpty()) {
            paymentRepository.saveAll(stale);
            log.info(
                    "Expired {} stale PENDING {} payment(s) for contract {}",
                    stale.size(),
                    type,
                    contract.getId());
        }
    }

    private void assertAmountVerified(Contract contract) {
        var verification =
                aiService.verifyContractAmount(
                        contract.getTermsAndConditions(), contract.getAgreedAmount());
        if (!Boolean.TRUE.equals(verification.getMatches())) {
            String note =
                    verification.getNote() != null
                            ? verification.getNote()
                            : "Số tiền trong văn bản không khớp giá thỏa thuận.";
            throw new ApiException(ErrorCode.REQUEST_FAILED, "AI đối chiếu lệch số: " + note);
        }
    }

    private PaymentResponse createVnpayPayment(
            Contract contract,
            com.sam.be.common.constant.enums.PaymentType type,
            BigDecimal amount,
            String orderInfo,
            String returnUrl) {
        Payment payment =
                Payment.builder()
                        .contract(contract)
                        .paymentType(type)
                        .amount(amount)
                        .currency("VND")
                        .status(PaymentStatus.PENDING)
                        .installmentNo(1)
                        .build();
        payment = paymentRepository.save(payment);
        String txnRef = payment.getId().toString().replace("-", "");
        payment.setPaymentGatewayId(txnRef);

        if (returnUrl != null && !returnUrl.isBlank()) {
            if (!vnpayProperties.isReturnUrlAllowed(returnUrl)) {
                throw new ApiException(ErrorCode.INVALID_RETURN_URL);
            }
        } else {
            returnUrl = vnpayProperties.getDefaultReturnUrl();
        }

        long amountVnd = amount.multiply(BigDecimal.valueOf(100)).longValueExact();
        String vnpayUrl =
                vnpayClient.buildPaymentUrl(txnRef, amountVnd, orderInfo, null, returnUrl);

        log.info("Created {} payment {} for contract {}", type, payment.getId(), contract.getId());
        return toResponse(payment, vnpayUrl);
    }

    @Override
    @Transactional
    public PaymentResponse confirmFunding(
            UUID userId, UUID paymentId, ConfirmFundingRequest request) {
        Payment payment =
                paymentRepository
                        .findById(paymentId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        Contract contract = payment.getContract();
        boolean isClient =
                contract.getClient() != null && contract.getClient().getId().equals(userId);
        boolean isFreelancer =
                contract.getFreelancer() != null && contract.getFreelancer().getId().equals(userId);
        if (!isClient && !isFreelancer) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        // Đúng người trả đúng khoản: fund chỉ client, deposit chỉ freelancer
        if (payment.getPaymentType()
                        == com.sam.be.common.constant.enums.PaymentType.CONTRACT_FUND
                && !isClient) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        if (payment.getPaymentType()
                        == com.sam.be.common.constant.enums.PaymentType.SECURITY_DEPOSIT
                && !isFreelancer) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        // Idempotent: IPN tới trước rồi thì thôi
        if (payment.getStatus() == PaymentStatus.HELD_IN_ESCROW
                || payment.getStatus() == PaymentStatus.RELEASED) {
            return toResponse(payment, null);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ApiException(ErrorCode.REQUEST_FAILED);
        }

        // Đối chiếu với query trên URL return của VNPay (chống báo bừa)
        if (request != null) {
            if (request.getTxnRef() != null
                    && !request.getTxnRef().equals(payment.getPaymentGatewayId())) {
                throw new ApiException(ErrorCode.REQUEST_FAILED, "Mã giao dịch không khớp.");
            }
            if (request.getAmountVnd() != null) {
                long expected =
                        payment.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
                if (request.getAmountVnd() != expected) {
                    throw new ApiException(ErrorCode.REQUEST_FAILED, "Số tiền không khớp.");
                }
            }
        }

        payment.setStatus(PaymentStatus.HELD_IN_ESCROW);
        payment.setEscrowHeldAt(LocalDateTime.now());
        paymentRepository.save(payment);

        pushPaymentEvent(
                contract,
                payment,
                "PAYMENT_ESCROW_HELD",
                "Tiền ký quỹ (" + payment.getAmount() + " VND) đã vào Escrow.");
        log.info("Funding confirmed by FE for payment {}", payment.getId());

        maybeStartProject(contract);
        return toResponse(payment, null);
    }

    @Override
    @Transactional
    public Map<String, String> handleVnpayIpn(Map<String, String> params) {
        if (!vnpaySigner.verify(params, vnpayProperties.getHashSecret())) {
            log.warn("VNPay IPN rejected: invalid signature, txnRef={}", params.get("vnp_TxnRef"));
            return Map.of("RspCode", "97", "Message", "Invalid signature");
        }

        String txnRef = params.get("vnp_TxnRef");
        Payment payment = paymentRepository.findByPaymentGatewayId(txnRef).orElse(null);
        if (payment == null) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }

        long expectedVnd = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
        long receivedVnd;
        try {
            receivedVnd = Long.parseLong(params.get("vnp_Amount"));
        } catch (NumberFormatException | NullPointerException e) {
            return Map.of("RspCode", "04", "Message", "Invalid amount");
        }
        if (receivedVnd != expectedVnd) {
            log.warn(
                    "VNPay IPN amount mismatch for payment {}: expected {} got {}",
                    payment.getId(),
                    expectedVnd,
                    receivedVnd);
            return Map.of("RspCode", "04", "Message", "Invalid amount");
        }

        if (payment.getStatus() == PaymentStatus.HELD_IN_ESCROW) {
            return Map.of("RspCode", "02", "Message", "Order already confirmed");
        }

        if ("00".equals(params.get("vnp_ResponseCode"))
                && payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.HELD_IN_ESCROW);
            payment.setEscrowHeldAt(LocalDateTime.now());
            paymentRepository.save(payment);

            Contract contract = payment.getContract();
            pushPaymentEvent(
                    contract,
                    payment,
                    "PAYMENT_ESCROW_HELD",
                    "Tiền ký quỹ (" + payment.getAmount() + " VND) đã vào Escrow.");
            log.info("Escrow held for payment {}", payment.getId());

            // Đủ cả 2 khoản (client 100% + freelancer cọc 2%) thì cho dự án chạy
            maybeStartProject(contract);
        }
        return Map.of("RspCode", "00", "Message", "Confirm Success");
    }

    private void maybeStartProject(Contract contract) {
        var job = contract.getJob();
        if (job.getStatus()
                != com.sam.be.common.constant.enums.JobStatus.AWAITING_PAYMENT) {
            return;
        }
        boolean fundPaid =
                paymentRepository
                        .findAllByContractIdAndPaymentType(
                                contract.getId(),
                                com.sam.be.common.constant.enums.PaymentType.CONTRACT_FUND)
                        .stream()
                        .anyMatch(
                                p ->
                                        p.getStatus() == PaymentStatus.HELD_IN_ESCROW
                                                || p.getStatus() == PaymentStatus.RELEASED);
        boolean depositPaid =
                paymentRepository
                        .findAllByContractIdAndPaymentType(
                                contract.getId(),
                                com.sam.be.common.constant.enums.PaymentType.SECURITY_DEPOSIT)
                        .stream()
                        .anyMatch(
                                p ->
                                        p.getStatus() == PaymentStatus.HELD_IN_ESCROW
                                                || p.getStatus() == PaymentStatus.RELEASED);
        if (!fundPaid || !depositPaid) {
            return;
        }
        job.setStatus(com.sam.be.common.constant.enums.JobStatus.IN_PROGRESS);
        jobRepository.save(job);
        pushPaymentEvent(
                contract, null, "PROJECT_STARTED", "Đã nạp đủ tiền, dự án chính thức bắt đầu!");
        log.info("Project started for contract {}", contract.getId());
    }

    @Override
    @Transactional
    public PaymentResponse releaseEscrow(UUID userId, UUID paymentId) {
        Payment payment =
                paymentRepository
                        .findById(paymentId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        Contract contract = payment.getContract();
        boolean isOwner =
                contract.getClient() != null && contract.getClient().getId().equals(userId);
        if (!isOwner && !SecurityUtils.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        if (payment.getStatus() != PaymentStatus.HELD_IN_ESCROW) {
            throw new ApiException(ErrorCode.REQUEST_FAILED);
        }

        payment.setStatus(PaymentStatus.RELEASED);
        payment.setReleasedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        pushPaymentEvent(
                contract,
                payment,
                "PAYMENT_RELEASED",
                "Tiền ký quỹ (" + payment.getAmount() + " VND) đã được giải ngân.");
        log.info("Escrow released for payment {}", payment.getId());
        return toResponse(payment, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getByContract(UUID userId, UUID contractId) {
        Contract contract = contractService.getContractById(contractId);

        boolean isClient =
                contract.getClient() != null && contract.getClient().getId().equals(userId);
        boolean isFreelancer =
                contract.getFreelancer() != null && contract.getFreelancer().getId().equals(userId);
        if (!isClient && !isFreelancer && !SecurityUtils.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        return paymentRepository.findAllByContractId(contractId).stream()
                .sorted(Comparator.comparing(Payment::getInstallmentNo))
                .map(p -> toResponse(p, null))
                .toList();
    }

    private void pushPaymentEvent(Contract contract, Payment payment, String type, String message) {
        notificationService.sendPaymentNotification(
                contract.getClient().getId(),
                contract.getId(),
                payment != null ? payment.getId() : null,
                payment != null ? payment.getAmount() : null,
                type,
                message);
        notificationService.sendPaymentNotification(
                contract.getFreelancer().getId(),
                contract.getId(),
                payment != null ? payment.getId() : null,
                payment != null ? payment.getAmount() : null,
                type,
                message);
    }

    private PaymentResponse toResponse(Payment payment, String vnpayUrl) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .contractId(payment.getContract().getId())
                .installment(
                        payment.getPaymentType() != null
                                ? payment.getPaymentType().name()
                                : null)
                .paymentType(payment.getPaymentType())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .vnpayUrl(vnpayUrl)
                .escrowHeldAt(payment.getEscrowHeldAt())
                .releasedAt(payment.getReleasedAt())
                .build();
    }
}
