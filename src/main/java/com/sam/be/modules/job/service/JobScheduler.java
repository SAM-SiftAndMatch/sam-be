package com.sam.be.modules.job.service;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.infrastructure.cache.service.CacheInvalidationService;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.repository.JobRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@ConditionalOnProperty(name = "jobs.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class JobScheduler {

    private final JobRepository jobRepository;
    private final CacheInvalidationService cacheInvalidationService;

    /**
     * Chạy mỗi phút (60,000ms) để kiểm tra các Job đã quá hạn chót (deadline < now) mà vẫn đang ở
     * trạng thái OPEN. Sau đó chuyển tất cả sang CANCELLED.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cancelExpiredJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<Job> expiredJobs = jobRepository.findByStatusAndDeadlineBefore(JobStatus.OPEN, now);

        if (!expiredJobs.isEmpty()) {
            log.info("Found {} expired jobs. Cancelling...", expiredJobs.size());
            expiredJobs.forEach(job -> job.setStatus(JobStatus.CANCELLED));
            jobRepository.saveAll(expiredJobs);
            cacheInvalidationService.jobsChanged(
                    expiredJobs.stream().map(Job::getId).toList(),
                    expiredJobs.stream().map(job -> job.getClient().getId()).toList());
            log.info("Successfully cancelled {} expired jobs.", expiredJobs.size());
        }
    }
}
