package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sam.be.common.constant.enums.PaymentStatus;
import com.sam.be.common.constant.enums.PaymentType;
import com.sam.be.modules.contract.entity.Contract;
import com.sam.be.modules.payment.entity.Payment;
import com.sam.be.modules.payment.repository.PaymentRepository;
import com.sam.be.modules.payment.service.impl.PaymentServiceImpl;
import com.sam.be.modules.user.entity.User;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class PaymentFundingTrustTest {
    @Test
    void browserConfirmationCannotMintEscrow() {
        UUID actor = UUID.randomUUID(), paymentId = UUID.randomUUID();
        var repository = mock(PaymentRepository.class);
        Contract contract =
                Contract.builder()
                        .id(UUID.randomUUID())
                        .client(User.builder().id(actor).build())
                        .freelancer(User.builder().id(UUID.randomUUID()).build())
                        .build();
        Payment payment =
                Payment.builder()
                        .id(paymentId)
                        .contract(contract)
                        .paymentType(PaymentType.CONTRACT_FUND)
                        .amount(new BigDecimal("10000000"))
                        .status(PaymentStatus.PENDING)
                        .build();
        when(repository.findById(paymentId)).thenReturn(Optional.of(payment));
        var service =
                new PaymentServiceImpl(
                        repository,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        var result = service.confirmFunding(actor, paymentId, null);
        assertEquals(PaymentStatus.PENDING, result.getStatus());
        assertNull(payment.getVerifiedAt());
        verify(repository, never()).save(any());
    }

    @Test
    void anotherMemberCannotConfirmTheOtherPersonsFunding() {
        UUID client = UUID.randomUUID(),
                freelancer = UUID.randomUUID(),
                paymentId = UUID.randomUUID();
        var repository = mock(PaymentRepository.class);
        var contract =
                Contract.builder()
                        .id(UUID.randomUUID())
                        .client(User.builder().id(client).build())
                        .freelancer(User.builder().id(freelancer).build())
                        .build();
        var payment =
                Payment.builder()
                        .id(paymentId)
                        .contract(contract)
                        .paymentType(PaymentType.CONTRACT_FUND)
                        .amount(BigDecimal.TEN)
                        .build();
        when(repository.findById(paymentId)).thenReturn(Optional.of(payment));
        var service =
                new PaymentServiceImpl(
                        repository,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        assertThrows(
                com.sam.be.common.exception.ApiException.class,
                () -> service.confirmFunding(freelancer, paymentId, null));
        verify(repository, never()).save(any());
    }
}
