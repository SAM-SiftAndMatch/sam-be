package com.sam.be.modules.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.modules.finance.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.*;

@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class CheckoutRecoveryPostgresTest {
    DriverManagerDataSource source;
    JdbcTemplate db;
    TransactionTemplate tx;
    FinanceStore store;
    Pay2sClient provider;
    CheckoutService service;
    CheckoutCreationWorker worker;
    Pay2sIpnService ipn;
    PaymentReconciliationService cases;
    String schema;
    UUID owner = UUID.randomUUID(), object = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("FINANCE_TEST_DATABASE_URL"),
                user = System.getenv().getOrDefault("FINANCE_TEST_DATABASE_USER", "postgres"),
                password = System.getenv().getOrDefault("FINANCE_TEST_DATABASE_PASSWORD", "");
        schema = "checkout_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(url, user, password);
                var st = c.createStatement()) {
            st.execute("CREATE SCHEMA " + schema);
        }
        source = new DriverManagerDataSource(url, user, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setConnectionProperties(properties);
        db = new JdbcTemplate(source);
        var tm = new DataSourceTransactionManager(source);
        tx = new TransactionTemplate(tm);
        db.execute(
                "CREATE TABLE users(id UUID PRIMARY KEY); CREATE TABLE jobs(id UUID PRIMARY KEY); CREATE TABLE payments(id UUID PRIMARY KEY,provider TEXT,environment TEXT,payment_gateway_id TEXT,contract_id UUID,payment_type TEXT,maintenance_order_id UUID,status TEXT); CREATE TABLE user_subscriptions(id UUID PRIMARY KEY,user_id UUID,package_id UUID,status TEXT)");
        db.execute(
                "CREATE FUNCTION finance_immutable() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'immutable'; END $$");
        db.execute(
                "CREATE TABLE finance_journals(id UUID PRIMARY KEY,event_key TEXT UNIQUE,contract_id UUID,owner_id UUID,description TEXT,sandbox BOOLEAN,created_at TIMESTAMPTZ); CREATE TABLE finance_entries(id UUID PRIMARY KEY,journal_id UUID,account TEXT,debit NUMERIC,credit NUMERIC)");
        for (String migration :
                List.of(
                        "V39__Unified_Pay2s_Checkouts.sql",
                        "V40__Durable_Checkout_Dispatch_And_Reconciliation.sql",
                        "V41__Allow_Confirmed_Expired_Checkout_Cancellation.sql")) {
            try (var input = getClass().getResourceAsStream("/db/migration/" + migration)) {
                assertNotNull(input);
                db.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        db.update("INSERT INTO users VALUES (?)", owner);
        store = new FinanceStore(db, Clock.systemUTC());
        provider = mock(Pay2sClient.class);
        when(provider.enabled()).thenReturn(true);
        when(provider.environment()).thenReturn("SANDBOX");
        when(provider.merchantScope()).thenReturn("merchant");
        when(provider.verify(any())).thenReturn(true);
        var returns = mock(PaymentReturnPolicy.class);
        when(returns.resolve(any())).thenReturn("https://sam.example/payment/return");
        service = new CheckoutService(store, provider, returns);
        cases = new PaymentReconciliationService(store);
        worker = new CheckoutCreationWorker(store, provider, tm, cases);
        ipn =
                new Pay2sIpnService(
                        provider, store, mock(PaymentService.class), tm, cases, new ObjectMapper());
    }

    @AfterEach
    void cleanup() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        if (db != null) db.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    CheckoutService.Checkout intent() {
        return tx.execute(
                t ->
                        service.create(
                                "SUBSCRIPTION",
                                object,
                                owner,
                                new BigDecimal("149000"),
                                "https://sam.example/payment/return"));
    }

    com.fasterxml.jackson.databind.node.ObjectNode payload(UUID id) {
        return new ObjectMapper()
                .createObjectNode()
                .put("orderId", id.toString().replace("-", ""))
                .put("resultCode", 0)
                .put("amount", 149000)
                .put("transId", "PAY2S_INVOICE")
                .put("orderInfo", "SAMGOITEST01");
    }

    @Test
    void businessIntentAndTaskCommitBeforeExternalCall() {
        var order = intent();
        assertEquals("CREATING", order.paymentStatus());
        when(provider.create(anyString(), anyLong(), anyString(), anyString()))
                .thenAnswer(
                        a -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            assertEquals(
                                    1,
                                    db.queryForObject(
                                            "SELECT count(*) FROM payment_creation_tasks",
                                            Integer.class));
                            return new Pay2sClient.Checkout(
                                    "https://sandbox-payment.pay2s.vn/pay/test",
                                    true,
                                    "P2S123456789ABC");
                        });
        worker.tick();
        assertEquals("PENDING", service.get(owner, order.orderId()).paymentStatus());
        assertEquals("P2S123456789ABC", service.get(owner, order.orderId()).transferContent());
    }

    @Test
    void crashAfterProviderCreatedNeverBlindlyCreatesAgain() {
        var order = intent();
        when(provider.create(anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new AssertionError("simulated process death after provider accepted"));
        assertThrows(AssertionError.class, () -> worker.tick());
        db.update(
                "UPDATE payment_creation_tasks SET locked_until=clock_timestamp()-INTERVAL '1 second'");
        worker.tick();
        worker.tick();
        assertEquals("UNKNOWN", service.get(owner, order.orderId()).paymentStatus());
        verify(provider, times(1)).create(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void ipnBeforeCheckoutResponseCannotBeDowngraded() {
        var order = intent();
        when(provider.create(anyString(), anyLong(), anyString(), anyString()))
                .thenAnswer(
                        a -> {
                            ipn.accept(payload(order.orderId()));
                            return new Pay2sClient.Checkout(
                                    "https://sandbox-payment.pay2s.vn/pay/test", true);
                        });
        worker.tick();
        assertEquals("PAID", service.get(owner, order.orderId()).paymentStatus());
        assertEquals(
                1,
                db.queryForObject("SELECT count(*) FROM payment_checkout_receipts", Integer.class));
    }

    @Test
    void simultaneousCallbacksAndChangedTimestampOnlyRecordOneInvoice() throws Exception {
        var order = intent();
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var first =
                    pool.submit(
                            () -> {
                                gate.await();
                                return ipn.accept(payload(order.orderId()).put("responseTime", 1));
                            });
            var second =
                    pool.submit(
                            () -> {
                                gate.await();
                                return ipn.accept(payload(order.orderId()).put("responseTime", 2));
                            });
            gate.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS).get("success"));
            assertTrue(second.get(10, TimeUnit.SECONDS).get("success"));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(
                1,
                db.queryForObject("SELECT count(*) FROM payment_checkout_receipts", Integer.class));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM finance_journals", Integer.class));
        ipn.accept(payload(order.orderId()).put("amount", 149001));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM finance_journals", Integer.class));
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT count(*) FROM payment_reconciliation_cases WHERE reason='INVOICE_RECEIPT_CONFLICT'",
                        Integer.class));
    }

    @Test
    void concurrentBusinessRequestsStillHaveOneDurableCheckout() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            Callable<UUID> buy =
                    () -> {
                        gate.await();
                        return intent().orderId();
                    };
            var a = pool.submit(buy);
            var b = pool.submit(buy);
            gate.countDown();
            assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(
                1, db.queryForObject("SELECT count(*) FROM payment_creation_tasks", Integer.class));
    }

    @Test
    void databaseRejectsPaidAndAppliedRegressions() {
        var order = intent();
        ipn.accept(payload(order.orderId()));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () ->
                        db.update(
                                "UPDATE payment_checkouts SET status='PENDING' WHERE id=?",
                                order.orderId()));
        db.update(
                "UPDATE payment_checkouts SET fulfillment_status='APPLIED' WHERE id=?",
                order.orderId());
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () ->
                        db.update(
                                "UPDATE payment_checkouts SET fulfillment_status='RETRY' WHERE id=?",
                                order.orderId()));
    }

    @Test
    void oldInvoiceAfterApprovedReplacementIsReviewableWithoutUniqueViolation() {
        var old = intent();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        db.update("INSERT INTO users VALUES (?),(?)", first, second);
        db.update("UPDATE payment_checkouts SET status='UNKNOWN' WHERE id=?", old.orderId());
        tx.executeWithoutResult(
                t -> cases.open("closed-case", old.orderId(), null, "CREATE_UNKNOWN"));
        UUID caseId =
                db.queryForObject(
                        "SELECT id FROM payment_reconciliation_cases WHERE case_key='closed-case'",
                        UUID.class);
        tx.executeWithoutResult(
                t ->
                        cases.action(
                                first,
                                caseId,
                                "PROPOSE_PROVIDER_CLOSED",
                                "verified provider record"));
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () ->
                        tx.executeWithoutResult(
                                t ->
                                        cases.action(
                                                first,
                                                caseId,
                                                "APPROVE_PROVIDER_CLOSED",
                                                "same operator")));
        tx.executeWithoutResult(
                t ->
                        cases.action(
                                second,
                                caseId,
                                "APPROVE_PROVIDER_CLOSED",
                                "second operator checked statement"));
        var replacement = intent();
        assertNotEquals(old.orderId(), replacement.orderId());
        assertTrue(ipn.accept(payload(old.orderId())).get("success"));
        assertEquals(
                "REVIEW",
                db.queryForObject(
                        "SELECT disposition FROM payment_checkout_receipts WHERE checkout_id=?",
                        String.class,
                        old.orderId()));
        assertEquals("CREATING", service.get(owner, replacement.orderId()).paymentStatus());
    }
    @Test
    void lateAllocationMovesLiabilityOnceAndRequiresTwoOperators(){
        var order=intent();db.update("UPDATE payment_checkouts SET status='EXPIRED' WHERE id=?",order.orderId());
        ipn.accept(payload(order.orderId()));
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();db.update("INSERT INTO users VALUES (?),(?)",first,second);
        UUID caseId=db.queryForObject("SELECT id FROM payment_reconciliation_cases WHERE checkout_id=?",UUID.class,order.orderId());
        tx.executeWithoutResult(t->cases.action(first,caseId,"PROPOSE_LATE_ALLOCATION","verified source transfer"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx.executeWithoutResult(t->cases.action(first,caseId,"APPROVE_LATE_ALLOCATION","same admin")));
        tx.executeWithoutResult(t->cases.action(second,caseId,"APPROVE_LATE_ALLOCATION","verified amount and business eligibility"));
        assertEquals("PAID",service.get(owner,order.orderId()).paymentStatus());
        assertEquals(0,db.queryForObject("SELECT COALESCE(sum(credit-debit),0) FROM finance_entries WHERE account=?",BigDecimal.class,"sandbox:receipt-review:"+order.orderId()).signum());
        assertEquals(0,db.queryForObject("SELECT sum(debit-credit) FROM finance_entries",BigDecimal.class).signum());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx.executeWithoutResult(t->cases.action(second,caseId,"APPROVE_LATE_ALLOCATION","repeat")));
        assertEquals(2,db.queryForObject("SELECT count(*) FROM finance_journals",Integer.class));
    }

    private UUID expiredCheckout() {
        UUID id = UUID.randomUUID();
        String memo = Pay2sClient.transferMemo(id.toString().replace("-", ""), "SUBSCRIPTION");
        db.update("INSERT INTO payment_checkouts(id,purpose,object_id,owner_id,amount,environment,status,transfer_content,expires_at,support_code,merchant_scope) VALUES (?,'SUBSCRIPTION',?,?,149000,'SANDBOX','EXPIRED',?,clock_timestamp()-INTERVAL '1 minute',?,'merchant')",
                id, object, owner, memo, memo);
        db.update("INSERT INTO payment_creation_tasks(checkout_id,return_url,state) VALUES (?,'https://sam.example/payment/return','DONE')", id);
        return id;
    }

    @Test
    void parallelProviderClosuresAuditOnceAndPurchasesShareOneReplacement() throws Exception {
        UUID old = expiredCheckout();
        var retries = new CheckoutRetryService(store, provider, service, cases, tx.getTransactionManager());
        var together = new CountDownLatch(2);
        when(provider.cancel(any())).thenAnswer(call -> {
            together.countDown();
            assertTrue(together.await(10, TimeUnit.SECONDS));
            return new Pay2sClient.Cancellation("CANCEL_CONFIRMED");
        });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> retries.prepare(owner, old));
            var second = pool.submit(() -> retries.prepare(owner, old));
            assertEquals("CANCELLED", first.get(15, TimeUnit.SECONDS).paymentStatus());
            assertEquals("CANCELLED", second.get(15, TimeUnit.SECONDS).paymentStatus());
            assertEquals(1, db.queryForObject("SELECT count(*) FROM payment_reconciliation_events WHERE action='PROVIDER_CLOSED_FOR_RETRY'", Integer.class));
            var one = pool.submit(this::intent);
            var two = pool.submit(this::intent);
            UUID replacement = one.get(15, TimeUnit.SECONDS).orderId();
            assertEquals(replacement, two.get(15, TimeUnit.SECONDS).orderId());
            assertNotEquals(old, replacement);
            assertEquals(2, db.queryForObject("SELECT count(*) FROM payment_checkouts", Integer.class));
            assertEquals(1, db.queryForObject("SELECT count(*) FROM payment_creation_tasks WHERE state='READY'", Integer.class));
            verify(provider, never()).create(anyString(), anyLong(), anyString(), anyString());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void sameClientKeyResumesReplacementAfterProviderAcknowledgesClosure() {
        UUID old = expiredCheckout();
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Idempotency-Key", UUID.randomUUID().toString());
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        tx.executeWithoutResult(t -> {
            assertNull(service.replay(owner, "SUBSCRIPTION", "package"));
            assertEquals(old, intent().orderId());
        });
        when(provider.cancel(any())).thenReturn(new Pay2sClient.Cancellation("CANCEL_CONFIRMED"));
        var retries = new CheckoutRetryService(store, provider, service, cases, tx.getTransactionManager());
        retries.prepare(owner, old);
        var next = tx.execute(t -> {
            assertNull(service.replay(owner, "SUBSCRIPTION", "package"));
            return intent();
        });
        assertNotEquals(old, next.orderId());
        assertEquals(next.orderId(), tx.execute(t -> service.replay(owner, "SUBSCRIPTION", "package")).orderId());
        assertEquals(1, db.queryForObject("SELECT count(*) FROM payment_idempotency_keys", Integer.class));
        assertEquals(old, db.queryForObject("SELECT checkout_id FROM payment_idempotency_keys", UUID.class));
    }

    @Test
    void secondWorkerDoesNotResendWhenFirstWorkerOutlivesLease() throws Exception {
        var order=intent();var started=new CountDownLatch(1);var finish=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        when(provider.create(anyString(),anyLong(),anyString(),anyString())).thenAnswer(a->{started.countDown();assertTrue(finish.await(15,TimeUnit.SECONDS));return new Pay2sClient.Checkout("https://sandbox-payment.pay2s.vn/pay/test",true);});
        try {
            var running=pool.submit(()->worker.tick());assertTrue(started.await(15,TimeUnit.SECONDS));
            db.update("UPDATE payment_creation_tasks SET locked_until=clock_timestamp()-INTERVAL '1 second'");
            worker.tick();assertEquals("UNKNOWN",service.get(owner,order.orderId()).paymentStatus());
            finish.countDown();running.get(15,TimeUnit.SECONDS);
            assertEquals("PENDING",service.get(owner,order.orderId()).paymentStatus());
            verify(provider,times(1)).create(anyString(),anyLong(),anyString(),anyString());
        } finally {finish.countDown();pool.shutdownNow();}
    }

}
