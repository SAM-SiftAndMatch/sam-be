package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class EarlyWithdrawalTransportTest {
    @Test
    void multipartEvidenceIsNotTruncatedByTheJsonBodyLimitAndStillHasNoStoreHeaders()
            throws Exception {
        var filter = new FinancePrivacyFilter();
        var request = new MockHttpServletRequest("POST", "/api/v1/finance/withdrawals/early");
        request.setContentType("multipart/form-data; boundary=proof");
        request.setContent(new byte[50000]);
        var response = new MockHttpServletResponse();
        filter.doFilter(
                request,
                response,
                (incoming, outgoing) ->
                        assertEquals(50000, incoming.getInputStream().readAllBytes().length));
        assertEquals(200, response.getStatus());
        assertEquals("no-store, max-age=0", response.getHeader("Cache-Control"));
    }

    @Test
    void oversizedMultipartAndOversizedJsonAreRejectedBeforeTheController() throws Exception {
        for (boolean multipart : List.of(true, false)) {
            var request = new MockHttpServletRequest("POST", "/api/v1/finance/withdrawals/early");
            request.setContentType(
                    multipart ? "multipart/form-data; boundary=proof" : "application/json");
            request.setContent(new byte[multipart ? 26 * 1024 * 1024 + 1 : 32769]);
            var response = new MockHttpServletResponse();
            new FinancePrivacyFilter()
                    .doFilter(
                            request,
                            response,
                            (incoming, outgoing) -> fail("Oversized request reached controller"));
            assertEquals(413, response.getStatus());
        }
    }

    @Test
    void earlyWithdrawalDtoRequiresReasonAndValidFinancialFields() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var input =
                    new FinanceController.EarlyWithdrawInput(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            java.math.BigDecimal.TEN,
                            " ",
                            "secret");
            assertTrue(
                    validator.validate(input).stream()
                            .anyMatch(
                                    error -> error.getPropertyPath().toString().equals("reason")));
            assertFalse(input.toString().contains("secret"));
        }
    }

    @Test
    void payoutReceiptMultipartIsNotTruncatedAndRequiresAnAdmin() throws Exception {
        var request =
                new MockHttpServletRequest(
                        "POST",
                        "/api/v1/finance/admin/withdrawals/"
                                + UUID.randomUUID()
                                + "/confirm-manual-payment");
        request.setContentType("multipart/form-data; boundary=receipt");
        request.setContent(new byte[50000]);
        var response = new MockHttpServletResponse();
        new FinancePrivacyFilter()
                .doFilter(
                        request,
                        response,
                        (incoming, outgoing) ->
                                assertEquals(
                                        50000, incoming.getInputStream().readAllBytes().length));
        assertEquals("no-store, max-age=0", response.getHeader("Cache-Control"));
        for (String name : List.of("finishWithImages", "payoutReceiptImage")) {
            var method =
                    Arrays.stream(FinanceController.class.getDeclaredMethods())
                            .filter(m -> m.getName().equals(name))
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    "hasRole('ADMIN')",
                    method.getAnnotation(
                                    org.springframework.security.access.prepost.PreAuthorize.class)
                            .value());
        }
        assertEquals(
                "hasRole('ADMIN')",
                WithdrawalImageService.class
                        .getMethod("readReceipt", UUID.class, UUID.class)
                        .getAnnotation(
                                org.springframework.security.access.prepost.PreAuthorize.class)
                        .value());
    }
}
