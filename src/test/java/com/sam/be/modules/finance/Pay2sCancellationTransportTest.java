package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.exception.PaymentGatewayException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class Pay2sCancellationTransportTest {
    private static final String ORDER = "0123456789abcdef0123456789abcdef";
    private final ObjectMapper json = new ObjectMapper();
    private Pay2sClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new Pay2sClientTest().client();
        ReflectionTestUtils.setField(client, "http", builder.build());
    }

    private void expectSuccess(String wrongField) {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(
                        request -> {
                            var body =
                                    json.readTree(
                                            ((MockClientHttpRequest) request).getBodyAsString());
                            String requestId = body.path("requestId").asText();
                            assertEquals(6, body.size());
                            assertEquals("cancel", body.path("requestType").asText());
                            assertEquals(ORDER, body.path("orderId").asText());
                            assertNotEquals(ORDER, requestId);
                            assertEquals(
                                    Pay2sClient.sign(
                                            "secret",
                                            "accessKey=access&orderId="
                                                    + ORDER
                                                    + "&partnerCode=partner&requestId="
                                                    + requestId
                                                    + "&requestType=cancel"),
                                    body.path("signature").asText());
                            var result =
                                    json.createObjectNode()
                                            .put("partnerCode", "partner")
                                            .put("requestId", requestId)
                                            .put("orderId", ORDER)
                                            .put("resultCode", 0)
                                            .put("status", "cancelled");
                            if (wrongField != null) result.put(wrongField, "wrong");
                            return withSuccess(result.toString(), MediaType.APPLICATION_JSON)
                                    .createResponse(request);
                        });
    }

    @Test
    void cancellationUsesItsOwnSignatureAndAcceptsIdempotentAcknowledgement() {
        expectSuccess(null);
        expectSuccess(null);
        assertNotNull(client.cancel(ORDER).requestId());
        assertNotNull(client.cancel(ORDER).requestId());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"orderId", "requestId", "partnerCode", "status", "resultCode"})
    void wrongAcknowledgementNeverAllowsReplacement(String field) {
        expectSuccess(field);
        var failure = assertThrows(PaymentGatewayException.class, () -> client.cancel(ORDER));
        assertEquals("CANCEL_NOT_CONFIRMED", failure.diagnostics().get("reason"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 404, 409, 429, 500})
    void providerRejectionNeverMeansUnpaidOrClosed(int status) {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/cancel"))
                .andRespond(
                        withStatus(HttpStatusCode.valueOf(status))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"status\":false}"));
        var failure = assertThrows(PaymentGatewayException.class, () -> client.cancel(ORDER));
        assertEquals("CANCEL_NOT_CONFIRMED", failure.diagnostics().get("reason"));
        assertEquals(String.valueOf(status), failure.diagnostics().get("upstreamHttpStatus"));
        server.verify();
    }

    @Test
    void lostAcknowledgementRemainsUnknown() {
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/cancel"))
                .andRespond(
                        request -> {
                            throw new SocketTimeoutException("private response");
                        });
        var failure = assertThrows(PaymentGatewayException.class, () -> client.cancel(ORDER));
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("private response"));
        assertEquals("CANCEL_NOT_CONFIRMED", failure.diagnostics().get("reason"));
        server.verify();
    }
}
