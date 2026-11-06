package com.sam.be.modules.job.service;

import com.sam.be.modules.finance.FinanceStore;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobRecommendationWorker {
    private final FinanceStore store;
    private final JobService jobs;
    private final PlatformTransactionManager transactions;

    @Scheduled(fixedDelay = 5000, scheduler = "recommendationScheduler")
    public void tick() {
        AtomicReference<UUID> current = new AtomicReference<>();
        try {
            new TransactionTemplate(transactions)
                    .executeWithoutResult(
                            tx -> {
                                var rows =
                                        store.rows(
                                                "SELECT job_id FROM job_addon_tasks WHERE status IN ('PENDING','RETRY') AND next_attempt_at<=now() ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED");
                                if (rows.isEmpty()) return;
                                UUID id = FinanceStore.id(rows.getFirst(), "jobId");
                                current.set(id);
                                jobs.processPaidRecommendations(id);
                                store.db.update(
                                        "UPDATE job_addon_tasks SET status='DONE' WHERE job_id=?",
                                        id);
                            });
        } catch (Exception ex) {
            if (current.get() != null)
                new TransactionTemplate(transactions)
                        .executeWithoutResult(
                                tx ->
                                        store.db.update(
                                                "UPDATE job_addon_tasks SET status='RETRY',attempt_count=attempt_count+1,next_attempt_at=now()+make_interval(secs => LEAST(3600, 10 * power(2,LEAST(attempt_count,8)))::int) WHERE job_id=?",
                                                current.get()));
            log.warn(
                    "PAID_RECOMMENDATION_RETRY job={} type={}",
                    current.get(),
                    ex.getClass().getSimpleName());
        }
    }
}
