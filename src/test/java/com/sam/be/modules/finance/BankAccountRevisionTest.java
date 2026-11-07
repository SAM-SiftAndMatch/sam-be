package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BankAccountRevisionTest {
    final UUID owner = UUID.randomUUID(), oldId = UUID.randomUUID(), newId = UUID.randomUUID();
    final FinanceStore store = mock(FinanceStore.class);
    final JdbcTemplate db = mock(JdbcTemplate.class);
    final BankDataCipher cipher = BankDataCipherTest.cipher();
    BankAccountService service;
    Map<String, Object> old;

    @BeforeEach
    void prepare() {
        when(store.jdbc()).thenReturn(db);
        service = spy(new BankAccountService(store, cipher, new ObjectMapper()));
        old =
                new HashMap<>(
                        Map.of(
                                "id",
                                oldId,
                                "ownerId",
                                owner,
                                "bankCode",
                                "VCB",
                                "status",
                                "VERIFIED"));
        old.put(
                "detailsEncrypted",
                cipher.encrypt(
                        service.encode(new BankAccountService.Details("00123456789", "USER A")),
                        "bank:" + owner + ":" + oldId));
        when(store.one(
                        "SELECT * FROM finance_bank_accounts WHERE id=? AND owner_id=? FOR UPDATE",
                        oldId,
                        owner))
                .thenReturn(old);
        when(store.one(
                        "SELECT bank_code,details_encrypted FROM finance_bank_accounts WHERE id=? AND owner_id=?",
                        oldId,
                        owner))
                .thenReturn(old);
        when(db.queryForObject(anyString(), eq(Long.class), eq(oldId))).thenReturn(0L);
        doReturn(Map.of("id", newId, "status", "PENDING"))
                .when(service)
                .add(owner, "ACB", "00987654321", "USER A");
    }

    @Test
    void editKeepsOldCiphertextAndCreatesAnUnverifiedRevision() {
        String ciphertext = old.get("detailsEncrypted").toString();
        var result = service.replace(owner, oldId, "acb", "00987654321", "USER A");
        assertEquals("PENDING", result.get("status"));
        assertEquals(ciphertext, old.get("detailsEncrypted"));
        var order = inOrder(db, service);
        order.verify(db)
                .update("UPDATE finance_bank_accounts SET status='DISABLED' WHERE id=?", oldId);
        order.verify(service).add(owner, "ACB", "00987654321", "USER A");
        order.verify(db)
                .update("UPDATE finance_bank_accounts SET replaced_by=? WHERE id=?", newId, oldId);
        verify(store)
                .audit(
                        eq(owner),
                        eq(owner),
                        eq(oldId),
                        eq("BANK_UPDATED"),
                        argThat(note -> !note.contains("00987654321") && !note.contains("USER A")));
    }

    @Test
    void cannotReplaceBeneficiaryOfAnActiveWithdrawal() {
        when(db.queryForObject(anyString(), eq(Long.class), eq(oldId))).thenReturn(1L);
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.replace(owner, oldId, "ACB", "00987654321", "USER A"));
        verify(db, never()).update(anyString(), any(Object[].class));
        verify(service, never()).add(any(), any(), any(), any());
    }

    @Test
    void retryWithSameDetailsReturnsTheAlreadyCreatedRevision() {
        old.put("status", "DISABLED");
        old.put("replacedBy", newId);
        String replacementCipher =
                cipher.encrypt(
                        service.encode(new BankAccountService.Details("00987654321", "USER A")),
                        "bank:" + owner + ":" + newId);
        when(store.one(
                        "SELECT bank_code,details_encrypted FROM finance_bank_accounts WHERE id=? AND owner_id=?",
                        newId,
                        owner))
                .thenReturn(Map.of("bankCode", "ACB", "detailsEncrypted", replacementCipher));
        when(store.one(startsWith("SELECT id,"), eq(newId), eq(owner)))
                .thenReturn(Map.of("id", newId, "status", "PENDING"));
        assertEquals(
                newId, service.replace(owner, oldId, "ACB", "00987654321", "USER A").get("id"));
        verify(db, never()).update(anyString(), any(Object[].class));
        verify(service, never()).add(any(), any(), any(), any());
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.replace(owner, oldId, "ACB", "00987654321", "CHANGED NAME"));
    }

    @Test
    void anotherOwnerCannotChangeOrProbeBankDetails() {
        UUID outsider = UUID.randomUUID();
        when(store.one(
                        "SELECT * FROM finance_bank_accounts WHERE id=? AND owner_id=? FOR UPDATE",
                        oldId,
                        outsider))
                .thenThrow(FinanceStore.missing());
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.replace(outsider, oldId, "ACB", "00987654321", "USER A"));
        verify(db, never()).update(anyString(), any(Object[].class));
        verify(service, never()).add(any(), any(), any(), any());
    }
}
