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
import org.springframework.test.util.ReflectionTestUtils;

class Pay2sUnifiedIpnTest {
    Pay2sClient provider = mock(Pay2sClient.class);
    FinanceStore store = mock(FinanceStore.class);
    PaymentService payments = mock(PaymentService.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    UUID id = UUID.randomUUID();
    Map<String, Object> order;
    Pay2sIpnService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(store, "db", db);
        service = new Pay2sIpnService(provider, store, payments);
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
        when(store.rows(startsWith("SELECT * FROM payment_checkouts"), eq(id)))
                .thenReturn(List.of(order));
        when(store.rows(startsWith("SELECT checkout_id"), eq("SANDBOX"), eq("PAY2S_RECEIPT")))
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
                        any(),
                        eq("PAY2S_RECEIPT"),
                        any(),
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
        when(store.rows(startsWith("SELECT checkout_id"), eq("SANDBOX"), eq("PAY2S_RECEIPT")))
                .thenReturn(List.of(Map.of("checkoutId", id)));
        assertTrue(service.accept(payload()).get("success"));
        verifyNoInteractions(db);
    }

    @Test
    void wrongAmountIsSavedForReviewWithoutGrantingBenefits() {
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
                .update(
                        startsWith("UPDATE payment_checkouts SET status='PAID'"),
                        any(),
                        any(),
                        any(),
                        any());
    }

    @Test
    void latePaymentIsReviewableInsteadOfSilentlyDiscarded() {
        order.put("expiresAt", "2026-11-06T11:59:59Z");
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
        service.accept(payload().put("resultCode", 2));
        verify(db)
                .update(
                        eq(
                                "UPDATE payment_checkouts SET status='CANCELLED' WHERE id=? AND status IN ('CREATING','PENDING','UNKNOWN','EXPIRED')"),
                        eq(id));
    }

    @Test
    void productionCannotConsumeSandboxCheckout() {
        when(provider.environment()).thenReturn("LIVE");
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.accept(payload()));
        verifyNoInteractions(db);
    }
}
