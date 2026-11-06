package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.modules.payment.controller.PaymentController;
import com.sam.be.modules.payment.service.Pay2sIpnService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class Pay2sIpnTransportTest {
    @Test
    void realMvcConvertersDeliverNumericIpnWithoutCrossJacksonBinding() throws Exception {
        var payments = mock(Pay2sIpnService.class);
        when(payments.accept(any()))
                .thenAnswer(
                        invocation -> {
                            com.fasterxml.jackson.databind.JsonNode body =
                                    invocation.getArgument(0);
                            assertEquals(250000, body.get("amount").longValue());
                            assertEquals("tx1", body.get("transId").textValue());
                            return Map.of("success", true);
                        });
        var controller = new PaymentController(null, new ObjectMapper(), payments);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(
                        post("/api/v1/payments/pay2s-ipn")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":250000,\"transId\":\"tx1\"}"))
                .andExpect(status().isOk());
        verify(payments).accept(any());
    }

    @Test
    void duplicateFieldsAreRejectedBeforeSignatureOrFinancialProcessing() {
        var payments = mock(Pay2sIpnService.class);
        var controller = new PaymentController(null, new ObjectMapper(), payments);
        assertThrows(
                com.sam.be.common.exception.ApiException.class,
                () -> controller.pay2sIpn("{\"amount\":250000,\"amount\":1}"));
        verifyNoInteractions(payments);
    }
}
