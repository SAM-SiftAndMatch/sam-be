package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class Pay2sClientTest {
    Pay2sClient client() {
        return new Pay2sClient(
                "pay2s",
                "SANDBOX",
                "https://sandbox-payment.pay2s.vn",
                "access",
                "secret",
                "partner",
                "ACB",
                "00123456",
                "https://example.com/ipn");
    }

    ObjectNode payload() {
        var body = new ObjectMapper().createObjectNode();
        body.put("accessKey", "access");
        body.put("amount", 250000);
        body.put("extraData", "");
        body.put("message", "Paymentsuccessful");
        body.put("orderId", "order1");
        body.put("orderInfo", "SAMorder1");
        body.put("orderType", "pay2s");
        body.put("partnerCode", "partner");
        body.put("payType", "bank");
        body.put("requestId", 1234);
        body.put("responseTime", 1790000000000L);
        body.put("resultCode", 0);
        body.put("transId", "transaction1");
        body.put(
                "m2signature",
                Pay2sClient.sign(
                        "secret",
                        "accessKey=access&amount=250000&extraData=&message=Paymentsuccessful&orderId=order1&orderInfo=SAMorder1&orderType=pay2s&partnerCode=partner&payType=bank&requestId=1234&responseTime=1790000000000&resultCode=0&transId=transaction1"));
        return body;
    }

    @Test
    void hmacMatchesRfc4231Vector() {
        assertEquals(
                "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
                Pay2sClient.sign("\u000b".repeat(20), "Hi There"));
    }

    @Test
    void verifiesProviderSignatureIncludingNumericFields() {
        assertTrue(client().verify(payload()));
    }

    @Test
    void rejectsAmountAndTransactionTampering() {
        var body = payload();
        body.put("amount", 1);
        assertFalse(client().verify(body));
        body = payload();
        body.put("transId", "other");
        assertFalse(client().verify(body));
    }

    @Test
    void rejectsMissingFieldsAndInvalidSignatures() {
        var body = payload();
        body.remove("extraData");
        assertFalse(client().verify(body));
        body = payload();
        body.put("m2signature", "bad");
        assertFalse(client().verify(body));
    }

    @Test
    void rejectsWrongMerchantAndObjects() {
        var body = payload();
        body.put("partnerCode", "other");
        assertFalse(client().verify(body));
        body = payload();
        body.putObject("amount").put("value", 250000);
        assertFalse(client().verify(body));
    }

    @Test
    void productionDoesNotAllowSandboxOriginOrHttpIpn() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new Pay2sClient(
                                "pay2s",
                                "LIVE",
                                "https://sandbox-payment.pay2s.vn",
                                "a",
                                "s",
                                "p",
                                "ACB",
                                "001",
                                "https://example.com"));
        assertThrows(
                IllegalStateException.class,
                () ->
                        new Pay2sClient(
                                "pay2s",
                                "LIVE",
                                "https://payment.pay2s.vn",
                                "a",
                                "s",
                                "p",
                                "ACB",
                                "001",
                                "http://example.com"));
    }
}
