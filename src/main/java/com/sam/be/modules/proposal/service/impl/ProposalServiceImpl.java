package com.sam.be.modules.proposal.service.impl;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.constant.enums.ProposalStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.chat.entity.ChatRoom;
import com.sam.be.modules.chat.service.ChatService;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.repository.AiJobRecommendationRepository;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.proposal.dto.request.ProposalCreateRequest;
import com.sam.be.modules.proposal.dto.response.AcceptProposalResponse;
import com.sam.be.modules.proposal.dto.response.ProposalResponse;
import com.sam.be.modules.proposal.entity.Proposal;
import com.sam.be.modules.proposal.repository.ProposalRepository;
import com.sam.be.modules.proposal.service.ProposalService;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.FreelancerProfileRepository;
import com.sam.be.modules.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProposalServiceImpl implements ProposalService {

    private final ProposalRepository proposalRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final FreelancerProfileRepository freelancerProfileRepository;
    private final AiJobRecommendationRepository aiJobRecommendationRepository;
    private final ChatService chatService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ProposalResponse submitProposal(UUID freelancerId, ProposalCreateRequest request) {
        Job job =
                jobRepository
                        .findById(request.getJobId())
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        // Chỉ nộp khi job còn mở — IN_PROGRESS/CANCELLED... là đã đóng tuyển
        if (job.getStatus() != JobStatus.OPEN) {
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED, "Dự án đã đóng, không nhận hồ sơ nữa.");
        }
        // Job gấp trong 5 phút độc quyền: người ngoài top 5 không được nộp
        assertCanAccess(job, freelancerId);

        if (proposalRepository.findByJobIdAndFreelancerId(job.getId(), freelancerId).isPresent()) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Bạn đã nộp hồ sơ cho dự án này.");
        }
        if (request.getProposedBudget() == null || request.getProposedBudget().signum() <= 0) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Giá đề xuất phải lớn hơn 0.");
        }

        User freelancer =
                userRepository
                        .findById(freelancerId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        Proposal saved =
                proposalRepository.save(
                        Proposal.builder()
                                .job(job)
                                .freelancer(freelancer)
                                .coverLetter(request.getCoverLetter())
                                .proposedBudget(request.getProposedBudget())
                                .estimatedDurationDays(request.getEstimatedDurationDays())
                                .attachmentUrl(request.getAttachmentUrl())
                                .attachmentName(request.getAttachmentName())
                                .status(ProposalStatus.PENDING)
                                .build());

        notificationService.sendProposalNotification(
                job.getClient().getId(),
                job.getId(),
                job.getTitle(),
                saved.getId(),
                "PROPOSAL_RECEIVED",
                freelancer.getFullName() + " vừa nộp hồ sơ cho dự án của bạn.");

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProposalResponse> getProposalsByJob(UUID clientId, UUID jobId) {
        Job job =
                jobRepository
                        .findById(jobId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (job.getClient() == null || !job.getClient().getId().equals(clientId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        return proposalRepository.findAllByJobId(jobId).stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProposalResponse> getMyProposals(UUID freelancerId) {
        return proposalRepository.findAllByFreelancerId(freelancerId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProposalResponse getMyProposalForJob(UUID freelancerId, UUID jobId) {
        Proposal proposal =
                proposalRepository
                        .findByJobIdAndFreelancerId(jobId, freelancerId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return mapToResponse(proposal);
    }

    @Override
    @Transactional
    public void inviteProposal(UUID clientId, UUID proposalId) {
        Proposal proposal = getOwnedProposal(clientId, proposalId);
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Chỉ mời được hồ sơ đang chờ duyệt.");
        }
        proposal.setStatus(ProposalStatus.INVITED);
        proposalRepository.save(proposal);

        notificationService.sendProposalNotification(
                proposal.getFreelancer().getId(),
                proposal.getJob().getId(),
                proposal.getJob().getTitle(),
                proposal.getId(),
                "PROPOSAL_INVITE",
                "Client mời bạn hợp tác, đồng ý để mở phòng chat!");
    }

    @Override
    @Transactional
    public AcceptProposalResponse acceptProposal(UUID freelancerId, UUID proposalId) {
        Proposal proposal =
                proposalRepository
                        .findById(proposalId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (proposal.getFreelancer() == null
                || !proposal.getFreelancer().getId().equals(freelancerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        if (proposal.getStatus() != ProposalStatus.INVITED) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Hồ sơ không ở trạng thái được mời.");
        }
        proposal.setStatus(ProposalStatus.ACCEPTED);
        proposalRepository.save(proposal);

        ChatRoom room = chatService.getOrCreateRoom(proposal.getJob(), proposal.getFreelancer());

        notificationService.sendProposalNotification(
                proposal.getJob().getClient().getId(),
                proposal.getJob().getId(),
                proposal.getJob().getTitle(),
                proposal.getId(),
                "PROPOSAL_ACCEPTED",
                proposal.getFreelancer().getFullName() + " đã đồng ý hợp tác, vào chat ngay!");

        return AcceptProposalResponse.builder().roomId(room.getId()).build();
    }

    @Override
    @Transactional
    public void rejectProposal(UUID userId, UUID proposalId) {
        Proposal proposal =
                proposalRepository
                        .findById(proposalId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        boolean isClient =
                proposal.getJob().getClient() != null
                        && proposal.getJob().getClient().getId().equals(userId);
        boolean isFreelancer =
                proposal.getFreelancer() != null && proposal.getFreelancer().getId().equals(userId);
        if (!isClient && !isFreelancer) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        if (isClient && proposal.getStatus() == ProposalStatus.PENDING) {
            proposal.setStatus(ProposalStatus.REJECTED);
        } else if (isFreelancer && proposal.getStatus() == ProposalStatus.INVITED) {
            proposal.setStatus(ProposalStatus.REJECTED);
        } else {
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED, "Thao tác không hợp lệ với trạng thái hiện tại.");
        }
        proposalRepository.save(proposal);

        UUID otherSide =
                isClient ? proposal.getFreelancer().getId() : proposal.getJob().getClient().getId();
        notificationService.sendProposalNotification(
                otherSide,
                proposal.getJob().getId(),
                proposal.getJob().getTitle(),
                proposal.getId(),
                "PROPOSAL_REJECTED",
                "Một hồ sơ vừa bị từ chối.");
    }

    // Client chỉ thao tác hồ sơ thuộc job mình đăng
    private Proposal getOwnedProposal(UUID clientId, UUID proposalId) {
        Proposal proposal =
                proposalRepository
                        .findById(proposalId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (proposal.getJob().getClient() == null
                || !proposal.getJob().getClient().getId().equals(clientId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        return proposal;
    }

    // Chặn nộp hồ sơ vào job gấp đang trong 5 phút độc quyền (giống chặn xem chi tiết)
    private void assertCanAccess(Job job, UUID freelancerId) {
        if (!Boolean.TRUE.equals(job.getIsUrgentHiring())
                || job.getCreatedAt() == null
                || !job.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(5))) {
            return;
        }
        boolean invited =
                aiJobRecommendationRepository
                        .findByJobIdAndFreelancerId(job.getId(), freelancerId)
                        .isPresent();
        if (!invited) {
            throw new ApiException(
                    ErrorCode.FORBIDDEN_ACTION,
                    "Dự án tuyển gấp đang trong 5 phút độc quyền, bạn chưa được mời nộp hồ sơ.");
        }
    }

    private ProposalResponse mapToResponse(Proposal proposal) {
        User dev = proposal.getFreelancer();
        String headline =
                freelancerProfileRepository
                        .findByUserId(dev.getId())
                        .map(p -> p.getHeadline())
                        .orElse("");
        return ProposalResponse.builder()
                .id(proposal.getId())
                .jobId(proposal.getJob().getId())
                .jobTitle(proposal.getJob().getTitle())
                .freelancerId(dev.getId())
                .freelancerName(dev.getFullName())
                .headline(headline)
                .coverLetter(proposal.getCoverLetter())
                .proposedBudget(proposal.getProposedBudget())
                .estimatedDurationDays(proposal.getEstimatedDurationDays())
                .attachmentUrl(proposal.getAttachmentUrl())
                .attachmentName(proposal.getAttachmentName())
                .status(proposal.getStatus())
                .createdAt(proposal.getCreatedAt())
                .build();
    }
}
