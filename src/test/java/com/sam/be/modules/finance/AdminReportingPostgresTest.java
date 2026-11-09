package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class AdminReportingPostgresTest {
    JdbcTemplate db;
    String schema;
    AdminReportingService reports;
    UUID client = UUID.randomUUID(),
            freelancer = UUID.randomUUID(),
            job = UUID.randomUUID(),
            contract = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("FINANCE_TEST_DATABASE_URL"),
                user = System.getenv().getOrDefault("FINANCE_TEST_DATABASE_USER", "postgres"),
                password = System.getenv().getOrDefault("FINANCE_TEST_DATABASE_PASSWORD", "");
        schema = "report_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, user, password);
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        var source = new DriverManagerDataSource(url, user, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setConnectionProperties(properties);
        db = new JdbcTemplate(source);
        db.execute(
                """
            CREATE TABLE users(id UUID PRIMARY KEY,email TEXT,full_name TEXT,role TEXT,is_active BOOLEAN,created_at TIMESTAMPTZ,password_hash TEXT);
            CREATE TABLE jobs(id UUID PRIMARY KEY,title TEXT,status TEXT);
            CREATE TABLE contracts(id UUID PRIMARY KEY,job_id UUID,client_id UUID);
            CREATE TABLE finance_journals(id UUID PRIMARY KEY,event_key TEXT,contract_id UUID,owner_id UUID,description TEXT,sandbox BOOLEAN,created_at TIMESTAMPTZ);
            CREATE TABLE finance_entries(id UUID PRIMARY KEY,journal_id UUID,account_key TEXT,debit NUMERIC,credit NUMERIC);
            CREATE TABLE payment_checkouts(id UUID PRIMARY KEY,purpose TEXT,object_id UUID,owner_id UUID);
            CREATE TABLE user_subscriptions(id UUID PRIMARY KEY,package_id UUID,target_project_id UUID);
            CREATE TABLE service_packages(id UUID PRIMARY KEY,name TEXT);
            CREATE TABLE job_addon_orders(id UUID PRIMARY KEY,job_id UUID);
            CREATE TABLE payment_reconciliation_cases(id UUID PRIMARY KEY,state TEXT);
            CREATE TABLE finance_withdrawals(id UUID PRIMARY KEY,status TEXT);
            """);
        try (var input =
                getClass().getResourceAsStream("/db/migration/V42__Admin_Reporting_Indexes.sql")) {
            assertNotNull(input);
            db.execute(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        db.update(
                "INSERT INTO users VALUES (?, 'client@example.test','Khách hàng','CLIENT',true,now(),'PRIVATE_HASH')",
                client);
        db.update(
                "INSERT INTO users VALUES (?, 'worker@example.test','Percent %','FREELANCER',false,now(),'PRIVATE_HASH')",
                freelancer);
        db.update("INSERT INTO jobs VALUES (?, 'Website 100%','IN_PROGRESS')", job);
        db.update("INSERT INTO contracts VALUES (?,?,?)", contract, job, client);
        UUID pack = UUID.randomUUID(),
                sub = UUID.randomUUID(),
                addon = UUID.randomUUID(),
                packageCheckout = UUID.randomUUID(),
                addonCheckout = UUID.randomUUID();
        db.update("INSERT INTO service_packages VALUES (?,'Gói Pro')", pack);
        db.update("INSERT INTO user_subscriptions VALUES (?,?,?)", sub, pack, job);
        db.update("INSERT INTO job_addon_orders VALUES (?,?)", addon, job);
        db.update(
                "INSERT INTO payment_checkouts VALUES (?,'SUBSCRIPTION',?,?)",
                packageCheckout,
                sub,
                client);
        db.update(
                "INSERT INTO payment_checkouts VALUES (?,'JOB_ADDON',?,?)",
                addonCheckout,
                addon,
                client);
        add(
                "checkout-fulfilled:" + packageCheckout,
                null,
                100,
                false,
                "live:platform-revenue",
                "2026-11-08T17:00:00Z");
        add(
                "settlement:" + UUID.randomUUID(),
                contract,
                30,
                false,
                "live:platform-revenue",
                "2026-11-09T04:00:00Z");
        add(
                "checkout-fulfilled:" + addonCheckout,
                null,
                20,
                false,
                "live:platform-revenue",
                "2026-11-09T04:00:00Z");
        add("other", null, 10, false, "live:platform-revenue", "2026-11-09T04:00:00Z");
        add("adjustment", null, -5, false, "live:platform-revenue", "2026-11-09T04:00:00Z");
        add("sandbox", null, 900, true, "live:platform-revenue", "2026-11-09T04:00:00Z");
        add("escrow", contract, 500, false, "live:escrow", "2026-11-09T04:00:00Z");
        add("not-fulfilled", null, 500, false, "live:service-pending", "2026-11-09T04:00:00Z");
        add("before", null, 7, false, "live:platform-revenue", "2026-11-08T16:59:59Z");
        add("after", null, 11, false, "live:platform-revenue", "2026-11-09T17:00:00Z");
        reports =
                new AdminReportingService(
                        new FinanceStore(
                                db,
                                Clock.fixed(
                                        Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC)));
    }

    @AfterEach
    void cleanup() {
        if (db != null) db.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    void add(String key, UUID contractId, int amount, boolean sandbox, String account, String at) {
        UUID id = UUID.randomUUID();
        db.update(
                "INSERT INTO finance_journals VALUES (?,?,?,?,?,?,?)",
                id,
                key,
                contractId,
                client,
                "Doanh thu",
                sandbox,
                OffsetDateTime.parse(at));
        db.update(
                "INSERT INTO finance_entries VALUES (?,?,?,?,?)",
                UUID.randomUUID(),
                id,
                account,
                Math.max(0, -amount),
                Math.max(0, amount));
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> totals(Map<String, Object> result) {
        return (Map<String, Object>) result.get("totals");
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("items");
    }

    Map<String, Object> revenue(String source, UUID project, String q) {
        var day = LocalDate.of(2026, 11, 9);
        return reports.revenue(day, day, source, project, q, 0);
    }

    @Test
    void earnedLiveRevenueExcludesEscrowSandboxAndUnfulfilledReceiptsAndIncludesAdjustments() {
        var report = revenue("", null, "");
        assertEquals(
                0, new BigDecimal("155").compareTo(FinanceStore.money(totals(report), "total")));
        assertEquals(5L, totals(report).get("count"));
        assertEquals(
                0, new BigDecimal("100").compareTo(FinanceStore.money(totals(report), "packages")));
        assertEquals(0, new BigDecimal("5").compareTo(FinanceStore.money(totals(report), "other")));
        assertEquals(5, items(report).size());
    }

    @Test
    void filtersSourcesProjectAndLiteralNameWithoutSqlWildcards() {
        assertEquals(1, items(revenue("PROJECT", job, "100%")).size());
        assertEquals(
                "Website 100%",
                items(revenue("PROJECT", job, "100%")).getFirst().get("projectTitle"));
        assertEquals(3, items(revenue("", job, "")).size());
        assertEquals("Gói Pro", items(revenue("PACKAGE", job, "")).getFirst().get("packageName"));
        assertTrue(items(revenue("", null, "' OR true --")).isEmpty());
    }

    @Test
    void userSearchFiltersRoleStatusAndReturnsOnlyPublicAdminFields() {
        var result = reports.users("FREELANCER", "%", "INACTIVE", 0);
        assertEquals(1L, result.get("total"));
        var item = items(result).getFirst();
        assertEquals(freelancer, item.get("id"));
        assertEquals(
                Set.of("id", "email", "fullName", "role", "isActive", "createdAt"), item.keySet());
        assertEquals(0L, reports.users("CLIENT", "%", "", 0).get("total"));
    }

    @Test
    void overviewUsesSameLedgerAndCountsOperationalQueues() {
        var overview = reports.overview();
        assertNotNull(overview.get("users"));
        @SuppressWarnings("unchecked")
        var revenue = (Map<String, Object>) overview.get("revenue");
        assertEquals(0, new BigDecimal("173").compareTo(FinanceStore.money(revenue, "total")));
    }
}
