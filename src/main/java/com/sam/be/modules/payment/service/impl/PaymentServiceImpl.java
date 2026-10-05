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
import com.sam.be.modules.payment.dto.request.CreateEscrowRequest;
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

    private static final BigDecimal ESCROW_RATIO = new BigDecimal("0.5");

    private final PaymentRepository paymentRepository;

    private final ContractService contractService;

    private final VnpayClient vnpayClient;

    private final VnpaySigner vnpaySigner;

    private final VnpayProperties vnpayProperties;

    private final NotificationService notificationService;

    @Override
    @Transactional
    public PaymentResponse createEscrow(UUID userId, CreateEscrowRequest request) {
        Contract contract = contractService.getContractById(request.getContractId());

        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONTRACT_NOT_ACTIVE);
        }
        if (contract.getClient() == null || !contract.getClient().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        List<Payment> existing = paymentRepository.findAllByContractId(contract.getId());
        boolean hasUnfinished =
                existing.stream()
                        .anyMatch(
                                p ->
                                        p.getStatus() == PaymentStatus.PENDING
                                                || p.getStatus() == PaymentStatus.HELD_IN_ESCROW);
        if (hasUnfinished) {
            throw new ApiException(ErrorCode.ESCROW_PAYMENT_EXISTS);
        }
        boolean maxedOut = existing.stream().anyMatch(p -> p.getInstallmentNo() == 2);
        if (maxedOut) {
            throw new ApiException(ErrorCode.ESCROW_PAYMENT_EXISTS);
        }

        boolean hasReleased =
                existing.stream().anyMatch(p -> p.getStatus() == PaymentStatus.RELEASED);
        int installmentNo = hasReleased ? 2 : 1;

        BigDecimal amount =
                contract.getAgreedAmount().multiply(ESCROW_RATIO).setScale(2, RoundingMode.HALF_UP);

        Payment payment =
                Payment.builder()
                        .contract(contract)
                        .amount(amount)
                        .currency("VND")
                        .status(PaymentStatus.PENDING)
                        .installmentNo(installmentNo)
                        .build();
        payment = paymentRepository.save(payment);
        String txnRef = payment.getId().toString().replace("-", "");
        payment.setPaymentGatewayId(txnRef);

        String returnUrl = request.getReturnUrl();
        if (returnUrl != null && !returnUrl.isBlank()) {
            if (!vnpayProperties.isReturnUrlAllowed(returnUrl)) {
                throw new ApiException(ErrorCode.INVALID_RETURN_URL);
            }
        } else {
            returnUrl = vnpayProperties.getDefaultReturnUrl();
        }

        long amountVnd = amount.multiply(BigDecimal.valueOf(100)).longValueExact();
        String vnpayUrl =
                vnpayClient.buildPaymentUrl(
                        txnRef,
                        amountVnd,
                        "Thanh toán ký quỹ hợp đồng " + contract.getId(),
                        null,
                        returnUrl);

        log.info(
                "Created escrow payment {} (installment {}) for contract {}",
                payment.getId(),
                installmentNo,
                contract.getId());
        return toResponse(payment, vnpayUrl);
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
        }
        return Map.of("RspCode", "00", "Message", "Confirm Success");
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
                payment.getId(),
                payment.getAmount(),
                type,
                message);
        notificationService.sendPaymentNotification(
                contract.getFreelancer().getId(),
                contract.getId(),
                payment.getId(),
                payment.getAmount(),
                type,
                message);
    }

    private PaymentResponse toResponse(Payment payment, String vnpayUrl) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .contractId(payment.getContract().getId())
                .installment(payment.getInstallmentNo() == 1 ? "DEPOSIT" : "FINAL")
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .vnpayUrl(vnpayUrl)
                .escrowHeldAt(payment.getEscrowHeldAt())
                .releasedAt(payment.getReleasedAt())
                .build();
    }
}
