package com.sam.be.modules.wallet.service.impl;

import com.sam.be.common.constant.enums.*;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.contract.entity.Contract;
import com.sam.be.modules.contract.repository.ContractRepository;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.payment.entity.Payment;
import com.sam.be.modules.payment.repository.PaymentRepository;
import com.sam.be.modules.wallet.dto.response.ProjectSettlementResponse;
import com.sam.be.modules.wallet.entity.ProjectSettlement;
import com.sam.be.modules.wallet.entity.WalletAccount;
import com.sam.be.modules.wallet.entity.WalletLedgerEntry;
import com.sam.be.modules.wallet.repository.ProjectSettlementRepository;
import com.sam.be.modules.wallet.repository.WalletAccountRepository;
import com.sam.be.modules.wallet.repository.WalletLedgerEntryRepository;
import com.sam.be.modules.wallet.service.SandboxSettlementService;
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
public class SandboxSettlementServiceImpl implements SandboxSettlementService {

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.10");

    private final ContractRepository contractRepository;
    private final PaymentRepository paymentRepository;
    private final ProjectSettlementRepository settlementRepository;
    private final WalletAccountRepository walletAccountRepository;
    private final WalletLedgerEntryRepository ledgerEntryRepository;
    private final JobRepository jobRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ProjectSettlementResponse settle(UUID contractId, ProjectDelivery delivery) {
        Contract contract =
                contractRepository
                        .findByIdForSettlement(contractId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (settlementRepository.findByContractId(contractId).isPresent()) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Hợp đồng này đã được quyết toán");
        }
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED, "Hợp đồng không còn ở trạng thái thực hiện");
        }

        List<Payment> payments = paymentRepository.findAllByContractIdForSettlement(contractId);
        Payment fund = requireHeldPayment(payments, PaymentType.CONTRACT_FUND);
        Payment deposit = requireHeldPayment(payments, PaymentType.SECURITY_DEPOSIT);
        BigDecimal gross = fund.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal platformFee =
                gross.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal payout = gross.subtract(platformFee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal depositRefund = deposit.getAmount().setScale(2, RoundingMode.HALF_UP);
        LocalDateTime now = LocalDateTime.now();

        ProjectSettlement settlement =
                settlementRepository.save(
                        ProjectSettlement.builder()
                                .contract(contract)
                                .delivery(delivery)
                                .grossAmount(gross)
                                .freelancerPayout(payout)
                                .platformFee(platformFee)
                                .depositRefund(depositRefund)
                                .status(SettlementStatus.SIMULATED_COMPLETED)
                                .completedAt(now)
                                .build());

        WalletAccount freelancerWallet = getOrCreateFreelancerWallet(contract);
        WalletAccount platformWallet = getOrCreatePlatformWallet();
        credit(
                freelancerWallet,
                settlement,
                contract,
                payout,
                WalletEntryType.PROJECT_PAYOUT,
                "Ghi có sandbox 90% giá trị hợp đồng");
        credit(
                freelancerWallet,
                settlement,
                contract,
                depositRefund,
                WalletEntryType.SECURITY_DEPOSIT_REFUND,
                "Hoàn cọc cam kết sandbox");
        credit(
                platformWallet,
                settlement,
                contract,
                platformFee,
                WalletEntryType.PLATFORM_COMMISSION,
                "Phí nền tảng sandbox 10% từ hợp đồng");

        fund.setStatus(PaymentStatus.RELEASED);
        fund.setReleasedAt(now);
        deposit.setStatus(PaymentStatus.REFUNDED);
        deposit.setReleasedAt(now);
        paymentRepository.saveAll(List.of(fund, deposit));

        contract.setStatus(ContractStatus.COMPLETED);
        contract.setCompletedAt(now);
        contract.getJob().setStatus(JobStatus.COMPLETED);
        contractRepository.save(contract);
        jobRepository.save(contract.getJob());

        notificationService.sendPaymentNotification(
                contract.getFreelancer().getId(),
                contract.getId(),
                fund.getId(),
                payout.add(depositRefund),
                "SANDBOX_SETTLEMENT_COMPLETED",
                "Dự án đã được nghiệm thu. Tiền đã được ghi có vào ví thử nghiệm.");
        notificationService.sendPaymentNotification(
                contract.getClient().getId(),
                contract.getId(),
                fund.getId(),
                gross,
                "SANDBOX_SETTLEMENT_COMPLETED",
                "Bạn đã nghiệm thu sản phẩm. Quyết toán sandbox đã hoàn tất.");
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

    private WalletAccount getOrCreateFreelancerWallet(Contract contract) {
        UUID freelancerId = contract.getFreelancer().getId();
        return walletAccountRepository
                .findByOwnerIdForUpdate(freelancerId)
                .orElseGet(
                        () ->
                                walletAccountRepository.save(
                                        WalletAccount.builder()
                                                .owner(contract.getFreelancer())
                                                .walletKey("freelancer:" + freelancerId)
                                                .walletType(WalletType.FREELANCER)
                                                .availableBalance(BigDecimal.ZERO)
                                                .build()));
    }

    private WalletAccount getOrCreatePlatformWallet() {
        return walletAccountRepository
                .findByWalletKeyForUpdate("platform")
                .orElseGet(
                        () ->
                                walletAccountRepository.save(
                                        WalletAccount.builder()
                                                .walletKey("platform")
                                                .walletType(WalletType.PLATFORM)
                                                .availableBalance(BigDecimal.ZERO)
                                                .build()));
    }

    private void credit(
            WalletAccount wallet,
            ProjectSettlement settlement,
            Contract contract,
            BigDecimal amount,
            WalletEntryType entryType,
            String description) {
        BigDecimal newBalance =
                wallet.getAvailableBalance().add(amount).setScale(2, RoundingMode.HALF_UP);
        wallet.setAvailableBalance(newBalance);
        walletAccountRepository.save(wallet);
        ledgerEntryRepository.save(
                WalletLedgerEntry.builder()
                        .wallet(wallet)
                        .settlement(settlement)
                        .contract(contract)
                        .entryType(entryType)
                        .amount(amount)
                        .balanceAfter(newBalance)
                        .description(description)
                        .build());
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
                .sandbox(true)
                .build();
    }
}
