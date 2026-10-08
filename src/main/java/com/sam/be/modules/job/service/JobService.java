package com.sam.be.modules.job.service;

import com.sam.be.modules.job.dto.request.JobCreateRequest;
import com.sam.be.modules.job.dto.response.AcceptInvitationResponse;
import com.sam.be.modules.job.dto.response.AiRecommendationResponse;
import com.sam.be.modules.job.dto.response.JobResponse;
import java.util.List;
import java.util.UUID;

public interface JobService {
    JobResponse createJob(UUID clientId, JobCreateRequest request);

    JobResponse cancelJob(UUID clientId, UUID jobId);

    JobResponse getJobById(UUID jobId);

    List<JobResponse> getJobsByClientId(UUID clientId);

    /** Lấy tất cả OPEN jobs, sắp xếp mới nhất trước (public endpoint) */
    List<JobResponse> getAllOpenJobs();

    List<AiRecommendationResponse> getJobRecommendations(UUID clientId, UUID jobId);

    AiRecommendationResponse getMyJobRecommendation(UUID freelancerId, UUID jobId);

    void inviteCandidate(UUID clientId, UUID jobId, UUID recommendationId);

    AcceptInvitationResponse acceptJobInvitation(
            UUID freelancerId, UUID jobId, UUID recommendationId);

    void rejectJobInvitation(UUID freelancerId, UUID jobId, UUID recommendationId);

    void devRequestChat(UUID freelancerId, UUID jobId, UUID recommendationId);

    AcceptInvitationResponse clientAcceptDevRequest(
            UUID clientId, UUID jobId, UUID recommendationId);
}
