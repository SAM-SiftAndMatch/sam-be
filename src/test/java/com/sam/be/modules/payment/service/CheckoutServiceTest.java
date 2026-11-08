package com.sam.be.modules.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sam.be.modules.finance.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

class CheckoutServiceTest {
    FinanceStore store = mock(FinanceStore.class);
    Pay2sClient provider = mock(Pay2sClient.class);
    PaymentReturnPolicy returns = mock(PaymentReturnPolicy.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    CheckoutService service;
    UUID owner = UUID.randomUUID(), object = UUID.randomUUID(), id = UUID.randomUUID();
    Map<String, Object> row;

    @BeforeEach
    void setup() {
        when(store.jdbc()).thenReturn(db);
        service = new CheckoutService(store, provider, returns);
        when(provider.environment()).thenReturn("LIVE");
        when(returns.resolve(any())).thenReturn("https://sam.example/payment/return");
        when(db.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class))
                .thenReturn(OffsetDateTime.parse("2026-11-06T12:00:00Z"));
        row =
                new HashMap<>(
                        Map.of(
                                "id",
                                id,
                                "purpose",
                                "SUBSCRIPTION",
                                "objectId",
                                object,
                                "ownerId",
                                owner,
                                "amount",
                                new BigDecimal("149000"),
                                "environment",
                                "LIVE",
                                "status",
                                "PENDING",
                                "fulfillmentStatus",
                                "NONE",
                                "transferContent",
                                "SAMGOITEST12",
                                "expiresAt",
                                "2026-11-06T12:15:00Z"));
        row.put("checkoutUrl", "https://payment.pay2s.vn/pay/test");
        when(store.rows(
                        startsWith("SELECT * FROM payment_checkouts WHERE purpose"),
                        eq("SUBSCRIPTION"),
                        eq(object),
                        eq("LIVE")))
                .thenReturn(List.of(row));
    }

    @Test
    void repeatedCheckoutReusesOrderAndOriginalPriceAfterCatalogChanges() {
        var c =
                service.create(
                        "SUBSCRIPTION",
                        object,
                        owner,
                        new BigDecimal("999999"),
                        "https://sam.example/payment/return");
        assertEquals(id, c.orderId());
        assertEquals(new BigDecimal("149000"), c.amount());
        verify(provider, never()).create(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void uncertainCreateDoesNotCreateSecondProviderOrder() {
        row.put("status", "UNKNOWN");
        assertEquals(
                "UNKNOWN",
                service.create(
                                "SUBSCRIPTION",
                                object,
                                owner,
                                new BigDecimal("149000"),
                                "https://sam.example/payment/return")
                        .paymentStatus());
        verify(provider, never()).create(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void closedOrderWithLatePaymentCannotCreateAnotherCheckout() {
        row.put("status", "CANCELLED");
        row.put("replacementAllowed", true);
        when(store.rows(startsWith("SELECT id FROM payment_checkout_receipts"), eq(id)))
                .thenReturn(List.of(Map.of("id", UUID.randomUUID())));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.create("SUBSCRIPTION", object, owner,
                        new BigDecimal("149000"), "https://sam.example/payment/return"));
        verify(db, never()).update(startsWith("INSERT INTO payment_checkouts"), any(Object[].class));
    }

    @Test
    void paymentStatusIsOnlyVisibleToItsOwner() {
        when(store.one(anyString(), eq(id))).thenReturn(row);
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.get(UUID.randomUUID(), id));
        assertEquals(id, service.get(owner, id).orderId());
    }

    @Test
    void confirmedUnpaidClosureDoesNotDisplayAnOpenMoneyReview() {
        row.put("status", "CANCELLED");
        row.put("replacementAllowed", true);
        when(store.one(anyString(), eq(id))).thenReturn(row);
        assertFalse(service.get(owner, id).reviewRequired());
        row.put("replacementAllowed", false);
        assertTrue(service.get(owner, id).reviewRequired());
    }
}
