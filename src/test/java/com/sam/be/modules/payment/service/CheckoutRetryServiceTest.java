package com.sam.be.modules.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sam.be.modules.finance.FinanceStore;
import com.sam.be.modules.finance.Pay2sClient;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

class CheckoutRetryServiceTest {
    private final UUID owner = UUID.randomUUID(),
            id = UUID.randomUUID(),
            caseId = UUID.randomUUID();
    private final FinanceStore store = mock(FinanceStore.class);
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final Pay2sClient provider = mock(Pay2sClient.class);
    private final CheckoutService checkouts = mock(CheckoutService.class);
    private final PaymentReconciliationService cases = mock(PaymentReconciliationService.class);
    private final Map<String, Object> row = new HashMap<>();
    private CheckoutRetryService retries;

    @BeforeEach
    void setup() {
        when(store.jdbc()).thenReturn(db);
        when(provider.environment()).thenReturn("LIVE");
        when(provider.merchantScope()).thenReturn("merchant");
        row.putAll(
                Map.of(
                        "id",
                        id,
                        "ownerId",
                        owner,
                        "status",
                        "EXPIRED",
                        "environment",
                        "LIVE",
                        "merchantScope",
                        "merchant",
                        "expiredNow",
                        true));
        when(store.one(startsWith("SELECT *,expires_at"), eq(id))).thenReturn(row);
        when(store.one(startsWith("SELECT id FROM payment_reconciliation_cases"), any()))
                .thenReturn(Map.of("id", caseId));
        when(provider.cancel(id.toString().replace("-", "")))
                .thenAnswer(
                        call -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            return new Pay2sClient.Cancellation("CANCEL_TEST");
                        });
        doAnswer(
                        call -> {
                            row.put("status", "CANCELLED");
                            row.put("replacementAllowed", true);
                            return 1;
                        })
                .when(db)
                .update(startsWith("UPDATE payment_checkouts SET status='CANCELLED'"), eq(id));
        var transactions =
                new AbstractPlatformTransactionManager() {
                    protected Object doGetTransaction() {
                        return new Object();
                    }

                    protected void doBegin(Object transaction, TransactionDefinition definition) {}

                    protected void doCommit(DefaultTransactionStatus status) {}

                    protected void doRollback(DefaultTransactionStatus status) {}
                };
        retries = new CheckoutRetryService(store, provider, checkouts, cases, transactions);
    }

    @Test
    void acknowledgedClosureIsAuditedAndReplayedWithoutAnotherProviderCall() {
        retries.prepare(owner, id);
        retries.prepare(owner, id);
        verify(provider, times(1)).cancel(any());
        verify(db, times(1))
                .update(startsWith("UPDATE payment_checkouts SET status='CANCELLED'"), eq(id));
        verify(db)
                .update(
                        contains("PROVIDER_CLOSED_FOR_RETRY"),
                        any(),
                        eq(caseId),
                        eq(owner),
                        eq("Pay2S cancel request: CANCEL_TEST"));
        verify(provider, never()).create(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void cancelledOrderWithoutEarlierProviderProofCanConfirmClosureAutomatically() {
        row.put("status", "CANCELLED");
        row.put("expiredNow", false);
        retries.prepare(owner, id);
        verify(provider).cancel(id.toString().replace("-", ""));
        assertEquals(true, row.get("replacementAllowed"));
    }

    @Test
    void
            alreadyApprovedClosureCanRecoverAfterMerchantRotationWithoutCallingNewKeysToCancelOldOrder() {
        row.put("status", "CANCELLED");
        row.put("replacementAllowed", true);
        row.put("merchantScope", "previous-merchant");
        retries.prepare(owner, id);
        verify(provider, never()).cancel(any());
        row.put("environment", "SANDBOX");
        assertThrows(ResponseStatusException.class, () -> retries.prepare(owner, id));
    }

    @Test
    void onlyOwnerCanCloseTheirOrder() {
        assertThrows(ResponseStatusException.class, () -> retries.prepare(UUID.randomUUID(), id));
        verify(provider, never()).cancel(any());
        verifyNoInteractions(db);
    }

    @Test
    void wrongEnvironmentOrMerchantNeverCallsProvider() {
        row.put("environment", "SANDBOX");
        assertThrows(ResponseStatusException.class, () -> retries.prepare(owner, id));
        row.put("environment", "LIVE");
        row.put("merchantScope", "previous-merchant");
        assertThrows(ResponseStatusException.class, () -> retries.prepare(owner, id));
        verify(provider, never()).cancel(any());
    }

    @Test
    void activeOrUnknownOrPaidOrdersCannotBeClosed() {
        for (String state : List.of("PENDING", "UNKNOWN", "PAID", "CREATING", "REVIEW")) {
            row.put("status", state);
            row.put("expiredNow", false);
            assertThrows(ResponseStatusException.class, () -> retries.prepare(owner, id));
        }
        verify(provider, never()).cancel(any());
    }

    @Test
    void existingReceiptPreventsCancellation() {
        when(store.rows(startsWith("SELECT id FROM payment_checkout_receipts"), eq(id)))
                .thenReturn(List.of(Map.of("id", UUID.randomUUID())));
        assertThrows(ResponseStatusException.class, () -> retries.prepare(owner, id));
        verify(provider, never()).cancel(any());
    }

    @Test
    void queuedVerifiedPaymentPreventsCancellationBeforeReceiptProcessing() {
        when(store.rows(startsWith("SELECT id FROM payment_provider_events"), any()))
                .thenReturn(List.of(Map.of("id", UUID.randomUUID())));
        assertThrows(ResponseStatusException.class, () -> retries.prepare(owner, id));
        verify(provider, never()).cancel(any());
    }

    @Test
    void paymentArrivingDuringProviderCallPreventsReplacement() {
        when(provider.cancel(any()))
                .thenAnswer(
                        call -> {
                            when(store.rows(
                                            startsWith("SELECT id FROM payment_checkout_receipts"),
                                            eq(id)))
                                    .thenReturn(List.of(Map.of("id", UUID.randomUUID())));
                            return new Pay2sClient.Cancellation("CANCEL_TEST");
                        });
        assertThrows(ResponseStatusException.class, () -> retries.prepare(owner, id));
        verifyNoInteractions(db);
    }

    @Test
    void cancellationFailureLeavesOldOrderIntactAndAllowsSafeCancellationRetry() {
        when(provider.cancel(any()))
                .thenThrow(new IllegalStateException("timeout"))
                .thenReturn(new Pay2sClient.Cancellation("CANCEL_RETRY"));
        assertThrows(IllegalStateException.class, () -> retries.prepare(owner, id));
        verifyNoInteractions(db);
        assertEquals("EXPIRED", row.get("status"));
        retries.prepare(owner, id);
        assertEquals("CANCELLED", row.get("status"));
        verify(provider, times(2)).cancel(any());
    }
}
