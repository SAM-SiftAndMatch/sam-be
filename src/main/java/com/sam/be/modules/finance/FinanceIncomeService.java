package com.sam.be.modules.finance;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Earned service revenue after fees. Deposits, wallet reservations and withdrawals are not income.
 */
@Service
@RequiredArgsConstructor
public class FinanceIncomeService {
    private final FinanceStore store;
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String FROM =
            """
        FROM finance_payables p
        LEFT JOIN project_settlements s ON s.id=p.settlement_id
        LEFT JOIN paid_maintenance_orders o ON o.id=p.maintenance_order_id
        """;
    private static final String SOURCE = FROM + " WHERE p.owner_id=? ";
    private static final String GROSS = "COALESCE(s.gross_amount,o.amount)";

    int reportYear(Integer year) {
        int value = year == null ? store.now().atZone(REPORT_ZONE).getYear() : year;
        if (value < 2000 || value > 2100)
            throw FinanceStore.invalid("Năm thống kê phải từ 2000 đến 2100");
        return value;
    }

    Instant start(int year) {
        return LocalDate.of(year, 1, 1).atStartOfDay(REPORT_ZONE).toInstant();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> summary(UUID owner, Integer selectedYear) {
        int year = reportYear(selectedYear);
        var totals =
                store.one(
                        """
            SELECT COALESCE(SUM(p.earnings) FILTER (WHERE NOT p.sandbox),0) AS total_net_income,
                   COALESCE(SUM(%s) FILTER (WHERE NOT p.sandbox),0) AS total_gross_income,
                   COALESCE(SUM(%s-p.earnings) FILTER (WHERE NOT p.sandbox),0) AS total_platform_fees,
                   COUNT(*) FILTER (WHERE NOT p.sandbox) AS completed_count,
                   COALESCE(SUM(p.earnings) FILTER (WHERE p.sandbox),0) AS sandbox_net_income
            """
                                        .formatted(GROSS, GROSS)
                                + SOURCE,
                        owner);
        var months =
                store.rows(
                        """
            SELECT EXTRACT(MONTH FROM p.accepted_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::int AS month,
                   COALESCE(SUM(p.earnings) FILTER (WHERE NOT p.sandbox),0) AS net_income,
                   COALESCE(SUM(%s) FILTER (WHERE NOT p.sandbox),0) AS gross_income,
                   COALESCE(SUM(%s-p.earnings) FILTER (WHERE NOT p.sandbox),0) AS platform_fees,
                   COUNT(*) FILTER (WHERE NOT p.sandbox) AS completed_count,
                   COALESCE(SUM(p.earnings) FILTER (WHERE p.sandbox),0) AS sandbox_net_income
            """
                                        .formatted(GROSS, GROSS)
                                + SOURCE
                                + " AND p.accepted_at>=? AND p.accepted_at<? GROUP BY 1 ORDER BY 1",
                        owner,
                        store.time(start(year)),
                        store.time(start(year + 1)));
        // Fill empty months so clients never fabricate revenue or infer missing months themselves.
        Map<Integer, Map<String, Object>> byMonth = new HashMap<>();
        for (var month : months) byMonth.put(((Number) month.get("month")).intValue(), month);
        List<Map<String, Object>> complete = new ArrayList<>();
        BigDecimal net = BigDecimal.ZERO, gross = net, fees = net, demo = net;
        long completed = 0;
        for (int m = 1; m <= 12; m++) {
            var month =
                    byMonth.getOrDefault(
                            m,
                            Map.of(
                                    "month",
                                    m,
                                    "netIncome",
                                    0,
                                    "grossIncome",
                                    0,
                                    "platformFees",
                                    0,
                                    "completedCount",
                                    0,
                                    "sandboxNetIncome",
                                    0));
            complete.add(month);
            net = net.add(FinanceStore.money(month, "netIncome"));
            gross = gross.add(FinanceStore.money(month, "grossIncome"));
            fees = fees.add(FinanceStore.money(month, "platformFees"));
            demo = demo.add(FinanceStore.money(month, "sandboxNetIncome"));
            completed += ((Number) month.get("completedCount")).longValue();
        }
        totals.put("year", year);
        totals.put("currency", "VND");
        totals.put("timezone", REPORT_ZONE.getId());
        totals.put("yearNetIncome", net);
        totals.put("yearGrossIncome", gross);
        totals.put("yearPlatformFees", fees);
        totals.put("yearCompletedCount", completed);
        totals.put("yearSandboxNetIncome", demo);
        totals.put("months", complete);
        return totals;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> entries(UUID owner, Integer selectedYear, int page) {
        int year = reportYear(selectedYear);
        Object[] period = {owner, store.time(start(year)), store.time(start(year + 1))};
        String filter = SOURCE + " AND NOT p.sandbox AND p.accepted_at>=? AND p.accepted_at<?";
        var count = store.one("SELECT COUNT(*) AS total_elements " + filter, period);
        var items =
                store.rows(
                        "SELECT p.id,p.job_id,j.title AS job_title,p.maintenance_order_id,p.accepted_at,"
                                + GROSS
                                + " AS gross_income,"
                                + GROSS
                                + "-p.earnings AS platform_fees,p.earnings AS net_income "
                                + (FROM + " JOIN jobs j ON j.id=p.job_id WHERE p.owner_id=? ")
                                + " AND NOT p.sandbox AND p.accepted_at>=? AND p.accepted_at<? ORDER BY p.accepted_at DESC,p.id DESC LIMIT 25 OFFSET ?",
                        owner,
                        period[1],
                        period[2],
                        Math.max(page, 0) * 25L);
        return Map.of(
                "items",
                items,
                "page",
                page,
                "size",
                25,
                "totalElements",
                count.get("totalElements"),
                "year",
                year);
    }
}
