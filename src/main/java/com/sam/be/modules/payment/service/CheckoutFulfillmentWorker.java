package com.sam.be.modules.payment.service;

import com.sam.be.modules.finance.*;
import com.sam.be.modules.job.service.JobAddonService;
import com.sam.be.modules.subscription.service.SubscriptionService;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckoutFulfillmentWorker {
    private final FinanceStore store;
    private final PaymentService payments;
    private final SubscriptionService subscriptions;
    private final JobAddonService addons;
    private final PlatformTransactionManager transactions;

    @Scheduled(fixedDelayString = "${payments.fulfillment-delay-ms:2000}")
    public void tick() {
        for (int n = 0; n < 5; n++) {
            AtomicReference<UUID> current = new AtomicReference<>();
            try {
                Boolean processed =
                        new TransactionTemplate(transactions)
                                .execute(
                                        tx -> {
                                            var rows =
                                                    store.rows(
                                                            "SELECT * FROM payment_checkouts WHERE status='PAID' AND fulfillment_status IN ('PENDING','RETRY') AND next_attempt_at<=clock_timestamp() ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED");
                                            if (rows.isEmpty()) return false;
                                            var row = rows.getFirst();
                                            UUID id = FinanceStore.id(row, "id");
                                            current.set(id);
                                            UUID object = FinanceStore.id(row, "objectId");
                                            String purpose = row.get("purpose").toString();
                                            String result;
                                            if ("SUBSCRIPTION".equals(purpose))
                                                result =
                                                        subscriptions.activateVerifiedPayment(
                                                                object,
                                                                row.get("environment").toString(),
                                                                FinanceStore.money(row, "amount"));
                                            else if ("JOB_ADDON".equals(purpose))
                                                result =
                                                        addons.activateVerifiedPayment(
                                                                object,
                                                                id.toString().replace("-", ""),
                                                                row.get("environment").toString());
                                            else {
                                                result =
                                                        payments.applyVerifiedPay2sPayment(
                                                                object,
                                                                id.toString().replace("-", ""),
                                                                row.get("transactionReference")
                                                                        .toString(),
                                                                FinanceStore.money(row, "amount"));
                                            }
                                            if (Set.of("SUBSCRIPTION", "JOB_ADDON")
                                                    .contains(purpose)) {
                                                String prefix =
                                                        "LIVE".equals(row.get("environment"))
                                                                ? "live:"
                                                                : "sandbox:";
                                                var amount = FinanceStore.money(row, "amount");
                                                store.journal(
                                                        "checkout-fulfilled:" + id,
                                                        null,
                                                        FinanceStore.id(row, "ownerId"),
                                                        "Checkout fulfillment",
                                                        !"LIVE".equals(row.get("environment")),
                                                        FinanceStore.debit(
                                                                prefix + "service-pending:" + id,
                                                                amount),
                                                        FinanceStore.credit(
                                                                prefix
                                                                        + ("APPLIED".equals(result)
                                                                                ? "platform-revenue"
                                                                                : "receipt-review:"
                                                                                        + id),
                                                                amount));
                                            }
                                            store.jdbc().update(
                                                    "UPDATE payment_checkouts SET fulfillment_status=? WHERE id=?",
                                                    result,
                                                    id);
                                            return true;
                                        });
                if (!Boolean.TRUE.equals(processed)) break;
            } catch (Exception ex) {
                if (current.get() == null) {
                    log.error("CHECKOUT_WORKER_FAILED type={}", ex.getClass().getSimpleName());
                    break;
                }
                new TransactionTemplate(transactions)
                        .executeWithoutResult(
                                tx ->
                                        store.jdbc().update(
                                                "UPDATE payment_checkouts SET fulfillment_status='RETRY',attempt_count=attempt_count+1,next_attempt_at=clock_timestamp()+make_interval(secs => LEAST(3600, 10 * power(2,LEAST(attempt_count,8)))::int) WHERE id=? AND status='PAID' AND fulfillment_status IN ('PENDING','RETRY')",
                                                current.get()));
                log.warn(
                        "CHECKOUT_FULFILLMENT_RETRY order={} type={}",
                        current.get(),
                        ex.getClass().getSimpleName());
            }
        }
    }
}
