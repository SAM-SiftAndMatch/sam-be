package com.sam.be.modules.subscription.service;

import com.sam.be.infrastructure.cache.service.CacheInvalidationService;
import com.sam.be.modules.finance.FinanceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SubscriptionExpiryWorker {
    private final FinanceStore store;
    private final CacheInvalidationService cache;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void expire() {
        store
                .rows(
                        "WITH expired AS (SELECT id FROM user_subscriptions WHERE status='ACTIVE' AND end_date<=? AND environment IN ('LIVE','SANDBOX') LIMIT 100 FOR UPDATE SKIP LOCKED) UPDATE user_subscriptions s SET status='EXPIRED' FROM expired e WHERE s.id=e.id RETURNING s.user_id",
                        java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()))
                .stream()
                .map(row -> FinanceStore.id(row, "userId"))
                .distinct()
                .forEach(cache::subscriptionChanged);
    }
}
