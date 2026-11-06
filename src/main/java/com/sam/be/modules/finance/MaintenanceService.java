package com.sam.be.modules.finance;

import static com.sam.be.modules.finance.FinanceStore.*;

import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.chat.dto.ChatMessageDto;
import com.sam.be.modules.chat.repository.ChatRoomRepository;
import com.sam.be.modules.chat.service.ChatService;
import com.sam.be.modules.notification.dto.NotificationMessage;
import com.sam.be.modules.notification.service.UserNotificationService;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class MaintenanceService {
    private final FinanceStore store;
    private final ChatRoomRepository rooms;
    private final ChatService chat;
    private final SimpMessagingTemplate messaging;
    private final UserNotificationService notifications;

    private Map<String, Object> payable(UUID actor, UUID job, boolean lock) {
        var p =
                store.one(
                        "SELECT p.*, "
                                + FinanceStore.BLOCKED
                                + " AS blocked FROM finance_payables p WHERE ((p.job_id=? AND p.maintenance_order_id IS NULL) OR p.maintenance_order_id=?)",
                        job,
                        job);
        if (!actor.equals(id(p, "ownerId"))
                && !actor.equals(id(p, "clientId"))
                && !SecurityUtils.isAdmin()) throw missing();
        if (lock) {
            store.lockOwner(id(p, "ownerId"));
            p =
                    store.one(
                            "SELECT p.*, "
                                    + FinanceStore.BLOCKED
                                    + " AS blocked FROM finance_payables p WHERE ((p.job_id=? AND p.maintenance_order_id IS NULL) OR p.maintenance_order_id=?) FOR UPDATE",
                            job,
                            job);
        }
        return p;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(UUID actor, UUID job) {
        var p = payable(actor, job, false);
        boolean active = FinancePolicy.canOpen(instant(p, "acceptedAt"), store.now());
        long count =
                store.db.queryForObject(
                        "SELECT COUNT(*) FROM maintenance_tickets WHERE payable_id=? AND kind='WARRANTY' AND closed_at IS NULL",
                        Long.class,
                        id(p, "id"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("acceptedAt", p.get("acceptedAt"));
        result.put("warrantyEndsAt", p.get("availableAt"));
        result.put(
                "maintenanceStatus",
                active ? "ACTIVE" : count > 0 ? "EXPIRED_WITH_OPEN_ISSUES" : "CLOSED");
        result.put("openIssueCount", count);
        result.put("canCreateWarrantyTicket", active && actor.equals(id(p, "clientId")));
        result.put("canRequestPaidMaintenance", !active && actor.equals(id(p, "clientId")));
        result.put("payoutBlocked", active || count > 0);
        result.put("sandbox", p.get("sandbox"));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> tickets(UUID actor, UUID job, int page) {
        var p = payable(actor, job, false);
        return store.rows(
                "SELECT * FROM maintenance_tickets WHERE payable_id=? ORDER BY created_at DESC,id DESC LIMIT 25 OFFSET ?",
                id(p, "id"),
                Math.max(0, page) * 25L);
    }

    @Transactional
    public Map<String, Object> create(
            UUID actor, UUID job, UUID requestKey, String kind, String title, String description) {
        var p = payable(actor, job, true);
        if (!actor.equals(id(p, "clientId"))) throw forbidden();
        var old =
                store.rows(
                        "SELECT * FROM maintenance_tickets WHERE creator_id=? AND request_key=?",
                        actor,
                        requestKey);
        if (!old.isEmpty()) {
            var previous = old.getFirst();
            if (!id(previous, "payableId").equals(id(p, "id"))
                    || !kind.equals(previous.get("kind"))
                    || !title.equals(previous.get("title"))
                    || !description.equals(previous.get("description")))
                throw invalid("Khóa yêu cầu đã dùng cho nội dung khác");
            return previous;
        }
        boolean active = FinancePolicy.canOpen(instant(p, "acceptedAt"), store.now());
        if ("WARRANTY".equals(kind) && !active)
            throw invalid("WARRANTY_WINDOW_EXPIRED: Đã hết thời hạn mở ticket miễn phí");
        if ("PAID".equals(kind) && active)
            throw invalid("Dự án còn trong thời gian bảo trì; hãy gửi ticket để phân loại");
        UUID ticket = UUID.randomUUID();
        Instant now = store.now();
        store.db.update(
                """
          INSERT INTO maintenance_tickets(id,payable_id,creator_id,request_key,kind,title,description,status,created_at,response_due_at,resolution_due_at)
          VALUES (?,?,?,?,?,?,?,'OPEN',?,?,?)
          """,
                ticket,
                id(p, "id"),
                actor,
                requestKey,
                kind,
                title,
                description,
                store.time(now),
                store.time(now.plus(Duration.ofHours(24))),
                store.time(now.plus(Duration.ofDays(7))));
        event(actor, ticket, "CREATED", description);
        announce(
                actor,
                p,
                ticket,
                "PAID".equals(kind)
                        ? "Yêu cầu báo giá bảo trì: " + title
                        : "Ticket bảo trì: " + title);
        return store.one("SELECT * FROM maintenance_tickets WHERE id=?", ticket);
    }

    private Map<String, Object> accessibleTicket(UUID actor, UUID ticket, boolean lock) {
        var row =
                store.one(
                        "SELECT t.*,p.job_id,p.owner_id,p.client_id FROM maintenance_tickets t JOIN finance_payables p ON p.id=t.payable_id WHERE t.id=?",
                        ticket);
        payable(actor, id(row, "jobId"), lock);
        return store.one(
                "SELECT t.*,p.job_id,p.owner_id,p.client_id FROM maintenance_tickets t JOIN finance_payables p ON p.id=t.payable_id WHERE t.id=?",
                ticket);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(UUID actor, UUID ticket, int page) {
        var row = accessibleTicket(actor, ticket, false);
        row.put(
                "events",
                store.rows(
                        "SELECT * FROM maintenance_ticket_events WHERE ticket_id=? ORDER BY created_at DESC,id DESC LIMIT 50 OFFSET ?",
                        ticket,
                        Math.max(0, page) * 50L));
        return row;
    }

    @Transactional
    public Map<String, Object> action(UUID actor, UUID ticket, String action, String content) {
        var row = accessibleTicket(actor, ticket, true);
        String status = row.get("status").toString();
        boolean owner = actor.equals(id(row, "ownerId")),
                client = actor.equals(id(row, "clientId")),
                admin = SecurityUtils.isAdmin();
        if (row.get("closedAt") != null)
            throw invalid("Ticket đã kết thúc; liên hệ hỗ trợ nếu có bất đồng");
        String next = status;
        switch (action) {
            case "COMMENT" -> {}
            case "START" -> {
                if (!owner || !Set.of("OPEN", "WAITING_CLIENT").contains(status)) throw forbidden();
                next = "IN_PROGRESS";
            }
            case "NEED_INFORMATION" -> {
                if (!owner || !Set.of("OPEN", "IN_PROGRESS").contains(status)) throw forbidden();
                next = "WAITING_CLIENT";
            }
            case "SUBMIT_FIX" -> {
                if (!owner
                        || !Set.of("OPEN", "IN_PROGRESS", "WAITING_CLIENT").contains(status)
                        || !"WARRANTY".equals(row.get("kind"))) throw forbidden();
                next = "FIX_SUBMITTED";
            }
            case "ACCEPT_FIX" -> {
                if (!client || !"FIX_SUBMITTED".equals(status)) throw forbidden();
                next = "RESOLVED";
            }
            case "REJECT_FIX" -> {
                if (!client || !"FIX_SUBMITTED".equals(status)) throw forbidden();
                next = "IN_PROGRESS";
            }
            case "ESCALATE" -> next = "DISPUTED";
            case "WITHDRAW" -> {
                if (!client || "DISPUTED".equals(status)) throw forbidden();
                next = "WITHDRAWN";
            }
            case "ADMIN_RESOLVE" -> {
                if (!admin) throw forbidden();
                next = "RESOLVED";
            }
            case "ADMIN_OUT_OF_SCOPE" -> {
                if (!admin) throw forbidden();
                next = "OUT_OF_SCOPE";
            }
            default -> throw invalid("Thao tác ticket không hợp lệ");
        }
        Instant now = store.now();
        if (!"COMMENT".equals(action))
            store.db.update(
                    "UPDATE maintenance_tickets SET status=?,closed_at=?,fix_submitted_at=? WHERE id=?",
                    next,
                    Set.of("RESOLVED", "OUT_OF_SCOPE", "WITHDRAWN").contains(next)
                            ? store.time(now)
                            : null,
                    "SUBMIT_FIX".equals(action)
                            ? store.time(now)
                            : row.get("fixSubmittedAt") == null
                                    ? null
                                    : store.time(instant(row, "fixSubmittedAt")),
                    ticket);
        event(actor, ticket, action, content);
        announce(actor, row, ticket, "Cập nhật ticket: " + action);
        if ("ESCALATE".equals(action)) notifyAdmins(id(row, "jobId"), ticket);
        return detail(actor, ticket, 0);
    }

    @Transactional
    public void recordOrderEvent(UUID actor, UUID ticket, String action, String content) {
        var row = accessibleTicket(actor, ticket, true);
        event(actor, ticket, action, content);
        announce(actor, row, ticket, "Cập nhật đơn bảo trì: " + action);
        if ("ORDER_ESCALATE".equals(action)) notifyAdmins(id(row, "jobId"), ticket);
    }

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> extend(UUID actor, UUID ticket, Instant deadline, String reason) {
        var row = accessibleTicket(actor, ticket, true);
        if (row.get("closedAt") != null || !"WARRANTY".equals(row.get("kind")))
            throw invalid("Ticket không còn được gia hạn");
        if (!deadline.isAfter(store.now())
                || deadline.isAfter(store.now().plus(Duration.ofDays(30))))
            throw invalid("Hạn gia hạn phải trong 30 ngày tới");
        store.db.update(
                "UPDATE maintenance_tickets SET resolution_due_at=?,overdue_at=NULL,status='IN_PROGRESS' WHERE id=?",
                store.time(deadline),
                ticket);
        event(
                actor,
                ticket,
                "ADMIN_EXTENDED",
                "Hạn cũ " + row.get("resolutionDueAt") + "; hạn mới " + deadline + ". " + reason);
        announce(actor, row, ticket, "Admin gia hạn xử lý ticket đến " + deadline);
        return detail(actor, ticket, 0);
    }

    private void event(UUID actor, UUID ticket, String action, String content) {
        store.db.update(
                "INSERT INTO maintenance_ticket_events VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),
                ticket,
                actor,
                action,
                content,
                store.time(store.now()));
    }

    private void announce(UUID actor, Map<String, Object> p, UUID ticket, String text) {
        UUID owner = id(p, "ownerId"), client = id(p, "clientId"), job = id(p, "jobId");
        var room = rooms.findByJobIdAndFreelancerId(job, owner);
        if (room.isPresent()) {
            UUID roomId = room.get().getId();
            ChatMessageDto message = chat.saveSystemMessage(roomId, text + " [" + ticket + "]");
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            messaging.convertAndSend("/topic/chat/" + roomId, message);
                        }
                    });
        }
        for (UUID recipient : List.of(owner, client))
            if (!recipient.equals(actor))
                notifications.persistAndPublish(
                        recipient,
                        NotificationMessage.builder()
                                .type("MAINTENANCE_TICKET_UPDATED")
                                .jobId(job)
                                .roomId(room.map(r -> r.getId()).orElse(null))
                                .message(text)
                                .build());
        store.audit(actor, owner, ticket, "TICKET_UPDATED", text);
    }

    private void notifyAdmins(UUID job, UUID ticket) {
        for (var admin :
                store.rows(
                        "SELECT id FROM users WHERE role='ADMIN' AND is_active=true ORDER BY id LIMIT 100"))
            notifications.persistAndPublish(
                    id(admin, "id"),
                    NotificationMessage.builder()
                            .type("MAINTENANCE_ESCALATED")
                            .jobId(job)
                            .message("Ticket bảo trì cần admin hỗ trợ: " + ticket)
                            .build());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminTickets(int page) {
        return store.rows(
                "SELECT t.*,j.title AS job_title,p.job_id,COALESCE(p.maintenance_order_id,p.job_id) AS scope_id FROM maintenance_tickets t JOIN finance_payables p ON p.id=t.payable_id JOIN jobs j ON j.id=p.job_id ORDER BY (t.closed_at IS NULL) DESC,(t.status='DISPUTED') DESC,t.resolution_due_at,t.id LIMIT 25 OFFSET ?",
                Math.max(0, page) * 25L);
    }

    /**
     * Called by a scheduler through the transactional proxy; serializes with payouts and tickets.
     */
    @Transactional
    public void escalateOverdue(UUID ticket) {
        var row =
                store.one(
                        "SELECT t.*,p.job_id,p.owner_id,p.client_id FROM maintenance_tickets t JOIN finance_payables p ON p.id=t.payable_id WHERE t.id=?",
                        ticket);
        store.lockOwner(id(row, "ownerId"));
        row =
                store.one(
                        "SELECT t.*,p.job_id,p.owner_id,p.client_id FROM maintenance_tickets t JOIN finance_payables p ON p.id=t.payable_id WHERE t.id=? FOR UPDATE OF t",
                        ticket);
        if (row.get("closedAt") != null
                || row.get("overdueAt") != null
                || !"WARRANTY".equals(row.get("kind"))) return;
        Instant now = store.now();
        boolean expired = !now.isBefore(instant(row, "resolutionDueAt"));
        Long responses =
                store.db.queryForObject(
                        "SELECT COUNT(*) FROM maintenance_ticket_events WHERE ticket_id=? AND actor_id=?",
                        Long.class,
                        ticket,
                        id(row, "ownerId"));
        if ("OPEN".equals(row.get("status"))
                && !now.isBefore(instant(row, "responseDueAt"))
                && (responses == null || responses == 0)) expired = true;
        if ("FIX_SUBMITTED".equals(row.get("status")) && row.get("fixSubmittedAt") != null)
            expired = !now.isBefore(instant(row, "fixSubmittedAt").plus(Duration.ofHours(48)));
        if (!expired) return;
        store.db.update(
                "UPDATE maintenance_tickets SET status='DISPUTED',overdue_at=? WHERE id=?",
                store.time(now),
                ticket);
        event(null, ticket, "OVERDUE", "Quá hạn: chuyển admin xem xét; công nợ tiếp tục được giữ");
        announce(null, row, ticket, "Ticket quá hạn, cần admin hỗ trợ");
        notifyAdmins(id(row, "jobId"), ticket);
    }
}
