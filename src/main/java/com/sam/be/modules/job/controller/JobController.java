package com.sam.be.modules.job.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.job.dto.request.JobCreateRequest;
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
}