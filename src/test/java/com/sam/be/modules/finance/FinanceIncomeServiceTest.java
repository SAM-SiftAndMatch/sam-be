package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class FinanceIncomeServiceTest {
    @Test
    void reportingYearAndBoundariesUseVietnamTimezone() {
        var store = mock(FinanceStore.class);
        when(store.now()).thenReturn(Instant.parse("2026-12-31T18:00:00Z"));
        var service = new FinanceIncomeService(store);
        assertEquals(2027, service.reportYear(null));
        assertEquals(Instant.parse("2026-12-31T17:00:00Z"), service.start(2027));
        assertEquals(Instant.parse("2027-12-31T17:00:00Z"), service.start(2028));
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.reportYear(1999));
    }

    @Test
    void missingMonthsAreZeroFilledAndYearTotalsComeFromEarnedRevenue() {
        var store = mock(FinanceStore.class);
        UUID owner = UUID.randomUUID();
        when(store.one(anyString(), eq(owner)))
                .thenReturn(
                        new HashMap<>(
                                Map.of(
                                        "totalNetIncome",
                                        new BigDecimal("180000"),
                                        "sandboxNetIncome",
                                        new BigDecimal("999999"))));
        when(store.rows(anyString(), eq(owner), any(), any()))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "month",
                                        2,
                                        "netIncome",
                                        90000,
                                        "grossIncome",
                                        100000,
                                        "platformFees",
                                        10000,
                                        "completedCount",
                                        1L,
                                        "sandboxNetIncome",
                                        50000)));
        var summary = new FinanceIncomeService(store).summary(owner, 2026);
        assertEquals(new BigDecimal("90000"), summary.get("yearNetIncome"));
        assertEquals(new BigDecimal("10000"), summary.get("yearPlatformFees"));
        assertEquals(new BigDecimal("180000"), summary.get("totalNetIncome"));
        var months = (List<?>) summary.get("months");
        assertEquals(12, months.size());
        assertEquals(0, ((Map<?, ?>) months.getFirst()).get("netIncome"));
        assertEquals(90000, ((Map<?, ?>) months.get(1)).get("netIncome"));
    }
}
