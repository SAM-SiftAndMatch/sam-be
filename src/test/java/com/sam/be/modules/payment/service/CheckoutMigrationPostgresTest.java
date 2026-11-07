package com.sam.be.modules.payment.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Exercises an upgrade containing old pending purchases, rather than an empty V39 schema. */
@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class CheckoutMigrationPostgresTest {
    private static final UUID BUSINESS = UUID.fromString("f0000000-0000-0000-0000-000000000005");
    private static final UUID PRO = UUID.fromString("f0000000-0000-0000-0000-000000000006");
    private final UUID owner = UUID.randomUUID();
    private String schema;
    private JdbcTemplate db;
    private TransactionTemplate tx;
    private List<Map<String, Object>> original;

    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("FINANCE_TEST_DATABASE_URL");
        String user = System.getenv().getOrDefault("FINANCE_TEST_DATABASE_USER", "postgres");
        String password = System.getenv().getOrDefault("FINANCE_TEST_DATABASE_PASSWORD", "");
        schema = "checkout_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, user, password);
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        var source = new DriverManagerDataSource(url, user, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setConnectionProperties(properties);
        db = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        db.execute("CREATE TABLE users(id UUID PRIMARY KEY); CREATE TABLE jobs(id UUID PRIMARY KEY); CREATE TABLE payments(id UUID PRIMARY KEY,provider TEXT,environment TEXT,contract_id UUID,payment_type TEXT,maintenance_order_id UUID,status TEXT); CREATE TABLE user_subscriptions(id UUID PRIMARY KEY,user_id UUID,package_id UUID,status TEXT)");
        db.execute("CREATE FUNCTION finance_immutable() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'immutable'; END $$");
        db.update("INSERT INTO users VALUES (?)", owner);
        db.execute(migration("V39__Unified_Pay2s_Checkouts.sql"));
        for (UUID plan : List.of(BUSINESS, PRO)) {
            pending(plan, "LEGACY");
            pending(plan, "LEGACY");
        }
        original = subscriptions();
    }

    @AfterEach
    void cleanup() {
        if (db != null) db.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test
    void upgradePreservesDuplicateLegacyPendingPurchases() throws Exception {
        upgrade();
        assertEquals(original, subscriptions());
        // New constraints do not retroactively change the semantics of historical records.
        pending(BUSINESS, "LEGACY");
        assertEquals(5, db.queryForObject("SELECT count(*) FROM user_subscriptions WHERE environment='LEGACY' AND status='PENDING'", Integer.class));
    }

    @Test
    void liveAndSandboxStillRejectDuplicatePendingPurchasesIndependently() throws Exception {
        upgrade();
        for (UUID plan : List.of(BUSINESS, PRO)) {
            for (String environment : List.of("LIVE", "SANDBOX")) {
                pending(plan, environment);
                assertThrows(DuplicateKeyException.class, () -> pending(plan, environment));
            }
        }
        assertEquals(4, db.queryForObject("SELECT count(*) FROM user_subscriptions WHERE environment IN ('LIVE','SANDBOX')", Integer.class));
    }

    @Test
    void realLiveDuplicatesStillFailAndRollbackTheWholeMigration() {
        pending(BUSINESS, "LIVE");
        pending(BUSINESS, "LIVE");
        var before = subscriptions();
        assertThrows(DuplicateKeyException.class, this::upgrade);
        assertEquals(before, subscriptions());
        assertEquals(0, db.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND table_name='payment_checkouts' AND column_name='support_code'", Integer.class, schema));
    }

    private void pending(UUID plan, String environment) {
        db.update("INSERT INTO user_subscriptions(id,user_id,package_id,status,environment,amount_paid) VALUES (?,?,?,'PENDING',?,149000)", UUID.randomUUID(), owner, plan, environment);
    }

    private List<Map<String, Object>> subscriptions() {
        return db.queryForList("SELECT * FROM user_subscriptions ORDER BY id");
    }

    private void upgrade() throws Exception {
        String sql = migration("V40__Durable_Checkout_Dispatch_And_Reconciliation.sql");
        tx.executeWithoutResult(transaction -> db.execute(sql));
    }

    private String migration(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/" + name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
