package com.sam.be.modules.job.service;

import com.sam.be.modules.job.dto.request.JobCreateRequest;
import com.sam.be.modules.job.dto.response.JobResponse;

import java.util.List;
import java.util.UUID;

public interface JobService {
    JobResponse createJob(UUID clientId, JobCreateRequest request);
    JobResponse cancelJob(UUID clientId, UUID jobId);
    JobResponse getJobById(UUID jobId);
    List<JobResponse> getJobsByClientId(UUID clientId);
}