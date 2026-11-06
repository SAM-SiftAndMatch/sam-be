package com.sam.be.modules.payment.service.impl;

import com.sam.be.common.constant.enums.ContractStatus;
import com.sam.be.common.constant.enums.PaymentStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.response.PageRequests;
import com.sam.be.common.response.PageResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.infrastructure.cache.keys.RedisKeys;
import com.sam.be.infrastructure.cache.service.CacheInvalidationService;
import com.sam.be.infrastructure.cache.service.RedisCacheService;
import com.sam.be.modules.contract.entity.Contract;
import com.sam.be.modules.contract.service.ContractService;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.payment.dto.request.ConfirmFundingRequest;
import com.sam.be.modules.payment.dto.request.CreateEscrowRequest;
import com.sam.be.modules.payment.dto.response.FreelancerEscrowSummaryResponse;
import com.sam.be.modules.payment.dto.response.FundingStatusResponse;
import com.sam.be.modules.payment.dto.response.PaymentResponse;
import com.sam.be.modules.payment.entity.Payment;
import com.sam.be.modules.payment.repository.PaymentRepository;
import com.sam.be.modules.payment.service.PaymentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final com.sam.be.modules.payment.service.CheckoutService checkouts;
    private final com.sam.be.modules.contract.repository.ContractRepository
            fundingContractRepository;
    private final com.sam.be.modules.finance.Pay2sClient pay2s;
    private final com.sam.be.modules.finance.PaymentReturnPolicy paymentReturns;
    private final com.sam.be.modules.finance.FinanceStore financeStore;

    private final ContractService contractService;

    private final com.sam.be.modules.ai.service.AiService aiService;

    private final NotificationService notificationService;

    private final com.sam.be.modules.job.repository.JobRepository jobRepository;

    private final RedisCacheService redisCacheService;
    private final CacheInvalidationService cacheInvalidationService;

    @Override
    @Transactional(noRollbackFor = com.sam.be.common.exception.PaymentGatewayException.class)
    public PaymentResponse createMaintenanceFunding(UUID actor, UUID orderId) {
        var order = financeStore.one("SELECT * FROM paid_maintenance_orders WHERE id=?", orderId);
        if (!actor.equals(com.sam.be.modules.finance.FinanceStore.id(order, "clientId")))
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        financeStore.lockOwner(com.sam.be.modules.finance.FinanceStore.id(order, "ownerId"));
        order =
                financeStore.one(
                        "SELECT * FROM paid_maintenance_orders WHERE id=? FOR UPDATE", orderId);
        if (!"AWAITING_PAYMENT".equals(order.get("status")))
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED, "Đơn bảo trì không còn chờ thanh toán");
        var contract =
                fundingContractRepository
                        .findByIdForSettlement(
                                com.sam.be.modules.finance.FinanceStore.id(order, "contractId"))
                        .orElseThrow();
        var result =
                createFundingPayment(
                        contract,
                        com.sam.be.common.constant.enums.PaymentType.MAINTENANCE_FUND,
                        com.sam.be.modules.finance.FinanceStore.money(order, "amount"),
                        "Bao tri du an " + orderId,
                        resolveReturnUrl(null),
                        orderId);
        var payment = paymentRepository.findById(result.getPaymentId()).orElseThrow();
        payment.setMaintenanceOrderId(orderId);
        paymentRepository.saveAndFlush(payment);
        financeStore.db.update(
                "UPDATE paid_maintenance_orders SET payment_id=? WHERE id=?",
                payment.getId(),
                orderId);
        return result;
    }

    @Override
    @Transactional(noRollbackFor = com.sam.be.common.exception.PaymentGatewayException.class)
    public PaymentResponse createContractFund(UUID userId, CreateEscrowRequest request) {
        Contract contract =
                fundingContractRepository
                        .findByIdForSettlement(request.getContractId())
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONTRACT_NOT_ACTIVE);
        }
        if (contract.getClient() == null || !contract.getClient().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        // Chốt số tiền bằng AI đọc văn bản: lệch là chặn, bắt sửa hợp đồng trước
        assertAmountVerified(contract);

        String okUrl = resolveReturnUrl(request.getReturnUrl());

        BigDecimal amount = contract.getAgreedAmount().setScale(2, RoundingMode.HALF_UP);
        return createFundingPayment(
                contract,
                com.sam.be.common.constant.enums.PaymentType.CONTRACT_FUND,
                amount,
                "Nop 100% gia tri hop dong " + contract.getId(),
                okUrl,
                null);
    }

    @Override
    @Transactional(noRollbackFor = com.sam.be.common.exception.PaymentGatewayException.class)
    public PaymentResponse createSecurityDeposit(UUID userId, CreateEscrowRequest request) {
        Contract contract =
                fundingContractRepository
                        .findByIdForSettlement(request.getContractId())
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONTRACT_NOT_ACTIVE);
        }
        if (contract.getFreelancer() == null || !contract.getFreelancer().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        assertAmountVerified(contract);

        String okUrl = resolveReturnUrl(request.getReturnUrl());

        // Cọc cam kết 2%, làm tròn tới đồng
        BigDecimal amount =
                contract.getAgreedAmount()
                        .multiply(DEPOSIT_RATE)
                        .setScale(0, RoundingMode.HALF_UP)
                        .setScale(2);
        return createFundingPayment(
                contract,
                com.sam.be.common.constant.enums.PaymentType.SECURITY_DEPOSIT,
                amount,
                "Dat coc cam ket 2% hop dong " + contract.getId(),
                okUrl,
                null);
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
        BigDecimal fee =
                agreed.multiply(PLATFORM_FEE_RATE).setScale(0, RoundingMode.HALF_UP).setScale(2);
        BigDecimal payout = agreed.subtract(fee);

        PaymentStatus fundStatus =
                latestStatus(
                        contractId, com.sam.be.common.constant.enums.PaymentType.CONTRACT_FUND);
        PaymentStatus depositStatus =
                latestStatus(
                        contractId, com.sam.be.common.constant.enums.PaymentType.SECURITY_DEPOSIT);

        boolean fundPaid =
                fundStatus == PaymentStatus.HELD_IN_ESCROW
                        || fundStatus == PaymentStatus.RELEASED
                        || fundStatus == PaymentStatus.SETTLED;
        boolean depositPaid =
                depositStatus == PaymentStatus.HELD_IN_ESCROW
                        || depositStatus == PaymentStatus.RELEASED
                        || depositStatus == PaymentStatus.REFUNDED
                        || depositStatus == PaymentStatus.SETTLED;

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
        List<Payment> all = paymentRepository.findAllByContractIdAndPaymentType(contractId, type);
        // Ưu tiên trạng thái đã được nạp hoặc đã hoàn cọc, rồi tới đơn đang chờ.
        return all.stream()
                .map(Payment::getStatus)
                .filter(s -> s != PaymentStatus.EXPIRED)
                .sorted((a, b) -> Integer.compare(statusRank(a), statusRank(b)))
                .findFirst()
                .orElse(null);
    }

    private int statusRank(PaymentStatus s) {
        if (s == PaymentStatus.HELD_IN_ESCROW
                || s == PaymentStatus.RELEASED
                || s == PaymentStatus.REFUNDED
                || s == PaymentStatus.SETTLED) return 0;
        if (s == PaymentStatus.PENDING) return 1;
        return 2;
    }

    private String resolveReturnUrl(String returnUrl) {
        return paymentReturns.resolve(returnUrl);
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

    private PaymentResponse createFundingPayment(
            Contract contract,
            com.sam.be.common.constant.enums.PaymentType type,
            BigDecimal amount,
            String orderInfo,
            String returnUrl,
            UUID maintenanceOrder) {
        if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0)
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR, "Thanh toán VND phải là số nguyên dương");
        var existing =
                paymentRepository.findAllByContractIdAndPaymentType(contract.getId(), type).stream()
                        .filter(
                                p ->
                                        java.util.Objects.equals(
                                                p.getMaintenanceOrderId(), maintenanceOrder))
                        .toList();
        if (existing.stream()
                .anyMatch(
                        p ->
                                Set.of(
                                                PaymentStatus.HELD_IN_ESCROW,
                                                PaymentStatus.SETTLED,
                                                PaymentStatus.RELEASED)
                                        .contains(p.getStatus())))
            throw new ApiException(ErrorCode.ESCROW_PAYMENT_EXISTS);
        Payment payment =
                existing.stream()
                        .filter(
                                p ->
                                        p.getStatus() == PaymentStatus.PENDING
                                                && "PAY2S".equals(p.getProvider()))
                        .findFirst()
                        .orElseGet(
                                () ->
                                        paymentRepository.save(
                                                Payment.builder()
                                                        .contract(contract)
                                                        .maintenanceOrderId(maintenanceOrder)
                                                        .paymentType(type)
                                                        .amount(amount)
                                                        .currency("VND")
                                                        .status(PaymentStatus.PENDING)
                                                        .installmentNo(1)
                                                        .provider("PAY2S")
                                                        .environment(pay2s.environment())
                                                        .build()));
        if (!pay2s.environment().equals(payment.getEnvironment()))
            throw com.sam.be.modules.finance.FinanceStore.invalid(
                    "Khoản thanh toán thuộc môi trường khác");
        UUID payer =
                type == com.sam.be.common.constant.enums.PaymentType.SECURITY_DEPOSIT
                        ? contract.getFreelancer().getId()
                        : contract.getClient().getId();
        var checkout =
                checkouts.create(
                        type.name(), payment.getId(), payer, payment.getAmount(), returnUrl);
        payment.setPaymentGatewayId(checkout.orderId().toString().replace("-", ""));
        payment.setCheckoutUrl(checkout.checkoutUrl());
        paymentRepository.save(payment);
        cacheInvalidationService.paymentsChanged(
                contract.getId(), contract.getFreelancer().getId());
        return toResponse(payment, checkout.checkoutUrl());
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
        if (payment.getPaymentType() == com.sam.be.common.constant.enums.PaymentType.CONTRACT_FUND
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
                || payment.getStatus() == PaymentStatus.RELEASED
                || payment.getStatus() == PaymentStatus.SETTLED) {
            return toResponse(payment, null);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ApiException(ErrorCode.REQUEST_FAILED);
        }

        // Browser redirects are navigation only. Only verified provider IPN changes funding.
        return toResponse(payment, payment.getCheckoutUrl());
    }

    @Override
    @Transactional
    public Map<String, Boolean> handlePay2sIpn(com.fasterxml.jackson.databind.JsonNode payload) {
        if (!pay2s.verify(payload))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid Pay2S signature");
        String order = payload.path("orderId").asText();
        Payment payment =
                paymentRepository
                        .findByPaymentGatewayId(order)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"PAY2S".equals(payment.getProvider())
                || !pay2s.environment().equals(payment.getEnvironment()))
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        financeStore.lockOwner(payment.getContract().getFreelancer().getId());
        fundingContractRepository
                .findByIdForSettlement(payment.getContract().getId())
                .orElseThrow();
        payment = paymentRepository.findByIdForFunding(payment.getId()).orElseThrow();
        java.math.BigDecimal amount;
        try {
            amount = new java.math.BigDecimal(payload.path("amount").asText());
            if (amount.signum() <= 0
                    || amount.stripTrailingZeros().scale() > 0
                    || amount.precision() > 15) throw new IllegalArgumentException();
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Invalid Pay2S amount");
        }
        String transaction = payload.path("transId").asText();
        if (transaction.isBlank() || transaction.length() > 150)
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Invalid Pay2S transaction");
        if ("0".equals(payload.path("resultCode").asText()))
            recordReceipt(payment, transaction, amount);
        else if ("2".equals(payload.path("resultCode").asText())
                && amount.compareTo(payment.getAmount()) == 0
                && payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.EXPIRED);
            paymentRepository.save(payment);
            cacheInvalidationService.paymentsChanged(
                    payment.getContract().getId(), payment.getContract().getFreelancer().getId());
        }
        return Map.of("success", true);
    }

    @Override
    @Transactional
    public String applyVerifiedPay2sPayment(
            UUID paymentId, String order, String transaction, BigDecimal amount) {
        Payment payment =
                paymentRepository
                        .findById(paymentId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"PAY2S".equals(payment.getProvider())
                || !pay2s.environment().equals(payment.getEnvironment()))
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        financeStore.lockOwner(payment.getContract().getFreelancer().getId());
        fundingContractRepository
                .findByIdForSettlement(payment.getContract().getId())
                .orElseThrow();
        payment = paymentRepository.findByIdForFunding(payment.getId()).orElseThrow();
        payment.setPaymentGatewayId(order);
        recordReceipt(payment, transaction, amount);
        var receipt =
                financeStore.one(
                        "SELECT disposition FROM finance_receipts WHERE provider='PAY2S' AND environment=? AND provider_reference=?",
                        payment.getEnvironment(),
                        transaction);
        return "APPLIED".equals(receipt.get("disposition")) ? "APPLIED" : "REVIEW";
    }

    private void recordReceipt(Payment payment, String transaction, BigDecimal amount) {
        var existing =
                financeStore.rows(
                        "SELECT payment_id FROM finance_receipts WHERE provider=? AND environment=? AND provider_reference=?",
                        payment.getProvider(),
                        payment.getEnvironment(),
                        transaction);
        if (!existing.isEmpty()) {
            if (!payment.getId()
                    .equals(
                            com.sam.be.modules.finance.FinanceStore.id(
                                    existing.getFirst(), "paymentId")))
                throw new ApiException(
                        ErrorCode.REQUEST_FAILED, "Transaction reference already assigned");
            return;
        }
        boolean otherFunded =
                payment.getMaintenanceOrderId() == null
                        && paymentRepository
                                .findAllByContractIdAndPaymentType(
                                        payment.getContract().getId(), payment.getPaymentType())
                                .stream()
                                .anyMatch(
                                        p ->
                                                !p.getId().equals(payment.getId())
                                                        && (p.getStatus()
                                                                        == PaymentStatus
                                                                                .HELD_IN_ESCROW
                                                                || p.getStatus()
                                                                        == PaymentStatus.RELEASED
                                                                || p.getStatus()
                                                                        == PaymentStatus.SETTLED));
        boolean applied =
                payment.getStatus() == PaymentStatus.PENDING
                        && amount.compareTo(payment.getAmount()) == 0
                        && !otherFunded;
        UUID receipt = UUID.randomUUID();
        financeStore.db.update(
                "INSERT INTO finance_receipts(id,payment_id,provider,environment,provider_reference,amount,disposition,created_at) VALUES (?,?,?,?,?,?,?,?)",
                receipt,
                payment.getId(),
                payment.getProvider(),
                payment.getEnvironment(),
                transaction,
                amount,
                applied ? "APPLIED" : "REVIEW",
                financeStore.time(financeStore.now()));
        String prefix = "LIVE".equals(payment.getEnvironment()) ? "live:" : "sandbox:";
        financeStore.journal(
                "receipt:" + receipt,
                payment.getContract().getId(),
                payment.getContract().getFreelancer().getId(),
                applied ? "Thanh toán đã xác minh" : "Tiền nhận cần đối soát",
                !"LIVE".equals(payment.getEnvironment()),
                com.sam.be.modules.finance.FinanceStore.debit(
                        prefix + "cash:platform-bank", amount),
                com.sam.be.modules.finance.FinanceStore.credit(
                        applied
                                ? prefix + "escrow:" + payment.getId()
                                : prefix + "unapplied:" + receipt,
                        amount));
        if (!applied) return;
        payment.setVerifiedAt(financeStore.now());
        payment.setProviderTransactionId(transaction);
        payment.setStatus(PaymentStatus.HELD_IN_ESCROW);
        payment.setEscrowHeldAt(LocalDateTime.now());
        paymentRepository.save(payment);
        pushPaymentEvent(
                payment.getContract(),
                payment,
                "PAYMENT_ESCROW_HELD",
                "Đã xác nhận tiền thanh toán từ nhà cung cấp.");
        cacheInvalidationService.paymentsChanged(
                payment.getContract().getId(), payment.getContract().getFreelancer().getId());
        if (payment.getMaintenanceOrderId() != null) {
            financeStore.db.update(
                    "UPDATE paid_maintenance_orders SET status=CASE WHEN status='DISPUTED' THEN status ELSE 'FUNDED' END,funded_at=?,due_at=?::timestamptz+delivery_days*INTERVAL '24 hours' WHERE id=? AND status IN ('AWAITING_PAYMENT','DISPUTED')",
                    financeStore.time(financeStore.now()),
                    financeStore.time(financeStore.now()),
                    payment.getMaintenanceOrderId());
        } else maybeStartProject(payment.getContract());
    }

    private void maybeStartProject(Contract contract) {
        var job = contract.getJob();
        if (job.getStatus() != com.sam.be.common.constant.enums.JobStatus.AWAITING_PAYMENT) {
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
                                                || p.getStatus() == PaymentStatus.RELEASED
                                                || p.getStatus() == PaymentStatus.SETTLED);
        boolean depositPaid =
                paymentRepository
                        .findAllByContractIdAndPaymentType(
                                contract.getId(),
                                com.sam.be.common.constant.enums.PaymentType.SECURITY_DEPOSIT)
                        .stream()
                        .anyMatch(
                                p ->
                                        p.getStatus() == PaymentStatus.HELD_IN_ESCROW
                                                || p.getStatus() == PaymentStatus.RELEASED
                                                || p.getStatus() == PaymentStatus.SETTLED);
        if (!fundPaid || !depositPaid) {
            return;
        }
        job.setStatus(com.sam.be.common.constant.enums.JobStatus.IN_PROGRESS);
        jobRepository.save(job);
        // Đồng bộ các cache liên quan sau khi giao dịch DB commit.
        cacheInvalidationService.jobChanged(
                job.getId(), contract.getClient().getId(), contract.getFreelancer().getId());
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
        if (!SecurityUtils.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        throw new ApiException(
                ErrorCode.REQUEST_FAILED,
                "Không thể đổi trạng thái escrow trực tiếp. Hãy nghiệm thu, xử lý ticket và chi trả qua nghiệp vụ công nợ.");
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

        return redisCacheService.getListOrSet(
                RedisKeys.contractPayments(contractId),
                Duration.ofMinutes(15),
                PaymentResponse.class,
                () ->
                        paymentRepository.findAllByContractId(contractId).stream()
                                .sorted(Comparator.comparing(Payment::getInstallmentNo))
                                .map(p -> toResponse(p, null))
                                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> getByContractPage(
            UUID userId, UUID contractId, int page, int size) {
        Contract contract = contractService.getContractById(contractId);
        boolean isClient =
                contract.getClient() != null && contract.getClient().getId().equals(userId);
        boolean isFreelancer =
                contract.getFreelancer() != null && contract.getFreelancer().getId().equals(userId);
        if (!isClient && !isFreelancer && !SecurityUtils.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        var pageable =
                PageRequests.of(
                        page,
                        size,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.asc("installmentNo"),
                                org.springframework.data.domain.Sort.Order.asc("id")));
        java.util.function.Supplier<PageResponse<PaymentResponse>> source =
                () ->
                        PageResponse.from(
                                paymentRepository
                                        .findAllByContractId(contractId, pageable)
                                        .map(payment -> toResponse(payment, null)));
        if (page >= 2) return source.get();
        return redisCacheService.getPageOrSet(
                RedisKeys.page(RedisKeys.contractPayments(contractId), page, size),
                Duration.ofMinutes(5),
                PaymentResponse.class,
                source);
    }

    @Override
    @Transactional(readOnly = true)
    public FreelancerEscrowSummaryResponse getFreelancerEscrowSummary(
            UUID requesterId, UUID freelancerId) {
        if (!requesterId.equals(freelancerId) && !SecurityUtils.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        return redisCacheService.getOrSet(
                RedisKeys.freelancerEscrowSummary(freelancerId),
                Duration.ofSeconds(30),
                FreelancerEscrowSummaryResponse.class,
                () -> loadFreelancerEscrowSummary(freelancerId));
    }

    private FreelancerEscrowSummaryResponse loadFreelancerEscrowSummary(UUID freelancerId) {
        var totals =
                paymentRepository.summarizeFreelancerEscrow(
                        freelancerId,
                        com.sam.be.common.constant.enums.PaymentType.CONTRACT_FUND,
                        com.sam.be.common.constant.enums.PaymentType.SECURITY_DEPOSIT,
                        PaymentStatus.HELD_IN_ESCROW,
                        PaymentStatus.RELEASED);
        BigDecimal heldFunds = zeroIfNull(totals != null ? totals.getHeldFunds() : null);
        BigDecimal releasedFunds = zeroIfNull(totals != null ? totals.getReleasedFunds() : null);
        BigDecimal lockedDeposit = zeroIfNull(totals != null ? totals.getLockedDeposit() : null);

        return FreelancerEscrowSummaryResponse.builder()
                .freelancerId(freelancerId)
                .currency("VND")
                .heldForPayout(netPayout(heldFunds))
                .lockedSecurityDeposit(lockedDeposit)
                .releasedToFreelancer(netPayout(releasedFunds))
                .platformFeePending(
                        heldFunds.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP))
                .fundedProjectCount(
                        totals != null && totals.getFundedProjectCount() != null
                                ? totals.getFundedProjectCount().intValue()
                                : 0)
                .build();
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal netPayout(BigDecimal grossAmount) {
        return grossAmount
                .subtract(grossAmount.multiply(PLATFORM_FEE_RATE))
                .setScale(2, RoundingMode.HALF_UP);
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

    private PaymentResponse toResponse(Payment payment, String checkoutUrl) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getPaymentGatewayId())
                .contractId(payment.getContract().getId())
                .installment(
                        payment.getPaymentType() != null ? payment.getPaymentType().name() : null)
                .paymentType(payment.getPaymentType())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .checkoutUrl(checkoutUrl)
                .provider(payment.getProvider())
                .environment(payment.getEnvironment())
                .escrowHeldAt(payment.getEscrowHeldAt())
                .releasedAt(payment.getReleasedAt())
                .build();
    }
}
