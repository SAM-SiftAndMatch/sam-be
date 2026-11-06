package com.sam.be.modules.finance;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class FinanceStore {
    public final JdbcTemplate db;
    private final Clock clock;

    public FinanceStore(JdbcTemplate db, @Qualifier("financeClock") Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public OffsetDateTime time(Instant t) {
        return t.atOffset(ZoneOffset.UTC);
    }

    public void lockOwner(UUID owner) {
        if (db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", owner).isEmpty())
            throw missing();
    }

    public Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = rows(sql, args);
        if (rows.isEmpty()) throw missing();
        return rows.getFirst();
    }

    public List<Map<String, Object>> rows(String sql, Object... args) {
        return db.query(
                sql,
                (rs, index) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    ResultSetMetaData meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String raw = meta.getColumnLabel(i);
                        StringBuilder name = new StringBuilder();
                        boolean upper = false;
                        for (char c : raw.toCharArray()) {
                            if (c == '_') {
                                upper = true;
                                continue;
                            }
                            name.append(upper ? Character.toUpperCase(c) : c);
                            upper = false;
                        }
                        Object value = rs.getObject(i);
                        if (value instanceof Timestamp t) value = t.toInstant().toString();
                        if (value instanceof OffsetDateTime t) value = t.toInstant().toString();
                        row.put(name.toString(), value);
                    }
                    return row;
                },
                args);
    }

    public static UUID id(Map<String, Object> row, String key) {
        return UUID.fromString(row.get(key).toString());
    }

    public static BigDecimal money(Map<String, Object> row, String key) {
        return new BigDecimal(row.get(key).toString());
    }

    public static Instant instant(Map<String, Object> row, String key) {
        return Instant.parse(row.get(key).toString());
    }

    public static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu");
    }

    public static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public static ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");
    }

    public void audit(UUID actor, UUID owner, UUID subject, String action, String detail) {
        db.update(
                "INSERT INTO finance_audit_events VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                actor,
                owner,
                subject,
                action,
                detail,
                time(now()));
    }

    public record Line(String account, BigDecimal debit, BigDecimal credit) {}

    public static Line debit(String account, BigDecimal amount) {
        return new Line(account, amount, BigDecimal.ZERO);
    }

    public static Line credit(String account, BigDecimal amount) {
        return new Line(account, BigDecimal.ZERO, amount);
    }

    public void journal(
            String key,
            UUID contract,
            UUID owner,
            String description,
            boolean sandbox,
            Line... lines) {
        if (lines.length < 2
                || Arrays.stream(lines)
                                .map(l -> l.debit().subtract(l.credit()))
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .signum()
                        != 0) throw new IllegalArgumentException("Unbalanced journal");
        UUID id = UUID.randomUUID();
        int inserted =
                db.update(
                        "INSERT INTO finance_journals(id,event_key,contract_id,owner_id,description,sandbox,created_at) VALUES (?,?,?,?,?,?,?) ON CONFLICT(event_key) DO NOTHING",
                        id,
                        key,
                        contract,
                        owner,
                        description,
                        sandbox,
                        time(now()));
        if (inserted == 0) return;
        for (Line line : lines) {
            if (line.debit().signum() == 0 && line.credit().signum() == 0) continue;
            db.update(
                    "INSERT INTO finance_entries VALUES (?,?,?,?,?)",
                    UUID.randomUUID(),
                    id,
                    line.account(),
                    line.debit(),
                    line.credit());
        }
    }

    public static final String BLOCKED =
            "EXISTS(SELECT 1 FROM maintenance_tickets t WHERE t.payable_id=p.id AND t.kind='WARRANTY' AND t.closed_at IS NULL)";

    public List<Map<String, Object>> payables(UUID owner) {
        Instant now = now();
        return rows(
                "SELECT p.*,j.title AS job_title,"
                        + BLOCKED
                        + " AS blocked FROM finance_payables p JOIN jobs j ON j.id=p.job_id WHERE p.owner_id=? ORDER BY p.available_at,p.id",
                owner);
    }

    public boolean eligible(Map<String, Object> p) {
        return !(Boolean) p.get("sandbox")
                && FinancePolicy.canRelease(
                        instant(p, "availableAt"), now(), (Boolean) p.get("blocked"));
    }

    public BigDecimal remaining(Map<String, Object> p) {
        return money(p, "amount")
                .subtract(money(p, "paid"))
                .subtract(money(p, "reserved"))
                .subtract(money(p, "adjusted"));
    }
}
