package com.sam.be.modules.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sam.be.modules.finance.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

class Pay2sUnifiedIpnTest {
    Pay2sClient provider = mock(Pay2sClient.class);
    FinanceStore store = mock(FinanceStore.class);
    PaymentService payments = mock(PaymentService.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    UUID id = UUID.randomUUID();
    Map<String, Object> order;
    Pay2sIpnService service;
    org.springframework.transaction.PlatformTransactionManager tm =
            mock(org.springframework.transaction.PlatformTransactionManager.class);
    PaymentReconciliationService cases = mock(PaymentReconciliationService.class);
    UUID eventId = UUID.randomUUID();
    Map<String, Object> event;

    @BeforeEach
    void setup() {
        when(store.jdbc()).thenReturn(db);
        service = new Pay2sIpnService(provider, store, payments, tm, cases, new ObjectMapper());
        when(tm.getTransaction(any()))
                .thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        when(provider.merchantScope()).thenReturn("merchant");
        event =
                new HashMap<>(
                        Map.of(
                                "environment",
                                "SANDBOX",
                                "merchantScope",
                                "merchant",
                                "state",
                                "PENDING",
                                "orderReference",
                                id.toString().replace("-", ""),
                                "providerReference",
                                "PAY2S_RECEIPT",
                                "resultCode",
                                0,
                                "amountText",
                                "149000"));
        when(store.one(startsWith("SELECT id FROM payment_provider_events"), any(), any(), any()))
                .thenReturn(Map.of("id", eventId));
        when(store.one(eq("SELECT * FROM payment_provider_events WHERE id=?"), eq(eventId)))
                .thenAnswer(
                        a -> {
                            event.put(
                                    "verifiedPayload",
                                    payload()
                                            .put("amount", event.get("amountText").toString())
                                            .toString());
                            return event;
                        });
        when(store.rows(startsWith("SELECT id FROM payment_provider_events"), eq(eventId)))
                .thenReturn(List.of(Map.of("id", eventId)));
        when(provider.environment()).thenReturn("SANDBOX");
        when(provider.verify(any())).thenReturn(true);
        when(store.now()).thenReturn(Instant.parse("2026-11-06T12:00:00Z"));
        when(store.time(any()))
                .thenAnswer(a -> ((Instant) a.getArgument(0)).atOffset(ZoneOffset.UTC));
        order =
                new HashMap<>(
                        Map.of(
                                "purpose",
                                "SUBSCRIPTION",
                                "ownerId",
                                UUID.randomUUID(),
                                "environment",
                                "SANDBOX",
                                "status",
                                "PENDING",
                                "amount",
                                new BigDecimal("149000"),
                                "expiresAt",
                                "2026-11-06T12:15:00Z"));
        order.put("payableNow", true);
        when(store.rows(startsWith("SELECT *,expires_at"), eq(id))).thenReturn(List.of(order));
        when(store.rows(
                        startsWith("SELECT checkout_id,amount"),
                        eq("SANDBOX"),
                        eq("PAY2S_RECEIPT")))
                .thenReturn(List.of());
    }

    ObjectNode payload() {
        return new ObjectMapper()
                .createObjectNode()
                .put("orderId", id.toString().replace("-", ""))
                .put("resultCode", 0)
                .put("amount", 149000)
                .put("transId", "PAY2S_RECEIPT");
    }

    @Test
    void authenticPaymentPersistsReceiptAndDurableFulfillmentBeforeAck() {
        assertTrue(service.accept(payload()).get("success"));
        verify(db)
                .update(
                        startsWith("INSERT INTO payment_checkout_receipts"),
                        any(),
                        eq(id),
                        eq("SANDBOX"),
                        eq("PAY2S_RECEIPT"),
                        eq(new BigDecimal("149000")),
                        eq("ACCEPTED"));
        verify(db)
                .update(
                        startsWith("UPDATE payment_checkouts SET status='PAID'"),
                        eq("PAY2S_RECEIPT"),
                        eq(id));
        verifyNoInteractions(payments);
    }

    @Test
    void forgedCallbackDoesNotTouchDatabase() {
        when(provider.verify(any())).thenReturn(false);
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.accept(payload()));
        verifyNoInteractions(db);
    }

    @Test
    void repeatedProviderTransactionIsAcknowledgedWithoutEnqueuingOrCreditingTwice() {
        when(store.rows(
                        startsWith("SELECT checkout_id,amount"),
                        eq("SANDBOX"),
                        eq("PAY2S_RECEIPT")))
                .thenReturn(List.of(Map.of("checkoutId", id, "amount", new BigDecimal("149000"))));
        assertTrue(service.accept(payload()).get("success"));
        verify(db, never())
                .update(
                        startsWith("INSERT INTO payment_checkout_receipts"),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());
        verifyNoInteractions(payments);
    }

