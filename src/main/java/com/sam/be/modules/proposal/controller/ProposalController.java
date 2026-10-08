package com.sam.be.modules.proposal.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.proposal.dto.request.ProposalCreateRequest;
import com.sam.be.modules.proposal.dto.response.AcceptProposalResponse;
import com.sam.be.modules.proposal.dto.response.ProposalResponse;
import com.sam.be.modules.proposal.service.ProposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/proposals")
@RequiredArgsConstructor
@Tag(name = "Proposal Management", description = "Freelancer proposals for public jobs")
public class ProposalController {

    private final ProposalService proposalService;

    @PostMapping
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(
            summary = "Submit a proposal",
            description =
                    "Freelancer submits a proposal (cover letter + budget + PDF) for an OPEN job")
    public ApiResponse<ProposalResponse> submitProposal(
            @Valid @RequestBody ProposalCreateRequest request) {
        ProposalResponse response =
                proposalService.submitProposal(SecurityUtils.getCurrentUserId(), request);
        return ApiResponse.<ProposalResponse>builder().result(response).build();
    }

    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "List proposals of a job",
            description = "Client views all proposals submitted for their job")
    public ApiResponse<List<ProposalResponse>> getByJob(@PathVariable UUID jobId) {
        List<ProposalResponse> response =
                proposalService.getProposalsByJob(SecurityUtils.getCurrentUserId(), jobId);
        return ApiResponse.<List<ProposalResponse>>builder().result(response).build();
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "List my proposals", description = "Freelancer views own proposals")
    public ApiResponse<List<ProposalResponse>> getMine() {
        List<ProposalResponse> response =
                proposalService.getMyProposals(SecurityUtils.getCurrentUserId());
        return ApiResponse.<List<ProposalResponse>>builder().result(response).build();
    }

    @GetMapping("/me/job/{jobId}")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(
            summary = "Get my proposal for a job",
            description = "Freelancer checks own proposal status (to see client invitation)")
    public ApiResponse<ProposalResponse> getMineForJob(@PathVariable UUID jobId) {
        ProposalResponse response =
                proposalService.getMyProposalForJob(SecurityUtils.getCurrentUserId(), jobId);
        return ApiResponse.<ProposalResponse>builder().result(response).build();
    }

    @PostMapping("/{proposalId}/invite")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Invite a candidate",
            description = "Client invites a PENDING proposal, freelancer accepts to open chat")
    public ApiResponse<Void> inviteProposal(@PathVariable UUID proposalId) {
        proposalService.inviteProposal(SecurityUtils.getCurrentUserId(), proposalId);
        return ApiResponse.<Void>builder().build();
    }

    @PostMapping("/{proposalId}/accept")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(
            summary = "Accept a client invitation",
            description = "Freelancer accepts and opens the chat room")
    public ApiResponse<AcceptProposalResponse> acceptProposal(@PathVariable UUID proposalId) {
        AcceptProposalResponse response =
                proposalService.acceptProposal(SecurityUtils.getCurrentUserId(), proposalId);
        return ApiResponse.<AcceptProposalResponse>builder().result(response).build();
    }

    @PostMapping("/{proposalId}/reject")
    @Operation(
            summary = "Reject a proposal",
            description = "Client rejects a PENDING proposal, freelancer rejects an INVITED one")
    public ApiResponse<Void> rejectProposal(@PathVariable UUID proposalId) {
        proposalService.rejectProposal(SecurityUtils.getCurrentUserId(), proposalId);
        return ApiResponse.<Void>builder().build();
    }
}
