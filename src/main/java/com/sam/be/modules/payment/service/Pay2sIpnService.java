package com.sam.be.modules.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.modules.finance.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class Pay2sIpnService {
    private final Pay2sClient pay2s;
    private final FinanceStore store;
    private final PaymentService legacyPay2sPayments;
    private final PlatformTransactionManager transactions;
    private final PaymentReconciliationService cases;
    private final ObjectMapper json;
    private io.micrometer.core.instrument.MeterRegistry metrics;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void metrics(io.micrometer.core.instrument.MeterRegistry registry) { this.metrics=registry; }


    public Map<String, Boolean> accept(JsonNode payload) {
        if (!pay2s.verify(payload)) {
            if (metrics!=null) metrics.counter("sam.payments.ipn.rejected","reason","signature").increment();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Pay2S signature");
        }
        var safe = json.createObjectNode();
        for (String key :
                List.of(
                        "accessKey",
                        "amount",
                        "extraData",
                        "message",
                        "orderId",
                        "orderInfo",
                        "orderType",
                        "partnerCode",
                        "payType",
                        "requestId",
                        "responseTime",
                        "resultCode",
                        "transId",
                        "m2signature")) safe.set(key, payload.get(key));
        String hash = ProviderIdentity.hash(safe.toString());
        TransactionTemplate capture = new TransactionTemplate(transactions);
        capture.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UUID id =
                capture.execute(
                        tx -> {
                            UUID event = UUID.randomUUID();
                            store.jdbc().update(
                                    "INSERT INTO payment_provider_events(id,environment,merchant_scope,event_hash,verified_payload,order_reference,result_code,amount_text,provider_reference,transfer_content) VALUES (?,?,?,?,?::jsonb,?,?,?,?,?) ON CONFLICT(environment,merchant_scope,event_hash) DO NOTHING",
                                    event,
                                    pay2s.environment(),
                                    pay2s.merchantScope(),
                                    hash,
                                    safe.toString(),
                                    clip(payload.path("orderId").asText(), 80),
                                    payload.path("resultCode").asInt(-1),
                                    clip(payload.path("amount").asText(), 80),
                                    clip(payload.path("transId").asText(), 150),
                                    clip(payload.path("orderInfo").asText(), 32));
                            return FinanceStore.id(
                                    store.one(
                                            "SELECT id FROM payment_provider_events WHERE environment=? AND merchant_scope=? AND event_hash=?",
                                            pay2s.environment(),
                                            pay2s.merchantScope(),
                                            hash),
                                    "id");
                        });
        tryProcess(id);
        return Map.of("success", true);
    }

    @Scheduled(fixedDelayString = "${payments.ipn-retry-delay-ms:2000}")
    public void retry() {
        for (var row :
                store.rows(
                        "SELECT id FROM payment_provider_events WHERE state='PENDING' AND next_attempt_at<=clock_timestamp() ORDER BY next_attempt_at LIMIT 20"))
            tryProcess(FinanceStore.id(row, "id"));
    }

    void tryProcess(UUID id) {
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> process(id));
        } catch (Exception ex) {
            new TransactionTemplate(transactions)
                    .executeWithoutResult(
                            tx ->
                                    store.jdbc().update(
                                            "UPDATE payment_provider_events SET attempt_count=attempt_count+1,next_attempt_at=clock_timestamp()+make_interval(secs=>LEAST(3600,10*power(2,LEAST(attempt_count,8)))::int),error_code='PROCESSING_RETRY' WHERE id=? AND state='PENDING'",
                                            id));
            log.warn("PAY2S_INBOX_RETRY event={} type={}", id, ex.getClass().getSimpleName());
        }
    }

    private void process(UUID eventId) {
        var event = store.one("SELECT * FROM payment_provider_events WHERE id=?", eventId);
        if (!"PENDING".equals(event.get("state"))) return;
        if (!pay2s.environment().equals(event.get("environment"))
                || !pay2s.merchantScope().equals(event.get("merchantScope"))) {
            review(eventId, null, "MERCHANT_CHANGED");
            return;
        }
        String order = event.get("orderReference").toString();
        String reference = event.get("providerReference").toString();
        try {
            var signed=json.readTree(event.get("verifiedPayload").toString());
            if (!order.equals(signed.path("orderId").asText()) || !reference.equals(signed.path("transId").asText()) || !event.get("amountText").toString().equals(signed.path("amount").asText())) {
                review(eventId,null,"INVALID_PROVIDER_FIELDS");return;
            }
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }

        store.jdbc().queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                pay2s.environment() + ":" + reference);
        UUID checkout;
        try {
            if (!order.matches("[0-9a-f]{32}")) throw new IllegalArgumentException();
            checkout =
                    UUID.fromString(
                            order.substring(0, 8)
                                    + "-"
                                    + order.substring(8, 12)
                                    + "-"
                                    + order.substring(12, 16)
                                    + "-"
                                    + order.substring(16, 20)
                                    + "-"
                                    + order.substring(20));
        } catch (IllegalArgumentException ex) {
            review(eventId, null, "UNMATCHED_ORDER");
            return;
        }
        var rows =
                store.rows(
                        "SELECT *,expires_at>clock_timestamp() AS payable_now FROM payment_checkouts WHERE id=? FOR UPDATE",
                        checkout);
        var claimed =
                store.rows(
                        "SELECT id FROM payment_provider_events WHERE id=? AND state='PENDING' FOR UPDATE SKIP LOCKED",
                        eventId);
        if (claimed.isEmpty()) return;
        if (rows.isEmpty()) {
            if (!store.rows(
                            "SELECT id FROM payments WHERE payment_gateway_id=? AND provider='PAY2S' AND environment=?",
                            order,
                            pay2s.environment())
                    .isEmpty()) {
                try {
                    legacyPay2sPayments.handlePay2sIpn(
                            json.readTree(event.get("verifiedPayload").toString()));
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException(ex);
                }
                applied(eventId);
            } else review(eventId, null, "UNMATCHED_ORDER");
            return;
        }
        var row = rows.getFirst();
        if (!pay2s.environment().equals(row.get("environment"))
                || row.get("merchantScope") != null
                        && !pay2s.merchantScope().equals(row.get("merchantScope"))) {
            review(eventId, checkout, "MERCHANT_MISMATCH");
            return;
        }
        int code = ((Number) event.get("resultCode")).intValue();
        if (code == 2) {
            BigDecimal cancelledAmount;
            try {
                cancelledAmount = new BigDecimal(event.get("amountText").toString());
            } catch (NumberFormatException ex) {
                review(eventId, checkout, "INVALID_CANCELLATION_AMOUNT");
                return;
            }
            if (cancelledAmount.compareTo(FinanceStore.money(row, "amount")) != 0) {
                review(eventId, checkout, "CANCELLATION_AMOUNT_MISMATCH");
                return;
            }
            if ("PAID".equals(row.get("status"))
                    || "REVIEW".equals(row.get("status"))
                    || !store.rows(
                                    "SELECT id FROM payment_checkout_receipts WHERE checkout_id=?",
                                    checkout)
                            .isEmpty()
                    || !store.rows(
                                    "SELECT id FROM payment_provider_events WHERE order_reference=? AND result_code=0",
                                    order)
                            .isEmpty()) {
                cases.open(
                        "checkout:" + checkout + ":CANCEL_WITH_PAYMENT",
                        checkout,
                        eventId,
                        "CANCELLATION_WITH_PAYMENT");
                applied(eventId);
                return;
            }
            store.jdbc()
                    .update(
                            "UPDATE payment_checkouts SET status='CANCELLED',replacement_allowed=true WHERE id=? AND status IN ('CREATING','PENDING','UNKNOWN','EXPIRED','CANCELLED')",
                            checkout);
            store.jdbc()
                    .update(
                            "UPDATE payment_creation_tasks SET state='REJECTED',generation=generation+1,locked_until=NULL,updated_at=clock_timestamp() WHERE checkout_id=?",
                            checkout);
            store.jdbc()
                    .update(
                            "UPDATE payment_reconciliation_cases SET state='RESOLVED',resolution='PROVIDER_CANCELLED_UNPAID',resolved_at=clock_timestamp(),updated_at=clock_timestamp() WHERE checkout_id=? AND state<>'RESOLVED' AND reason IN ('PROVIDER_CANCELLED','CHECKOUT_EXPIRED','CREATE_UNKNOWN','CREATE_STUCK','LEGACY_UNCERTAIN')",
                            checkout);
            applied(eventId);
            return;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(event.get("amountText").toString());
        } catch (NumberFormatException ex) {
            review(eventId, checkout, "INVALID_AMOUNT");
            return;
        }
        if (code != 0
                || amount.signum() <= 0
                || amount.precision() > 18
                || amount.stripTrailingZeros().scale() > 0
                || reference.isBlank()) {
            review(eventId, checkout, "INVALID_RECEIPT");
            return;
        }
        var duplicate =
                store.rows(
                        "SELECT checkout_id,amount,disposition FROM payment_checkout_receipts WHERE environment=? AND provider_reference=?",
                        row.get("environment"),
                        reference);
        if (!duplicate.isEmpty()) {
            var old = duplicate.getFirst();
            if (!checkout.equals(FinanceStore.id(old, "checkoutId"))
                    || amount.compareTo(FinanceStore.money(old, "amount")) != 0)
                review(eventId, checkout, "INVOICE_RECEIPT_CONFLICT");
            else if ("REVIEW".equals(old.get("disposition")) && !"PAID".equals(row.get("status"))) review(eventId,checkout,amount.compareTo(FinanceStore.money(row,"amount"))<0?"UNDERPAID":amount.compareTo(FinanceStore.money(row,"amount"))>0?"OVERPAID":"LATE_OR_DUPLICATE_PAYMENT");
            else applied(eventId);
            return;
        }
        // transId identifies an invoice; a changed amount is not a second bank transfer.
        boolean accepted =
                amount.compareTo(FinanceStore.money(row, "amount")) == 0
                        && Set.of("CREATING", "PENDING", "UNKNOWN")
                                .contains(row.get("status").toString())
                        && Boolean.TRUE.equals(row.get("payableNow"));
        UUID receipt = UUID.randomUUID();
        store.jdbc().update(
                "INSERT INTO payment_checkout_receipts(id,checkout_id,environment,provider_reference,amount,disposition) VALUES (?,?,?,?,?,?)",
                receipt,
                checkout,
                row.get("environment"),
                reference,
                amount,
                accepted ? "ACCEPTED" : "REVIEW");
        boolean service =
                Set.of("SUBSCRIPTION", "JOB_ADDON").contains(row.get("purpose").toString());
        if (service || !accepted) {
            String prefix = "LIVE".equals(row.get("environment")) ? "live:" : "sandbox:";
            store.journal(
                    "checkout-receipt:"
                            + checkout
                            + ":"
                            + UUID.nameUUIDFromBytes(
                                    reference.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    null,
                    FinanceStore.id(row, "ownerId"),
                    "Pay2S verified invoice",
                    !"LIVE".equals(row.get("environment")),
                    FinanceStore.debit(prefix + "cash:platform-bank", amount),
                    FinanceStore.credit(
                            prefix + (accepted ? "service-pending:" : "receipt-review:") + checkout,
                            amount));
        }
        if (accepted) {
            store.jdbc().update(
                    "UPDATE payment_checkouts SET status='PAID',paid_at=clock_timestamp(),transaction_reference=?,fulfillment_status='PENDING',next_attempt_at=clock_timestamp() WHERE id=?",
                    reference,
                    checkout);
            cases.resolveTechnical(checkout);
            applied(eventId);
        } else {
            store.jdbc().update(
                    "UPDATE payment_checkouts c SET status='REVIEW' WHERE c.id=? AND c.status<>'PAID' AND NOT EXISTS(SELECT 1 FROM payment_checkouts other WHERE other.id<>c.id AND other.purpose=c.purpose AND other.object_id=c.object_id AND other.environment=c.environment AND other.status IN ('CREATING','PENDING','UNKNOWN','PAID','REVIEW'))",
                    checkout);
            review(
                    eventId,
                    checkout,
                    amount.compareTo(FinanceStore.money(row, "amount")) < 0
                            ? "UNDERPAID"
                            : amount.compareTo(FinanceStore.money(row, "amount")) > 0
                                    ? "OVERPAID"
                                    : "LATE_OR_DUPLICATE_PAYMENT");
        }
    }

    private void applied(UUID id) {
        store.jdbc().update(
                "UPDATE payment_provider_events SET state='APPLIED',processed_at=clock_timestamp(),error_code=NULL WHERE id=?",
                id);
    }

    private void review(UUID id, UUID checkout, String reason) {
        cases.open("event:" + id + ":" + reason, checkout, id, reason);
        store.jdbc().update(
                "UPDATE payment_provider_events SET state='REVIEW',processed_at=clock_timestamp(),error_code=? WHERE id=?",
                reason,
                id);
    }

    private static String clip(String value, int length) {
        return value.substring(0, Math.min(value.length(), length));
    }
}
