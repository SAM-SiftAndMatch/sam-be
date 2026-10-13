package com.sam.be.infrastructure.cache.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sam.be.infrastructure.cache.keys.RedisKeys;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CacheInvalidationServiceTest {

    private RecordingCache cache;
    private CacheInvalidationService invalidation;

    @BeforeEach
    void setUp() {
        cache = new RecordingCache();
        invalidation = new CacheInvalidationService(cache);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void jobChangeInvalidatesEveryRelatedReadAfterCommit() {
        UUID jobId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID freelancerId = UUID.randomUUID();

        invalidation.jobChanged(jobId, clientId, freelancerId);
        assertFalse(cache.deleted.contains(RedisKeys.jobDetail(jobId)));

        commit();

        assertTrue(cache.deleted.contains(RedisKeys.jobDetail(jobId)));
        assertTrue(cache.deleted.contains(RedisKeys.openJobs()));
        assertTrue(cache.deleted.contains(RedisKeys.pagesPattern(RedisKeys.openJobs())));
        assertTrue(cache.deleted.contains(RedisKeys.clientJobs(clientId)));
        assertTrue(cache.deleted.contains(RedisKeys.pagesPattern(RedisKeys.clientJobs(clientId))));
        assertTrue(cache.deleted.contains(RedisKeys.freelancerProjects(freelancerId)));
        assertTrue(
                cache.deleted.contains(
                        RedisKeys.pagesPattern(RedisKeys.freelancerProjects(freelancerId))));
    }

    @Test
    void rollbackLeavesExistingCacheIntact() {
        UUID contractId = UUID.randomUUID();
        invalidation.paymentsChanged(contractId);

        for (TransactionSynchronization synchronization :
                TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        assertFalse(cache.deleted.contains(RedisKeys.contractPayments(contractId)));
    }

    @Test
    void profileChangeInvalidatesEmbeddedRecommendationAndProposalData() {
        UUID freelancerId = UUID.randomUUID();
        UUID recommendationJobId = UUID.randomUUID();
        UUID proposalJobId = UUID.randomUUID();

        invalidation.profileChanged(
                freelancerId,
                java.util.List.of(recommendationJobId),
                java.util.List.of(proposalJobId));
        commit();

        assertTrue(cache.deleted.contains(RedisKeys.freelancerProfile(freelancerId)));
        assertTrue(cache.deleted.contains(RedisKeys.jobRecommendations(recommendationJobId)));
        assertTrue(cache.deleted.contains(RedisKeys.proposalsByJob(proposalJobId)));
        assertTrue(cache.deleted.contains(RedisKeys.proposalsByFreelancer(freelancerId)));
    }

    private void commit() {
        for (TransactionSynchronization synchronization :
                TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
    }

    private static class RecordingCache implements RedisCacheService {
        private final List<String> deleted = new ArrayList<>();

        @Override
        public <T> Optional<T> get(String key, Class<T> clazz) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<List<T>> getList(String key, Class<T> elementClass) {
            return Optional.empty();
        }

        @Override
        public void set(String key, Object value, Duration ttl) {}

        @Override
        public void delete(String key) {
            deleted.add(key);
        }

        @Override
        public void deleteByPattern(String pattern) {
            deleted.add(pattern);
        }

        @Override
        public boolean hasKey(String key) {
            return false;
        }

        @Override
        public <T> T getOrSet(String key, Duration ttl, Class<T> clazz, Supplier<T> dbSupplier) {
            return dbSupplier.get();
        }

        @Override
        public <T> List<T> getListOrSet(
                String key, Duration ttl, Class<T> elementClass, Supplier<List<T>> dbSupplier) {
            return dbSupplier.get();
        }

        @Override
        public <T> com.sam.be.common.response.PageResponse<T> getPageOrSet(
                String key,
                Duration ttl,
                Class<T> elementClass,
                Supplier<com.sam.be.common.response.PageResponse<T>> dbSupplier) {
            return dbSupplier.get();
        }
    }
}
