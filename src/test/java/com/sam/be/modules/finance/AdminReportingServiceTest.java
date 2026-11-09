package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminReportingServiceTest {
    FinanceStore store = mock(FinanceStore.class);
    AdminReportingService service = new AdminReportingService(store);

    @Test
    void rejectsUnboundedOrInvalidFiltersBeforeQueryingDatabase() {
        var day = LocalDate.of(2026, 11, 9);
        assertThrows(
                ResponseStatusException.class,
                () -> service.revenue(day, day.minusDays(1), "", null, "", 0));
        assertThrows(
                ResponseStatusException.class,
                () -> service.revenue(day.minusYears(3), day, "", null, "", 0));
        assertThrows(
                ResponseStatusException.class,
                () -> service.revenue(day, day, "ESCROW", null, "", 0));
        assertThrows(ResponseStatusException.class, () -> service.users("SUPERADMIN", "", "", 0));
        assertThrows(ResponseStatusException.class, () -> service.users("", "", "LOCKED", 0));
        assertThrows(
                ResponseStatusException.class, () -> service.users("", "x".repeat(101), "", 0));
        assertThrows(ResponseStatusException.class, () -> service.users("", "", "", -1));
        verifyNoInteractions(store);
    }

    @Test
    void zeroFillsVietnameseCalendarDaysIncludingEmptyRevenue() {
        when(store.one(anyString(), any(Object[].class)))
                .thenReturn(Map.of("count", 0L, "total", 0));
        var from = LocalDate.of(2026, 11, 7);
        var response = service.revenue(from, from.plusDays(2), "", null, "", 0);
        var series = (List<?>) response.get("series");
        assertEquals(3, series.size());
        assertEquals(Map.of("day", "2026-11-07", "amount", 0, "count", 0), series.getFirst());
        assertEquals("Asia/Ho_Chi_Minh", response.get("timezone"));
    }
}
