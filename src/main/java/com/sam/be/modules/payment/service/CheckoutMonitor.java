package com.sam.be.modules.payment.service;

import com.sam.be.modules.finance.FinanceStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CheckoutMonitor {
    private final FinanceStore store;
    private final PaymentReconciliationService cases;
    private final AtomicLong open = new AtomicLong(),
            overdue = new AtomicLong(),
            inbox = new AtomicLong();

    public CheckoutMonitor(
            FinanceStore store, PaymentReconciliationService cases, MeterRegistry registry) {
        this.store = store;
        this.cases = cases;
        registry.gauge("sam.payments.reconciliation.open", open);
        registry.gauge("sam.payments.reconciliation.overdue", overdue);
        registry.gauge("sam.payments.inbox.pending", inbox);
    }

    @Scheduled(fixedDelayString = "${payments.monitor-delay-ms:30000}")
    @Transactional
    public void tick() {
        store.jdbc().update(
                "UPDATE payment_checkouts SET status='EXPIRED' WHERE status='PENDING' AND expires_at<=clock_timestamp()");
        for (var row :
                store.rows(
                        "SELECT * FROM (SELECT id,CASE WHEN status='CREATING' THEN 'CREATE_STUCK' WHEN status='UNKNOWN' THEN 'CREATE_UNKNOWN' WHEN status='EXPIRED' THEN 'CHECKOUT_EXPIRED' ELSE 'FULFILLMENT_STUCK' END AS reason FROM payment_checkouts WHERE (status='CREATING' AND created_at<clock_timestamp()-INTERVAL '60 seconds') OR (status='UNKNOWN' AND created_at<clock_timestamp()-INTERVAL '2 minutes') OR status='EXPIRED' OR (status='PAID' AND fulfillment_status<>'APPLIED' AND paid_at<clock_timestamp()-INTERVAL '2 minutes')) c WHERE NOT EXISTS(SELECT 1 FROM payment_reconciliation_cases r WHERE r.case_key='checkout:'||c.id||':'||c.reason) LIMIT 200")) {
            var id = FinanceStore.id(row, "id");
            String reason = row.get("reason").toString();
            cases.open("checkout:" + id + ":" + reason, id, null, reason);
        }
        for (var row :
                store.rows(
                        "SELECT id FROM payment_provider_events WHERE state='PENDING' AND created_at<clock_timestamp()-INTERVAL '2 minutes' LIMIT 100")) {
            var id = FinanceStore.id(row, "id");
            cases.open("event:" + id + ":INBOX_STUCK", null, id, "INBOX_STUCK");
        }
        store.jdbc().update(
                "UPDATE payment_reconciliation_cases r SET state='RESOLVED',resolution='RECOVERED',resolved_at=clock_timestamp(),updated_at=clock_timestamp() FROM payment_checkouts c WHERE c.id=r.checkout_id AND r.state<>'RESOLVED' AND ((r.reason='FULFILLMENT_STUCK' AND c.fulfillment_status='APPLIED') OR (r.reason IN ('CREATE_STUCK','CREATE_UNKNOWN','LEGACY_UNCERTAIN') AND c.status IN ('PENDING','PAID')))");
        open.set(
                store.jdbc().queryForObject(
                        "SELECT count(*) FROM payment_reconciliation_cases WHERE state<>'RESOLVED'",
                        Long.class));
        overdue.set(
                store.jdbc().queryForObject(
                        "SELECT count(*) FROM payment_reconciliation_cases WHERE state<>'RESOLVED' AND due_at<clock_timestamp()",
                        Long.class));
        inbox.set(
                store.jdbc().queryForObject(
                        "SELECT count(*) FROM payment_provider_events WHERE state='PENDING'",
                        Long.class));
    }
}
