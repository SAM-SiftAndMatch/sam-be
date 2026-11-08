package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.sam.be.common.constant.enums.PaymentType;
import com.sam.be.common.exception.PaymentGatewayException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class Pay2sReceiverDiagnosticsTest {
    private static final String ORDER = "0123456789abcdef0123456789abcdef";
    private static final String ACCOUNT = "098765432318";
    private static final String URL = "https://payment.pay2s.vn/pay?t=PRIVATE_CHECKOUT_TOKEN";
    private Pay2sClient client;
    private MockRestServiceServer server;
    private Logger logger;
    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void prepare() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client =
                new Pay2sClient(
                        "pay2s",
                        "LIVE",
                        "https://payment.pay2s.vn",
                        "PRIVATE_ACCESS_KEY",
                        "PRIVATE_SECRET_KEY",
                        "PRIVATE_PARTNER_CODE",
                        "MBB",
                        ACCOUNT,
                        "https://api.example.com/ipn");
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

    private void respond(String extra) {
        server.expect(requestTo("https://payment.pay2s.vn/v1/gateway/api/create"))
                .andExpect(jsonPath("$.bankAccounts[0].bank_id").value("MBB"))
                .andExpect(jsonPath("$.bankAccounts[0].account_number").value(ACCOUNT))
                .andRespond(
                        withSuccess(
                                "{\"resultCode\":0,\"orderId\":\""
                                        + ORDER
                                        + "\",\"amount\":250000,\"payUrl\":\""
                                        + URL
                                        + "\""
                                        + extra
                                        + "}",
                                MediaType.APPLICATION_JSON));
    }

    private Pay2sClient.Checkout create() {
        return client.create(
                ORDER, 250000, "https://app.example.com/return", PaymentType.CONTRACT_FUND);
    }

    private String logs() {
        server.verify();
        var text =
                String.join(
                        "\n",
                        events.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
        for (String sensitive :
                new String[] {
                    ACCOUNT,
                    "PRIVATE_ACCESS_KEY",
                    "PRIVATE_SECRET_KEY",
                    "PRIVATE_PARTNER_CODE",
                    "PRIVATE_CHECKOUT_TOKEN",
                    "PRIVATE_ACCOUNT_NAME",
                    "PRIVATE_QR"
                }) {
            assertFalse(text.contains(sensitive), "Sensitive value in diagnostics");
        }
        assertTrue(events.list.stream().allMatch(event -> event.getThrowableProxy() == null));
        assertTrue(text.contains("orderId=" + ORDER));
        assertTrue(text.contains("bank=MBB accountLast4=****2318"));
        return text;
    }

    @Test
    void comparesActualReceiverWithoutLoggingCredentialsOrQrPayload() {
        respond(
                ",\"environment\":\"production\",\"isDemo\":false,\"qrList\":[{\"bank_id\":\"MBB\","
                        + "\"account_number\":\""
                        + ACCOUNT
                        + "\",\"account_name\":\"PRIVATE_ACCOUNT_NAME\","
                        + "\"qrData\":\"PRIVATE_QR\"}]");
        assertEquals(URL, create().url());
        String text = logs();
        assertTrue(text.contains("providerEnvironment=production providerIsDemo=false"));
        assertTrue(text.contains("receivers=MBB:****2318 receiverMatch=EXACT_MATCH"));
    }

    @Test
    void reportsDifferentBankWithoutTreatingDiagnosticsAsReceiverValidation() {
        respond(",\"qrList\":[{\"bank_id\":\"ACB\",\"account_number\":\"P2S99999999\"}]");
        assertEquals(URL, create().url());
        String text = logs();
        assertFalse(text.contains("P2S99999999"));
        assertTrue(text.contains("providerEnvironment=UNKNOWN providerIsDemo=UNKNOWN"));
        assertTrue(text.contains("receivers=ACB:REDACTED receiverMatch=DIFFERENT"));
    }

    @Test
    void missingReceiverInformationIsUnverifiedRatherThanAnExactMatch() {
        respond("");
        assertEquals(URL, create().url());
        assertTrue(logs().contains("qrCount=0 receivers=UNAVAILABLE receiverMatch=UNVERIFIED"));
    }

    @Test
    void demoResponseOnLiveRemainsRejectedAndProvidesMaskedEvidence() {
        respond(
                ",\"environment\":\"demo\",\"isDemo\":true,\"qrList\":[{\"bank_id\":\"ACB\","
                        + "\"account_number\":\"P2S99999999\"}]");
        var exception = assertThrows(PaymentGatewayException.class, this::create);
        assertEquals("ENVIRONMENT_MISMATCH", exception.diagnostics().get("reason"));
        assertTrue(logs().contains("providerEnvironment=demo providerIsDemo=true"));
    }

    @Test
    void malformedProviderFieldsCannotInjectLogLinesOrExposeShortAccounts() {
        respond(
                ",\"environment\":\"PRIVATE_SECRET_KEY\\nFAKE\",\"isDemo\":\"PRIVATE_SECRET_KEY\","
                        + "\"qrList\":[{\"bank_id\":\"MBB\\nFAKE\",\"account_number\":\"1234\"}]");
        create();
        String text = logs();
        assertFalse(text.contains("FAKE"));
        assertTrue(text.contains("receivers=UNKNOWN:REDACTED"));
    }
}
