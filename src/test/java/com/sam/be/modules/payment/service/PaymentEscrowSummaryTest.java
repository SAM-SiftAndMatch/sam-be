package com.sam.be.modules.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sam.be.common.constant.enums.PaymentStatus;
import com.sam.be.common.constant.enums.PaymentType;
import com.sam.be.infrastructure.cache.keys.RedisKeys;
import com.sam.be.infrastructure.cache.service.RedisCacheService;
import com.sam.be.modules.payment.dto.response.FreelancerEscrowSummaryResponse;
import com.sam.be.modules.payment.repository.PaymentRepository;
import com.sam.be.modules.payment.service.impl.PaymentServiceImpl;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class PaymentEscrowSummaryTest {

    @Test
    void escrowSummaryUsesAggregateAndHandlesEmptyAmounts() {
        PaymentRepository repository = mock(PaymentRepository.class);
        RedisCacheService cache = mock(RedisCacheService.class);
        PaymentRepository.EscrowTotals totals = mock(PaymentRepository.EscrowTotals.class);
        UUID freelancerId = UUID.randomUUID();
        when(cache.getOrSet(
                        eq(RedisKeys.freelancerEscrowSummary(freelancerId)),
                        eq(Duration.ofSeconds(30)),
                        eq(FreelancerEscrowSummaryResponse.class),
                        any()))
                .thenAnswer(
                        invocation -> {
                            Supplier<?> loader = invocation.getArgument(3);
                            return loader.get();
                        });
        when(repository.summarizeFreelancerEscrow(
                        eq(freelancerId),
                        eq(PaymentType.CONTRACT_FUND),
                        eq(PaymentType.SECURITY_DEPOSIT),
                        eq(PaymentStatus.HELD_IN_ESCROW),
                        eq(PaymentStatus.RELEASED)))
                .thenReturn(totals);
        when(totals.getHeldFunds()).thenReturn(new BigDecimal("1000.00"));
        when(totals.getLockedDeposit()).thenReturn(new BigDecimal("20.00"));
        when(totals.getFundedProjectCount()).thenReturn(1L);
        var service =
                new PaymentServiceImpl(
                        repository, null, null, null, null, null, null, null, cache, null);

        var result = service.getFreelancerEscrowSummary(freelancerId, freelancerId);

        assertEquals(new BigDecimal("900.00"), result.getHeldForPayout());
        assertEquals(new BigDecimal("0.00"), result.getReleasedToFreelancer());
        assertEquals(new BigDecimal("20.00"), result.getLockedSecurityDeposit());
        assertEquals(new BigDecimal("100.00"), result.getPlatformFeePending());
        assertEquals(1, result.getFundedProjectCount());
        verify(cache)
                .getOrSet(
                        eq(RedisKeys.freelancerEscrowSummary(freelancerId)),
                        eq(Duration.ofSeconds(30)),
                        eq(FreelancerEscrowSummaryResponse.class),
                        any());
    }

    @Test
    void escrowSummaryCacheHitSkipsAggregateQuery() {
        PaymentRepository repository = mock(PaymentRepository.class);
        RedisCacheService cache = mock(RedisCacheService.class);
        UUID freelancerId = UUID.randomUUID();
        FreelancerEscrowSummaryResponse cached =
                FreelancerEscrowSummaryResponse.builder().freelancerId(freelancerId).build();
        when(cache.getOrSet(
                        eq(RedisKeys.freelancerEscrowSummary(freelancerId)),
                        eq(Duration.ofSeconds(30)),
                        eq(FreelancerEscrowSummaryResponse.class),
                        any()))
                .thenReturn(cached);
        var service =
                new PaymentServiceImpl(
                        repository, null, null, null, null, null, null, null, cache, null);

        assertSame(cached, service.getFreelancerEscrowSummary(freelancerId, freelancerId));
        verifyNoInteractions(repository);
    }
}
