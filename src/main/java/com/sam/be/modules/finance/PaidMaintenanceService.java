package com.sam.be.modules.finance;

import static com.sam.be.modules.finance.FinanceStore.*;

import com.sam.be.common.security.util.SecurityUtils;
import java.math.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaidMaintenanceService {
    private final FinanceStore store;
    private final MaintenanceService maintenance;

    private Map<String, Object> ticket(UUID actor, UUID id, boolean lock) {
        var row =
                store.one(
                        "SELECT t.*,p.owner_id,p.client_id,p.contract_id,p.job_id FROM maintenance_tickets t JOIN finance_payables p ON p.id=t.payable_id WHERE t.id=? AND t.kind='PAID'",
                        id);
        if (!actor.equals(id(row, "ownerId"))
                && !actor.equals(id(row, "clientId"))
                && !SecurityUtils.isAdmin()) throw missing();
        if (lock) store.lockOwner(id(row, "ownerId"));
        return row;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> quotes(UUID actor, UUID ticket) {
        ticket(actor, ticket, false);
        return store.rows(
                "SELECT * FROM maintenance_quotes WHERE ticket_id=? ORDER BY created_at DESC,id DESC LIMIT 50",
                ticket);
    }

    @Transactional
    public Map<String, Object> quote(
            UUID actor, UUID ticket, BigDecimal amount, int days, String scope) {
        var row = ticket(actor, ticket, true);
        if (!actor.equals(id(row, "ownerId"))) throw forbidden();
        row = ticket(actor, ticket, false);
        if (row.get("closedAt") != null
                || !store.rows("SELECT id FROM paid_maintenance_orders WHERE ticket_id=?", ticket)
                        .isEmpty()) throw invalid("Yêu cầu đã kết thúc hoặc đã chấp thuận báo giá");
        if (amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 0
                || amount.compareTo(new BigDecimal("1000000000")) > 0
                || days < 1
                || days > 90) throw invalid("Báo giá không hợp lệ");
        store.jdbc().update(
                "UPDATE maintenance_quotes SET status='SUPERSEDED' WHERE ticket_id=? AND status='QUOTED'",
                ticket);
        UUID quote = UUID.randomUUID();
        store.jdbc().update(
                "INSERT INTO maintenance_quotes(id,ticket_id,amount,delivery_days,scope,created_at) VALUES (?,?,?,?,?,?)",
                quote,
                ticket,
                amount,
                days,
                scope,
                store.time(store.now()));
        store.audit(
                actor,
                id(row, "ownerId"),
                ticket,
                "MAINTENANCE_QUOTED",
                "Báo giá mới; chưa phát sinh thanh toán");
        maintenance.recordOrderEvent(
                actor,
                ticket,
                "QUOTE_CREATED",
                "Freelancer đã gửi báo giá bảo trì riêng: "
                        + amount.toPlainString()
                        + " VND, "
                        + days
                        + " ngày thực hiện.");
        return store.one("SELECT * FROM maintenance_quotes WHERE id=?", quote);
    }

    @Transactional
    public Map<String, Object> accept(UUID actor, UUID quote) {
        var q = store.one("SELECT * FROM maintenance_quotes WHERE id=?", quote);
        var t = ticket(actor, id(q, "ticketId"), true);
        if (!actor.equals(id(t, "clientId"))) throw forbidden();
        q = store.one("SELECT * FROM maintenance_quotes WHERE id=? FOR UPDATE", quote);
        var old = store.rows("SELECT * FROM paid_maintenance_orders WHERE quote_id=?", quote);
        if (!old.isEmpty()) return old.getFirst();
        if (!"QUOTED".equals(q.get("status")) || t.get("closedAt") != null)
            throw invalid("Báo giá không còn hiệu lực");
        UUID order = UUID.randomUUID();
        store.jdbc().update(
                "INSERT INTO paid_maintenance_orders(id,quote_id,ticket_id,owner_id,client_id,contract_id,job_id,amount,delivery_days,scope,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                order,
                quote,
                id(t, "id"),
                id(t, "ownerId"),
                actor,
                id(t, "contractId"),
                id(t, "jobId"),
                q.get("amount"),
                q.get("deliveryDays"),
                q.get("scope"),
                store.time(store.now()));
        store.jdbc().update("UPDATE maintenance_quotes SET status='ACCEPTED' WHERE id=?", quote);
        event(
                actor,
                order,
                "ACCEPTED_QUOTE",
                "Khách hàng chấp thuận phạm vi, giá và bảo trì 7 ngày cho đơn mới; chưa thanh toán");
        maintenance.recordOrderEvent(
                actor,
                id(t, "id"),
                "QUOTE_ACCEPTED",
                "Khách hàng chấp thuận báo giá; đơn bảo trì riêng đang chờ thanh toán.");
        return store.one("SELECT * FROM paid_maintenance_orders WHERE id=?", order);
    }

    private Map<String, Object> order(UUID actor, UUID order, boolean lock) {
        var row = store.one("SELECT * FROM paid_maintenance_orders WHERE id=?", order);
        if (!actor.equals(id(row, "ownerId"))
                && !actor.equals(id(row, "clientId"))
                && !SecurityUtils.isAdmin()) throw missing();
        if (lock) {
            store.lockOwner(id(row, "ownerId"));
            row = store.one("SELECT * FROM paid_maintenance_orders WHERE id=? FOR UPDATE", order);
        }
        return row;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> orders(UUID actor, UUID scope) {
        var p =
                store.one(
                        "SELECT p.owner_id,p.client_id,p.job_id FROM finance_payables p WHERE (p.job_id=? AND p.maintenance_order_id IS NULL) OR p.maintenance_order_id=?",
                        scope,
                        scope);
        if (!actor.equals(id(p, "ownerId"))
                && !actor.equals(id(p, "clientId"))
                && !SecurityUtils.isAdmin()) throw missing();
        return store.rows(
                "SELECT o.*,pay.status AS payment_status,pay.checkout_url,(SELECT e.content FROM paid_maintenance_order_events e WHERE e.order_id=o.id AND e.action='SUBMIT' ORDER BY e.created_at DESC,e.id DESC LIMIT 1) AS submitted_result FROM paid_maintenance_orders o LEFT JOIN payments pay ON pay.id=o.payment_id WHERE o.job_id=? ORDER BY o.created_at DESC,o.id DESC LIMIT 50",
                id(p, "jobId"));
    }

    @Transactional
    public Map<String, Object> action(UUID actor, UUID order, String action, String content) {
        var row = order(actor, order, true);
        String current = row.get("status").toString();
        if ("ACCEPT".equals(action) && "COMPLETED".equals(current)) return row;
        if ("COMPLETED".equals(current)) throw invalid("Đơn bảo trì đã nghiệm thu");
        boolean client = actor.equals(id(row, "clientId")),
                owner = actor.equals(id(row, "ownerId"));
        String next;
        switch (action) {
            case "START" -> {
                if (!owner || !"FUNDED".equals(current)) throw forbidden();
                next = "IN_PROGRESS";
            }
            case "SUBMIT" -> {
                if (!owner || !Set.of("FUNDED", "IN_PROGRESS").contains(current)) throw forbidden();
                next = "SUBMITTED";
            }
            case "REVISION" -> {
                if (!client || !"SUBMITTED".equals(current)) throw forbidden();
                next = "IN_PROGRESS";
            }
            case "ESCALATE" -> next = "DISPUTED";
            case "ADMIN_RESUME" -> {
                if (!SecurityUtils.isAdmin() || !"DISPUTED".equals(current)) throw forbidden();
                next = row.get("fundedAt") == null ? "AWAITING_PAYMENT" : "IN_PROGRESS";
            }
            case "ADMIN_COMPLETE" -> {
                if (!SecurityUtils.isAdmin() || !Set.of("DISPUTED", "SUBMITTED").contains(current))
                    throw forbidden();
                next = "COMPLETED";
            }
            case "ACCEPT" -> {
                if (!client || !"SUBMITTED".equals(current)) throw forbidden();
                next = "COMPLETED";
            }
            default -> throw invalid("Thao tác không hợp lệ");
        }
        if ("COMPLETED".equals(next)) complete(actor, row);
        store.jdbc().update(
                "UPDATE paid_maintenance_orders SET status=?,submitted_at=CASE WHEN ?='SUBMITTED' THEN ? ELSE submitted_at END,completed_at=CASE WHEN ?='COMPLETED' THEN ? ELSE completed_at END WHERE id=?",
                next,
                next,
                store.time(store.now()),
                next,
                store.time(store.now()),
                order);
        event(actor, order, action, content);
        maintenance.recordOrderEvent(
                actor,
                id(row, "ticketId"),
                "ORDER_" + action,
                "Đơn bảo trì " + order + ": " + content);
        return store.one("SELECT * FROM paid_maintenance_orders WHERE id=?", order);
    }

    private void complete(UUID actor, Map<String, Object> order) {
        UUID orderId = id(order, "id"),
                owner = id(order, "ownerId"),
                contract = id(order, "contractId");
        var payment =
                store.one(
                        "SELECT * FROM payments WHERE id=? AND status='HELD_IN_ESCROW' AND verified_at IS NOT NULL",
                        id(order, "paymentId"));
        boolean sandbox = !"LIVE".equals(payment.get("environment"));
        String prefix = sandbox ? "sandbox:" : "live:";
        BigDecimal gross = money(order, "amount"),
                fee = gross.multiply(new BigDecimal("0.10")).setScale(0, RoundingMode.HALF_UP),
                net = gross.subtract(fee);
        if (net.signum() <= 0) throw invalid("Giá trị đơn không đủ để quyết toán");
        var accepted = store.now();
        store.jdbc().update(
                "INSERT INTO finance_payables(id,contract_id,job_id,owner_id,client_id,amount,earnings,deposit_refund,sandbox,accepted_at,available_at,maintenance_order_id) VALUES (?,?,?,?,?,?,?,0,?,?,?,?)",
                UUID.randomUUID(),
                contract,
                id(order, "jobId"),
                owner,
                id(order, "clientId"),
                net,
                net,
                sandbox,
                store.time(accepted),
                store.time(accepted.plus(FinancePolicy.WARRANTY)),
                orderId);
        store.journal(
                "maintenance-completed:" + orderId,
                contract,
                owner,
                "Quyết toán đơn bảo trì có phí; chờ bảo trì 7 ngày",
                sandbox,
                debit(prefix + "escrow:" + id(order, "paymentId"), gross),
                credit(prefix + "payable:" + owner, net),
                credit(prefix + "platform-revenue", fee));
        store.jdbc().update(
                "UPDATE payments SET status='SETTLED',released_at=? WHERE id=?",
                java.time.LocalDateTime.ofInstant(accepted, java.time.ZoneOffset.UTC),
                id(order, "paymentId"));
        store.jdbc().update(
                "UPDATE maintenance_tickets SET status='RESOLVED',closed_at=? WHERE id=?",
                store.time(accepted),
                id(order, "ticketId"));
        store.audit(
                actor,
                owner,
                orderId,
                "PAID_MAINTENANCE_SETTLED",
                "Công nợ riêng; không thay đổi quyết toán dự án gốc");
    }

    private void event(UUID actor, UUID order, String action, String content) {
        store.jdbc().update(
                "INSERT INTO paid_maintenance_order_events VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),
                order,
                actor,
                action,
                content,
                store.time(store.now()));
    }
}
