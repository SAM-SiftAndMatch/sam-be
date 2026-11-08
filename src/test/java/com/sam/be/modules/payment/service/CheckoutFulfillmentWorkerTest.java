package com.sam.be.modules.payment.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sam.be.modules.finance.FinanceStore;
import com.sam.be.modules.job.service.JobAddonService;
import com.sam.be.modules.subscription.service.SubscriptionService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class CheckoutFulfillmentWorkerTest {
    FinanceStore store = mock(FinanceStore.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    PaymentService payments = mock(PaymentService.class);
    SubscriptionService subscriptions = mock(SubscriptionService.class);
    JobAddonService addons = mock(JobAddonService.class);
    PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
    UUID id = UUID.randomUUID(), object = UUID.randomUUID();
    CheckoutFulfillmentWorker worker;

    @BeforeEach
    void setup() {
        when(store.jdbc()).thenReturn(db);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var row =
                Map.<String, Object>of(
                        "id",
                        id,
                        "objectId",
                        object,
                        "purpose",
                        "SUBSCRIPTION",
                        "environment",
                        "LIVE",
                        "amount",
                        new BigDecimal("149000"),
                        "ownerId",
                        UUID.randomUUID());
        when(store.rows(startsWith("SELECT * FROM payment_checkouts")))
                .thenReturn(List.of(row), List.of());
        worker = new CheckoutFulfillmentWorker(store, payments, subscriptions, addons, tm);
    }

    @Test
    void confirmedSubscriptionIsFulfilledAndMarkedApplied() {
        when(subscriptions.activateVerifiedPayment(object, "LIVE", new BigDecimal("149000")))
                .thenReturn("APPLIED");
        worker.tick();
        verify(subscriptions, times(1))
                .activateVerifiedPayment(object, "LIVE", new BigDecimal("149000"));
        verify(db)
                .update(
                        eq("UPDATE payment_checkouts SET fulfillment_status=? WHERE id=?"),
                        eq("APPLIED"),
                        eq(id));
        verifyNoInteractions(payments, addons);
    }

    @Test
    void failedActivationRollsBackAndPersistsARetryWithoutRequestingAnotherPayment() {
        when(subscriptions.activateVerifiedPayment(any(), anyString(), any()))
                .thenThrow(new IllegalStateException("temporary"));
        worker.tick();
        verify(tm).rollback(any());
        verify(db)
                .update(
                        startsWith("UPDATE payment_checkouts SET fulfillment_status='RETRY'"),
                        eq(id));
        verify(db, never())
                .update(
                        eq("UPDATE payment_checkouts SET fulfillment_status=? WHERE id=?"),
                        any(),
                        any());
    }
}
