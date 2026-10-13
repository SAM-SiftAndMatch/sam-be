package com.sam.be.infrastructure.cache.service;

import com.sam.be.infrastructure.cache.keys.RedisKeys;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Invalidate related read models only after the transaction that changed the DB commits. */
@Service
@RequiredArgsConstructor
public class CacheInvalidationService {

    private final RedisCacheService cache;

    public void jobChanged(UUID jobId, UUID clientId, UUID freelancerId) {
        afterCommit(
                () -> {
                    cache.delete(RedisKeys.jobDetail(jobId));
                    cache.delete(RedisKeys.openJobs());
                    cache.deleteByPattern(RedisKeys.pagesPattern(RedisKeys.openJobs()));
                    if (clientId != null) cache.delete(RedisKeys.clientJobs(clientId));
                    if (clientId != null)
                        cache.deleteByPattern(
                                RedisKeys.pagesPattern(RedisKeys.clientJobs(clientId)));
                    if (freelancerId != null)
                        cache.delete(RedisKeys.freelancerProjects(freelancerId));
                    if (freelancerId != null)
                        cache.deleteByPattern(
                                RedisKeys.freelancerProjectsPagesPattern(freelancerId));
                });
    }

    public void jobsChanged(Collection<UUID> jobIds, Collection<UUID> clientIds) {
        afterCommit(
                () -> {
                    new HashSet<>(jobIds)
                            .forEach(jobId -> cache.delete(RedisKeys.jobDetail(jobId)));
                    new HashSet<>(clientIds)
                            .forEach(
                                    clientId -> {
                                        cache.delete(RedisKeys.clientJobs(clientId));
                                        cache.deleteByPattern(
                                                RedisKeys.pagesPattern(
                                                        RedisKeys.clientJobs(clientId)));
                                    });
                    cache.delete(RedisKeys.openJobs());
                    cache.deleteByPattern(RedisKeys.pagesPattern(RedisKeys.openJobs()));
                });
    }

    public void recommendationChanged(UUID jobId) {
        afterCommit(() -> cache.delete(RedisKeys.jobRecommendations(jobId)));
    }

    public void profileChanged(
            UUID freelancerId, Collection<UUID> recommendationJobs, Collection<UUID> proposalJobs) {
        afterCommit(
                () -> {
                    cache.delete(RedisKeys.freelancerProfile(freelancerId));
                    recommendationJobs.forEach(
                            jobId -> cache.delete(RedisKeys.jobRecommendations(jobId)));
                    proposalJobs.forEach(jobId -> cache.delete(RedisKeys.proposalsByJob(jobId)));
                    proposalJobs.forEach(
                            jobId ->
                                    cache.deleteByPattern(
                                            RedisKeys.pagesPattern(
                                                    RedisKeys.proposalsByJob(jobId))));
                    cache.delete(RedisKeys.proposalsByFreelancer(freelancerId));
                    cache.deleteByPattern(
                            RedisKeys.pagesPattern(RedisKeys.proposalsByFreelancer(freelancerId)));
                });
    }

    public void clientProfileChanged(UUID clientId) {
        afterCommit(() -> cache.delete(RedisKeys.clientProfile(clientId)));
    }

    public void skillCatalogChanged() {
        afterCommit(() -> cache.deleteByPattern(RedisKeys.skillPattern()));
    }

    public void subscriptionChanged(UUID userId) {
        afterCommit(
                () -> {
                    cache.delete(RedisKeys.userSubscriptions(userId));
                    cache.deleteByPattern(
                            RedisKeys.pagesPattern(RedisKeys.userSubscriptions(userId)));
                });
    }

    public void paymentsChanged(UUID contractId, UUID freelancerId) {
        afterCommit(
                () -> {
                    cache.delete(RedisKeys.contractPayments(contractId));
                    cache.deleteByPattern(
                            RedisKeys.pagesPattern(RedisKeys.contractPayments(contractId)));
                    cache.delete(RedisKeys.freelancerEscrowSummary(freelancerId));
                });
    }

    public void completedContractChanged(UUID jobId) {
        afterCommit(() -> cache.delete(RedisKeys.completedContractByJob(jobId)));
    }

    public void proposalChanged(UUID jobId, UUID freelancerId) {
        afterCommit(
                () -> {
                    cache.delete(RedisKeys.proposalsByJob(jobId));
                    cache.delete(RedisKeys.proposalsByFreelancer(freelancerId));
                    cache.deleteByPattern(RedisKeys.pagesPattern(RedisKeys.proposalsByJob(jobId)));
                    cache.deleteByPattern(
                            RedisKeys.pagesPattern(RedisKeys.proposalsByFreelancer(freelancerId)));
                });
    }

    public void timelineChanged(UUID jobId) {
        afterCommit(
                () -> {
                    cache.delete(RedisKeys.workflowTimeline(jobId));
                    cache.delete(RedisKeys.workflowTimelineSummary(jobId));
                });
    }

    public void deliveriesChanged(UUID jobId) {
        afterCommit(
                () -> {
                    cache.delete(RedisKeys.workflowDeliveries(jobId));
                    cache.deleteByPattern(
                            RedisKeys.pagesPattern(RedisKeys.workflowDeliveries(jobId)));
                });
    }

    public void qaPlanChanged(UUID deliveryId) {
        afterCommit(() -> cache.delete(RedisKeys.workflowQaPlan(deliveryId)));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }
}
