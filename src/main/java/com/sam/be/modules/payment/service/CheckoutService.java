package com.sam.be.modules.payment.service;

import com.sam.be.modules.finance.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
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
            String paidAt,
            String supportCode,
            boolean reviewRequired) {}

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Checkout create(
            String purpose, UUID objectId, UUID owner, BigDecimal amount, String returnUrl) {
        store.jdbc().queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", purpose + ":" + objectId);
        String target = returns.resolve(returnUrl);
        var previous =
                store.rows(
                        "SELECT * FROM payment_checkouts WHERE purpose=? AND object_id=? AND environment=? ORDER BY created_at DESC,id DESC LIMIT 1 FOR UPDATE",
                        purpose,
                        objectId,
                        pay2s.environment());
        if (!previous.isEmpty()) {
            var old = previous.getFirst();
            if (!owner.equals(FinanceStore.id(old, "ownerId"))
                    || !pay2s.environment().equals(old.get("environment")))
                throw FinanceStore.invalid("Đơn thanh toán thuộc người dùng hoặc môi trường khác");
            // An expired link is not proof that the provider order is closed. Never silently
            // replace it.
            if (!("CANCELLED".equals(old.get("status"))
                    && Boolean.TRUE.equals(old.get("replacementAllowed")))) {
                bind(owner, purpose, FinanceStore.id(old, "id"));
                var checkout = view(old);
                logReusedCheckout(checkout);
                return checkout;
            }
            if (!store.rows("SELECT id FROM payment_checkout_receipts WHERE checkout_id=?",
                    FinanceStore.id(old, "id")).isEmpty()
                    || !store.rows("SELECT id FROM payment_provider_events WHERE order_reference=? AND result_code=0",
                            old.get("id").toString().replace("-", "")).isEmpty())
                throw FinanceStore.invalid("Đơn cũ có thông tin nhận tiền; cần đối soát trước khi tạo lại");
            amount = FinanceStore.money(old, "amount");
        }
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0)
            throw FinanceStore.invalid("Giá thanh toán phải là số nguyên VND dương");
        if (!pay2s.enabled()) throw FinanceStore.invalid("Thanh toán Pay2S chưa được cấu hình");
        UUID id = UUID.randomUUID();
        String memo = Pay2sClient.transferMemo(id.toString().replace("-", ""), purpose);
        store.jdbc().update(
                "INSERT INTO payment_checkouts(id,purpose,object_id,owner_id,amount,environment,status,transfer_content,expires_at,support_code,merchant_scope) VALUES (?,?,?,?,?,?,'CREATING',?,clock_timestamp()+INTERVAL '15 minutes',?,?)",
                id,
                purpose,
                objectId,
                owner,
                amount,
                pay2s.environment(),
                memo,
                memo,
                pay2s.merchantScope());
        store.jdbc().update(
                "INSERT INTO payment_creation_tasks(checkout_id,return_url,state) VALUES (?,?,'READY')",
                id,
                target);
        bind(owner, purpose, id);
        // Both business order and task commit in the caller's transaction. HTTP is worker-only.
        return view(load(id));
    }

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Checkout replay(UUID owner, String purpose, String businessPayload) {
        var context = PaymentRequestKey.prepare(owner, purpose, businessPayload);
        if (context == null) return null;
        store.jdbc().queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                owner + ":" + pay2s.environment() + ":" + purpose + ":" + context.keyHash());
        var rows =
                store.rows(
                        "SELECT checkout_id,payload_hash FROM payment_idempotency_keys WHERE owner_id=? AND environment=? AND purpose=? AND key_hash=?",
                        owner,
                        pay2s.environment(),
                        purpose,
                        context.keyHash());
        if (rows.isEmpty()) return null;
        if (!context.payloadHash().equals(rows.getFirst().get("payloadHash")))
            throw FinanceStore.invalid("Idempotency-Key đã dùng cho nội dung mua khác");
        var row = load(FinanceStore.id(rows.getFirst(), "checkoutId"));
        if (!owner.equals(FinanceStore.id(row, "ownerId"))) throw FinanceStore.forbidden();
        if ("CANCELLED".equals(row.get("status"))
                && Boolean.TRUE.equals(row.get("replacementAllowed"))) {
            // Keep the original idempotency evidence immutable. Follow attempts only within
            // the same business purchase after the original checkout was safely closed.
            var attempts = store.rows(
                    "SELECT * FROM payment_checkouts WHERE purpose=? AND object_id=? AND environment=? ORDER BY created_at DESC,id DESC LIMIT 1",
                    purpose, FinanceStore.id(row, "objectId"), pay2s.environment());
            if (attempts.isEmpty()) throw FinanceStore.invalid("Không tìm được lịch sử đơn thanh toán");
            row = attempts.getFirst();
            if (!owner.equals(FinanceStore.id(row, "ownerId"))) throw FinanceStore.forbidden();
            if ("CANCELLED".equals(row.get("status"))
                    && Boolean.TRUE.equals(row.get("replacementAllowed"))) return null;
        }
        var checkout = view(row);
        logReusedCheckout(checkout);
        return checkout;
    }

    private void logReusedCheckout(Checkout checkout) {
        log.info(
                "PAY2S_CHECKOUT_REUSED orderId={} environment={} status={} createdAt={}",
                checkout.orderId(),
                checkout.environment(),
                checkout.paymentStatus(),
                checkout.createdAt());
    }

    private void bind(UUID owner, String purpose, UUID checkout) {
        var c = PaymentRequestKey.current(owner, purpose);
        if (c != null) {
            int bound = store.jdbc().update(
                    "INSERT INTO payment_idempotency_keys(owner_id,environment,purpose,key_hash,payload_hash,checkout_id) VALUES (?,?,?,?,?,?) ON CONFLICT(owner_id,environment,purpose,key_hash) DO NOTHING",
                    owner,
                    pay2s.environment(),
                    purpose,
                    c.keyHash(),
                    c.payloadHash(),
                    checkout);
            if (bound != 1 && store.rows(
                    "SELECT k.checkout_id FROM payment_idempotency_keys k JOIN payment_checkouts original ON original.id=k.checkout_id JOIN payment_checkouts target ON target.id=? WHERE k.owner_id=? AND k.environment=? AND k.purpose=? AND k.key_hash=? AND k.payload_hash=? AND original.owner_id=target.owner_id AND original.environment=target.environment AND original.purpose=target.purpose AND original.object_id=target.object_id AND original.amount=target.amount AND (original.id=target.id OR (original.status='CANCELLED' AND original.replacement_allowed=true))",
                    checkout, owner, pay2s.environment(), purpose, c.keyHash(), c.payloadHash()).isEmpty())
                throw FinanceStore.invalid("Idempotency-Key chưa được phép theo đơn thay thế");
        }
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
                        "SELECT * FROM payment_checkouts WHERE purpose=? AND object_id=? AND environment=? ORDER BY created_at DESC,id DESC LIMIT 1",
                        purpose,
                        object,
                        pay2s.environment());
        return rows.isEmpty() ? null : view(rows.getFirst());
    }

    public Instant databaseNow() {
        return store.jdbc()
                .queryForObject("SELECT clock_timestamp()", java.time.OffsetDateTime.class)
                .toInstant();
    }

    private Checkout view(Map<String, Object> row) {
        String status = row.get("status").toString();
        if ("PENDING".equals(status)
                && !FinanceStore.instant(row, "expiresAt").isAfter(databaseNow()))
            status = "EXPIRED";
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
                Objects.toString(
                        row.get("providerTransferContent"),
                        Objects.toString(row.get("transferContent"), "")),
                row.get("expiresAt").toString(),
                row.get("createdAt") == null ? null : row.get("createdAt").toString(),
                row.get("paidAt") == null ? null : row.get("paidAt").toString(),
                Objects.toString(
                        row.get("supportCode"),
                        "SAM" + row.get("id").toString().replace("-", "").toUpperCase(Locale.ROOT)),
                Set.of("UNKNOWN", "REVIEW", "EXPIRED").contains(status)
                        || "CANCELLED".equals(status) && !Boolean.TRUE.equals(row.get("replacementAllowed"))
                        || "REVIEW".equals(row.get("fulfillmentStatus")));
    }
}
