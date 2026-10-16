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
    private final JobInvitationService invitationService;

    /**
     * Chạy mỗi phút (60,000ms) để kiểm tra các Job đã quá hạn chót (deadline < now) mà vẫn đang ở
     * trạng thái OPEN. Sau đó chuyển tất cả sang CANCELLED.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cancelExpiredJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<java.util.UUID> expiredJobs = jobRepository.findIdsByStatusAndDeadlineBefore(JobStatus.OPEN, now);

        if (!expiredJobs.isEmpty()) {
            log.info("Found {} expired jobs. Cancelling...", expiredJobs.size());
            List<Job> cancelled = expiredJobs.stream()
                    .map(id -> jobRepository.findByIdForWorkflowUpdate(id).orElse(null))
                    .filter(job -> job != null && job.getStatus() == JobStatus.OPEN
                            && job.getDeadline() != null && job.getDeadline().isBefore(now))
                    .toList();
            cancelled.forEach(job -> {
                job.setStatus(JobStatus.CANCELLED);
                invitationService.closeForJob(job.getId());
            });
            jobRepository.saveAll(cancelled);
            cacheInvalidationService.jobsChanged(
                    cancelled.stream().map(Job::getId).toList(),
                    cancelled.stream().map(job -> job.getClient().getId()).toList());
            log.info("Successfully cancelled {} expired jobs.", cancelled.size());
        }
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expireInvitations() {
        invitationService.expirePending();
    }
}
