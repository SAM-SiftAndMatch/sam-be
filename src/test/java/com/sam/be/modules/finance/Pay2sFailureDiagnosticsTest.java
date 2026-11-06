package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sam.be.common.exception.GlobalExceptionHandler;
import com.sam.be.common.exception.PaymentGatewayException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class Pay2sFailureDiagnosticsTest {
    static final String ORDER = "0123456789abcdef0123456789abcdef";
    static final String SENSITIVE = "SECRET_OR_ACCOUNT_DATA_MUST_NOT_LEAK";
    Pay2sClient client;
    MockRestServiceServer server;
    Logger logger;
    ListAppender<ILoggingEvent> events;

    @BeforeEach
    void prepare() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new Pay2sClientTest().client();
        ReflectionTestUtils.setField(client, "http", builder.build());
        logger = (Logger) LoggerFactory.getLogger(Pay2sClient.class);
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void cleanup() {
        logger.detachAppender(events);
        events.stop();
    }

    PaymentGatewayException failure(String reason) {
        var exception =
                assertThrows(
                        PaymentGatewayException.class,
                        () -> client.create(ORDER, 250000, "https://example.com/return"));
        assertEquals(reason, exception.diagnostics().get("reason"));
        assertDoesNotThrow(
                () -> java.util.UUID.fromString(exception.diagnostics().get("reference")));
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains(SENSITIVE));
        assertFalse(events.list.isEmpty());
        for (var event : events.list) {
            assertFalse(event.getFormattedMessage().contains(SENSITIVE));
            assertNull(event.getThrowableProxy());
        }
        server.verify();
        return exception;
    }

    @Test
    void unauthorizedJsonHasSafeDiagnosticsAndCustomerEnvelope() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andRespond(
                        withStatus(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"resultCode\":41,\"message\":\"" + SENSITIVE + "\"}"));
        var exception = failure("ACCESS_DENIED");
        assertEquals("401", exception.diagnostics().get("upstreamHttpStatus"));
        assertEquals("41", exception.diagnostics().get("upstreamResultCode"));
        var response =
                new GlobalExceptionHandler()
                        .handleFinanceStatus(
                                exception,
                                new MockHttpServletRequest("POST", "/api/v1/payments/fund"));
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(1005, response.getBody().getCode());
        assertEquals("ACCESS_DENIED", response.getBody().getErrors().get("reason"));
        assertTrue(
                response.getBody().getMessage().contains(exception.diagnostics().get("reference")));
    }

    @Test
    void providerRejectionClassifiesSignatureWithoutEchoingVendorMessage() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andRespond(
                        withSuccess(
                                "{\"resultCode\":42,\"message\":\"Invalid signature: "
                                        + SENSITIVE
                                        + "\"}",
                                MediaType.APPLICATION_JSON));
        assertEquals("42", failure("SIGNATURE_REJECTED").diagnostics().get("upstreamResultCode"));
    }

    @Test
    void htmlGatewayFailurePreservesHttpStatusWithoutLoggingResponse() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andRespond(
                        withStatus(HttpStatus.BAD_GATEWAY)
                                .contentType(MediaType.TEXT_HTML)
                                .body("<html>" + SENSITIVE + "</html>"));
        assertEquals("502", failure("HTTP_REJECTED").diagnostics().get("upstreamHttpStatus"));
    }

    @Test
    void malformedSuccessResponseIsDistinguishedFromConnectionFailure() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andRespond(withSuccess("not JSON " + SENSITIVE, MediaType.APPLICATION_JSON));
        failure("INVALID_RESPONSE");
    }

    @Test
    void timeoutIsReportedAsGatewayTimeout() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andRespond(
                        request -> {
                            throw new SocketTimeoutException(SENSITIVE);
                        });
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, failure("TIMEOUT").getStatusCode());
    }

    @Test
    void dnsFailureIsDistinguishedWithoutExposingTheExceptionMessage() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andRespond(
                        request -> {
                            throw new UnknownHostException(SENSITIVE);
                        });
        failure("DNS_FAILED");
    }

    @Test
    void tlsFailureDoesNotDisableCertificateValidation() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andRespond(
                        request -> {
                            throw new SSLHandshakeException(SENSITIVE);
                        });
        failure("TLS_FAILED");
    }

    @Test
    void amountMismatchIsStillRejected() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andRespond(
                        withSuccess(
                                "{\"resultCode\":0,\"amount\":1,\"orderId\":\""
                                        + ORDER
                                        + "\",\"payUrl\":\"https://sandbox-payment.pay2s.vn/pay/test\"}",
                                MediaType.APPLICATION_JSON));
        failure("PAYMENT_MISMATCH");
    }

    @Test
    void untrustedRedirectStillFailsClosed() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andRespond(
                        withSuccess(
                                "{\"resultCode\":0,\"amount\":250000,\"orderId\":\""
                                        + ORDER
                                        + "\",\"payUrl\":\"https://attacker.example/pay?t="
                                        + SENSITIVE
                                        + "\"}",
                                MediaType.APPLICATION_JSON));
        failure("UNTRUSTED_CHECKOUT_URL");
    }

    @Test
    void oversizedBodyIsBounded() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andRespond(withSuccess("x".repeat(65537), MediaType.APPLICATION_JSON));
        failure("RESPONSE_TOO_LARGE");
    }

    @Test
    void sandboxConfigurationCannotPresentAnExplicitlyLiveCheckout() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andRespond(
                        withSuccess(
                                "{\"resultCode\":0,\"amount\":250000,\"orderId\":\""
                                        + ORDER
                                        + "\",\"payUrl\":\"https://payment.pay2s.vn/pay/test\",\"isDemo\":false,\"environment\":\"production\"}",
                                MediaType.APPLICATION_JSON));
        failure("ENVIRONMENT_MISMATCH");
    }

    @Test
    void badRequestWithVendorCallbackMessageIsNotLostAsGenericHttpRejection() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"message\":\"Invalid ipnUrl: " + SENSITIVE + "\"}"));
        var exception = failure("IPN_URL_REJECTED");
        assertEquals("400", exception.diagnostics().get("upstreamHttpStatus"));
    }

    @Test
    void nestedValidationErrorCanIdentifySignatureWithoutLeakingProviderBody() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        "{\"error\":{\"message\":\"Chữ ký không hợp lệ: "
                                                + SENSITIVE
                                                + "\"}}"));
        failure("SIGNATURE_REJECTED");
    }

    @Test
    void rejectedRedirectIsDistinguishedFromIpnAndIncludesOnlySafeRuntimeOrigins() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"message\":\"Invalid redirectUrl: " + SENSITIVE + "\"}"));
        var exception = failure("REDIRECT_URL_REJECTED");
        assertEquals("https://example.com", exception.diagnostics().get("ipnOrigin"));
        assertEquals("https://example.com", exception.diagnostics().get("redirectOrigin"));
    }
}
