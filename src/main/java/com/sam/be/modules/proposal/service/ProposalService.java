package com.sam.be.modules.proposal.service;

import com.sam.be.modules.proposal.dto.request.ProposalCreateRequest;
import com.sam.be.modules.proposal.dto.response.AcceptProposalResponse;
import com.sam.be.modules.proposal.dto.response.ProposalResponse;
import java.util.List;
import java.util.UUID;

public interface ProposalService {
    // Freelancer nộp hồ sơ cho job đang mở (kèm PDF + thư chào)
    ProposalResponse submitProposal(UUID freelancerId, ProposalCreateRequest request);

    // Client xem toàn bộ hồ sơ của 1 job mình đăng
    List<ProposalResponse> getProposalsByJob(UUID clientId, UUID jobId);

    // Freelancer xem hồ sơ mình đã nộp (tất cả hoặc theo job)
    List<ProposalResponse> getMyProposals(UUID freelancerId);

    ProposalResponse getMyProposalForJob(UUID freelancerId, UUID jobId);

    // Client mời hợp tác (PENDING -> INVITED), chờ dev đồng ý mới mở chat
    void inviteProposal(UUID clientId, UUID proposalId);

    // Freelancer đồng ý lời mời (INVITED -> ACCEPTED) và mở phòng chat
    AcceptProposalResponse acceptProposal(UUID freelancerId, UUID proposalId);

    // Từ chối: client (PENDING -> REJECTED) hoặc freelancer (INVITED -> REJECTED)
    void rejectProposal(UUID userId, UUID proposalId);
}
