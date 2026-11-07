package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class FinancePolicyTest {
    private final Instant accepted = Instant.parse("2026-11-06T07:30:00Z");

    @Test
    void exact168HoursBoundary() {
        Instant end = accepted.plusSeconds(168 * 3600);
        assertTrue(FinancePolicy.canOpen(accepted, end.minusNanos(1)));
        assertFalse(FinancePolicy.canOpen(accepted, end));
        assertFalse(FinancePolicy.canRelease(end, end.minusNanos(1), false));
        assertTrue(FinancePolicy.canRelease(end, end, false));
    }

    @Test
    void noEarlyTicketOrWithdrawal() {
        assertFalse(FinancePolicy.canOpen(accepted, accepted.minusSeconds(1)));
        assertFalse(
                FinancePolicy.canRelease(
                        accepted.plus(FinancePolicy.WARRANTY),
                        accepted.plus(Duration.ofDays(3)),
                        false));
    }

    @Test
    void oldIssueKeepsBlockingAfterWindowEnds() {
        assertFalse(
                FinancePolicy.canRelease(
                        accepted.plus(FinancePolicy.WARRANTY),
                        accepted.plus(Duration.ofDays(13)),
                        true));
        assertTrue(
                FinancePolicy.canRelease(
                        accepted.plus(FinancePolicy.WARRANTY),
                        accepted.plus(Duration.ofDays(13)),
                        false));
    }

    @Test
    void sandboxIsNeverEligibleEvenAfterDeadline() {
        var store =
                new FinanceStore(
                        org.mockito.Mockito.mock(JdbcTemplate.class),
                        Clock.fixed(accepted.plus(Duration.ofDays(9)), ZoneOffset.UTC));
        Map<String, Object> p = new HashMap<>();
        p.put("sandbox", true);
        p.put("availableAt", accepted.plus(FinancePolicy.WARRANTY).toString());
        p.put("blocked", false);
        assertFalse(store.eligible(p));
        p.put("sandbox", false);
        assertTrue(store.eligible(p));
        p.put("blocked", true);
        assertFalse(store.eligible(p));
    }
}
