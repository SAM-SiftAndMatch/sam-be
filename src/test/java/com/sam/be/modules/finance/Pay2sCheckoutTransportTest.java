package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

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
                                                        + "&orderInfo=SAM"
                                                        + order.substring(0, 25)
                                                        + "&partnerCode=partner&redirectUrl=https://example.com/return&requestId="
                                                        + order
                                                        + "&requestType=pay2s")))
                .andRespond(
                        withSuccess(
                                "{\"resultCode\":0,\"orderId\":\""
                                        + order
                                        + "\",\"amount\":250000,\"payUrl\":\"https://sandbox-payment.pay2s.vn/pay/test\"}",
                                MediaType.APPLICATION_JSON));
        var checkout = client.create(order, 250000, "https://example.com/return");
        assertEquals("https://sandbox-payment.pay2s.vn/pay/test", checkout.url());
        assertTrue(checkout.demo());
        server.verify();
    }
}
