package com.sam.be.modules.wallet.service.impl;

import com.sam.be.common.constant.enums.*;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.infrastructure.cache.service.CacheInvalidationService;
import com.sam.be.modules.contract.entity.Contract;
import com.sam.be.modules.contract.repository.ContractRepository;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.payment.entity.Payment;
import com.sam.be.modules.payment.repository.PaymentRepository;
import com.sam.be.modules.wallet.dto.response.ProjectSettlementResponse;
import com.sam.be.modules.wallet.entity.ProjectSettlement;
import com.sam.be.modules.wallet.repository.ProjectSettlementRepository;
import com.sam.be.modules.wallet.service.ProjectSettlementService;
import com.sam.be.modules.workflow.entity.ProjectDelivery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectSettlementServiceImpl implements ProjectSettlementService {

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.10");

    private final ContractRepository contractRepository;
    private final com.sam.be.modules.finance.FinanceLedgerService financeLedger;
    private final PaymentRepository paymentRepository;
    private final ProjectSettlementRepository settlementRepository;
    private final JobRepository jobRepository;
    private final NotificationService notificationService;
    private final CacheInvalidationService cacheInvalidationService;

    @Override
    @Transactional
    public ProjectSettlementResponse settle(UUID contractId, ProjectDelivery delivery) {
        Contract contract =
                contractRepository
                        .findByIdForSettlement(contractId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        var existing = settlementRepository.findByContractId(contractId);
        if (existing.isPresent()) return toResponse(existing.get());
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED, "Hợp đồng không còn ở trạng thái thực hiện");
        }

        List<Payment> payments = paymentRepository.findAllByContractIdForSettlement(contractId);
        Payment fund = requireHeldPayment(payments, PaymentType.CONTRACT_FUND);
        Payment deposit = requireHeldPayment(payments, PaymentType.SECURITY_DEPOSIT);
        BigDecimal gross = fund.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal platformFee =
                gross.multiply(PLATFORM_FEE_RATE).setScale(0, RoundingMode.HALF_UP).setScale(2);
        BigDecimal payout = gross.subtract(platformFee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal depositRefund = deposit.getAmount().setScale(2, RoundingMode.HALF_UP);
        LocalDateTime now = delivery.getReviewedAt();
        if (financeLedger.isLive(fund) != financeLedger.isLive(deposit))
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED,
                    "Không thể quyết toán gộp thanh toán thật và sandbox");
        boolean live = financeLedger.isLive(fund) && financeLedger.isLive(deposit);

        ProjectSettlement settlement =
                settlementRepository.saveAndFlush(
                        ProjectSettlement.builder()
                                .contract(contract)
                                .delivery(delivery)
                                .grossAmount(gross)
                                .freelancerPayout(payout)
                                .platformFee(platformFee)
                                .depositRefund(depositRefund)
                                .status(
                                        live
                                                ? SettlementStatus.POSTED
                                                : SettlementStatus.SIMULATED_COMPLETED)
                                .completedAt(now)
                                .build());

        financeLedger.recordSettlement(contract, settlement, fund, deposit);

        fund.setStatus(PaymentStatus.SETTLED);
        fund.setReleasedAt(now);
        deposit.setStatus(PaymentStatus.SETTLED);
        deposit.setReleasedAt(now);
        paymentRepository.saveAll(List.of(fund, deposit));

        contract.setStatus(ContractStatus.COMPLETED);
        contract.setCompletedAt(now);
        contract.getJob().setStatus(JobStatus.COMPLETED);
        contractRepository.save(contract);
        jobRepository.save(contract.getJob());
        cacheInvalidationService.paymentsChanged(contractId, contract.getFreelancer().getId());
        cacheInvalidationService.completedContractChanged(contract.getJob().getId());
        cacheInvalidationService.jobChanged(
                contract.getJob().getId(),
                contract.getClient().getId(),
                contract.getFreelancer().getId());

        notificationService.sendPaymentNotification(
                contract.getFreelancer().getId(),
                contract.getId(),
                fund.getId(),
                payout.add(depositRefund),
                "PROJECT_SETTLEMENT_POSTED",
                "Đã nghiệm thu và ghi công nợ; chỉ được rút sau 7 ngày bảo trì và xử lý xong ticket.");
        notificationService.sendPaymentNotification(
                contract.getClient().getId(),
                contract.getId(),
                fund.getId(),
                gross,
                "PROJECT_SETTLEMENT_POSTED",
                "Đã nghiệm thu. Bạn có 7 ngày để mở ticket bảo trì trong không gian dự án.");
        return toResponse(settlement);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectSettlementResponse getByContract(UUID requesterId, UUID contractId) {
        Contract contract =
                contractRepository
                        .findById(contractId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        boolean isMember =
                contract.getClient().getId().equals(requesterId)
                        || contract.getFreelancer().getId().equals(requesterId);
        if (!isMember && !SecurityUtils.isAdmin())
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        ProjectSettlement settlement =
                settlementRepository
                        .findByContractId(contractId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return toResponse(settlement);
    }

    private Payment requireHeldPayment(List<Payment> payments, PaymentType type) {
        return payments.stream()
                .filter(payment -> payment.getPaymentType() == type)
                .filter(payment -> payment.getStatus() == PaymentStatus.HELD_IN_ESCROW)
                .findFirst()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.REQUEST_FAILED,
                                        "Khoản ký quỹ chưa sẵn sàng để quyết toán"));
    }

    private ProjectSettlementResponse toResponse(ProjectSettlement settlement) {
        return ProjectSettlementResponse.builder()
                .id(settlement.getId())
                .contractId(settlement.getContract().getId())
                .deliveryId(settlement.getDelivery().getId())
                .grossAmount(settlement.getGrossAmount())
                .freelancerPayout(settlement.getFreelancerPayout())
                .platformFee(settlement.getPlatformFee())
                .depositRefund(settlement.getDepositRefund())
                .status(settlement.getStatus())
                .completedAt(settlement.getCompletedAt())
                .sandbox(settlement.getStatus() == SettlementStatus.SIMULATED_COMPLETED)
                .build();
    }
}
