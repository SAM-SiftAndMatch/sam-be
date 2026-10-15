package com.sam.be.modules.proposal.service.impl;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.constant.enums.ProposalStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.response.PageRequests;
import com.sam.be.common.response.PageResponse;
import com.sam.be.infrastructure.cache.keys.RedisKeys;
import com.sam.be.infrastructure.cache.service.CacheInvalidationService;
import com.sam.be.infrastructure.cache.service.RedisCacheService;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.repository.AiJobRecommendationRepository;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.job.entity.JobInvitation;
import com.sam.be.modules.job.service.JobInvitationService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
    private final JobInvitationService invitationService;
    private final NotificationService notificationService;
    private final RedisCacheService redisCacheService;
    private final CacheInvalidationService cacheInvalidationService;

    @Override
    @Transactional
    public ProposalResponse submitProposal(UUID freelancerId, ProposalCreateRequest request) {
        Job job =
                jobRepository
                        .findByIdForWorkflowUpdate(request.getJobId())
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
        cacheInvalidationService.proposalChanged(job.getId(), freelancerId);

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
        return redisCacheService.getListOrSet(
                RedisKeys.proposalsByJob(jobId),
                Duration.ofMinutes(1),
                ProposalResponse.class,
                () ->
                        proposalRepository.findAllByJobId(jobId).stream()
                                .map(this::mapToResponse)
                                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProposalResponse> getMyProposals(UUID freelancerId) {
        return redisCacheService.getListOrSet(
                RedisKeys.proposalsByFreelancer(freelancerId),
                Duration.ofMinutes(1),
                ProposalResponse.class,
                () ->
                        proposalRepository.findAllByFreelancerId(freelancerId).stream()
                                .map(this::mapToResponse)
                                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProposalResponse> getProposalsByJobPage(
            UUID clientId, UUID jobId, int page, int size, ProposalStatus status) {
        Job job =
                jobRepository
                        .findById(jobId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (job.getClient() == null || !job.getClient().getId().equals(clientId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        var pageable = PageRequests.of(page, size, org.springframework.data.domain.Sort.unsorted());
        Supplier<PageResponse<ProposalResponse>> source =
                () -> mapProposalPage(proposalRepository.findJobPage(jobId, status, pageable));
        if (status == null && page < 2) {
            return redisCacheService.getPageOrSet(
                    RedisKeys.page(RedisKeys.proposalsByJob(jobId), page, size),
                    Duration.ofMinutes(1),
                    ProposalResponse.class,
                    source);
        }
        return source.get();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProposalResponse> getMyProposalsPage(
            UUID freelancerId, int page, int size, ProposalStatus status) {
        var pageable = PageRequests.of(page, size, org.springframework.data.domain.Sort.unsorted());
        Supplier<PageResponse<ProposalResponse>> source =
                () ->
                        mapProposalPage(
                                proposalRepository.findFreelancerPage(
                                        freelancerId, status, pageable));
        if (status == null && page < 2) {
            return redisCacheService.getPageOrSet(
                    RedisKeys.page(RedisKeys.proposalsByFreelancer(freelancerId), page, size),
                    Duration.ofMinutes(1),
                    ProposalResponse.class,
                    source);
        }
        return source.get();
    }

    private PageResponse<ProposalResponse> mapProposalPage(Page<Proposal> page) {
        List<UUID> freelancerIds =
                page.getContent().stream().map(p -> p.getFreelancer().getId()).distinct().toList();
        Map<UUID, String> headlines =
                freelancerIds.isEmpty()
                        ? Map.of()
                        : freelancerProfileRepository.findAllByUserIdIn(freelancerIds).stream()
                                .collect(
                                        Collectors.toMap(
                                                p -> p.getUser().getId(),
                                                p ->
                                                        p.getHeadline() == null
                                                                ? ""
                                                                : p.getHeadline()));
        return new PageResponse<>(
                page.getContent().stream()
                        .map(
                                p ->
                                        mapToResponse(
                                                p,
                                                headlines.getOrDefault(
                                                        p.getFreelancer().getId(), "")))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
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
        invitationService.invite(clientId, proposal.getJob().getId(),
                proposal.getFreelancer().getId(), null, JobInvitation.Source.PROPOSAL, proposalId);
        proposal.setStatus(ProposalStatus.INVITED);
        proposalRepository.save(proposal);
        cacheInvalidationService.proposalChanged(
                proposal.getJob().getId(), proposal.getFreelancer().getId());

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
        var invitation = invitationService.accept(freelancerId,
                invitationService.findId(proposal.getJob().getId(), freelancerId));
        proposal.setStatus(ProposalStatus.ACCEPTED);
        proposalRepository.save(proposal);
        cacheInvalidationService.proposalChanged(
                proposal.getJob().getId(), proposal.getFreelancer().getId());

        notificationService.sendProposalNotification(
                proposal.getJob().getClient().getId(),
                proposal.getJob().getId(),
                proposal.getJob().getTitle(),
                proposal.getId(),
                "PROPOSAL_ACCEPTED",
                proposal.getFreelancer().getFullName() + " đã đồng ý hợp tác, vào chat ngay!");

        return AcceptProposalResponse.builder().roomId(invitation.roomId()).build();
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
            invitationService.decline(userId,
                    invitationService.findId(proposal.getJob().getId(), userId));
            proposal.setStatus(ProposalStatus.REJECTED);
        } else {
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED, "Thao tác không hợp lệ với trạng thái hiện tại.");
        }
        proposalRepository.save(proposal);
        cacheInvalidationService.proposalChanged(
                proposal.getJob().getId(), proposal.getFreelancer().getId());

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
        return mapToResponse(proposal, headline);
    }

    private ProposalResponse mapToResponse(Proposal proposal, String headline) {
        User dev = proposal.getFreelancer();
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
