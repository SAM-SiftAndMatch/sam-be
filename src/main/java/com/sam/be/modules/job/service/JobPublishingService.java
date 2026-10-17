package com.sam.be.modules.job.service;

import com.sam.be.modules.job.dto.request.JobCreateRequest;
import com.sam.be.modules.job.dto.response.JobResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobPublishingService {
    private final JobService jobService;
    private final JobAddonService addonService;

    @Transactional
    public JobResponse create(UUID clientId, JobCreateRequest request) {
        JobResponse created = jobService.createJob(clientId, request);
        if (Boolean.TRUE.equals(request.getIsFeatured()) || Boolean.TRUE.equals(request.getIsUrgentHiring())) {
            addonService.select(clientId, created.getId(),
                    Boolean.TRUE.equals(request.getIsFeatured()),
                    Boolean.TRUE.equals(request.getIsUrgentHiring()));
            return jobService.getJobById(created.getId());
        }
        return created;
    }
}
