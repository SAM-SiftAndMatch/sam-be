package com.sam.be.modules.payment.service;

import com.sam.be.common.exception.PaymentGatewayException;
import com.sam.be.modules.finance.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckoutService {
    private final FinanceStore store;
    private final Pay2sClient pay2s;
    private final PaymentReturnPolicy returns;

    public record Checkout(
            UUID orderId,
            String purpose,
            UUID objectId,
            BigDecimal amount,
            String currency,
            String provider,
            String environment,
            String paymentStatus,
            String fulfillmentStatus,
            String checkoutUrl,
            String transferContent,
            String expiresAt,
            String createdAt,
            String paidAt) {}

    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public Checkout create(
            String purpose, UUID objectId, UUID owner, BigDecimal amount, String returnUrl) {
        // Serialize requests for the same business order without locking another user's account.
        store.db.queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", purpose + ":" + objectId);
        String target = returns.resolve(returnUrl);
        var previous =
                store.rows(
                        "SELECT * FROM payment_checkouts WHERE purpose=? AND object_id=? ORDER BY created_at DESC, id DESC LIMIT 1",
                        purpose,
                        objectId);
        if (!previous.isEmpty()) {
            var old = previous.getFirst();
            if (!owner.equals(FinanceStore.id(old, "ownerId"))
                    || !pay2s.environment().equals(old.get("environment")))
                throw FinanceStore.invalid(
                        "Đơn thanh toán thuộc môi trường khác; tạo đơn nghiệp vụ mới");
            String state = old.get("status").toString();
            if (Set.of("PAID", "REVIEW", "UNKNOWN", "CREATING").contains(state)) {
                if (Set.of("UNKNOWN", "CREATING").contains(state))
                    throw FinanceStore.invalid(
                            "Đang kiểm tra lần tạo thanh toán trước. Không tạo thêm đơn để tránh trả tiền lặp.");
                return view(old);
            }
            if ("PENDING".equals(state)
                    && FinanceStore.instant(old, "expiresAt").isAfter(store.now()))
                return view(old);
            store.db.update(
                    "UPDATE payment_checkouts SET status='EXPIRED' WHERE id=? AND status='PENDING'",
                    FinanceStore.id(old, "id"));
            amount = FinanceStore.money(old, "amount"); // price snapshot survives retries
        }
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0)
            throw FinanceStore.invalid("Giá thanh toán phải là số nguyên VND dương");
        UUID id = UUID.randomUUID();
        String order = id.toString().replace("-", "");
        String memo = Pay2sClient.transferMemo(order, purpose);
        store.db.update(
                "INSERT INTO payment_checkouts(id,purpose,object_id,owner_id,amount,environment,status,transfer_content,expires_at) VALUES (?,?,?,?,?,?,'CREATING',?,?)",
                id,
                purpose,
                objectId,
                owner,
                amount,
                pay2s.environment(),
                memo,
                store.time(store.now().plus(Duration.ofMinutes(15))));
        try {
            var result = pay2s.create(order, amount.longValueExact(), target, purpose);
            store.db.update(
                    "UPDATE payment_checkouts SET checkout_url=?,status='PENDING' WHERE id=?",
                    result.url(),
                    id);
        } catch (PaymentGatewayException ex) {
            // A timed-out create may already exist at the provider. Preserve the reference for
            // IPN/review.
            String http = ex.diagnostics().get("upstreamHttpStatus");
            boolean rejected =
                    http != null && http.matches("4[0-9]{2}") && !http.equals("408")
                            || Set.of("IPN_URL_INVALID", "REDIRECT_URL_INVALID")
                                    .contains(ex.diagnostics().get("reason"));
            store.db.update(
                    "UPDATE payment_checkouts SET status=? WHERE id=?",
                    rejected ? "CANCELLED" : "UNKNOWN",
                    id);
            throw ex;
        }
        return view(load(id));
    }

    public Map<String, Object> load(UUID id) {
        return store.one("SELECT * FROM payment_checkouts WHERE id=?", id);
    }

    public Checkout get(UUID actor, UUID id) {
        var row = load(id);
        if (!actor.equals(FinanceStore.id(row, "ownerId"))) throw FinanceStore.forbidden();
        return view(row);
    }

    public List<Checkout> history(UUID actor, int page) {
        return store
                .rows(
                        "SELECT * FROM payment_checkouts WHERE owner_id=? ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",
                        actor,
                        Math.multiplyExact(page, 20))
                .stream()
                .map(this::view)
                .toList();
    }

    public List<Map<String, Object>> review(int page) {
        return store.rows(
                "SELECT c.id,c.purpose,c.owner_id,c.amount,c.environment,c.status,c.fulfillment_status,c.attempt_count,c.created_at,t.status AS recommendation_status,r.provider_reference,r.amount AS received_amount,r.disposition FROM payment_checkouts c LEFT JOIN job_addon_orders o ON o.id=c.object_id AND c.purpose='JOB_ADDON' LEFT JOIN job_addon_tasks t ON t.job_id=o.job_id LEFT JOIN payment_checkout_receipts r ON r.checkout_id=c.id WHERE c.status IN ('UNKNOWN','REVIEW') OR c.fulfillment_status IN ('RETRY','REVIEW') OR r.disposition='REVIEW' OR t.status='RETRY' ORDER BY c.created_at DESC,c.id LIMIT 20 OFFSET ?",
                Math.multiplyExact(page, 20));
    }

    public Checkout latest(String purpose, UUID object) {
        var rows =
                store.rows(
                        "SELECT * FROM payment_checkouts WHERE purpose=? AND object_id=? ORDER BY created_at DESC,id DESC LIMIT 1",
                        purpose,
                        object);
        return rows.isEmpty() ? null : view(rows.getFirst());
    }

    private Checkout view(Map<String, Object> row) {
        String status = row.get("status").toString();
        if ("PENDING".equals(status)
                && !FinanceStore.instant(row, "expiresAt").isAfter(store.now())) status = "EXPIRED";
        return new Checkout(
                FinanceStore.id(row, "id"),
                row.get("purpose").toString(),
                FinanceStore.id(row, "objectId"),
                FinanceStore.money(row, "amount"),
                "VND",
                "PAY2S",
                row.get("environment").toString(),
                status,
                row.get("fulfillmentStatus").toString(),
                "PENDING".equals(status) ? (String) row.get("checkoutUrl") : null,
                row.get("transferContent").toString(),
                row.get("expiresAt").toString(),
                row.get("createdAt") == null ? null : row.get("createdAt").toString(),
                row.get("paidAt") == null ? null : row.get("paidAt").toString());
    }
}
