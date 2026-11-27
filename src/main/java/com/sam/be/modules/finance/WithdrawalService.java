package com.sam.be.modules.finance;

import static com.sam.be.modules.finance.FinanceStore.*;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalService {
    private final FinanceStore store;
    private final BankDataCipher cipher;
    private final BankAccountService banks;
    private final WithdrawalImageService images;
    private final WithdrawalNotifications notifications;
    private final boolean enabled, dualControl;
    private final BigDecimal minimum, dailyLimit;
    static final String PUBLIC =
            "id,owner_id,bank_account_id,bank_code,last_four,amount,status,approved_by,operator_id,bank_reference,evidence_reference,created_at,updated_at,request_type,early_payable_id,request_reason";

    public WithdrawalService(
            FinanceStore store,
            BankDataCipher cipher,
            BankAccountService banks,
            WithdrawalImageService images,
            WithdrawalNotifications notifications,
            @Value("${finance.live-payouts-enabled:false}") boolean enabled,
            @Value("${finance.dual-control:true}") boolean dualControl,
            @Value("${finance.minimum-withdrawal:50000}") BigDecimal minimum,
            @Value("${finance.daily-withdrawal-limit:100000000}") BigDecimal dailyLimit) {
        this.store = store;
        this.cipher = cipher;
        this.banks = banks;
        this.images = images;
        this.notifications = notifications;
        this.enabled = enabled;
        this.dualControl = dualControl;
        this.minimum = minimum;
        this.dailyLimit = dailyLimit;
        if (enabled && !cipher.configured())
            throw new IllegalStateException("Live payouts require bank encryption keys");
        if (minimum.signum() <= 0 || dailyLimit.compareTo(minimum) < 0)
            throw new IllegalStateException("Invalid withdrawal limits");
    }

    public boolean enabled() {
        return enabled;
    }

    public BigDecimal minimum() {
        return minimum;
    }

    public BigDecimal dailyLimit() {
        return dailyLimit;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID owner, int page) {
        return store.rows(
                "SELECT "
                        + PUBLIC
                        + " FROM finance_withdrawals WHERE owner_id=? ORDER BY created_at DESC,id DESC LIMIT 25 OFFSET ?",
                owner,
                Math.max(0, page) * 25L);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> adminList(int page) {
        return adminList(page, null);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> adminList(int page, String type) {
        if (type != null && !Set.of("NORMAL", "EARLY").contains(type))
            throw invalid("Loại yêu cầu rút tiền không hợp lệ");
        return store.rows(
                "SELECT "
                        + PUBLIC
                        + " FROM finance_withdrawals WHERE (CAST(? AS VARCHAR) IS NULL OR request_type=?) ORDER BY created_at DESC,id DESC LIMIT 25 OFFSET ?",
                type,
                type,
                Math.max(0, page) * 25L);
    }

    @Transactional
    public Map<String, Object> create(UUID owner, UUID bankId, UUID requestKey, BigDecimal amount) {
        return createInternal(owner, bankId, requestKey, amount, null);
    }

    private record Early(UUID payable, String reason, List<WithdrawalImageService.Image> images) {}

    @Transactional
    public Map<String, Object> createEarly(
            UUID owner,
            UUID bankId,
            UUID requestKey,
            BigDecimal amount,
            UUID payable,
            String reason,
            List<org.springframework.web.multipart.MultipartFile> files) {
        if (!enabled) throw invalid("Chức năng chi tiền thật chưa được bật");
        if (reason == null || reason.isBlank() || reason.trim().length() > 2000)
            throw invalid("Cần nhập lý do xin rút sớm (tối đa 2000 ký tự)");
        if (payable == null) throw invalid("Cần chọn dự án xin rút sớm");
        return createInternal(
                owner,
                bankId,
                requestKey,
                amount,
                new Early(payable, reason.trim(), images.validate(files)));
    }

    private Map<String, Object> createInternal(
            UUID owner, UUID bankId, UUID requestKey, BigDecimal amount, Early early) {
        if (!enabled) throw invalid("Chức năng chi tiền thật chưa được bật");
        if (amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 0
                || amount.compareTo(minimum) < 0
                || amount.compareTo(dailyLimit) > 0)
            throw invalid("Số tiền rút không nằm trong hạn mức cho phép");
        store.lockOwner(owner);
        var old =
                store.rows(
                        "SELECT "
                                + PUBLIC
                                + " FROM finance_withdrawals WHERE owner_id=? AND request_key=?",
                        owner,
                        requestKey);
        if (!old.isEmpty()) {
            var previous = old.getFirst();
            if (!bankId.equals(id(previous, "bankAccountId"))
                    || amount.compareTo(money(previous, "amount")) != 0
                    || !(early == null ? "NORMAL" : "EARLY")
                            .equals(previous.getOrDefault("requestType", "NORMAL"))
                    || (early != null
                            && (!early.payable().equals(id(previous, "earlyPayableId"))
                                    || !early.reason().equals(previous.get("requestReason"))
                                    || !images.matches(id(previous, "id"), early.images()))))
                throw invalid("Khóa yêu cầu đã dùng cho nội dung khác");
            return previous;
        }
        var bank =
                store.one(
                        "SELECT * FROM finance_bank_accounts WHERE id=? AND owner_id=? AND status='VERIFIED'",
                        bankId,
                        owner);
        BigDecimal today =
                store.jdbc()
                        .queryForObject(
                                "SELECT COALESCE(SUM(amount),0) FROM finance_withdrawals WHERE owner_id=? AND created_at>=? AND status NOT IN ('FAILED','REJECTED','CANCELLED')",
                                BigDecimal.class,
                                owner,
                                store.time(
                                        store.now()
                                                .atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                                                .toLocalDate()
                                                .atStartOfDay(
                                                        java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                                                .toInstant()));
        if (today.add(amount).compareTo(dailyLimit) > 0)
            throw invalid("Vượt hạn mức rút trong ngày");
        var payables = store.payables(owner);
        if (early != null) {
            payables = payables.stream().filter(p -> early.payable().equals(id(p, "id"))).toList();
            if (payables.isEmpty()) throw missing();
            var p = payables.getFirst();
            if ((Boolean) p.get("sandbox")
                    || (Boolean) p.get("blocked")
                    || p.get("maintenanceOrderId") != null
                    || !FinancePolicy.canOpen(instant(p, "acceptedAt"), store.now()))
                throw invalid(
                        "Chỉ xin rút sớm cho dự án thật đã nghiệm thu, còn trong 7 ngày bảo trì và không có ticket chặn chi");
            if (!store.rows(
                            "SELECT id FROM finance_withdrawals WHERE early_payable_id=? AND status IN ('REQUESTED','APPROVED','PROCESSING','RECONCILIATION_REQUIRED')",
                            early.payable())
                    .isEmpty()) throw invalid("Dự án đã có yêu cầu rút sớm đang xử lý");
        }
        BigDecimal total =
                payables.stream()
                        .filter(p -> early != null || store.eligible(p))
                        .map(p -> early == null ? store.remaining(p) : earlyRemaining(p))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(amount) < 0)
            throw invalid(
                    "Công nợ khả dụng không đủ; khoản bảo trì hoặc ticket chưa kết thúc không được rút");
        UUID withdrawal = UUID.randomUUID();
        String snapshot =
                cipher.decrypt(
                        bank.get("detailsEncrypted").toString(), "bank:" + owner + ":" + bankId);
        store.jdbc()
                .update(
                        """
            INSERT INTO finance_withdrawals(id,owner_id,request_key,bank_account_id,beneficiary_encrypted,bank_code,last_four,amount,status,created_at,updated_at,request_type,early_payable_id,request_reason)
            VALUES (?,?,?,?,?,?,?,?,'REQUESTED',?,?,?,?,?)
            """,
                        withdrawal,
                        owner,
                        requestKey,
                        bankId,
                        cipher.encrypt(snapshot, "withdrawal:" + owner + ":" + withdrawal),
                        bank.get("bankCode"),
                        bank.get("lastFour"),
                        amount,
                        store.time(store.now()),
                        store.time(store.now()),
                        early == null ? "NORMAL" : "EARLY",
                        early == null ? null : early.payable(),
                        early == null ? null : early.reason());
        BigDecimal remaining = amount;
        for (var p : payables) {
            if (early == null && !store.eligible(p)) continue;
            BigDecimal part =
                    (early == null ? store.remaining(p) : earlyRemaining(p)).min(remaining);
            if (part.signum() <= 0) continue;
            UUID payable = id(p, "id");
            store.jdbc()
                    .update(
                            "UPDATE finance_payables SET reserved=reserved+? WHERE id=?",
                            part,
                            payable);
            store.jdbc()
                    .update(
                            "INSERT INTO finance_withdrawal_allocations VALUES (?,?,?)",
                            withdrawal,
                            payable,
                            part);
            remaining = remaining.subtract(part);
            if (remaining.signum() == 0) break;
        }
        store.audit(owner, owner, withdrawal, "REQUESTED", "Đã giữ riêng số tiền của yêu cầu rút");
        if (early != null) images.save(owner, withdrawal, early.images());
        var result =
                store.one("SELECT " + PUBLIC + " FROM finance_withdrawals WHERE id=?", withdrawal);
        notifications.changed(result, "REQUESTED");
        return result;
    }

    static BigDecimal earlyRemaining(Map<String, Object> payable) {
        return money(payable, "earnings")
                .subtract(money(payable, "paid"))
                .subtract(money(payable, "reserved"))
                .subtract(money(payable, "adjusted"))
                .max(BigDecimal.ZERO);
    }

    private Map<String, Object> locked(UUID withdrawal) {
        UUID owner =
                id(
                        store.one(
                                "SELECT owner_id FROM finance_withdrawals WHERE id=?", withdrawal),
                        "ownerId");
        store.lockOwner(owner);
        return store.one("SELECT * FROM finance_withdrawals WHERE id=? FOR UPDATE", withdrawal);
    }

    private void eligibleAllocations(Map<String, Object> request) {
        eligibleAllocations(request, false);
    }

    private void eligibleAllocations(Map<String, Object> request, boolean reconciledTransfer) {
        UUID withdrawal = id(request, "id");
        boolean early = "EARLY".equals(request.get("requestType"));
        var allocations =
                store.rows(
                        "SELECT p.*, "
                                + FinanceStore.BLOCKED
                                + " AS blocked FROM finance_payables p JOIN finance_withdrawal_allocations a ON a.payable_id=p.id WHERE a.withdrawal_id=?",
                        withdrawal);
        if (allocations.isEmpty() || (early && allocations.size() != 1))
            throw invalid("Lệnh chi chưa được phân bổ công nợ hợp lệ");
        for (var p : allocations) {
            boolean eligible =
                    early
                            ? id(p, "id").equals(id(request, "earlyPayableId"))
                                    && id(p, "ownerId").equals(id(request, "ownerId"))
                                    && !(Boolean) p.get("sandbox")
                                    && (reconciledTransfer || !(Boolean) p.get("blocked"))
                                    && p.get("maintenanceOrderId") == null
                            : !(Boolean) p.get("sandbox")
                                    && FinancePolicy.canRelease(
                                            instant(p, "availableAt"),
                                            store.now(),
                                            !reconciledTransfer && (Boolean) p.get("blocked"));
            if (!eligible) throw invalid("Công nợ được phân bổ đang có chặn chi");
        }
    }

    private void status(UUID actor, Map<String, Object> row, String target, String note) {
        store.jdbc()
                .update(
                        "UPDATE finance_withdrawals SET status=?,updated_at=? WHERE id=?",
                        target,
                        store.time(store.now()),
                        id(row, "id"));
        store.audit(actor, id(row, "ownerId"), id(row, "id"), target, note);
        notifications.changed(row, target);
    }

    private void release(UUID withdrawal) {
        for (var allocation :
                store.rows(
                        "SELECT * FROM finance_withdrawal_allocations WHERE withdrawal_id=?",
                        withdrawal))
            store.jdbc()
                    .update(
                            "UPDATE finance_payables SET reserved=reserved-? WHERE id=?",
                            money(allocation, "amount"),
                            id(allocation, "payableId"));
    }

    @Transactional
    public void cancel(UUID actor, UUID withdrawal) {
        var row = locked(withdrawal);
        if (!actor.equals(id(row, "ownerId"))) throw missing();
        if (!"REQUESTED".equals(row.get("status")))
            throw invalid("Chỉ hủy được yêu cầu chưa duyệt");
        release(withdrawal);
        status(actor, row, "CANCELLED", "Freelancer hủy trước khi duyệt");
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void approve(UUID actor, UUID withdrawal) {
        approve(actor, withdrawal, null);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void approve(UUID actor, UUID withdrawal, String note) {
        if (!enabled) throw invalid("Chức năng chi tiền thật chưa được bật");
        var row = locked(withdrawal);
        if (!"REQUESTED".equals(row.get("status"))) throw invalid("Yêu cầu không còn chờ duyệt");
        if ("EARLY".equals(row.get("requestType"))
                && (note == null || note.isBlank() || note.length() > 1000))
            throw invalid("Admin cần ghi lý do chấp thuận chi sớm (tối đa 1000 ký tự)");
        eligibleAllocations(row);
        store.one(
                "SELECT id FROM finance_bank_accounts WHERE id=? AND status='VERIFIED'",
                id(row, "bankAccountId"));
        store.jdbc()
                .update(
                        "UPDATE finance_withdrawals SET approved_by=? WHERE id=?",
                        actor,
                        withdrawal);
        status(
                actor,
                row,
                "APPROVED",
                "Đã duyệt, chưa chuyển tiền ngân hàng"
                        + (note == null || note.isBlank() ? "" : ". " + note.trim()));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> startManual(UUID actor, UUID withdrawal) {
        if (!enabled) throw invalid("Chức năng chi tiền thật chưa được bật");
        var row = locked(withdrawal);
        String current = row.get("status").toString();
        if (!Set.of("APPROVED", "PROCESSING").contains(current))
            throw invalid("Lệnh chi chưa được duyệt hoặc đã kết thúc");
        if (dualControl && actor.equals(row.get("approvedBy")))
            throw invalid("Người thực hiện chuyển khoản phải khác người duyệt");
        if ("PROCESSING".equals(current) && !actor.equals(row.get("operatorId")))
            throw invalid("Lệnh chi đang được người khác thực hiện");
        eligibleAllocations(row);
        store.one(
                "SELECT id FROM finance_bank_accounts WHERE id=? AND status='VERIFIED'",
                id(row, "bankAccountId"));
        var details =
                banks.decode(
                        cipher.decrypt(
                                row.get("beneficiaryEncrypted").toString(),
                                "withdrawal:" + id(row, "ownerId") + ":" + withdrawal));
        if ("APPROVED".equals(current)) {
            store.jdbc()
                    .update(
                            "UPDATE finance_withdrawals SET operator_id=? WHERE id=?",
                            actor,
                            withdrawal);
            status(
                    actor,
                    row,
                    "PROCESSING",
                    "Đã nhận thực hiện chuyển khoản thủ công; cần đối soát trước thao tác lại");
        }
        store.audit(
                actor,
                id(row, "ownerId"),
                withdrawal,
                "BENEFICIARY_VIEWED",
                "Xem bản chụp người nhận của lệnh chi");
        return Map.of(
                "bankCode",
                row.get("bankCode"),
                "accountNumber",
                details.accountNumber(),
                "accountHolder",
                details.accountHolder(),
                "amount",
                row.get("amount"),
                "withdrawalId",
                withdrawal);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void finishManual(
            UUID actor, UUID withdrawal, String bankReference, String evidence, String note) {
        bankReference = bankReference.toUpperCase(Locale.ROOT);
        var row = locked(withdrawal);
        if ("PAID".equals(row.get("status"))) {
            if (!bankReference.equals(row.get("bankReference")))
                throw invalid("Lệnh đã chi với mã giao dịch khác");
            return;
        }
        if (!Set.of("PROCESSING", "RECONCILIATION_REQUIRED").contains(row.get("status")))
            throw invalid("Lệnh chưa ở trạng thái đối soát chuyển khoản");
        if (!actor.equals(row.get("operatorId"))) throw forbidden();
        // A newly opened warranty ticket may stop a transfer, but cannot undo one already sent.
        // An independent admin must first move a blocked, potentially sent transfer to
        // reconciliation.
        eligibleAllocations(row, "RECONCILIATION_REQUIRED".equals(row.get("status")));
        UUID owner = id(row, "ownerId");
        store.jdbc()
                .update(
                        "UPDATE finance_withdrawals SET bank_reference=?,evidence_reference=? WHERE id=?",
                        bankReference,
                        evidence,
                        withdrawal);
        for (var a :
                store.rows(
                        "SELECT * FROM finance_withdrawal_allocations WHERE withdrawal_id=?",
                        withdrawal))
            store.jdbc()
                    .update(
                            "UPDATE finance_payables SET reserved=reserved-?,paid=paid+? WHERE id=?",
                            money(a, "amount"),
                            money(a, "amount"),
                            id(a, "payableId"));
        store.journal(
                "withdrawal-paid:" + withdrawal,
                null,
                owner,
                "Chi trả ngân hàng đã đối soát",
                false,
                debit("live:payable:" + owner, money(row, "amount")),
                credit("live:cash:platform-bank", money(row, "amount")));
        status(actor, row, "PAID", note);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void rejectOrReconcile(UUID actor, UUID withdrawal, String action, String note) {
        var row = locked(withdrawal);
        String current = row.get("status").toString();
        switch (action) {
            case "REJECT" -> {
                if (!Set.of("REQUESTED", "APPROVED").contains(current))
                    throw invalid("Không thể từ chối lệnh đã gửi");
                release(withdrawal);
                status(actor, row, "REJECTED", note);
            }
            case "RECONCILE" -> {
                if (!Set.of("PROCESSING", "RECONCILIATION_REQUIRED").contains(current))
                    throw invalid("Lệnh không chờ đối soát");
                if (dualControl && actor.equals(row.get("operatorId")))
                    throw invalid("Cần admin độc lập xác nhận chuyển lệnh sang đối soát");
                status(actor, row, "RECONCILIATION_REQUIRED", note);
            }
            case "CONFIRM_FAILED" -> {
                if (!"RECONCILIATION_REQUIRED".equals(current))
                    throw invalid("Phải đối soát trước khi xác nhận không chuyển tiền");
                if (dualControl && actor.equals(row.get("operatorId")))
                    throw invalid(
                            "Cần người kiểm tra độc lập xác nhận giao dịch không chuyển tiền");
                release(withdrawal);
                status(actor, row, "FAILED", note);
            }
            default -> throw invalid("Thao tác không hợp lệ");
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(UUID actor, UUID withdrawal, boolean admin) {
        var row =
                store.one("SELECT " + PUBLIC + " FROM finance_withdrawals WHERE id=?", withdrawal);
        if (!actor.equals(id(row, "ownerId")) && !admin) throw missing();
        if ("EARLY".equals(row.get("requestType"))) {
            row.put(
                    "project",
                    store.one(
                            "SELECT p.job_id,j.title AS job_title,p.accepted_at,p.available_at,p.earnings,p.deposit_refund,p.reserved,p.paid,"
                                    + FinanceStore.BLOCKED
                                    + " AS blocked FROM finance_payables p JOIN jobs j ON j.id=p.job_id WHERE p.id=?",
                            id(row, "earlyPayableId")));
            row.put("images", images.metadata(withdrawal));
        }
        row.put(
                "history",
                store.rows(
                        "SELECT actor_id,action,detail,created_at FROM finance_audit_events WHERE subject_id=? ORDER BY created_at,id",
                        withdrawal));
        return row;
    }
}
