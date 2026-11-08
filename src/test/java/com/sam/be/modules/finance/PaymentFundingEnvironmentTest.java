package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sam.be.common.constant.enums.*;
import com.sam.be.common.exception.ApiException;
import com.sam.be.infrastructure.cache.service.*;
import com.sam.be.modules.ai.dto.response.AiAmountVerification;
import com.sam.be.modules.ai.service.AiService;
import com.sam.be.modules.contract.entity.Contract;
import com.sam.be.modules.contract.repository.ContractRepository;
import com.sam.be.modules.contract.service.ContractService;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.payment.dto.request.CreateEscrowRequest;
import com.sam.be.modules.payment.entity.Payment;
import com.sam.be.modules.payment.repository.PaymentRepository;
import com.sam.be.modules.payment.service.CheckoutService;
import com.sam.be.modules.payment.service.impl.PaymentServiceImpl;
import com.sam.be.modules.user.entity.User;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentFundingEnvironmentTest {
    private final UUID actor = UUID.randomUUID();
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final CheckoutService checkouts = mock(CheckoutService.class);
    private final Pay2sClient provider = mock(Pay2sClient.class);
    private final ContractRepository contracts = mock(ContractRepository.class);
    private final ContractService contractService = mock(ContractService.class);
    private final AiService ai = mock(AiService.class);
    private PaymentServiceImpl service;
    private Contract contract;

    @BeforeEach
    void setup() {
        contract =
                Contract.builder()
                        .id(UUID.randomUUID())
                        .status(ContractStatus.ACTIVE)
                        .agreedAmount(new BigDecimal("1000000"))
                        .termsAndConditions("contract")
                        .client(User.builder().id(actor).build())
                        .freelancer(User.builder().id(UUID.randomUUID()).build())
                        .job(
                                Job.builder()
                                        .id(UUID.randomUUID())
                                        .status(JobStatus.AWAITING_PAYMENT)
                                        .build())
                        .build();
        when(provider.environment()).thenReturn("LIVE");
        when(contracts.findByIdForSettlement(contract.getId())).thenReturn(Optional.of(contract));
        when(contractService.getContractById(contract.getId())).thenReturn(contract);
        var verification = new AiAmountVerification();
        verification.setMatches(true);
        when(ai.verifyContractAmount(anyString(), any())).thenReturn(verification);
        var returns = mock(PaymentReturnPolicy.class);
        when(returns.resolve(any())).thenReturn("https://sam.example/payment/return");
        when(payments.save(any()))
                .thenAnswer(
                        call -> {
                            Payment payment = call.getArgument(0);
                            if (payment.getId() == null) payment.setId(UUID.randomUUID());
                            return payment;
                        });
        var checkout =
                new CheckoutService.Checkout(
                        UUID.randomUUID(),
                        "CONTRACT_FUND",
                        UUID.randomUUID(),
                        new BigDecimal("1000000"),
                        "VND",
                        "PAY2S",
                        "LIVE",
                        "CREATING",
                        "NONE",
                        null,
                        "SAMTEST",
                        "2026-11-08T12:15:00Z",
                        null,
                        null,
                        "SAMTEST",
                        false);
        when(checkouts.create(anyString(), any(), any(), any(), any())).thenReturn(checkout);
        service =
                new PaymentServiceImpl(
                        payments,
                        checkouts,
                        contracts,
                        provider,
                        returns,
                        mock(FinanceStore.class),
                        contractService,
                        ai,
                        mock(NotificationService.class),
                        mock(JobRepository.class),
                        mock(RedisCacheService.class),
                        mock(CacheInvalidationService.class));
    }

    private Payment payment(String environment, PaymentType type, PaymentStatus status) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .contract(contract)
                .environment(environment)
                .provider("PAY2S")
                .paymentType(type)
                .status(status)
                .amount(new BigDecimal("1000000"))
                .build();
    }

    @Test
    void oldSandboxPendingDoesNotBlockOrBecomeLiveFunding() {
        var old = payment("SANDBOX", PaymentType.CONTRACT_FUND, PaymentStatus.PENDING);
        when(payments.findAllByContractIdAndPaymentType(
                        contract.getId(), PaymentType.CONTRACT_FUND))
                .thenReturn(List.of(old));
        var response =
                service.createContractFund(actor, new CreateEscrowRequest(contract.getId(), null));
        assertEquals("LIVE", response.getEnvironment());
        assertNotEquals(old.getId(), response.getPaymentId());
        assertEquals("SANDBOX", old.getEnvironment());
        assertEquals(PaymentStatus.PENDING, old.getStatus());
        verify(checkouts)
                .create(eq("CONTRACT_FUND"), eq(response.getPaymentId()), eq(actor), any(), any());
    }

    @Test
    void livePendingIsReusedEvenWhenSandboxPaymentAppearsFirst() {
        var old = payment("SANDBOX", PaymentType.CONTRACT_FUND, PaymentStatus.PENDING);
        var current = payment("LIVE", PaymentType.CONTRACT_FUND, PaymentStatus.PENDING);
        when(payments.findAllByContractIdAndPaymentType(
                        contract.getId(), PaymentType.CONTRACT_FUND))
                .thenReturn(List.of(old, current));
        var response =
                service.createContractFund(actor, new CreateEscrowRequest(contract.getId(), null));
        assertEquals(current.getId(), response.getPaymentId());
    }

    @Test
    void liveAlreadyPaidStillPreventsAnotherCharge() {
        var paid = payment("LIVE", PaymentType.CONTRACT_FUND, PaymentStatus.HELD_IN_ESCROW);
        when(payments.findAllByContractIdAndPaymentType(
                        contract.getId(), PaymentType.CONTRACT_FUND))
                .thenReturn(List.of(paid));
        assertThrows(
                ApiException.class,
                () ->
                        service.createContractFund(
                                actor, new CreateEscrowRequest(contract.getId(), null)));
        verify(checkouts, never()).create(anyString(), any(), any(), any(), any());
    }

    @Test
    void sandboxPaymentNeverSatisfiesLiveFundingStatus() {
        when(payments.findAllByContractIdAndPaymentType(
                        contract.getId(), PaymentType.CONTRACT_FUND))
                .thenReturn(
                        List.of(
                                payment(
                                        "LIVE",
                                        PaymentType.CONTRACT_FUND,
                                        PaymentStatus.HELD_IN_ESCROW)));
        when(payments.findAllByContractIdAndPaymentType(
                        contract.getId(), PaymentType.SECURITY_DEPOSIT))
                .thenReturn(
                        List.of(
                                payment(
                                        "SANDBOX",
                                        PaymentType.SECURITY_DEPOSIT,
                                        PaymentStatus.HELD_IN_ESCROW)));
        var status = service.getFundingStatus(actor, contract.getId());
        assertTrue(status.isFundPaid());
        assertFalse(status.isDepositPaid());
        assertFalse(status.isAllPaid());
    }
}
