package com.sam.be.modules.job.service;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.response.PageResponse;
import com.sam.be.modules.job.dto.request.JobCreateRequest;
import com.sam.be.modules.job.dto.response.AcceptInvitationResponse;
import com.sam.be.modules.job.dto.response.AiRecommendationResponse;
import com.sam.be.modules.job.dto.response.JobResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface JobService {
    JobResponse createJob(UUID clientId, JobCreateRequest request);

    /** Only the server-side add-on entitlement flow may call this method. */
    void activateAddons(UUID jobId, boolean featured, boolean urgent);

    JobResponse cancelJob(UUID clientId, UUID jobId);

    JobResponse getJobById(UUID jobId);

    List<JobResponse> getJobsByClientId(UUID clientId);

    List<JobResponse> getProjectsByFreelancerId(UUID requesterId, UUID freelancerId);

    /** Lấy tất cả OPEN jobs, sắp xếp mới nhất trước (public endpoint) */
    List<JobResponse> getAllOpenJobs();

    PageResponse<JobResponse> getOpenJobsPage(
            int page,
            int size,
            String search,
            Integer skillId,
            String skillNames,
            String customSkill,
            boolean negotiable,
            BigDecimal minBudget,
            BigDecimal maxBudget,
            String sort);

    PageResponse<JobResponse> getClientJobsPage(
            UUID clientId, int page, int size, JobStatus status, String search);

    PageResponse<JobResponse> getFreelancerProjectsPage(
            UUID requesterId,
            UUID freelancerId,
            int page,
            int size,
            JobStatus status,
            String group,
            String search);

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
