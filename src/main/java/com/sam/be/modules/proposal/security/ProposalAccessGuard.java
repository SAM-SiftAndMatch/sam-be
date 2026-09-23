package com.sam.be.modules.proposal.security;

import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.proposal.repository.ProposalRepository;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component("proposalAccessGuard")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ProposalAccessGuard {

    ProposalRepository proposalRepository;

    /** Kiểm tra người dùng có phải là Freelancer đã tạo ra Proposal này hay không. */
    public boolean isAuthor(UUID proposalId) {
        if (proposalId == null) {
            return false;
        }

        if (SecurityUtils.isAdmin()) {
            return true;
        }

        UUID currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (currentUserId == null) {
            return false;
        }

        return proposalRepository
                .findById(proposalId)
                .map(
                        p ->
                                p.getFreelancer() != null
                                        && currentUserId.equals(p.getFreelancer().getId()))
                .orElse(false);
    }

    /**
     * Kiểm tra quyền xem proposal: Phải là tác giả (Freelancer) HOẶC là chủ dự án (Client) HOẶC
     * Admin.
     */
    public boolean canView(UUID proposalId) {
        if (proposalId == null) {
            return false;
        }

        if (SecurityUtils.isAdmin()) {
            return true;
        }

        UUID currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (currentUserId == null) {
            return false;
        }

        return proposalRepository
                .findById(proposalId)
                .map(
                        p -> {
                            boolean isFreelancer =
                                    p.getFreelancer() != null
                                            && currentUserId.equals(p.getFreelancer().getId());
                            boolean isJobClient =
                                    p.getJob() != null
                                            && p.getJob().getClient() != null
                                            && currentUserId.equals(p.getJob().getClient().getId());
                            return isFreelancer || isJobClient;
                        })
                .orElse(false);
    }

    /**
     * Kiểm tra quyền chấp nhận/từ chối proposal: Chỉ có chủ dự án (Client) hoặc Admin mới được
     * duyệt.
     */
    public boolean canAcceptOrReject(UUID proposalId) {
        if (proposalId == null) {
            return false;
        }

        if (SecurityUtils.isAdmin()) {
            return true;
        }

        UUID currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (currentUserId == null) {
            return false;
        }

        return proposalRepository
                .findById(proposalId)
                .map(
                        p ->
                                p.getJob() != null
                                        && p.getJob().getClient() != null
                                        && currentUserId.equals(p.getJob().getClient().getId()))
                .orElse(false);
    }
}
