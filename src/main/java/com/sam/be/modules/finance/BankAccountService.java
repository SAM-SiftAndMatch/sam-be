package com.sam.be.modules.finance;

import static com.sam.be.modules.finance.FinanceStore.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.security.util.SecurityUtils;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BankAccountService {
    private final FinanceStore store;
    private final BankDataCipher cipher;
    private final ObjectMapper json;

    public record Details(String accountNumber, String accountHolder) {
        @Override
        public String toString() {
            return "BankDetails[REDACTED]";
        }
    }

    private static final String PUBLIC =
            "id,owner_id,bank_code,last_four,status,verification_note,created_at,verified_at,replaced_by";

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID owner) {
        return store.rows(
                "SELECT "
                        + PUBLIC
                        + " FROM finance_bank_accounts WHERE owner_id=? ORDER BY created_at DESC,id DESC",
                owner);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> setupStatus(UUID owner) {
        var counts =
                store.one(
                        "SELECT COUNT(*) FILTER(WHERE status='VERIFIED') AS verified,COUNT(*) FILTER(WHERE status='PENDING') AS pending FROM finance_bank_accounts WHERE owner_id=?",
                        owner);
        boolean verified = ((Number) counts.get("verified")).longValue() > 0;
        long pending = ((Number) counts.get("pending")).longValue();
        return Map.of(
                "status",
                verified ? "READY" : pending > 0 ? "PENDING" : "ACTION_REQUIRED",
                "hasVerifiedAccount",
                verified,
                "hasBankAccount",
                verified || pending > 0,
                "needsBankAccount",
                !verified && pending == 0,
                "pendingCount",
                pending);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminList(int page) {
        return store.rows(
                "SELECT b."
                        + PUBLIC.replace(",", ",b.")
                        + ",u.full_name AS owner_name FROM finance_bank_accounts b JOIN users u ON u.id=b.owner_id ORDER BY b.created_at DESC,b.id DESC LIMIT 25 OFFSET ?",
                Math.max(page, 0) * 25L);
    }

    @Transactional
    public Map<String, Object> add(UUID owner, String code, String account, String holder) {
        store.lockOwner(owner);
        var normalized = normalize(code, account, holder);
        String normalizedCode = normalized[0], number = normalized[1], name = normalized[2];
        String fingerprint = cipher.fingerprint(owner + ":" + normalizedCode + ":" + number);
        var existing =
                store.rows(
                        "SELECT "
                                + PUBLIC
                                + " FROM finance_bank_accounts WHERE owner_id=? AND fingerprint=? AND status IN ('PENDING','VERIFIED')",
                        owner,
                        fingerprint);
        if (!existing.isEmpty()) {
            var row = existing.getFirst();
            if (!sameDetails(owner, id(row, "id"), normalizedCode, number, name))
                throw invalid(
                        "Tài khoản đã tồn tại với thông tin khác; hãy dùng chức năng Cập nhật");
            return row;
        }
        UUID id = UUID.randomUUID();
        String encoded = encode(new Details(number, name));
        store.jdbc().update(
                "INSERT INTO finance_bank_accounts(id,owner_id,bank_code,details_encrypted,last_four,fingerprint,created_at) VALUES (?,?,?,?,?,?,?)",
                id,
                owner,
                normalizedCode,
                cipher.encrypt(encoded, "bank:" + owner + ":" + id),
                number.substring(number.length() - 4),
                fingerprint,
                store.time(store.now()));
        store.audit(owner, owner, id, "BANK_ADDED", "Tài khoản chờ xác minh");
        return store.one("SELECT " + PUBLIC + " FROM finance_bank_accounts WHERE id=?", id);
    }

    private String[] normalize(String code, String account, String holder) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        String number = account.replace(" ", "");
        String name = holder.trim().replaceAll("\\s+", " ");
        if (!normalizedCode.matches("[A-Z0-9]{2,20}")
                || !number.matches("[A-Za-z0-9]{4,40}")
                || name.length() < 2
                || name.length() > 150
                || name.chars().anyMatch(Character::isISOControl))
            throw invalid("Thông tin tài khoản nhận tiền không hợp lệ");
        return new String[] {normalizedCode, number, name};
    }

    private boolean sameDetails(
            UUID owner, UUID record, String code, String account, String holder) {
        var row =
                store.one(
                        "SELECT bank_code,details_encrypted FROM finance_bank_accounts WHERE id=? AND owner_id=?",
                        record,
                        owner);
        var details =
                decode(
                        cipher.decrypt(
                                row.get("detailsEncrypted").toString(),
                                "bank:" + owner + ":" + record));
        return code.equals(row.get("bankCode"))
                && account.equals(details.accountNumber())
                && holder.equals(details.accountHolder());
    }

    @Transactional
    public Map<String, Object> replace(
            UUID owner, UUID previous, String code, String account, String holder) {
        store.lockOwner(owner);
        var bank =
                store.one(
                        "SELECT * FROM finance_bank_accounts WHERE id=? AND owner_id=? FOR UPDATE",
                        previous,
                        owner);
        var normalized = normalize(code, account, holder);
        if (bank.get("replacedBy") != null) {
            UUID replacement = id(bank, "replacedBy");
            if (!sameDetails(owner, replacement, normalized[0], normalized[1], normalized[2]))
                throw invalid("Tài khoản đã được cập nhật; tải lại danh sách trước khi sửa tiếp");
            return store.one(
                    "SELECT " + PUBLIC + " FROM finance_bank_accounts WHERE id=? AND owner_id=?",
                    replacement,
                    owner);
        }
        if ("DISABLED".equals(bank.get("status")))
            throw invalid("Tài khoản đã ngừng sử dụng; hãy thêm tài khoản mới");
        if (!"REJECTED".equals(bank.get("status"))
                && sameDetails(owner, previous, normalized[0], normalized[1], normalized[2]))
            return store.one(
                    "SELECT " + PUBLIC + " FROM finance_bank_accounts WHERE id=? AND owner_id=?",
                    previous,
                    owner);
        Long active =
                store.jdbc().queryForObject(
                        "SELECT COUNT(*) FROM finance_withdrawals WHERE bank_account_id=? AND status IN ('REQUESTED','APPROVED','PROCESSING','RECONCILIATION_REQUIRED')",
                        Long.class,
                        previous);
        if (active != null && active > 0)
            throw invalid(
                    "Tài khoản đang gắn với lệnh chi chưa kết thúc. Hãy thêm tài khoản mới hoặc chờ lệnh chi được xử lý");
        store.jdbc().update("UPDATE finance_bank_accounts SET status='DISABLED' WHERE id=?", previous);
        var replacement = add(owner, normalized[0], normalized[1], normalized[2]);
        store.jdbc().update(
                "UPDATE finance_bank_accounts SET replaced_by=? WHERE id=?",
                id(replacement, "id"),
                previous);
        store.audit(
                owner,
                owner,
                previous,
                "BANK_UPDATED",
                "Thay bằng bản thông tin mới "
                        + replacement.get("id")
                        + "; giữ lịch sử người nhận");
        return replacement;
    }

    @Transactional
    public void disable(UUID owner, UUID id) {
        store.lockOwner(owner);
        if (store.jdbc().update(
                        "UPDATE finance_bank_accounts SET status='DISABLED' WHERE id=? AND owner_id=?",
                        id,
                        owner)
                != 1) throw missing();
        store.audit(owner, owner, id, "BANK_DISABLED", "Ngừng sử dụng; lịch sử giữ nguyên");
    }

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public void verify(UUID actor, UUID id, boolean approved, String note) {
        var bank = store.one("SELECT owner_id,status FROM finance_bank_accounts WHERE id=?", id);
        UUID owner = id(bank, "ownerId");
        store.lockOwner(owner);
        bank =
                store.one(
                        "SELECT owner_id,status FROM finance_bank_accounts WHERE id=? FOR UPDATE",
                        id);
        if (!"PENDING".equals(bank.get("status")))
            throw invalid("Tài khoản không còn chờ xác minh");
        if (actor.equals(owner)) throw forbidden();
        store.jdbc().update(
                "UPDATE finance_bank_accounts SET status=?,verification_note=?,verified_by=?,verified_at=? WHERE id=?",
                approved ? "VERIFIED" : "REJECTED",
                note,
                actor,
                store.time(store.now()),
                id);
        store.audit(actor, owner, id, approved ? "BANK_VERIFIED" : "BANK_REJECTED", note);
    }

    @Transactional
    public Map<String, Object> reveal(UUID actor, UUID id) {
        if (!SecurityUtils.isAdmin()) throw forbidden();
        var bank = store.one("SELECT * FROM finance_bank_accounts WHERE id=?", id);
        UUID owner = id(bank, "ownerId");
        store.audit(actor, owner, id, "BANK_DETAILS_VIEWED", "Hiển thị để xác minh người nhận");
        Details details =
                decode(
                        cipher.decrypt(
                                bank.get("detailsEncrypted").toString(),
                                "bank:" + owner + ":" + id));
        return Map.of(
                "bankCode",
                bank.get("bankCode"),
                "accountNumber",
                details.accountNumber(),
                "accountHolder",
                details.accountHolder());
    }

    String encode(Details details) {
        try {
            return json.writeValueAsString(details);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot encode bank details");
        }
    }

    Details decode(String text) {
        try {
            return json.readValue(text, Details.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot decode bank details");
        }
    }
}
