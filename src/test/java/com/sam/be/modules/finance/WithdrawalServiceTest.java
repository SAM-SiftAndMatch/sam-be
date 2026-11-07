package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class WithdrawalServiceTest {
    @Test
    void doesNotReserveMaintenanceOrBlockedOrSandboxFunds() {
        var db = mock(JdbcTemplate.class);
        UUID owner = UUID.randomUUID(), bank = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-10T00:00:00Z");
        var store = spy(new FinanceStore(db, Clock.fixed(now, ZoneOffset.UTC)));
        doNothing().when(store).lockOwner(owner);
        doReturn(List.of())
                .when(store)
                .rows(
                        "SELECT id,owner_id,bank_account_id,bank_code,last_four,amount,status,approved_by,operator_id,bank_reference,evidence_reference,created_at,updated_at FROM finance_withdrawals WHERE owner_id=? AND request_key=?",
                        owner,
                        new UUID(0, 1));
        doReturn(Map.of("id", bank))
                .when(store)
                .one(
                        "SELECT * FROM finance_bank_accounts WHERE id=? AND owner_id=? AND status='VERIFIED'",
                        bank,
                        owner);
        when(db.queryForObject(anyString(), eq(BigDecimal.class), eq(owner), any()))
                .thenReturn(BigDecimal.ZERO);
        List<Map<String, Object>> payables = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            var p = new HashMap<String, Object>();
            p.put("amount", new BigDecimal("100000"));
            p.put("reserved", BigDecimal.ZERO);
            p.put("paid", BigDecimal.ZERO);
            p.put("adjusted", BigDecimal.ZERO);
            p.put("sandbox", index == 2);
            p.put("blocked", index == 1);
            p.put(
                    "availableAt",
                    (index == 0 ? now.plusSeconds(1) : now.minusSeconds(1)).toString());
            payables.add(p);
        }
        doReturn(payables).when(store).payables(owner);
        var service =
                new WithdrawalService(
                        store,
                        BankDataCipherTest.cipher(),
                        mock(BankAccountService.class),
                        true,
                        true,
                        new BigDecimal("50000"),
                        new BigDecimal("100000000"));
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.create(owner, bank, new UUID(0, 1), new BigDecimal("50000")));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentAmount() {
        var store = mock(FinanceStore.class);
        UUID owner = UUID.randomUUID(), bank = UUID.randomUUID(), key = UUID.randomUUID();
        when(store.rows(anyString(), eq(owner), eq(key)))
                .thenReturn(
                        List.of(Map.of("bankAccountId", bank, "amount", new BigDecimal("50000"))));
        var service =
                new WithdrawalService(
                        store,
                        BankDataCipherTest.cipher(),
                        mock(BankAccountService.class),
                        true,
                        true,
                        new BigDecimal("50000"),
                        new BigDecimal("100000000"));
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.create(owner, bank, key, new BigDecimal("60000")));
        verify(store, never()).payables(any());
    }

    @Test
    void livePayoutsCannotStartWithoutEncryptionKeys() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new WithdrawalService(
                                mock(FinanceStore.class),
                                new BankDataCipher("v1", "", ""),
                                mock(BankAccountService.class),
                                true,
                                true,
                                new BigDecimal("1"),
                                new BigDecimal("100")));
    }

    @Test
    void disabledPayoutsFailBeforeAnyMutation() {
        var store = mock(FinanceStore.class);
        var service =
                new WithdrawalService(
                        store,
                        BankDataCipherTest.cipher(),
                        mock(BankAccountService.class),
                        false,
                        true,
                        new BigDecimal("1"),
                        new BigDecimal("100"));
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () ->
                        service.create(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                BigDecimal.TEN));
        verifyNoInteractions(store);
    }
}
