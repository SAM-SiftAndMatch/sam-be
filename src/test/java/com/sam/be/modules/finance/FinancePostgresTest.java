package com.sam.be.modules.finance;

import static com.sam.be.modules.finance.FinanceStore.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs in an isolated disposable schema, never against application tables. */
@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class FinancePostgresTest {
    private DriverManagerDataSource source;
    private JdbcTemplate db;
    private TransactionTemplate tx;
    private String schema;
    private final UUID owner = UUID.randomUUID(),
            client = UUID.randomUUID(),
            job = UUID.randomUUID(),
            contract = UUID.randomUUID(),
            settlement = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-20T00:00:00Z");

    @BeforeEach
    void prepare() throws Exception {
        String url = System.getenv("FINANCE_TEST_DATABASE_URL");
        String user = System.getenv().getOrDefault("FINANCE_TEST_DATABASE_USER", "postgres");
        String password = System.getenv().getOrDefault("FINANCE_TEST_DATABASE_PASSWORD", "");
        schema = "finance_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = DriverManager.getConnection(url, user, password);
                Statement statement = c.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        source = new DriverManagerDataSource(url, user, password);
        Properties properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setConnectionProperties(properties);
        db = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        db.execute(
                "CREATE TABLE users(id UUID PRIMARY KEY); CREATE TABLE jobs(id UUID PRIMARY KEY,title TEXT); CREATE TABLE contracts(id UUID PRIMARY KEY); CREATE TABLE project_settlements(id UUID PRIMARY KEY); CREATE TABLE payments(id UUID PRIMARY KEY,payment_gateway_id TEXT,status TEXT)");
        try (var stream =
                getClass()
                        .getResourceAsStream(
                                "/db/migration/V37__Finance_Maintenance_And_Encrypted_Bank_Accounts.sql")) {
            assertNotNull(stream);
            db.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        db.update("INSERT INTO users VALUES (?),(?)", owner, client);
        db.update("INSERT INTO jobs VALUES (?,'Finance test')", job);
        db.update("INSERT INTO contracts VALUES (?)", contract);
        db.update("INSERT INTO project_settlements VALUES (?)", settlement);
    }

    @AfterEach
    void cleanup() {
        if (source != null && schema != null) db.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    private FinanceStore store() {
        return new FinanceStore(db, Clock.fixed(now, ZoneOffset.UTC));
    }

    private UUID payable() {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(
                s ->
                        db.update(
                                "INSERT INTO finance_payables(id,settlement_id,contract_id,job_id,owner_id,client_id,amount,earnings,deposit_refund,sandbox,accepted_at,available_at) VALUES (?,?,?,?,?,?,100000,90000,10000,false,?,?)",
                                id,
                                settlement,
                                contract,
                                job,
                                owner,
                                client,
                                store().time(now.minus(Duration.ofDays(10))),
                                store().time(now.minus(Duration.ofDays(3)))));
        return id;
    }

    @Test
    void balancedJournalCommitsAndCannotBeEditedOrExtended() {
        var store = store();
        tx.executeWithoutResult(
                s ->
                        store.journal(
                                "test",
                                contract,
                                owner,
                                "test",
                                false,
                                debit("cash", BigDecimal.TEN),
                                credit("escrow", BigDecimal.TEN)));
        UUID journal =
                db.queryForObject(
                        "SELECT id FROM finance_journals WHERE event_key='test'", UUID.class);
        assertThrows(
                Exception.class,
                () ->
                        db.update(
                                "UPDATE finance_entries SET debit=11 WHERE journal_id=? AND debit=10",
                                journal));
        assertThrows(
                Exception.class,
                () ->
                        db.update(
                                "INSERT INTO finance_entries VALUES (?,?,?,1,0)",
                                UUID.randomUUID(),
                                journal,
                                "cash"));
    }

    @Test
    void unbalancedJournalIsRejectedAtCommit() {
        assertThrows(
                Exception.class,
                () ->
                        tx.executeWithoutResult(
                                s -> {
                                    UUID id = UUID.randomUUID();
                                    db.update(
                                            "INSERT INTO finance_journals(id,event_key,description,sandbox,created_at) VALUES (?,'bad','test',false,now())",
                                            id);
                                    db.update(
                                            "INSERT INTO finance_entries VALUES (?,?,?,10,0)",
                                            UUID.randomUUID(),
                                            id,
                                            "cash");
                                }));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM finance_journals", Integer.class));
    }

    @Test
    void acceptedTimeAndAmountsCannotBeChanged() {
        UUID id = payable();
        assertThrows(
                Exception.class,
                () ->
                        db.update(
                                "UPDATE finance_payables SET accepted_at=accepted_at-interval '1 day',available_at=available_at-interval '1 day' WHERE id=?",
                                id));
        assertThrows(
                Exception.class,
                () ->
                        db.update(
                                "UPDATE finance_payables SET amount=200000,earnings=190000 WHERE id=?",
                                id));
    }

    @Test
    void reservationMustMatchAllocations() {
        UUID id = payable();
        assertThrows(
                Exception.class,
                () ->
                        tx.executeWithoutResult(
                                s ->
                                        db.update(
                                                "UPDATE finance_payables SET reserved=1 WHERE id=?",
                                                id)));
    }

    @Test
    void concurrentWithdrawalsCannotSpendSameMoneyTwice() throws Exception {
        UUID payable = payable(), bank = UUID.randomUUID();
        var cipher = BankDataCipherTest.cipher();
        var store = store();
        String snapshot = "{\"accountNumber\":\"00123456789\",\"accountHolder\":\"TEST USER\"}";
        db.update(
                "INSERT INTO finance_bank_accounts(id,owner_id,bank_code,details_encrypted,last_four,fingerprint,status,created_at) VALUES (?,?,'VCB',?,'6789',?,'VERIFIED',now())",
                bank,
                owner,
                cipher.encrypt(snapshot, "bank:" + owner + ":" + bank),
                cipher.fingerprint(owner + ":VCB:00123456789"));
        var service =
                new WithdrawalService(
                        store,
                        cipher,
                        new BankAccountService(
                                store, cipher, new com.fasterxml.jackson.databind.ObjectMapper()),
                        true,
                        false,
                        new BigDecimal("50000"),
                        new BigDecimal("1000000"));
        var barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt =
                    () -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        try {
                            tx.executeWithoutResult(
                                    s ->
                                            service.create(
                                                    owner,
                                                    bank,
                                                    UUID.randomUUID(),
                                                    new BigDecimal("75000")));
                            return true;
                        } catch (org.springframework.web.server.ResponseStatusException ex) {
                            return false;
                        }
                    };
            var a = pool.submit(attempt);
            var b = pool.submit(attempt);
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(
                    new BigDecimal("75000.00"),
                    db.queryForObject(
                            "SELECT reserved FROM finance_payables WHERE id=?",
                            BigDecimal.class,
                            payable));
            assertEquals(
                    1,
                    db.queryForObject("SELECT COUNT(*) FROM finance_withdrawals", Integer.class));
            String encoded =
                    db.queryForObject(
                            "SELECT beneficiary_encrypted FROM finance_withdrawals", String.class);
            assertFalse(encoded.contains("00123456789"));
        } finally {
            pool.shutdownNow();
        }
    }
}
