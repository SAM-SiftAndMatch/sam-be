package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class EarlyWithdrawalServiceTest {
    private final FinanceStore store = mock(FinanceStore.class);
    private final WithdrawalImageService images =
            new WithdrawalImageService(store, BankDataCipherTest.cipher());
    private final WithdrawalService service =
            new WithdrawalService(
                    store,
                    BankDataCipherTest.cipher(),
                    mock(BankAccountService.class),
                    images,
                    mock(WithdrawalNotifications.class),
                    true,
                    true,
                    BigDecimal.ONE,
                    new BigDecimal("100000000"));

    @Test
    void reasonAndImagesAreRequiredBeforeReservingFunds() {
        assertThrows(
                RuntimeException.class,
                () ->
                        service.createEarly(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                BigDecimal.TEN,
                                UUID.randomUUID(),
                                "   ",
                                List.of(WithdrawalImageServiceTest.png())));
        assertThrows(
                RuntimeException.class,
                () ->
                        service.createEarly(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                BigDecimal.TEN,
                                UUID.randomUUID(),
                                "Việc gấp",
                                List.of()));
        verifyNoInteractions(store);
    }

    @Test
    void depositAndExistingReservationsCannotBeSpentEarly() {
        assertEquals(
                new BigDecimal("60000"),
                WithdrawalService.earlyRemaining(
                        Map.of(
                                "earnings",
                                new BigDecimal("90000"),
                                "paid",
                                new BigDecimal("10000"),
                                "reserved",
                                new BigDecimal("15000"),
                                "adjusted",
                                new BigDecimal("5000"))));
    }

    private UUID approval(boolean early, boolean blocked) {
        when(store.jdbc()).thenReturn(mock(org.springframework.jdbc.core.JdbcTemplate.class));
        UUID owner = UUID.randomUUID(),
                withdrawal = UUID.randomUUID(),
                payable = UUID.randomUUID(),
                bank = UUID.randomUUID();
        when(store.one("SELECT owner_id FROM finance_withdrawals WHERE id=?", withdrawal))
                .thenReturn(Map.of("ownerId", owner));
        when(store.one("SELECT * FROM finance_withdrawals WHERE id=? FOR UPDATE", withdrawal))
                .thenReturn(
                        Map.of(
                                "id",
                                withdrawal,
                                "ownerId",
                                owner,
                                "status",
                                "REQUESTED",
                                "requestType",
                                early ? "EARLY" : "NORMAL",
                                "earlyPayableId",
                                payable,
                                "bankAccountId",
                                bank));
        when(store.now()).thenReturn(Instant.parse("2026-11-20T00:00:00Z"));
        when(store.rows(contains("JOIN finance_withdrawal_allocations"), eq(withdrawal)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id",
                                        payable,
                                        "ownerId",
                                        owner,
                                        "sandbox",
                                        false,
                                        "blocked",
                                        blocked,
                                        "availableAt",
                                        "2026-11-25T00:00:00Z")));
        return withdrawal;
    }

    @Test
    void adminCanApproveOnlyEarlyRequestsBeforeDeadlineAndMustRecordReason() {
        UUID request = approval(true, false);
        assertThrows(
                RuntimeException.class, () -> service.approve(UUID.randomUUID(), request, " "));
        assertDoesNotThrow(
                () ->
                        service.approve(
                                UUID.randomUUID(),
                                request,
                                "Đã kiểm tra bằng chứng, chấp thuận chi sớm"));
        UUID normal = approval(false, false);
        assertThrows(
                RuntimeException.class, () -> service.approve(UUID.randomUUID(), normal, null));
    }

    @Test
    void earlyApprovalCannotBypassWarrantyTickets() {
        UUID request = approval(true, true);
        assertThrows(
                RuntimeException.class,
                () -> service.approve(UUID.randomUUID(), request, "Việc gấp"));
        verify(store, never()).jdbc();
    }
}
