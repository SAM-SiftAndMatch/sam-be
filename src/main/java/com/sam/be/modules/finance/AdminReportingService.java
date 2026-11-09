package com.sam.be.modules.finance;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only reporting of earned LIVE revenue, never gross bank receipts or escrow balances. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminReportingService {
    private final FinanceStore store;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int SIZE = 20;
    private static final String REVENUE =
            """
        WITH posted AS (
          SELECT j.id,j.event_key,j.contract_id,j.owner_id,j.description,j.created_at,
                 SUM(e.credit-e.debit) AS amount
          FROM finance_journals j JOIN finance_entries e ON e.journal_id=j.id
          WHERE j.sandbox=false AND e.account_key='live:platform-revenue'
          GROUP BY j.id
        ), revenue AS (
          SELECT p.id,p.created_at,p.amount,p.description,
            CASE WHEN p.contract_id IS NOT NULL THEN 'PROJECT'
                 WHEN c.purpose='SUBSCRIPTION' THEN 'PACKAGE'
                 WHEN c.purpose='JOB_ADDON' THEN 'ADDON' ELSE 'OTHER' END AS source,
            COALESCE(j.id,aj.id,sj.id) AS project_id,
            COALESCE(j.title,aj.title,sj.title) AS project_title,
            sp.name AS package_name,
            CASE WHEN p.contract_id IS NOT NULL THEN
                   CASE WHEN p.event_key LIKE 'maintenance-completed:%' THEN 'Phí bảo trì dự án' ELSE 'Phí dịch vụ dự án' END
                 WHEN c.purpose='SUBSCRIPTION' THEN COALESCE(sp.name,'Gói dịch vụ')
                 WHEN c.purpose='JOB_ADDON' THEN 'Tiện ích dự án' ELSE p.description END AS title,
            payer.full_name AS payer_name,payer.email AS payer_email,
            c.id AS checkout_id,p.contract_id
          FROM posted p
          LEFT JOIN payment_checkouts c ON p.event_key='checkout-fulfilled:'||c.id::text
          LEFT JOIN contracts ct ON ct.id=p.contract_id
          LEFT JOIN jobs j ON j.id=ct.job_id
          LEFT JOIN user_subscriptions us ON c.purpose='SUBSCRIPTION' AND us.id=c.object_id
          LEFT JOIN service_packages sp ON sp.id=us.package_id
          LEFT JOIN jobs sj ON sj.id=us.target_project_id
          LEFT JOIN job_addon_orders a ON c.purpose='JOB_ADDON' AND a.id=c.object_id
          LEFT JOIN jobs aj ON aj.id=a.job_id
          LEFT JOIN users payer ON payer.id=COALESCE(c.owner_id,ct.client_id)
        )
        """;

    public Map<String, Object> overview() {
        var now = store.now().atZone(ZONE);
        var result = new LinkedHashMap<String, Object>();
        result.put("timezone", ZONE.getId());
        result.put("currency", "VND");
        result.put("asOf", store.now().toString());
        result.put(
                "users",
                store.one(
                        """
            SELECT count(*) AS total,
              count(*) FILTER(WHERE role='CLIENT') AS clients,
              count(*) FILTER(WHERE role='FREELANCER') AS freelancers,
              count(*) FILTER(WHERE role='ADMIN') AS admins,
              count(*) FILTER(WHERE is_active=true) AS active
            FROM users
            """));
        result.put(
                "operations",
                store.one(
                        """
            SELECT (SELECT count(*) FROM jobs) AS projects,
              (SELECT count(*) FROM jobs WHERE status='IN_PROGRESS') AS active_projects,
              (SELECT count(*) FROM payment_reconciliation_cases WHERE state<>'RESOLVED') AS open_cases,
              (SELECT count(*) FROM finance_withdrawals WHERE status IN ('REQUESTED','APPROVED','PROCESSING','RECONCILIATION_REQUIRED')) AS pending_withdrawals
            """));
        result.put(
                "revenue",
                store.one(
                        REVENUE
                                + """
            SELECT COALESCE(sum(amount),0) AS total,
              COALESCE(sum(amount) FILTER(WHERE created_at>=?),0) AS month_total,
              count(*) AS entries FROM revenue
            """,
                        now.toLocalDate().withDayOfMonth(1).atStartOfDay(ZONE).toOffsetDateTime()));
        result.put(
                "recentRevenue",
                store.rows(
                        REVENUE
                                + "SELECT * FROM revenue ORDER BY created_at DESC,id DESC LIMIT 5"));
        result.put(
                "recentUsers",
                store.rows(
                        "SELECT id,email,full_name,role,is_active,created_at FROM users ORDER BY created_at DESC NULLS LAST,id DESC LIMIT 5"));
        return result;
    }

    public Map<String, Object> revenue(
            LocalDate from, LocalDate to, String source, UUID projectId, String query, int page) {
        page(page);
        LocalDate end = to == null ? store.now().atZone(ZONE).toLocalDate() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) > 730)
            throw FinanceStore.invalid("Khoảng ngày phải hợp lệ và không vượt quá 2 năm");
        String kind = source == null ? "" : source.strip().toUpperCase(Locale.ROOT);
        if (!kind.isEmpty() && !Set.of("PACKAGE", "PROJECT", "ADDON", "OTHER").contains(kind))
            throw FinanceStore.invalid("Nguồn doanh thu không hợp lệ");
        String search = search(query);
        List<Object> args = new ArrayList<>();
        args.add(start.atStartOfDay(ZONE).toOffsetDateTime());
        args.add(end.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime());
        StringBuilder where = new StringBuilder(" WHERE created_at>=? AND created_at<?");
        if (!kind.isEmpty()) {
            where.append(" AND source=?");
            args.add(kind);
        }
        if (projectId != null) {
            where.append(" AND project_id=?");
            args.add(projectId);
        }
        if (!search.isEmpty()) {
            where.append(
                    " AND (title ILIKE ? ESCAPE '!' OR COALESCE(project_title,'') ILIKE ? ESCAPE '!' OR COALESCE(payer_name,'') ILIKE ? ESCAPE '!' OR COALESCE(payer_email,'') ILIKE ? ESCAPE '!')");
            for (int n = 0; n < 4; n++) args.add(pattern(search));
        }
        String filtered = REVENUE + ", filtered AS (SELECT * FROM revenue" + where + ") ";
        var totals =
                store.one(
                        filtered
                                + """
            SELECT count(*) AS count,COALESCE(sum(amount),0) AS total,
              COALESCE(sum(amount) FILTER(WHERE source='PACKAGE'),0) AS packages,
              COALESCE(sum(amount) FILTER(WHERE source='PROJECT'),0) AS projects,
              COALESCE(sum(amount) FILTER(WHERE source='ADDON'),0) AS addons,
              COALESCE(sum(amount) FILTER(WHERE source='OTHER'),0) AS other
            FROM filtered
            """,
                        args.toArray());
        var daily =
                store.rows(
                        filtered
                                + "SELECT to_char(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh','YYYY-MM-DD') AS day,SUM(amount) AS amount,count(*) AS count FROM filtered GROUP BY day ORDER BY day",
                        args.toArray());
        var byDay = new HashMap<String, Map<String, Object>>();
        for (var item : daily) byDay.put(item.get("day").toString(), item);
        List<Map<String, Object>> series = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1))
            series.add(
                    byDay.getOrDefault(
                            day.toString(),
                            Map.of("day", day.toString(), "amount", 0, "count", 0)));
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(SIZE);
        listArgs.add(page * (long) SIZE);
        var result = new LinkedHashMap<String, Object>();
        result.put("from", start);
        result.put("to", end);
        result.put("timezone", ZONE.getId());
        result.put("currency", "VND");
        result.put("totals", totals);
        result.put("series", series);
        result.put("page", page);
        result.put("size", SIZE);
        result.put(
                "items",
                store.rows(
                        filtered
                                + "SELECT * FROM filtered ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                        listArgs.toArray()));
        return result;
    }

    public Map<String, Object> users(String role, String query, String status, int page) {
        page(page);
        String kind = role == null ? "" : role.strip().toUpperCase(Locale.ROOT);
        if (!kind.isEmpty() && !Set.of("CLIENT", "FREELANCER", "ADMIN").contains(kind))
            throw FinanceStore.invalid("Vai trò không hợp lệ");
        String state = status == null ? "" : status.strip().toUpperCase(Locale.ROOT);
        if (!state.isEmpty() && !Set.of("ACTIVE", "INACTIVE").contains(state))
            throw FinanceStore.invalid("Trạng thái không hợp lệ");
        String search = search(query);
        var args = new ArrayList<Object>();
        StringBuilder where = new StringBuilder(" WHERE true");
        if (!kind.isEmpty()) {
            where.append(" AND role=?");
            args.add(kind);
        }
        if (!state.isEmpty()) {
            where.append(" AND is_active=?");
            args.add(state.equals("ACTIVE"));
        }
        if (!search.isEmpty()) {
            where.append(" AND (full_name ILIKE ? ESCAPE '!' OR email ILIKE ? ESCAPE '!')");
            args.add(pattern(search));
            args.add(pattern(search));
        }
        var result = new LinkedHashMap<String, Object>();
        result.put(
                "total",
                store.one("SELECT count(*) AS count FROM users" + where, args.toArray())
                        .get("count"));
        args.add(SIZE);
        args.add(page * (long) SIZE);
        result.put(
                "items",
                store.rows(
                        "SELECT id,email,full_name,role,is_active,created_at FROM users"
                                + where
                                + " ORDER BY created_at DESC NULLS LAST,id DESC LIMIT ? OFFSET ?",
                        args.toArray()));
        result.put("page", page);
        result.put("size", SIZE);
        return result;
    }

    private static String search(String query) {
        String value = query == null ? "" : query.strip();
        if (value.length() > 100) throw FinanceStore.invalid("Từ khóa tối đa 100 ký tự");
        return value;
    }

    private static String pattern(String value) {
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static void page(int value) {
        if (value < 0 || value > 100000) throw FinanceStore.invalid("Trang không hợp lệ");
    }
}
