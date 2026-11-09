package com.sam.be.modules.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sam.be.modules.finance.FinanceStore;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class PaymentDemoMismatchReconciliationTest {
    private final FinanceStore store = mock(FinanceStore.class);
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final PaymentReconciliationService service = new PaymentReconciliationService(store);
    private final UUID id = UUID.randomUUID(), checkout = UUID.randomUUID();
    private final UUID proposer = UUID.randomUUID(), approver = UUID.randomUUID();
    private final Map<String, Object> candidate = new HashMap<>();

    @BeforeEach
    void setup() {
        when(store.jdbc()).thenReturn(db);
        when(store.one(eq("SELECT checkout_id FROM payment_reconciliation_cases WHERE id=?"), eq(id)))
                .thenReturn(Map.of("checkoutId", checkout));
        when(store.one(startsWith("SELECT * FROM payment_reconciliation_cases"), eq(id)))
                .thenReturn(Map.of("state", "OPEN"));
        candidate.putAll(Map.of("environment", "LIVE", "status", "EXPIRED"));
        when(store.one(eq("SELECT * FROM payment_checkouts WHERE id=?"), eq(checkout))).thenReturn(candidate);
        when(store.rows(startsWith("SELECT actor_id FROM payment_reconciliation_events"),
                eq(id), eq("PROPOSE_DEMO_MISMATCH"))).thenReturn(List.of(Map.of("actorId", proposer)));
    }

    @Test
    void proposalOnlyRecordsEvidenceAndDoesNotCloseCheckout() {
        service.action(proposer, id, "PROPOSE_DEMO_MISMATCH", "fixture:demo QR and no real receipt");
        verify(db).update(startsWith("INSERT INTO payment_reconciliation_events"), any(), eq(id),
                eq(proposer), eq("PROPOSE_DEMO_MISMATCH"), anyString());
        verify(db, never()).update(startsWith("UPDATE payment_checkouts"), any(Object[].class));
    }

    @Test
    void secondAdminArchivesLocalRecordWithDistinctResolutionAndAudit() {
        service.action(approver, id, "APPROVE_DEMO_MISMATCH", "fixture:independently verified");
        verify(db).update(startsWith("UPDATE payment_checkouts SET status='CANCELLED'"), eq(checkout));
        verify(db).update(startsWith("UPDATE payment_reconciliation_cases SET state='RESOLVED'"),
                eq("DEMO_MISMATCH_NO_PAYMENT"), eq(id));
        verify(db).update(startsWith("INSERT INTO payment_reconciliation_events"), any(), eq(id),
                eq(approver), eq("APPROVE_DEMO_MISMATCH"), anyString());
    }

    @Test
    void proposerCannotApproveTheirOwnDemoClassification() {
        assertThrows(ResponseStatusException.class,
                () -> service.action(proposer, id, "APPROVE_DEMO_MISMATCH", "fixture:evidence"));
        verifyNoInteractions(db);
    }

    @Test
    void providerClosureProposalDoesNotAuthorizeDemoClassification() {
        when(store.rows(startsWith("SELECT actor_id FROM payment_reconciliation_events"),
                eq(id), eq("PROPOSE_DEMO_MISMATCH"))).thenReturn(List.of());
        when(store.rows(startsWith("SELECT actor_id FROM payment_reconciliation_events"),
                eq(id), eq("PROPOSE_PROVIDER_CLOSED"))).thenReturn(List.of(Map.of("actorId", proposer)));
        assertThrows(ResponseStatusException.class,
                () -> service.action(approver, id, "APPROVE_DEMO_MISMATCH", "fixture:evidence"));
        verifyNoInteractions(db);
    }

    @Test
    void onlyInactiveLiveRecordsAreEligible() {
        candidate.put("environment", "SANDBOX");
        assertThrows(ResponseStatusException.class,
                () -> service.action(proposer, id, "PROPOSE_DEMO_MISMATCH", "fixture:evidence"));
        candidate.put("environment", "LIVE");
        for (String state : List.of("PAID", "REVIEW", "CREATING", "PENDING")) {
            candidate.put("status", state);
            assertThrows(ResponseStatusException.class,
                    () -> service.action(approver, id, "APPROVE_DEMO_MISMATCH", "fixture:evidence"));
        }
        verifyNoInteractions(db);
    }

    @Test
    void anyReceiptPreventsArchivingAsAnUnpaidDemo() {
        when(store.rows(startsWith("SELECT id FROM payment_checkout_receipts"), eq(checkout)))
                .thenReturn(List.of(Map.of("id", UUID.randomUUID())));
        assertThrows(ResponseStatusException.class,
                () -> service.action(approver, id, "APPROVE_DEMO_MISMATCH", "fixture:evidence"));
        verifyNoInteractions(db);
    }

    @Test
    void verifiedSuccessInAnyEnvironmentPreventsDemoArchival() {
        when(store.rows(startsWith("SELECT id FROM payment_provider_events WHERE order_reference"),
                eq(checkout.toString().replace("-", ""))))
                .thenReturn(List.of(Map.of("id", UUID.randomUUID())));
        assertThrows(ResponseStatusException.class,
                () -> service.action(approver, id, "APPROVE_DEMO_MISMATCH", "fixture:evidence"));
        verifyNoInteractions(db);
    }

    @Test
    void runningWorkerPreventsDemoArchival() {
        when(store.rows(startsWith("SELECT checkout_id FROM payment_creation_tasks"), eq(checkout)))
                .thenReturn(List.of(Map.of("checkoutId", checkout)));
        assertThrows(ResponseStatusException.class,
                () -> service.action(approver, id, "APPROVE_DEMO_MISMATCH", "fixture:evidence"));
        verifyNoInteractions(db);
    }

    @Test
    void evidenceIsRequiredForBothProposalAndApproval() {
        for (String action : List.of("PROPOSE_DEMO_MISMATCH", "APPROVE_DEMO_MISMATCH")) {
            assertThrows(ResponseStatusException.class, () -> service.action(proposer, id, action, " "));
        }
        verifyNoInteractions(db);
    }
}
