package com.sam.be.modules.job.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.job.dto.request.JobCreateRequest;
import com.sam.be.modules.job.dto.response.AiRecommendationResponse;
import com.sam.be.modules.job.dto.response.JobResponse;
import com.sam.be.modules.job.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Job Management", description = "APIs for managing jobs by Clients")
public class JobController {

    private final JobService jobService;

    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Create a new job", description = "Client creates a new job post after SRS is generated")
    public ApiResponse<JobResponse> createJob(@Valid @RequestBody JobCreateRequest request) {
        JobResponse response = jobService.createJob(SecurityUtils.getCurrentUserId(), request);
        return ApiResponse.<JobResponse>builder().result(response).build();
    }

    @PatchMapping("/{jobId}/cancel")
    @PreAuthorize("hasRole('CLIENT') and @jobAccessGuard.isOwner(#jobId)")
    @Operation(summary = "Cancel a job", description = "Client cancels an OPEN job. Cannot be updated once published.")
    public ApiResponse<JobResponse> cancelJob(@PathVariable UUID jobId) {
        JobResponse response = jobService.cancelJob(SecurityUtils.getCurrentUserId(), jobId);
        return ApiResponse.<JobResponse>builder().result(response).build();
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get job by ID", description = "Retrieve public job details")
    public ApiResponse<JobResponse> getJobById(@PathVariable UUID jobId) {
        JobResponse response = jobService.getJobById(jobId);
        return ApiResponse.<JobResponse>builder().result(response).build();
    }

    @GetMapping("/client/me")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Get jobs of current client", description = "Retrieve all jobs posted by the logged-in client")
    public ApiResponse<List<JobResponse>> getMyJobs() {
        List<JobResponse> response = jobService.getJobsByClientId(SecurityUtils.getCurrentUserId());
        return ApiResponse.<List<JobResponse>>builder().result(response).build();
    }

    @GetMapping("/{jobId}/recommendations")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Get AI recommendations", description = "Client reviews top 5 AI matched candidates with skill experiences")
    public ApiResponse<List<AiRecommendationResponse>> getRecommendations(@PathVariable UUID jobId) {
        List<AiRecommendationResponse> response = jobService.getJobRecommendations(SecurityUtils.getCurrentUserId(), jobId);
        return ApiResponse.<List<AiRecommendationResponse>>builder().result(response).build();
    }

    @PostMapping("/{jobId}/recommendations/{recId}/invite")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Invite AI recommended candidate", description = "Client selects a candidate and triggers 1-touch claim notification")
    public ApiResponse<Void> inviteCandidate(@PathVariable UUID jobId, @PathVariable UUID recId) {
        jobService.inviteCandidate(SecurityUtils.getCurrentUserId(), jobId, recId);
        return ApiResponse.<Void>builder().build();
    }

    @PostMapping("/{jobId}/recommendations/{recId}/accept")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Accept AI job invitation", description = "Freelancer accepts the 1-touch claim invitation from client")
    public ApiResponse<Void> acceptInvitation(@PathVariable UUID jobId, @PathVariable UUID recId) {
        jobService.acceptJobInvitation(SecurityUtils.getCurrentUserId(), jobId, recId);
        return ApiResponse.<Void>builder().build();
    }

    @PostMapping("/{jobId}/recommendations/{recId}/reject")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Reject AI job invitation", description = "Freelancer rejects the invitation")
    public ApiResponse<Void> rejectInvitation(@PathVariable UUID jobId, @PathVariable UUID recId) {
        jobService.rejectJobInvitation(SecurityUtils.getCurrentUserId(), jobId, recId);
        return ApiResponse.<Void>builder().build();
    }
}