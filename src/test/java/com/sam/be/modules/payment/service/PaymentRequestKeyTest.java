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
}
