package com.sam.be.modules.payment.service;

import com.sam.be.common.exception.PaymentGatewayException;
import com.sam.be.modules.finance.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckoutCreationWorker {
    private final FinanceStore store;
    private final Pay2sClient provider;
    private final PlatformTransactionManager transactions;
    private final PaymentReconciliationService cases;

    @Value("${payments.creation-enabled:true}")
    private boolean enabled = true;

    @Scheduled(fixedDelayString = "${payments.creation-delay-ms:1000}")
    public void tick() {
        recoverExpiredLeases();
        if (!enabled) return;
        for (int i = 0; i < 5; i++) {
            var task =
                    new TransactionTemplate(transactions)
                            .execute(
                                    tx -> {
                                        var rows =
                                                store.rows(
                                                        "SELECT t.checkout_id FROM payment_creation_tasks t JOIN payment_checkouts c ON c.id=t.checkout_id WHERE t.state='READY' AND c.status='CREATING' ORDER BY t.updated_at LIMIT 1 FOR UPDATE OF t SKIP LOCKED");
                                        if (rows.isEmpty()) return null;
                                        UUID id = FinanceStore.id(rows.getFirst(), "checkoutId");
                                        UUID worker = UUID.randomUUID();
                                        return store.one(
                                                "UPDATE payment_creation_tasks SET state='LEASED',locked_by=?,locked_until=clock_timestamp()+INTERVAL '60 seconds',generation=generation+1,updated_at=clock_timestamp() WHERE checkout_id=? RETURNING *",
                                                worker,
                                                id);
                                    });
            if (task == null) break;
            dispatch(task);
        }
    }

    void dispatch(Map<String, Object> task) {
        UUID id = FinanceStore.id(task, "checkoutId");
        long generation = ((Number) task.get("generation")).longValue();
        var order =
                new TransactionTemplate(transactions)
                        .execute(
                                tx -> {
                                    var snapshot=store.one("SELECT * FROM payment_checkouts WHERE id=? FOR UPDATE",id);
                                    if (!"CREATING".equals(snapshot.get("status"))) {
                                        store.jdbc().update("UPDATE payment_creation_tasks SET state=CASE WHEN ?='UNKNOWN' THEN 'UNKNOWN' ELSE 'DONE' END,locked_until=NULL,updated_at=clock_timestamp() WHERE checkout_id=? AND generation=? AND state='LEASED'",snapshot.get("status"),id,generation);
                                        return null;
                                    }
                                    // Commit the possible-dispatch marker before HTTP.
                                    int changed=store.jdbc().update("UPDATE payment_creation_tasks SET dispatched_at=clock_timestamp(),updated_at=clock_timestamp() WHERE checkout_id=? AND state='LEASED' AND generation=? AND locked_until>clock_timestamp() AND dispatched_at IS NULL",id,generation);
                                    return changed==1?snapshot:null;
                                });
        if (order == null) return;
        try {
            var result =
                    provider.create(
                            id.toString().replace("-", ""),
                            FinanceStore.money(order, "amount").longValueExact(),
                            task.get("returnUrl").toString(),
                            order.get("purpose").toString());
            new TransactionTemplate(transactions)
                    .executeWithoutResult(
                            tx -> {
                                store.one(
                                        "SELECT id FROM payment_checkouts WHERE id=? FOR UPDATE",
                                        id);
                                store.jdbc().update("INSERT INTO payment_creation_observations(id,checkout_id,generation,checkout_url,transfer_content) VALUES (?,?,?,?,?)",UUID.randomUUID(),id,generation,result.url(),result.transferContent());
                                int changed =
                                        store.jdbc().update(
                                                "UPDATE payment_creation_tasks SET state='DONE',locked_until=NULL,updated_at=clock_timestamp() WHERE checkout_id=? AND generation=? AND state IN ('LEASED','UNKNOWN')",
                                                id,
                                                generation);
                                if (changed == 0) {
                                    cases.open("checkout:"+id+":LATE_CREATE_RESPONSE",id,null,"LATE_CREATE_RESPONSE");
                                    return;
                                }
                                store.jdbc().update(
                                        "UPDATE payment_checkouts SET checkout_url=?,provider_transfer_content=COALESCE(provider_transfer_content,?),status=CASE WHEN status IN ('CREATING','UNKNOWN') THEN 'PENDING' ELSE status END WHERE id=?",
                                        result.url(),
                                        result.transferContent(),
                                        id);
                                cases.resolveTechnical(id);
                            });
        } catch (Exception ex) {
            String reason =
                    ex instanceof PaymentGatewayException e
                            ? e.diagnostics().getOrDefault("reason", "UNKNOWN")
                            : "DISPATCH_FAILURE";
            if (Set.of("IPN_URL_INVALID", "REDIRECT_URL_INVALID").contains(reason)) {
                new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    store.one("SELECT id FROM payment_checkouts WHERE id=? FOR UPDATE",id);
                    store.jdbc().update("UPDATE payment_creation_tasks SET state='REJECTED',error_code=?,locked_until=NULL,updated_at=clock_timestamp() WHERE checkout_id=? AND generation=? AND state='LEASED'",reason,id,generation);
                    store.jdbc().update("UPDATE payment_checkouts SET status='CANCELLED',replacement_allowed=true WHERE id=? AND status='CREATING'",id);
                    cases.open("checkout:"+id+":LOCAL_CONFIGURATION_REJECTED",id,null,"LOCAL_CONFIGURATION_REJECTED");
                });
                return;
            }
            new TransactionTemplate(transactions)
                    .executeWithoutResult(
                            tx -> {
                                store.one(
                                        "SELECT id FROM payment_checkouts WHERE id=? FOR UPDATE",
                                        id);
                                store.jdbc().update(
                                        "UPDATE payment_creation_tasks SET state='UNKNOWN',error_code=?,locked_until=NULL,updated_at=clock_timestamp() WHERE checkout_id=? AND generation=? AND state='LEASED'",
                                        reason,
                                        id,
                                        generation);
                                store.jdbc().update(
                                        "UPDATE payment_checkouts SET status='UNKNOWN' WHERE id=? AND status='CREATING'",
                                        id);
                                // No HTTP status alone is used as proof of non-creation.
                                cases.open(
                                        "checkout:" + id + ":CREATE_UNKNOWN",
                                        id,
                                        null,
                                        "CREATE_UNKNOWN");
                            });
            log.warn("CHECKOUT_CREATE_UNCERTAIN order={} reason={}", id, reason);
        }
    }

    void recoverExpiredLeases() {
        for (var row : store.rows("SELECT checkout_id FROM payment_creation_tasks WHERE state='LEASED' AND locked_until<clock_timestamp() LIMIT 100")) {
            UUID id=FinanceStore.id(row,"checkoutId");
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                store.one("SELECT id FROM payment_checkouts WHERE id=? FOR UPDATE",id);
                int changed=store.jdbc().update("UPDATE payment_creation_tasks SET state=CASE WHEN dispatched_at IS NULL THEN 'READY' ELSE 'UNKNOWN' END,locked_by=NULL,locked_until=NULL,updated_at=clock_timestamp() WHERE checkout_id=? AND state='LEASED' AND locked_until<clock_timestamp()",id);
                if(changed==1) store.jdbc().update("UPDATE payment_checkouts SET status='UNKNOWN' WHERE id=? AND status='CREATING' AND EXISTS(SELECT 1 FROM payment_creation_tasks WHERE checkout_id=? AND state='UNKNOWN')",id,id);
            });
        }
    }
}
