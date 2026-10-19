package com.sam.be.modules.job.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.response.PageResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.job.dto.request.InviteFreelancerRequest;
import com.sam.be.modules.job.dto.request.SelectFreelancerRequest;
import com.sam.be.modules.job.dto.response.JobInvitationResponse;
import com.sam.be.modules.job.dto.response.ClientInvitationStatusResponse;
import com.sam.be.modules.job.entity.JobInvitation;
import com.sam.be.modules.job.service.JobInvitationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class JobInvitationController {
    private final JobInvitationService service;

    @PostMapping("/api/v1/jobs/{jobId}/invitations")
    @PreAuthorize("hasRole('CLIENT')")
    public ApiResponse<JobInvitationResponse> invite(@PathVariable UUID jobId,
            @Valid @RequestBody InviteFreelancerRequest request) {
        return ApiResponse.<JobInvitationResponse>builder().result(service.invite(
                SecurityUtils.getCurrentUserId(), jobId, request.freelancerId(), request.message(),
                JobInvitation.Source.DIRECT, null)).build();
    }

    @GetMapping("/api/v1/jobs/{jobId}/invitations/page")
    @PreAuthorize("hasRole('CLIENT')")
    public ApiResponse<PageResponse<JobInvitationResponse>> jobPage(@PathVariable UUID jobId,
            @RequestParam(required = false) JobInvitation.Status status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<JobInvitationResponse>>builder().result(service.getJobPage(
                SecurityUtils.getCurrentUserId(), jobId, status, page, size)).build();
    }

    @GetMapping("/api/v1/invitations/client/me/freelancer-statuses")
    @PreAuthorize("hasRole('CLIENT')")
    public ApiResponse<java.util.List<ClientInvitationStatusResponse>> clientFreelancerStatuses(
            @RequestParam java.util.List<UUID> freelancerIds) {
        return ApiResponse.<java.util.List<ClientInvitationStatusResponse>>builder().result(
                service.getOpenStatusesForFreelancers(SecurityUtils.getCurrentUserId(), freelancerIds)).build();
    }

    @PostMapping("/api/v1/jobs/{jobId}/selection")
    @PreAuthorize("hasRole('CLIENT')")
    public ApiResponse<JobInvitationResponse> select(@PathVariable UUID jobId,
            @Valid @RequestBody SelectFreelancerRequest request) {
        return ApiResponse.<JobInvitationResponse>builder().result(service.select(
                SecurityUtils.getCurrentUserId(), jobId, request.invitationId())).build();
    }

    @GetMapping("/api/v1/invitations/me/page")
    @PreAuthorize("hasRole('FREELANCER')")
    public ApiResponse<PageResponse<JobInvitationResponse>> mine(
            @RequestParam(required = false) JobInvitation.Status status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<JobInvitationResponse>>builder().result(service.getMine(
                SecurityUtils.getCurrentUserId(), status, page, size)).build();
    }

    @GetMapping("/api/v1/invitations/{id}")
    public ApiResponse<JobInvitationResponse> get(@PathVariable UUID id) {
        return ApiResponse.<JobInvitationResponse>builder().result(service.get(
                SecurityUtils.getCurrentUserId(), id)).build();
    }

    @PostMapping("/api/v1/invitations/{id}/accept")
    @PreAuthorize("hasRole('FREELANCER')")
    public ApiResponse<JobInvitationResponse> accept(@PathVariable UUID id) {
        return ApiResponse.<JobInvitationResponse>builder().result(service.accept(
                SecurityUtils.getCurrentUserId(), id)).build();
    }

    @PostMapping("/api/v1/invitations/{id}/decline")
    @PreAuthorize("hasRole('FREELANCER')")
    public ApiResponse<JobInvitationResponse> decline(@PathVariable UUID id) {
        return ApiResponse.<JobInvitationResponse>builder().result(service.decline(
                SecurityUtils.getCurrentUserId(), id)).build();
    }

    @PostMapping("/api/v1/invitations/{id}/withdraw")
    @PreAuthorize("hasRole('CLIENT')")
    public ApiResponse<JobInvitationResponse> withdraw(@PathVariable UUID id) {
        return ApiResponse.<JobInvitationResponse>builder().result(service.withdraw(
                SecurityUtils.getCurrentUserId(), id)).build();
    }
}
