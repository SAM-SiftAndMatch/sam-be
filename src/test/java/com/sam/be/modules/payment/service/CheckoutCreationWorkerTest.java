package com.sam.be.modules.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sam.be.modules.finance.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class CheckoutCreationWorkerTest {
    FinanceStore store = mock(FinanceStore.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    Pay2sClient provider = mock(Pay2sClient.class);
    PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
    PaymentReconciliationService cases = mock(PaymentReconciliationService.class);
    UUID id = UUID.randomUUID();
    CheckoutCreationWorker worker;

    @BeforeEach
    void setup() {
        when(store.jdbc()).thenReturn(db);
        when(tm.getTransaction(any())).thenAnswer(a -> new SimpleTransactionStatus());
        when(store.rows(startsWith("SELECT t.checkout_id")))
                .thenReturn(List.of(Map.of("checkoutId", id)), List.of());
        when(store.one(startsWith("UPDATE payment_creation_tasks"), any(), eq(id)))
                .thenReturn(
                        Map.of(
                                "checkoutId",
                                id,
                                "generation",
                                1L,
                                "returnUrl",
                                "https://sam.example/payment/return"));
        when(db.update(
                        startsWith("UPDATE payment_creation_tasks SET dispatched_at"),
                        eq(id),
                        eq(1L)))
                .thenReturn(1);
        when(store.one(eq("SELECT * FROM payment_checkouts WHERE id=? FOR UPDATE"), eq(id)))
                .thenReturn(Map.of("amount", new BigDecimal("149000"), "purpose", "SUBSCRIPTION", "status", "CREATING"));
        when(store.one(startsWith("SELECT id FROM payment_checkouts"), eq(id)))
                .thenReturn(Map.of("id", id));
        worker = new CheckoutCreationWorker(store, provider, tm, cases);
    }

    @Test
    void marksDispatchCommittedBeforeCallingProvider() {
        when(provider.create(anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new Pay2sClient.Checkout("https://payment.pay2s.vn/pay/test", false));
        when(db.update(
                        startsWith("UPDATE payment_creation_tasks SET state='DONE'"),
                        eq(id),
                        eq(1L)))
                .thenReturn(1);
        worker.tick();
        var order = inOrder(db, tm, provider);
        order.verify(db)
                .update(
                        startsWith("UPDATE payment_creation_tasks SET dispatched_at"),
                        eq(id),
                        eq(1L));
        order.verify(tm).commit(any());
        order.verify(provider).create(anyString(), eq(149000L), anyString(), eq("SUBSCRIPTION"));
        verify(db)
                .update(
                        contains("CASE WHEN status IN ('CREATING','UNKNOWN')"),
                        any(),
                        any(),
                        eq(id));
    }

    @Test
    void ambiguousFailureCreatesReviewAndNeverRetriesHttp() {
        when(provider.create(anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("connection lost"));
        worker.tick();
        worker.tick();
        verify(provider, times(1)).create(anyString(), anyLong(), anyString(), anyString());
        verify(cases).open(contains("CREATE_UNKNOWN"), eq(id), isNull(), eq("CREATE_UNKNOWN"));
    }

    @Test
    void workerThatLostLeaseCannotStartProviderCall() {
        when(db.update(
                        startsWith("UPDATE payment_creation_tasks SET dispatched_at"),
                        eq(id),
                        eq(1L)))
                .thenReturn(0);
        worker.tick();
        verifyNoInteractions(provider);
    }

    @Test
    void hardCrashLeavesRecoveryToTheCommittedTask() {
        when(provider.create(anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new AssertionError("process terminated"));
        assertThrows(AssertionError.class, worker::tick);
        verify(cases, never()).open(any(), any(), any(), any());
        verify(store).rows(startsWith("SELECT checkout_id FROM payment_creation_tasks WHERE state='LEASED'"));
    }
    @Test
    void alreadyPaidCheckoutIsNeverSentToCreate(){
        when(store.one(eq("SELECT * FROM payment_checkouts WHERE id=? FOR UPDATE"),eq(id))).thenReturn(Map.of("status","PAID"));
        worker.tick();verifyNoInteractions(provider);
    }

}
