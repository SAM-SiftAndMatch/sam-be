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
                "CREATE TABLE users(id UUID PRIMARY KEY); CREATE TABLE jobs(id UUID PRIMARY KEY,title TEXT); CREATE TABLE contracts(id UUID PRIMARY KEY); CREATE TABLE project_settlements(id UUID PRIMARY KEY,gross_amount NUMERIC(38,2) NOT NULL DEFAULT 100000); CREATE TABLE payments(id UUID PRIMARY KEY,payment_gateway_id TEXT,status TEXT,contract_id UUID,payment_type TEXT)");
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
        db.execute(
                "CREATE TABLE user_subscriptions(id UUID PRIMARY KEY,user_id UUID,package_id UUID,status TEXT)");
        try (var stream =
                getClass().getResourceAsStream("/db/migration/V39__Unified_Pay2s_Checkouts.sql")) {
            assertNotNull(stream);
            db.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        for (String migration :
                java.util.List.of(
                        "/db/migration/V40__Durable_Checkout_Dispatch_And_Reconciliation.sql",
                        "/db/migration/V41__Allow_Confirmed_Expired_Checkout_Cancellation.sql",
                        "/db/migration/V52__Early_Withdrawal_Requests.sql")) {
            try (var stream = getClass().getResourceAsStream(migration)) {
                assertNotNull(stream);
                db.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
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
        return payable(false);
    }

    private UUID payable(boolean pending) {
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
                                store().time(now.minus(Duration.ofDays(pending ? 1 : 10))),
                                store().time(now.plus(Duration.ofDays(pending ? 6 : -3)))));
        return id;
    }

    private UUID verifiedBank() {
        UUID bank = UUID.randomUUID();
        var cipher = BankDataCipherTest.cipher();
        db.update(
                "INSERT INTO finance_bank_accounts(id,owner_id,bank_code,details_encrypted,last_four,fingerprint,status,created_at) VALUES (?,?,'VCB',?,'6789',?,'VERIFIED',now())",
                bank,
                owner,
                cipher.encrypt(
                        "{\"accountNumber\":\"00123456789\",\"accountHolder\":\"TEST USER\"}",
                        "bank:" + owner + ":" + bank),
                UUID.randomUUID().toString());
        return bank;
    }

    private WithdrawalService earlyService() {
        var store = store();
        var cipher = BankDataCipherTest.cipher();
        return new WithdrawalService(
                store,
                cipher,
                new BankAccountService(
                        store, cipher, new com.fasterxml.jackson.databind.ObjectMapper()),
                new WithdrawalImageService(store, cipher),
                org.mockito.Mockito.mock(WithdrawalNotifications.class),
                true,
                true,
                new BigDecimal("50000"),
                new BigDecimal("1000000"));
    }

    @Test
    void earlyRequestStoresPrivateEvidenceAndDoesNotChangeWarrantyOrExposeDeposit() {
        UUID payable = payable(true), bank = verifiedBank(), key = UUID.randomUUID();
        var service = earlyService();
        var request =
                tx.execute(
                        s ->
                                service.createEarly(
                                        owner,
                                        bank,
                                        key,
                                        new BigDecimal("75000"),
                                        payable,
                                        "Cần tiền gấp",
                                        List.of(WithdrawalImageServiceTest.png())));
        UUID withdrawal = id(request, "id");
        assertEquals("EARLY", request.get("requestType"));
        assertEquals(
                new BigDecimal("75000.00"),
                db.queryForObject(
                        "SELECT reserved FROM finance_payables WHERE id=?",
                        BigDecimal.class,
                        payable));
        assertEquals(
                store().time(now.plus(Duration.ofDays(6))),
                db.queryForObject(
                        "SELECT available_at FROM finance_payables WHERE id=?",
                        java.time.OffsetDateTime.class,
                        payable));
        String encrypted =
                db.queryForObject(
                        "SELECT content_encrypted FROM finance_withdrawal_images WHERE withdrawal_id=?",
                        String.class,
                        withdrawal);
        assertTrue(encrypted.startsWith("gcm1."));
        var retry =
                tx.execute(
                        s ->
                                service.createEarly(
                                        owner,
                                        bank,
                                        key,
                                        new BigDecimal("75000"),
                                        payable,
                                        "Cần tiền gấp",
                                        List.of(WithdrawalImageServiceTest.png())));
        assertEquals(withdrawal, id(retry, "id"));
        assertEquals(
                1,
                db.queryForObject("SELECT COUNT(*) FROM finance_withdrawal_images", Integer.class));
        assertThrows(
                Exception.class,
                () ->
                        tx.executeWithoutResult(
                                s ->
                                        service.createEarly(
                                                owner,
                                                bank,
                                                key,
                                                new BigDecimal("75000"),
                                                payable,
                                                "Lý do khác",
                                                List.of(WithdrawalImageServiceTest.png()))));
        assertThrows(
                Exception.class,
                () ->
                        tx.executeWithoutResult(
                                s ->
                                        service.create(
                                                owner,
                                                bank,
                                                UUID.randomUUID(),
                                                new BigDecimal("50000"))));
        tx.executeWithoutResult(s -> service.cancel(owner, withdrawal));
        assertEquals(
                new BigDecimal("0.00"),
                db.queryForObject(
                        "SELECT reserved FROM finance_payables WHERE id=?",
                        BigDecimal.class,
                        payable));
        assertThrows(
                Exception.class,
                () ->
                        tx.executeWithoutResult(
                                s ->
                                        service.createEarly(
                                                owner,
                                                bank,
                                                UUID.randomUUID(),
                                                new BigDecimal("95000"),
                                                payable,
                                                "Cần tiền gấp",
                                                List.of(WithdrawalImageServiceTest.png()))));
    }

    @Test
    void earlyPayoutRequiresApprovalAndSeparateOperatorAndStillRespectsWarrantyTickets() {
        UUID payable = payable(true),
                bank = verifiedBank(),
                approver = UUID.randomUUID(),
                operator = UUID.randomUUID();
        db.update("INSERT INTO users VALUES (?),(?)", approver, operator);
        var service = earlyService();
        var request =
                tx.execute(
                        s ->
                                service.createEarly(
                                        owner,
                                        bank,
                                        UUID.randomUUID(),
                                        new BigDecimal("75000"),
                                        payable,
                                        "Cần tiền gấp",
                                        List.of(WithdrawalImageServiceTest.png())));
        UUID withdrawal = id(request, "id");
        assertThrows(
                Exception.class,
                () -> tx.executeWithoutResult(s -> service.startManual(operator, withdrawal)));
        tx.executeWithoutResult(
                s -> service.approve(approver, withdrawal, "Đã kiểm tra bằng chứng"));
        assertThrows(
                Exception.class,
                () -> tx.executeWithoutResult(s -> service.startManual(approver, withdrawal)));
        UUID ticket = UUID.randomUUID();
        db.update(
                "INSERT INTO maintenance_tickets(id,payable_id,creator_id,request_key,kind,title,description,status,created_at,response_due_at,resolution_due_at) VALUES (?,?,?,?,'WARRANTY','Bug','Bug','OPEN',?,?,?)",
                ticket,
                payable,
                client,
                UUID.randomUUID(),
                store().time(now),
                store().time(now.plus(Duration.ofDays(1))),
                store().time(now.plus(Duration.ofDays(2))));
        assertThrows(
                Exception.class,
                () -> tx.executeWithoutResult(s -> service.startManual(operator, withdrawal)));
        db.update(
                "UPDATE maintenance_tickets SET status='RESOLVED',closed_at=? WHERE id=?",
                store().time(now),
                ticket);
        tx.executeWithoutResult(s -> service.startManual(operator, withdrawal));
        tx.executeWithoutResult(
                s ->
                        service.finishManual(
                                operator,
                                withdrawal,
                                "EARLY001",
                                "private-proof",
                                "Đã đối chiếu ngân hàng"));
        tx.executeWithoutResult(
                s ->
                        service.finishManual(
                                operator, withdrawal, "EARLY001", "private-proof", "Gửi lại"));
        assertEquals(
                "PAID",
                db.queryForObject(
                        "SELECT status FROM finance_withdrawals WHERE id=?",
                        String.class,
                        withdrawal));
        assertEquals(
                new BigDecimal("75000.00"),
                db.queryForObject(
                        "SELECT paid FROM finance_payables WHERE id=?", BigDecimal.class, payable));
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT COUNT(*) FROM finance_journals WHERE event_key=?",
                        Integer.class,
                        "withdrawal-paid:" + withdrawal));
    }

    @Test
    void concurrentEarlyRequestsCannotReserveTheSameProjectTwice() throws Exception {
        UUID payable = payable(true), bank = verifiedBank();
        var service = earlyService();
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt =
                    () -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        try {
                            tx.executeWithoutResult(
                                    s ->
                                            service.createEarly(
                                                    owner,
                                                    bank,
                                                    UUID.randomUUID(),
                                                    new BigDecimal("50000"),
                                                    payable,
                                                    "Cần tiền gấp",
                                                    List.of(WithdrawalImageServiceTest.png())));
                            return true;
                        } catch (org.springframework.web.server.ResponseStatusException ex) {
                            return false;
                        }
                    };
            var a = pool.submit(attempt);
            var b = pool.submit(attempt);
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(
                    new BigDecimal("50000.00"),
                    db.queryForObject(
                            "SELECT reserved FROM finance_payables WHERE id=?",
                            BigDecimal.class,
                            payable));
            assertEquals(
                    1,
                    db.queryForObject("SELECT COUNT(*) FROM finance_withdrawals", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void evidencePersistenceFailureRollsBackBothRequestAndReservation() {
        UUID payable = payable(true), bank = verifiedBank();
        var store = store();
        var cipher = BankDataCipherTest.cipher();
        var brokenImages = new WithdrawalImageService(store, new BankDataCipher("v1", "", ""));
        var service =
                new WithdrawalService(
                        store,
                        cipher,
                        new BankAccountService(
                                store, cipher, new com.fasterxml.jackson.databind.ObjectMapper()),
                        brokenImages,
                        org.mockito.Mockito.mock(WithdrawalNotifications.class),
                        true,
                        true,
                        new BigDecimal("50000"),
                        new BigDecimal("1000000"));
        assertThrows(
                Exception.class,
                () ->
                        tx.executeWithoutResult(
                                s ->
                                        service.createEarly(
                                                owner,
                                                bank,
                                                UUID.randomUUID(),
                                                new BigDecimal("50000"),
                                                payable,
                                                "Việc gấp",
                                                List.of(WithdrawalImageServiceTest.png()))));
        assertEquals(
                0, db.queryForObject("SELECT COUNT(*) FROM finance_withdrawals", Integer.class));
        assertEquals(
                0,
                db.queryForObject("SELECT COUNT(*) FROM finance_withdrawal_images", Integer.class));
        assertEquals(
                new BigDecimal("0.00"),
                db.queryForObject(
                        "SELECT reserved FROM finance_payables WHERE id=?",
                        BigDecimal.class,
                        payable));
    }

    @Test
    void submittedReasonAndEvidenceCannotBeChangedOrExtended() {
        UUID payable = payable(true), bank = verifiedBank();
        var request =
                tx.execute(
                        s ->
                                earlyService()
                                        .createEarly(
                                                owner,
                                                bank,
                                                UUID.randomUUID(),
                                                new BigDecimal("50000"),
                                                payable,
                                                "Việc gấp",
                                                List.of(WithdrawalImageServiceTest.png())));
        UUID withdrawal = id(request, "id");
        assertThrows(
                Exception.class,
                () ->
                        db.update(
                                "UPDATE finance_withdrawals SET request_reason='Changed' WHERE id=?",
                                withdrawal));
        assertThrows(
                Exception.class,
                () ->
                        db.update(
                                "DELETE FROM finance_withdrawal_images WHERE withdrawal_id=?",
                                withdrawal));
        assertThrows(
                Exception.class,
                () ->
                        db.update(
                                "INSERT INTO finance_withdrawal_images SELECT ?,withdrawal_id,file_name,content_type,size_bytes,sha256,content_encrypted,created_at FROM finance_withdrawal_images WHERE withdrawal_id=?",
                                UUID.randomUUID(),
                                withdrawal));
    }

    @Test
    void elapsedWarrantyUsesNormalWithdrawalAndCannotCreateNewEarlyRequest() {
        UUID payable = payable(false), bank = verifiedBank();
        assertThrows(
                Exception.class,
                () ->
                        tx.executeWithoutResult(
                                s ->
                                        earlyService()
                                                .createEarly(
                                                        owner,
                                                        bank,
                                                        UUID.randomUUID(),
                                                        new BigDecimal("50000"),
                                                        payable,
                                                        "Việc gấp",
                                                        List.of(
                                                                WithdrawalImageServiceTest
                                                                        .png()))));
        assertEquals(
                0, db.queryForObject("SELECT COUNT(*) FROM finance_withdrawals", Integer.class));
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
                        org.mockito.Mockito.mock(WithdrawalImageService.class),
                        org.mockito.Mockito.mock(WithdrawalNotifications.class),
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

    private UUID checkout() {
        UUID id = UUID.randomUUID();
        // IPN processing checks expiry against PostgreSQL's clock, not the fixed finance clock.
        db.update(
                "INSERT INTO payment_checkouts(id,purpose,object_id,owner_id,amount,environment,status,transfer_content,expires_at) VALUES (?,'SUBSCRIPTION',?,?,149000,'SANDBOX','PENDING',?,clock_timestamp()+INTERVAL '15 minutes')",
                id,
                UUID.randomUUID(),
                owner,
                "SAMGOI" + id.toString().replace("-", "").substring(0, 16));
        return id;
    }

    @Test
    void unifiedCheckoutConcurrentCallbacksCommitOneReceiptAndOneFulfillment() throws Exception {
        UUID id = checkout();
        var provider = org.mockito.Mockito.mock(Pay2sClient.class);
        org.mockito.Mockito.when(provider.verify(org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        org.mockito.Mockito.when(provider.environment()).thenReturn("SANDBOX");
        org.mockito.Mockito.when(provider.merchantScope()).thenReturn("merchant");
        var ipn =
                new com.sam.be.modules.payment.service.Pay2sIpnService(
                        provider,
                        store(),
                        org.mockito.Mockito.mock(
                                com.sam.be.modules.payment.service.PaymentService.class),
                        new DataSourceTransactionManager(source),
                        new com.sam.be.modules.payment.service.PaymentReconciliationService(
                                store()),
                        new com.fasterxml.jackson.databind.ObjectMapper());
        var payload =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .createObjectNode()
                        .put("orderId", id.toString().replace("-", ""))
                        .put("amount", 149000)
                        .put("transId", "PAY2S_CONCURRENT")
                        .put("resultCode", 0);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            var a =
                    pool.submit(
                            () -> {
                                start.await();
                                return tx.execute(t -> ipn.accept(payload));
                            });
            var b =
                    pool.submit(
                            () -> {
                                start.await();
                                return tx.execute(t -> ipn.accept(payload));
                            });
            start.countDown();
            assertTrue(a.get(15, TimeUnit.SECONDS).get("success"));
            assertTrue(b.get(15, TimeUnit.SECONDS).get("success"));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT count(*) FROM payment_checkout_receipts WHERE checkout_id=?",
                        Integer.class,
                        id));
        assertEquals(
                "ACCEPTED",
                db.queryForObject(
                        "SELECT disposition FROM payment_checkout_receipts WHERE checkout_id=?",
                        String.class,
                        id));
        assertEquals(
                "PAID",
                db.queryForObject(
                        "SELECT status FROM payment_checkouts WHERE id=?", String.class, id));
        assertEquals(
                "PENDING",
                db.queryForObject(
                        "SELECT fulfillment_status FROM payment_checkouts WHERE id=?",
                        String.class,
                        id));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM finance_journals", Integer.class));
    }

    @Test
    void checkoutIdentityAndReceiptsCannotBeRewrittenAndPaidCannotRegress() {
        UUID id = checkout();
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> db.update("UPDATE payment_checkouts SET amount=1 WHERE id=?", id));
        db.update("UPDATE payment_checkouts SET status='PAID' WHERE id=?", id);
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> db.update("UPDATE payment_checkouts SET status='CANCELLED' WHERE id=?", id));
        db.update(
                "INSERT INTO payment_checkout_receipts(id,checkout_id,environment,provider_reference,amount,disposition) VALUES (?,?,'SANDBOX','IMMUTABLE',149000,'ACCEPTED')",
                UUID.randomUUID(),
                id);
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> db.update("DELETE FROM payment_checkout_receipts WHERE checkout_id=?", id));
    }

    @Test
    void fulfillmentBackoffAndSkipLockedSqlExecuteOnPostgres() {
        UUID id = checkout();
        db.update(
                "UPDATE payment_checkouts SET status='PAID',fulfillment_status='PENDING' WHERE id=?",
                id);
        tx.executeWithoutResult(
                t -> {
                    assertEquals(
                            1,
                            db.queryForList(
                                            "SELECT id FROM payment_checkouts WHERE status='PAID' AND fulfillment_status IN ('PENDING','RETRY') AND next_attempt_at<=now() ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED")
                                    .size());
                    db.update(
                            "UPDATE payment_checkouts SET fulfillment_status='RETRY',attempt_count=attempt_count+1,next_attempt_at=now()+make_interval(secs => LEAST(3600, 10 * power(2,LEAST(attempt_count,8)))::int) WHERE id=?",
                            id);
                });
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT attempt_count FROM payment_checkouts WHERE id=?",
                        Integer.class,
                        id));
    }
}
