package com.sam.be.modules.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sam.be.modules.finance.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class Pay2sIpnService {
    private final Pay2sClient pay2s;
    private final FinanceStore store;
    private final PaymentService legacyPay2sPayments;

    @Transactional
    public Map<String, Boolean> accept(JsonNode payload) {
        if (!pay2s.verify(payload))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Pay2S signature");
        String order = payload.path("orderId").asText();
        UUID id;
        try {
            if (!order.matches("[0-9a-f]{32}")) throw new IllegalArgumentException();
            id =
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
            throw FinanceStore.missing();
        }
        var rows = store.rows("SELECT * FROM payment_checkouts WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) return legacyPay2sPayments.handlePay2sIpn(payload);
        var row = rows.getFirst();
        if (!pay2s.environment().equals(row.get("environment")))
            throw FinanceStore.invalid("Payment environment mismatch");
        int code = payload.path("resultCode").asInt(-1);
        if (code == 2) {
            store.db.update(
                    "UPDATE payment_checkouts SET status='CANCELLED' WHERE id=? AND status IN ('CREATING','PENDING','UNKNOWN','EXPIRED')",
                    id);
            return Map.of("success", true);
        }
        if (code != 0) throw FinanceStore.invalid("Unsupported payment result");
        BigDecimal amount;
        try {
            amount = new BigDecimal(payload.path("amount").asText());
        } catch (NumberFormatException ex) {
            throw FinanceStore.invalid("Invalid payment amount");
        }
        String transaction = payload.path("transId").asText();
        if (amount.signum() <= 0
                || amount.precision() > 18
                || amount.scale() > 2
                || transaction.isBlank()
                || transaction.length() > 150)
            throw FinanceStore.invalid("Invalid payment receipt");
        var duplicate =
                store.rows(
                        "SELECT checkout_id FROM payment_checkout_receipts WHERE environment=? AND provider_reference=?",
                        row.get("environment"),
                        transaction);
        if (!duplicate.isEmpty()) {
            if (!id.equals(FinanceStore.id(duplicate.getFirst(), "checkoutId")))
                throw FinanceStore.invalid("Provider transaction belongs to another order");
            return Map.of("success", true);
        }
        boolean accepted =
                amount.compareTo(FinanceStore.money(row, "amount")) == 0
                        && Set.of("CREATING", "PENDING", "UNKNOWN")
                                .contains(row.get("status").toString())
                        && FinanceStore.instant(row, "expiresAt").isAfter(store.now());
        // A global per-environment uniqueness constraint also prevents races across different
        // orders.
        store.db.update(
                "INSERT INTO payment_checkout_receipts(id,checkout_id,environment,provider_reference,amount,disposition) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),
                id,
                row.get("environment"),
                transaction,
                amount,
                accepted ? "ACCEPTED" : "REVIEW");
        boolean serviceFee =
                Set.of("SUBSCRIPTION", "JOB_ADDON").contains(row.get("purpose").toString());
        if (serviceFee || !accepted) {
            String prefix = "LIVE".equals(row.get("environment")) ? "live:" : "sandbox:";
            store.journal(
                    "checkout-receipt:"
                            + id
                            + ":"
                            + UUID.nameUUIDFromBytes(
                                    transaction.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    null,
                    FinanceStore.id(row, "ownerId"),
                    "Pay2S checkout receipt",
                    !"LIVE".equals(row.get("environment")),
                    FinanceStore.debit(prefix + "cash:platform-bank", amount),
                    FinanceStore.credit(
                            prefix + (accepted ? "service-pending:" : "receipt-review:") + id,
                            amount));
        }
        if (accepted)
            store.db.update(
                    "UPDATE payment_checkouts SET status='PAID',paid_at=?,transaction_reference=?,fulfillment_status='PENDING',next_attempt_at=? WHERE id=?",
                    store.time(store.now()),
                    transaction,
                    store.time(store.now()),
                    id);
        else
            store.db.update(
                    "UPDATE payment_checkouts SET status='REVIEW' WHERE id=? AND status<>'PAID'",
                    id);
        return Map.of("success", true);
    }
}
