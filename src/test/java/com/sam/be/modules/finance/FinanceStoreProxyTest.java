package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sam.be.modules.payment.service.CheckoutMonitor;
import com.sam.be.modules.payment.service.PaymentReconciliationService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Reproduces repository and transaction proxies without opening a database connection. */
class FinanceStoreProxyTest {
    @Test
    void monitorCanUseTheRepositoryThroughSpringProxies() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var store = context.getBean(FinanceStore.class);
            var monitor = context.getBean(CheckoutMonitor.class);
            var jdbc = context.getBean(JdbcTemplate.class);
            assertTrue(AopUtils.isCglibProxy(store));
            assertTrue(AopUtils.isCglibProxy(monitor));

            assertDoesNotThrow(monitor::tick);

            verify(jdbc).update(startsWith("UPDATE payment_checkouts SET status='EXPIRED'"));
            verify(jdbc, times(3)).queryForObject(anyString(), eq(Long.class));
            verify(context.getBean(PlatformTransactionManager.class)).commit(any());
        }
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean
        static PersistenceExceptionTranslationPostProcessor repositoryProxies() {
            var postProcessor = new PersistenceExceptionTranslationPostProcessor();
            postProcessor.setProxyTargetClass(true);
            return postProcessor;
        }

        @Bean
        JdbcTemplate jdbcTemplate() {
            var jdbc = mock(JdbcTemplate.class);
            when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
            return jdbc;
        }

        @Bean(name = "financeClock")
        Clock financeClock() {
            return Clock.systemUTC();
        }

        @Bean
        FinanceStore financeStore(JdbcTemplate jdbc, Clock clock) {
            return new FinanceStore(jdbc, clock);
        }

        @Bean
        PaymentReconciliationService reconciliation() {
            return mock(PaymentReconciliationService.class);
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        CheckoutMonitor monitor(
                FinanceStore store, PaymentReconciliationService cases, MeterRegistry registry) {
            return new CheckoutMonitor(store, cases, registry);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            var transactions = mock(PlatformTransactionManager.class);
            when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
            return transactions;
        }
    }
}
