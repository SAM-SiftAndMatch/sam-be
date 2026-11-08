package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.sam.be.common.constant.enums.PaymentType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class Pay2sCheckoutTransportTest {
    @Test
    void collectionRequestAndResponseWorkWithActualHttpMessageConverters() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new Pay2sClientTest().client();
        ReflectionTestUtils.setField(client, "http", builder.build());
        String order = "0123456789abcdef0123456789abcdef";
        server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.amount").value(250000))
                .andExpect(jsonPath("$.orderId").value(order))
                .andExpect(jsonPath("$.orderInfo").value("SAMNAP3EB1BD439947EB76"))
                .andExpect(jsonPath("$.requestType").value("pay2s"))
                .andExpect(jsonPath("$.bankAccounts[0].account_number").value("00123456"))
                .andExpect(jsonPath("$.bankAccounts[0].bank_id").value("ACB"))
                .andExpect(
                        jsonPath("$.signature")
                                .value(
                                        Pay2sClient.sign(
                                                "secret",
                                                "accessKey=access&amount=250000&bankAccounts=Array&ipnUrl=https://example.com/ipn&orderId="
                                                        + order
                                                        + "&orderInfo=SAMNAP3EB1BD439947EB76"
                                                        + "&partnerCode=partner&redirectUrl=https://example.com/return&requestId="
                                                        + order
                                                        + "&requestType=pay2s")))
                .andRespond(
                        withSuccess(
                                "{\"resultCode\":0,\"orderId\":\""
                                        + order
                                        + "\",\"amount\":250000,\"payUrl\":\"https://sandbox-payment.pay2s.vn/pay/test\"}",
                                MediaType.APPLICATION_JSON));
        var checkout =
                client.create(
                        order, 250000, "https://example.com/return", PaymentType.CONTRACT_FUND);
        assertEquals("https://sandbox-payment.pay2s.vn/pay/test", checkout.url());
        assertTrue(checkout.demo());
        server.verify();
    }

    @Test
    void demoAccountCanUseCanonicalCreateApiAndReturnSandboxCheckout() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
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
                        "https://backend-tunnel.example/api/v1/payments/pay2s-ipn");
        ReflectionTestUtils.setField(client, "http", builder.build());
        String order = "0123456789abcdef0123456789abcdef";
        server.expect(requestTo("https://payment.pay2s.vn/v1/gateway/api/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Accept", "application/json"))
                .andExpect(jsonPath("$.bankAccounts[0].account_number").value("P2S99999999"))
                .andExpect(
                        jsonPath("$.redirectUrl")
                                .value(
                                        "https://backend-tunnel.example/api/v1/payments/pay2s-return"))
                .andExpect(
                        jsonPath("$.signature")
                                .value(
                                        Pay2sClient.sign(
                                                "secret",
                                                "accessKey=access&amount=250000&bankAccounts=Array&ipnUrl=https://backend-tunnel.example/api/v1/payments/pay2s-ipn&orderId="
                                                        + order
                                                        + "&orderInfo=SAMNAP3EB1BD439947EB76"
                                                        + "&partnerCode=partner&redirectUrl=https://backend-tunnel.example/api/v1/payments/pay2s-return&requestId="
                                                        + order
                                                        + "&requestType=pay2s")))
                .andExpect(
                        jsonPath("$.ipnUrl")
                                .value("https://backend-tunnel.example/api/v1/payments/pay2s-ipn"))
                .andRespond(
                        withSuccess(
                                "{\"resultCode\":0,\"orderId\":\""
                                        + order
                                        + "\",\"amount\":250000,\"isDemo\":true,\"environment\":\"demo\",\"payUrl\":\"https://sandbox-payment.pay2s.vn/pay/test\"}",
                                MediaType.APPLICATION_JSON));
        var checkout =
                client.create(
                        order,
                        250000,
                        "http://localhost:5173/payment/return",
                        PaymentType.CONTRACT_FUND);
        assertTrue(checkout.demo());
        assertEquals("https://sandbox-payment.pay2s.vn/pay/test", checkout.url());
        server.verify();
    }

    @Test
    void depositAndMaintenanceMemosAreSignedWithoutChangingTheReconciliationOrderId() {
        String order = "0123456789abcdef0123456789abcdef";
        for (var entry :
                java.util.Map.of(
                                PaymentType.SECURITY_DEPOSIT, "SAMCOC3EB1BD439947EB76",
                                PaymentType.MAINTENANCE_FUND, "SAMBAOTRI3EB1BD439947EB76")
                        .entrySet()) {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            var client = new Pay2sClientTest().client();
            ReflectionTestUtils.setField(client, "http", builder.build());
            String memo = entry.getValue();
            server.expect(requestTo("https://sandbox-payment.pay2s.vn/v1/gateway/api/create"))
                    .andExpect(jsonPath("$.orderId").value(order))
                    .andExpect(jsonPath("$.requestId").value(order))
                    .andExpect(jsonPath("$.orderInfo").value(memo))
                    .andExpect(
                            jsonPath("$.signature")
                                    .value(
                                            Pay2sClient.sign(
                                                    "secret",
                                                    "accessKey=access&amount=250000&bankAccounts=Array&ipnUrl=https://example.com/ipn&orderId="
                                                            + order
                                                            + "&orderInfo="
                                                            + memo
                                                            + "&partnerCode=partner&redirectUrl=https://example.com/return&requestId="
                                                            + order
                                                            + "&requestType=pay2s")))
                    .andRespond(
                            withSuccess(
                                    "{\"resultCode\":0,\"orderId\":\""
                                            + order
                                            + "\",\"amount\":250000,\"payUrl\":\"https://sandbox-payment.pay2s.vn/pay/test\"}",
                                    MediaType.APPLICATION_JSON));
            client.create(order, 250000, "https://example.com/return", entry.getKey());
            server.verify();
        }
    }

    @Test
    void transferMemoFitsProviderRulesAndDiffersBetweenOrdersAndPaymentTypes() {
        String order = "0123456789abcdef0123456789abcdef";
        var memos = new java.util.HashSet<String>();
        for (var type : PaymentType.values()) {
            String memo = Pay2sClient.transferMemo(order, type);
            assertTrue(memo.matches("[A-Z0-9]{10,32}"));
            assertFalse(memo.contains(order.substring(0, 16).toUpperCase()));
            assertTrue(memos.add(memo));
            assertNotEquals(
                    memo, Pay2sClient.transferMemo("1123456789abcdef0123456789abcdef", type));
        }
    }
}
