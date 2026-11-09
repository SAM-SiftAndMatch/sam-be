package com.sam.be.modules.payment.service;

import com.sam.be.modules.finance.FinanceStore;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class PaymentReconciliationService {
    private final FinanceStore store;

    public void open(String key, UUID checkout, UUID event, String reason) {
        UUID id = UUID.randomUUID();
        int added =
                store.jdbc().update(
                        "INSERT INTO payment_reconciliation_cases(id,case_key,checkout_id,event_id,reason) VALUES (?,?,?,?,?) ON CONFLICT(case_key) DO NOTHING",
                        id,
                        key,
                        checkout,
                        event,
                        reason);
        if (added == 1) {
            log.warn("PAYMENT_RECONCILIATION_OPENED case={} reason={}", id, reason);
            store.jdbc().update(
                    "INSERT INTO payment_reconciliation_events(id,case_id,action) VALUES (?,?,'OPENED')",
                    UUID.randomUUID(),
                    id);
        }
    }

    public void resolveTechnical(UUID checkout) {
        store.jdbc().update(
                "UPDATE payment_reconciliation_cases SET state='RESOLVED',resolution='PROVIDER_CONFIRMED',resolved_at=clock_timestamp(),updated_at=clock_timestamp() WHERE checkout_id=? AND state<>'RESOLVED' AND reason IN ('CREATE_UNKNOWN','CREATE_STUCK','LEGACY_UNCERTAIN') AND EXISTS(SELECT 1 FROM payment_checkouts WHERE id=? AND status IN ('PENDING','PAID'))",
                checkout,
                checkout);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> list(int page) {
        return store.rows(
                "SELECT r.*,c.support_code,c.amount,c.status AS payment_status,c.fulfillment_status,c.environment FROM payment_reconciliation_cases r LEFT JOIN payment_checkouts c ON c.id=r.checkout_id ORDER BY (r.state='RESOLVED'),r.due_at,r.id LIMIT 20 OFFSET ?",
                page * 20L);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> history(UUID id) {
        return store.rows(
                "SELECT actor_id,action,evidence_reference,created_at FROM payment_reconciliation_events WHERE case_id=? ORDER BY created_at,id",
                id);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void action(UUID actor, UUID id, String action, String evidence) {
        var initial =
                store.one("SELECT checkout_id FROM payment_reconciliation_cases WHERE id=?", id);
        UUID checkout =
                initial.get("checkoutId") == null ? null : FinanceStore.id(initial, "checkoutId");
        // All monetary actions use checkout -> case -> event/task lock ordering.
        if (checkout != null)
            store.one("SELECT id FROM payment_checkouts WHERE id=? FOR UPDATE", checkout);
        var row = store.one("SELECT * FROM payment_reconciliation_cases WHERE id=? FOR UPDATE", id);
        if ("RESOLVED".equals(row.get("state"))) throw FinanceStore.invalid("Hồ sơ đã kết thúc");
        if (!Set.of(
                        "CLAIM",
                        "NOTE",
                        "PROPOSE_PROVIDER_CLOSED",
                        "APPROVE_PROVIDER_CLOSED",
                        "PROPOSE_DEMO_MISMATCH",
                        "APPROVE_DEMO_MISMATCH",
                        "RETRY_EVENT",
                        "PROPOSE_LATE_ALLOCATION",
                        "APPROVE_LATE_ALLOCATION")
                .contains(action)) throw FinanceStore.invalid("Thao tác không hợp lệ");
        if (!"CLAIM".equals(action)
                && (evidence == null || evidence.isBlank() || evidence.length() > 500))
            throw FinanceStore.invalid("Cần mã bằng chứng hoặc ghi chú tối đa 500 ký tự");
        if ("CLAIM".equals(action) && row.get("assigneeId") != null && !actor.equals(FinanceStore.id(row,"assigneeId"))) throw FinanceStore.invalid("Hồ sơ đã được admin khác nhận xử lý");
        if ("CLAIM".equals(action))
            store.jdbc().update(
                    "UPDATE payment_reconciliation_cases SET assignee_id=?,state='INVESTIGATING',updated_at=clock_timestamp() WHERE id=? AND (assignee_id IS NULL OR assignee_id=?)",
                    actor,
                    id,
                    actor);
        boolean demoMismatch = Set.of("PROPOSE_DEMO_MISMATCH", "APPROVE_DEMO_MISMATCH").contains(action);
        if (demoMismatch) {
            if (checkout == null) throw FinanceStore.invalid("Hồ sơ không có checkout");
            var candidate = store.one("SELECT * FROM payment_checkouts WHERE id=?", checkout);
            if (!"LIVE".equals(candidate.get("environment"))
                    || !Set.of("EXPIRED", "UNKNOWN", "CANCELLED").contains(candidate.get("status")))
                throw FinanceStore.invalid("Chỉ xử lý đơn thử nghiệm bị ghi LIVE đã hết hạn hoặc chưa rõ kết quả; đơn đang hoạt động/đã nhận tiền cần quy trình khác");
        }
        if ("APPROVE_PROVIDER_CLOSED".equals(action) || "APPROVE_DEMO_MISMATCH".equals(action)) {
            if (checkout == null) throw FinanceStore.invalid("Hồ sơ không có checkout");
            var proposal =
                    store.rows(
                            "SELECT actor_id FROM payment_reconciliation_events WHERE case_id=? AND action=? ORDER BY created_at DESC LIMIT 1",
                            id,
                            demoMismatch ? "PROPOSE_DEMO_MISMATCH" : "PROPOSE_PROVIDER_CLOSED");
            if (proposal.isEmpty() || actor.equals(FinanceStore.id(proposal.getFirst(), "actorId")))
                throw FinanceStore.invalid("Cần đề nghị từ một admin khác");
            var c = store.one("SELECT * FROM payment_checkouts WHERE id=?", checkout);
            if (demoMismatch && !store.rows(
                    "SELECT id FROM payment_provider_events WHERE order_reference=? AND result_code=0",
                    checkout.toString().replace("-", "")).isEmpty())
                throw FinanceStore.invalid("Đã có thông báo nhận tiền; phải đối soát khoản tiền trước khi đóng đơn thử nghiệm");
            if (!store.rows("SELECT id FROM payment_provider_events WHERE environment=? AND order_reference=? AND result_code=0",c.get("environment"),checkout.toString().replace("-","")).isEmpty()) throw FinanceStore.invalid("Đã có thông báo nhận tiền, cần đối soát trước");
            if ("PAID".equals(c.get("status"))
                    || "REVIEW".equals(c.get("status"))
                    || !store.rows(
                                    "SELECT id FROM payment_checkout_receipts WHERE checkout_id=?",
                                    checkout)
                            .isEmpty())
                throw FinanceStore.invalid(
                        "Đơn có tiền hoặc cần đối soát tiền, không được xác nhận chưa thu");
            if (!store.rows(
                            "SELECT checkout_id FROM payment_creation_tasks WHERE checkout_id=? AND state='LEASED' AND locked_until>clock_timestamp()",
                            checkout)
                    .isEmpty()) throw FinanceStore.invalid("Tác vụ vẫn đang chạy");
            store.jdbc().update(
                    "UPDATE payment_creation_tasks SET state='REJECTED',generation=generation+1,locked_until=NULL,updated_at=clock_timestamp() WHERE checkout_id=?",
                    checkout);
            store.jdbc().update(
                    "UPDATE payment_checkouts SET status='CANCELLED',replacement_allowed=true WHERE id=?",
                    checkout);
            store.jdbc().update(
                    "UPDATE payment_reconciliation_cases SET state='RESOLVED',resolution=?,resolved_at=clock_timestamp(),updated_at=clock_timestamp() WHERE id=?",
                    demoMismatch ? "DEMO_MISMATCH_NO_PAYMENT" : "PROVIDER_CLOSED_NO_PAYMENT",
                    id);
        }
        if ("APPROVE_LATE_ALLOCATION".equals(action)) {
            if (checkout == null) throw FinanceStore.invalid("Hồ sơ không có checkout");
            var proposal =
                    store.rows(
                            "SELECT actor_id FROM payment_reconciliation_events WHERE case_id=? AND action='PROPOSE_LATE_ALLOCATION' ORDER BY created_at DESC LIMIT 1",
                            id);
            if (proposal.isEmpty() || actor.equals(FinanceStore.id(proposal.getFirst(), "actorId")))
                throw FinanceStore.invalid("Cần đề nghị phân bổ từ admin khác");
            var c = store.one("SELECT * FROM payment_checkouts WHERE id=?", checkout);
            if (!Set.of("REVIEW", "EXPIRED", "CANCELLED").contains(c.get("status").toString()))
                throw FinanceStore.invalid("Đơn không đủ điều kiện phân bổ muộn");
            var receipts =
                    store.rows(
                            "SELECT * FROM payment_checkout_receipts WHERE checkout_id=?",
                            checkout);
            if (receipts.size() != 1
                    || FinanceStore.money(receipts.getFirst(), "amount")
                                    .compareTo(FinanceStore.money(c, "amount"))
                            != 0)
                throw FinanceStore.invalid(
                        "Chỉ hỗ trợ phân bổ một khoản đã xác minh đúng giá; thiếu/thừa cần quy trình tài chính riêng");
            if (!store.rows(
                            "SELECT id FROM payment_checkouts WHERE purpose=? AND object_id=? AND environment=? AND id<>? AND status IN ('CREATING','PENDING','UNKNOWN','PAID','REVIEW')",
                            c.get("purpose"),
                            c.get("objectId"),
                            c.get("environment"),
                            checkout)
                    .isEmpty()) throw FinanceStore.invalid("Có đơn thay thế, cần đối soát trước");
            var r = receipts.getFirst();
            if (!"REVIEW".equals(r.get("disposition")))
                throw FinanceStore.invalid("Khoản tiền không ở trạng thái chờ phân bổ");
            String prefix = "LIVE".equals(c.get("environment")) ? "live:" : "sandbox:";
            boolean service =
                    Set.of("SUBSCRIPTION", "JOB_ADDON").contains(c.get("purpose").toString());
            var amount = FinanceStore.money(c, "amount");
            store.journal(
                    "checkout-reviewed-allocation:" + checkout,
                    null,
                    FinanceStore.id(c, "ownerId"),
                    "Approved late payment allocation",
                    !"LIVE".equals(c.get("environment")),
                    FinanceStore.debit(prefix + "receipt-review:" + checkout, amount),
                    FinanceStore.credit(
                            prefix
                                    + (service
                                            ? "service-pending:" + checkout
                                            : "cash:platform-bank"),
                            amount));
            store.jdbc().update(
                    "UPDATE payment_checkouts SET status='PAID',paid_at=COALESCE(paid_at,clock_timestamp()),transaction_reference=?,fulfillment_status='PENDING',next_attempt_at=clock_timestamp() WHERE id=?",
                    r.get("providerReference"),
                    checkout);
            store.jdbc().update(
                    "UPDATE payment_reconciliation_cases SET state='RESOLVED',resolution='LATE_ALLOCATION_APPROVED',resolved_at=clock_timestamp(),updated_at=clock_timestamp() WHERE id=?",
                    id);
        }
        if ("RETRY_EVENT".equals(action)) {
            if (row.get("eventId") == null)
                throw FinanceStore.invalid("Hồ sơ không có thông báo để kiểm tra lại");
            store.jdbc().update(
                    "UPDATE payment_provider_events SET state='PENDING',next_attempt_at=clock_timestamp(),error_code=NULL WHERE id=? AND state='REVIEW'",
                    FinanceStore.id(row, "eventId"));
        }
        store.jdbc().update(
                "INSERT INTO payment_reconciliation_events(id,case_id,actor_id,action,evidence_reference) VALUES (?,?,?,?,?)",
                UUID.randomUUID(),
                id,
                actor,
                action,
                evidence);
    }
}
