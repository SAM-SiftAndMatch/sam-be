package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sam.be.common.exception.GlobalExceptionHandler;
import com.sam.be.common.exception.PaymentGatewayException;
import com.sam.be.infrastructure.thirdparty.vnpay.VnpayProperties;
import com.sam.be.modules.payment.controller.PaymentReturnController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class Pay2sReturnRelayTest {
    private PaymentReturnPolicy policy(String fallback) {
        var legacy = new VnpayProperties();
        legacy.setAllowedReturnUrls(List.of("http://localhost:5173/client/payment/vnpay-return"));
        return new PaymentReturnPolicy(legacy, fallback, "http://localhost:5173", "SANDBOX");
    }

    @Test
    void publicReturnOnlyNavigatesToConfiguredFrontendAndDropsProviderAndAttackerParameters()
            throws Exception {
        var controller =
                new PaymentReturnController(
                        policy("http://localhost:5173/client/payment/vnpay-return"));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(
                        get("/api/v1/payments/pay2s-return")
                                .param("returnUrl", "https://attacker.example")
                                .param("next", "https://attacker.example")
                                .param("resultCode", "0")
                                .param("amount", "999999999"))
                .andExpect(status().isSeeOther())
                .andExpect(
                        header().string(
                                        "Location",
                                        "http://localhost:5173/client/payment/vnpay-return"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().string(""));
    }

    @Test
    void misconfiguredExternalFrontendCannotTurnRelayIntoAnOpenRedirect() throws Exception {
        var controller =
                new PaymentReturnController(
                        policy("https://attacker.example/client/payment/vnpay-return"));
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        mvc.perform(get("/api/v1/payments/pay2s-return"))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void localhostIpnIsRejectedBeforeAnyProviderCallAndReportsActualRuntimeOrigins() {
        var client =
                new Pay2sClient(
                        "pay2s",
                        "SANDBOX",
                        "https://payment.pay2s.vn",
                        "access",
                        "secret",
                        "partner",
                        "ACB",
                        "P2S99999999",
                        "http://localhost:8080/api/v1/payments/pay2s-ipn");
        var exception =
                assertThrows(
                        PaymentGatewayException.class,
                        () ->
                                client.create(
                                        "0123456789abcdef0123456789abcdef",
                                        250000,
                                        "http://localhost:5173/workspace/00000000-0000-0000-0000-000000000001/contract"));
        assertEquals("IPN_URL_INVALID", exception.diagnostics().get("reason"));
        assertEquals("http://localhost:8080", exception.diagnostics().get("ipnOrigin"));
        assertEquals("http://localhost:5173", exception.diagnostics().get("redirectOrigin"));
        assertFalse(exception.diagnostics().containsKey("upstreamHttpStatus"));
    }

    @Test
    void invalidCallbackCannotLeakCredentialsQueryOrFragmentIntoDiagnostics() {
        var client =
                new Pay2sClient(
                        "pay2s",
                        "SANDBOX",
                        "https://payment.pay2s.vn",
                        "access",
                        "secret",
                        "partner",
                        "ACB",
                        "P2S99999999",
                        "https://PRIVATE_CREDENTIAL@hooks.example/api/v1/payments/pay2s-ipn?token=PRIVATE_TOKEN#PRIVATE_FRAGMENT");
        var exception =
                assertThrows(
                        PaymentGatewayException.class,
                        () ->
                                client.create(
                                        "0123456789abcdef0123456789abcdef",
                                        250000,
                                        "http://localhost:5173/client/payment/vnpay-return"));
        assertEquals("IPN_URL_INVALID", exception.diagnostics().get("reason"));
        assertEquals("https://hooks.example", exception.diagnostics().get("ipnOrigin"));
        assertFalse(exception.diagnostics().toString().contains("PRIVATE_"));
    }
}