    @Test
    void wrongAmountIsSavedForReviewWithoutGrantingBenefits() {
        event.put("amountText", "149001");
        var body = payload().put("amount", 149001);
        service.accept(body);
        verify(db)
                .update(
                        startsWith("INSERT INTO payment_checkout_receipts"),
                        any(),
                        eq(id),
                        eq("SANDBOX"),
                        eq("PAY2S_RECEIPT"),
                        eq(new BigDecimal("149001")),
                        eq("REVIEW"));
        verify(db, never())
                .update(startsWith("UPDATE payment_checkouts SET status='PAID'"), any(), any());
    }

    @Test
    void latePaymentIsReviewableInsteadOfSilentlyDiscarded() {
        order.put("expiresAt", "2026-11-06T11:59:59Z");
        order.put("payableNow", false);
        service.accept(payload());
        verify(db)
                .update(
                        startsWith("INSERT INTO payment_checkout_receipts"),
                        any(),
                        eq(id),
                        eq("SANDBOX"),
                        eq("PAY2S_RECEIPT"),
                        any(),
                        eq("REVIEW"));
    }

    @Test
    void secondRealTransferDoesNotOverwriteFirstPayment() {
        order.put("status", "PAID");
        service.accept(payload());
        verify(db)
                .update(
                        startsWith("INSERT INTO payment_checkout_receipts"),
                        any(),
                        eq(id),
                        eq("SANDBOX"),
                        eq("PAY2S_RECEIPT"),
                        any(),
                        eq("REVIEW"));
    }

    @Test
    void cancellationDoesNotDowngradePaidOrder() {
        order.put("status", "PAID");
        event.put("resultCode", 2);
        service.accept(payload().put("resultCode", 2));
        verify(db, never())
                .update(
                        startsWith("UPDATE payment_checkouts SET status='CANCELLED'"),
                        any(UUID.class));
        verify(cases)
                .open(
                        contains("CANCEL_WITH_PAYMENT"),
                        eq(id),
                        eq(eventId),
                        eq("CANCELLATION_WITH_PAYMENT"));
    }

    @Test
    void signedCancellationOfUnpaidOrderAllowsReplacementWithoutAdmin() {
        event.put("resultCode", 2);
        service.accept(payload().put("resultCode", 2));
        verify(db)
                .update(
                        startsWith(
                                "UPDATE payment_checkouts SET status='CANCELLED',replacement_allowed=true"),
                        eq(id));
        verify(db).update(contains("resolution='PROVIDER_CANCELLED_UNPAID'"), eq(id));
        verifyNoInteractions(cases, payments);
    }

    @Test
    void cancellationCannotUnlockReplacementWhileVerifiedPaymentIsQueued() {
        event.put("resultCode", 2);
        when(store.rows(
                        startsWith("SELECT id FROM payment_provider_events WHERE order_reference="),
                        any()))
                .thenReturn(List.of(Map.of("id", UUID.randomUUID())));
        service.accept(payload().put("resultCode", 2));
        verify(db, never())
                .update(
                        startsWith("UPDATE payment_checkouts SET status='CANCELLED'"),
                        any(UUID.class));
        verify(cases)
                .open(
                        contains("CANCEL_WITH_PAYMENT"),
                        eq(id),
                        eq(eventId),
                        eq("CANCELLATION_WITH_PAYMENT"));
    }

    @Test
    void wrongCancellationAmountRequiresReviewRatherThanReplacement() {
        event.put("resultCode", 2);
        event.put("amountText", "1");
        service.accept(payload().put("resultCode", 2).put("amount", 1));
        verify(db, never())
                .update(
                        startsWith("UPDATE payment_checkouts SET status='CANCELLED'"),
                        any(UUID.class));
        verify(cases).open(anyString(), eq(id), eq(eventId), eq("CANCELLATION_AMOUNT_MISMATCH"));
    }

    @Test
    void productionCannotConsumeSandboxCheckout() {
        when(provider.environment()).thenReturn("LIVE");
        assertTrue(service.accept(payload()).get("success"));
        verify(cases)
                .open(contains("MERCHANT_CHANGED"), isNull(), eq(eventId), eq("MERCHANT_CHANGED"));
        verifyNoInteractions(payments);
    }
}
