package com.sam.be.modules.job.service;

import com.sam.be.modules.job.dto.request.JobCreateRequest;
import com.sam.be.modules.job.dto.response.JobResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobPublishingService {
    private final JobService jobService;
    private final JobAddonService addonService;
    private final JobSrsPdfService jobSrsPdfService;

    @Transactional
    public JobResponse create(UUID clientId, JobCreateRequest request) {
        JobResponse created = jobService.createJob(clientId, request);
        JobResponse response = created;
        if (Boolean.TRUE.equals(request.getIsFeatured()) || Boolean.TRUE.equals(request.getIsUrgentHiring())) {
            addonService.select(clientId, created.getId(),
                    Boolean.TRUE.equals(request.getIsFeatured()),
                    Boolean.TRUE.equals(request.getIsUrgentHiring()));
            response = jobService.getJobById(created.getId());
        }
        if (created.getSrsContent() != null && !created.getSrsContent().isBlank()) {
            UUID jobId = created.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        // Separate transaction: an enqueue failure must not undo the published job.
                        jobSrsPdfService.enqueue(jobId);
                    } catch (Exception ex) {
                        log.warn("Could not enqueue SRS PDF for job {}; recovery worker will retry", jobId, ex);
                    }
                }
            });
        }
        return response;
    }
}
