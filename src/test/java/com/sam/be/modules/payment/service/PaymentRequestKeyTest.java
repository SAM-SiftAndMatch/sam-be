package com.sam.be.modules.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sam.be.modules.finance.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;

class PaymentRequestKeyTest {
    FinanceStore store = mock(FinanceStore.class);
    Pay2sClient provider = mock(Pay2sClient.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    UUID owner = UUID.randomUUID();
    CheckoutService service;

    @BeforeEach
    void setup() {
        when(store.jdbc()).thenReturn(db);
        service = new CheckoutService(store, provider, mock(PaymentReturnPolicy.class));
        when(provider.environment()).thenReturn("LIVE");
        var request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", UUID.randomUUID().toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void reusedKeyWithChangedPurchaseIsRejectedBeforeAnyProviderCall() {
        when(store.rows(
                        startsWith("SELECT checkout_id,payload_hash"),
                        eq(owner),
                        eq("LIVE"),
                        eq("SUBSCRIPTION"),
                        any()))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "payloadHash",
                                        "different",
                                        "checkoutId",
                                        UUID.randomUUID())));
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.replay(owner, "SUBSCRIPTION", "other-package"));
        verify(provider, never()).create(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void malformedKeyIsRejected() {
        var request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "short");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.replay(owner, "SUBSCRIPTION", "package"));
        verifyNoInteractions(db);
    }

    @Test
    void samePurchaseKeyCanProceedAfterConfirmedUnpaidClosure() {
        UUID previous = UUID.randomUUID();
        UUID object = UUID.randomUUID();
        when(store.rows(startsWith("SELECT checkout_id,payload_hash"), eq(owner), eq("LIVE"),
                eq("SUBSCRIPTION"), any()))
                .thenReturn(List.of(Map.of("payloadHash", PaymentRequestKey.hash("SUBSCRIPTION:package"),
                        "checkoutId", previous)));
        var old = Map.<String, Object>of("ownerId", owner, "objectId", object,
                "status", "CANCELLED", "replacementAllowed", true);
        when(store.one(eq("SELECT * FROM payment_checkouts WHERE id=?"), eq(previous)))
                .thenReturn(old);
        when(store.rows(startsWith("SELECT * FROM payment_checkouts WHERE purpose"),
                eq("SUBSCRIPTION"), eq(object), eq("LIVE"))).thenReturn(List.of(old));
        assertNull(service.replay(owner, "SUBSCRIPTION", "package"));
        verify(provider, never()).create(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void originalKeyReadsExistingReplacementInsteadOfCreatingAnotherAttempt() {
        UUID previous = UUID.randomUUID(), replacement = UUID.randomUUID(), object = UUID.randomUUID();
        when(store.rows(startsWith("SELECT checkout_id,payload_hash"), eq(owner), eq("LIVE"),
                eq("SUBSCRIPTION"), any()))
                .thenReturn(List.of(Map.of("payloadHash", PaymentRequestKey.hash("SUBSCRIPTION:package"),
                        "checkoutId", previous)));
        when(store.one(eq("SELECT * FROM payment_checkouts WHERE id=?"), eq(previous)))
                .thenReturn(Map.of("ownerId", owner, "objectId", object,
                        "status", "CANCELLED", "replacementAllowed", true));
        var next = new HashMap<String, Object>();
        next.putAll(Map.of("id", replacement, "ownerId", owner, "objectId", object,
                "purpose", "SUBSCRIPTION", "amount", new java.math.BigDecimal("149000"),
                "environment", "LIVE", "status", "CREATING", "fulfillmentStatus", "NONE",
                "expiresAt", "2026-11-08T12:00:00Z"));
        when(store.rows(startsWith("SELECT * FROM payment_checkouts WHERE purpose"),
                eq("SUBSCRIPTION"), eq(object), eq("LIVE"))).thenReturn(List.of(next));
        assertEquals(replacement, service.replay(owner, "SUBSCRIPTION", "package").orderId());
        verify(provider, never()).create(anyString(), anyLong(), anyString(), anyString());
    }
}
