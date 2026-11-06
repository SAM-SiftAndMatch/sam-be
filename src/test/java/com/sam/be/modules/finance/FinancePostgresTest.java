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
                "CREATE TABLE users(id UUID PRIMARY KEY); CREATE TABLE jobs(id UUID PRIMARY KEY,title TEXT); CREATE TABLE contracts(id UUID PRIMARY KEY); CREATE TABLE project_settlements(id UUID PRIMARY KEY,gross_amount NUMERIC(38,2) NOT NULL DEFAULT 100000); CREATE TABLE payments(id UUID PRIMARY KEY,payment_gateway_id TEXT,status TEXT)");
        try (var stream =
                getClass()
                        .getResourceAsStream(
                                "/db/migration/V37__Finance_Maintenance_And_Encrypted_Bank_Accounts.sql")) {
            assertNotNull(stream);
            db.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (var stream =
                getClass()
                        .getResourceAsStream(
                                "/db/migration/V38__Freelancer_Income_And_Bank_Account_Revisions.sql")) {
            assertNotNull(stream);
            db.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        db.update("INSERT INTO users VALUES (?),(?)", owner, client);
        db.update("INSERT INTO jobs VALUES (?,'Finance test')", job);
        db.update("INSERT INTO contracts VALUES (?)", contract);
        db.update("INSERT INTO project_settlements(id) VALUES (?)", settlement);
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

    @Test
    void incomeExcludesDepositsAndDoesNotDecreaseAfterBankPayout() {
        UUID payable = payable();
        var income = new FinanceIncomeService(store());
        var before = income.summary(owner, 2026);
        assertEquals(new BigDecimal("90000.00"), before.get("totalNetIncome"));
        assertEquals(new BigDecimal("100000.00"), before.get("totalGrossIncome"));
        assertEquals(new BigDecimal("10000.00"), before.get("totalPlatformFees"));
        UUID bank = UUID.randomUUID(), withdrawal = UUID.randomUUID();
        db.update(
                "INSERT INTO finance_bank_accounts(id,owner_id,bank_code,details_encrypted,last_four,fingerprint,status,created_at) VALUES (?,?,'VCB','cipher','1234','fingerprint','VERIFIED',now())",
                bank,
                owner);
        tx.executeWithoutResult(
                status -> {
                    db.update(
                            "INSERT INTO finance_withdrawals(id,owner_id,request_key,bank_account_id,beneficiary_encrypted,bank_code,last_four,amount,status,created_at,updated_at) VALUES (?,?,?,?,'cipher','VCB','1234',100000,'PAID',now(),now())",
                            withdrawal,
                            owner,
                            UUID.randomUUID(),
                            bank);
                    db.update(
                            "INSERT INTO finance_withdrawal_allocations VALUES (?,?,100000)",
                            withdrawal,
                            payable);
                    db.update("UPDATE finance_payables SET paid=100000 WHERE id=?", payable);
                });
        assertEquals(
                before.get("totalNetIncome"), income.summary(owner, 2026).get("totalNetIncome"));
        assertEquals(1L, income.entries(owner, 2026, 0).get("totalElements"));
        assertEquals(0L, income.summary(client, 2026).get("completedCount"));
    }

    @Test
    void updatingSameAccountHolderPreservesCiphertextAndResetsVerification() {
        var store = store();
        var cipher = BankDataCipherTest.cipher();
        var banks =
                new BankAccountService(
                        store, cipher, new com.fasterxml.jackson.databind.ObjectMapper());
        var old = tx.execute(status -> banks.add(owner, "VCB", "00123456789", "USER A"));
        UUID oldId = id(old, "id");
        db.update("UPDATE finance_bank_accounts SET status='VERIFIED' WHERE id=?", oldId);
        String original =
                db.queryForObject(
                        "SELECT details_encrypted FROM finance_bank_accounts WHERE id=?",
                        String.class,
                        oldId);
        var replacement =
                tx.execute(status -> banks.replace(owner, oldId, "VCB", "00123456789", "USER B"));
        assertNotEquals(oldId, id(replacement, "id"));
        assertEquals("PENDING", replacement.get("status"));
        assertEquals(
                original,
                db.queryForObject(
                        "SELECT details_encrypted FROM finance_bank_accounts WHERE id=?",
                        String.class,
                        oldId));
        assertEquals(
                "DISABLED",
                db.queryForObject(
                        "SELECT status FROM finance_bank_accounts WHERE id=?",
                        String.class,
                        oldId));
        assertEquals("PENDING", banks.setupStatus(owner).get("status"));
        assertEquals(
                replacement.get("id"),
                tx.execute(status -> banks.replace(owner, oldId, "VCB", "00123456789", "USER B"))
                        .get("id"));
    }

    @Test
    void sandboxRevenueStaysSeparateAndDecemberUtcCanBelongToJanuaryInVietnam() {
        payable();
        UUID sandboxContract = UUID.randomUUID(), sandboxSettlement = UUID.randomUUID();
        db.update("INSERT INTO contracts VALUES (?)", sandboxContract);
        db.update(
                "INSERT INTO project_settlements(id,gross_amount) VALUES (?,500000)",
                sandboxSettlement);
        Instant accepted = Instant.parse("2026-12-31T18:00:00Z");
        db.update(
                "INSERT INTO finance_payables(id,settlement_id,contract_id,job_id,owner_id,client_id,amount,earnings,deposit_refund,sandbox,accepted_at,available_at) VALUES (?,?,?,?,?,?,450000,450000,0,true,?,?)",
                UUID.randomUUID(),
                sandboxSettlement,
                sandboxContract,
                job,
                owner,
                client,
                store().time(accepted),
                store().time(accepted.plus(FinancePolicy.WARRANTY)));
        var income = new FinanceIncomeService(store());
        var nextYear = income.summary(owner, 2027);
        assertEquals(new BigDecimal("90000.00"), nextYear.get("totalNetIncome"));
        assertEquals(0, ((BigDecimal) nextYear.get("yearNetIncome")).signum());
        assertEquals(new BigDecimal("450000.00"), nextYear.get("yearSandboxNetIncome"));
        var january = (Map<?, ?>) ((List<?>) nextYear.get("months")).getFirst();
        assertEquals(new BigDecimal("450000.00"), january.get("sandboxNetIncome"));
        assertEquals(0L, income.entries(owner, 2027, 0).get("totalElements"));
    }
}
