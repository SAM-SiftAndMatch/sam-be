package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class ManualPayoutControlTest {
    @Test
    void approverCannotExecuteTheirOwnManualTransferWhenDualControlIsEnabled() {
        var store = mock(FinanceStore.class);
        UUID actor = UUID.randomUUID(), owner = UUID.randomUUID(), id = UUID.randomUUID();
        when(store.one("SELECT owner_id FROM finance_withdrawals WHERE id=?", id))
                .thenReturn(Map.of("ownerId", owner));
        when(store.one("SELECT * FROM finance_withdrawals WHERE id=? FOR UPDATE", id))
                .thenReturn(
                        Map.of(
                                "id",
                                id,
                                "ownerId",
                                owner,
                                "status",
                                "APPROVED",
                                "approvedBy",
                                actor));
        var service =
                new WithdrawalService(
                        store,
                        BankDataCipherTest.cipher(),
                        mock(BankAccountService.class),
                        true,
                        true,
                        BigDecimal.ONE,
                        BigDecimal.TEN);
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.startManual(actor, id));
        verify(store, never()).audit(any(), any(), any(), any(), any());
    }

    @Test
    void paidTransferCanBeAcknowledgedAgainWithoutReposting() {
        var store = mock(FinanceStore.class);
        UUID actor = UUID.randomUUID(), owner = UUID.randomUUID(), id = UUID.randomUUID();
        when(store.one("SELECT owner_id FROM finance_withdrawals WHERE id=?", id))
                .thenReturn(Map.of("ownerId", owner));
        when(store.one("SELECT * FROM finance_withdrawals WHERE id=? FOR UPDATE", id))
                .thenReturn(
                        Map.of(
                                "id",
                                id,
                                "ownerId",
                                owner,
                                "status",
                                "PAID",
                                "bankReference",
                                "FT123"));
        var service =
                new WithdrawalService(
                        store,
                        BankDataCipherTest.cipher(),
                        mock(BankAccountService.class),
                        true,
                        true,
                        BigDecimal.ONE,
                        BigDecimal.TEN);
        assertDoesNotThrow(
                () -> service.finishManual(actor, id, "ft123", "private-proof", "confirmed"));
        verify(store, never()).audit(any(), any(), any(), any(), any());
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.finishManual(actor, id, "FT999", "private-proof", "changed"));
    }

    @Test
    void failedTransferNeedsIndependentReconciliationBeforeReleasingFunds() {
        var store = mock(FinanceStore.class);
        UUID actor = UUID.randomUUID(), owner = UUID.randomUUID(), id = UUID.randomUUID();
        when(store.one("SELECT owner_id FROM finance_withdrawals WHERE id=?", id))
                .thenReturn(Map.of("ownerId", owner));
        when(store.one("SELECT * FROM finance_withdrawals WHERE id=? FOR UPDATE", id))
                .thenReturn(
                        Map.of(
                                "id",
                                id,
                                "ownerId",
                                owner,
                                "status",
                                "RECONCILIATION_REQUIRED",
                                "operatorId",
                                actor));
        var service =
                new WithdrawalService(
                        store,
                        BankDataCipherTest.cipher(),
                        mock(BankAccountService.class),
                        true,
                        true,
                        BigDecimal.ONE,
                        BigDecimal.TEN);
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.rejectOrReconcile(actor, id, "CONFIRM_FAILED", "bank proof"));
        verify(store, never()).audit(any(), any(), any(), any(), any());
    }
}
