package com.sam.be.modules.job.service;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.response.PageRequests;
import com.sam.be.common.response.PageResponse;
import com.sam.be.infrastructure.cache.service.CacheInvalidationService;
import com.sam.be.modules.chat.entity.ChatRoom;
import com.sam.be.modules.chat.repository.ChatRoomRepository;
import com.sam.be.modules.chat.service.ChatService;
import com.sam.be.modules.job.dto.response.JobInvitationResponse;
import com.sam.be.modules.job.dto.response.ClientInvitationStatusResponse;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.entity.JobInvitation;
import com.sam.be.modules.job.repository.JobInvitationRepository;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.job.repository.AiJobRecommendationRepository;
import com.sam.be.modules.proposal.repository.ProposalRepository;
import com.sam.be.modules.contract.repository.ContractRepository;
import com.sam.be.common.constant.enums.RecommendationStatus;
import com.sam.be.common.constant.enums.ProposalStatus;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class JobInvitationService {
    private final JobRepository jobRepository;
    private final JobInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatService chatService;
    private final NotificationService notificationService;
    private final CacheInvalidationService cacheInvalidationService;
    private final AiJobRecommendationRepository recommendationRepository;
    private final ProposalRepository proposalRepository;
    private final ContractRepository contractRepository;
    private final EntityManager entityManager;

    @Transactional
    public JobInvitationResponse invite(UUID clientId, UUID jobId, UUID freelancerId,
            String message, JobInvitation.Source source, UUID sourceId) {
        Job job = lockOpenOwnedJob(jobId, clientId);
        JobInvitation existing = invitationRepository.findByJobIdAndFreelancerId(jobId, freelancerId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == JobInvitation.Status.PENDING
                    && existing.getExpiresAt() != null
                    && !existing.getExpiresAt().isAfter(LocalDateTime.now())) {
                existing.setStatus(JobInvitation.Status.EXPIRED);
                existing.setRespondedAt(LocalDateTime.now());
                syncLegacy(existing, false);
                return toResponse(existing);
            }
            if (existing.getStatus() == JobInvitation.Status.PENDING) return toResponse(existing);
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Freelancer này đã có lời mời hoặc phòng chat cho dự án.");
        }
        User freelancer = userRepository.findById(freelancerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (freelancer.getRole() != com.sam.be.common.constant.enums.UserRole.FREELANCER
                || !Boolean.TRUE.equals(freelancer.getIsActive())) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Tài khoản này không thể nhận lời mời.");
        }
        JobInvitation invitation = invitationRepository.save(JobInvitation.builder()
                .job(job).freelancer(freelancer).source(source).sourceId(sourceId)
                .status(JobInvitation.Status.PENDING).message(message == null ? null : message.trim())
                .expiresAt(LocalDateTime.now().plusDays(7)).build());
        syncLegacySent(invitation);
        if (source == JobInvitation.Source.DIRECT) {
            notifyAfterCommit(() -> notificationService.sendJobInvitationNotification(
                    freelancerId, clientId, job.getClient().getFullName(), jobId, job.getTitle(), invitation.getId(), null, "JOB_INVITE",
                    "Bạn nhận được lời mời tham gia dự án."));
        }
        return toResponse(invitation);
    }

    @Transactional
    public JobInvitationResponse accept(UUID freelancerId, UUID invitationId) {
        Job job = lockInvitationJob(invitationId);
        JobInvitation invitation = requireInvitation(invitationId);
        if (!invitation.getFreelancer().getId().equals(freelancerId)) throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        if (invitation.getStatus() == JobInvitation.Status.ACCEPTED
                || invitation.getStatus() == JobInvitation.Status.SELECTED) return toResponse(invitation);
        requirePendingOpen(invitation, job);
        invitation.setStatus(JobInvitation.Status.ACCEPTED);
        invitation.setRespondedAt(LocalDateTime.now());
        syncLegacy(invitation, true);
        ChatRoom room = chatService.getOrCreateRoom(job, invitation.getFreelancer());
        notifyAfterCommit(() -> notificationService.sendJobInvitationNotification(
                job.getClient().getId(), freelancerId, invitation.getFreelancer().getFullName(), job.getId(), job.getTitle(), invitationId, room.getId(),
                "JOB_INVITE_ACCEPTED", "Freelancer đã đồng ý lời mời của bạn."));
        return toResponse(invitation, room.getId());
    }

    @Transactional
    public JobInvitationResponse acceptAsClient(UUID clientId, UUID invitationId) {
        Job job = lockInvitationJob(invitationId);
        if (!job.getClient().getId().equals(clientId)) throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        JobInvitation invitation = requireInvitation(invitationId);
        requirePendingOpen(invitation, job);
        invitation.setStatus(JobInvitation.Status.ACCEPTED);
        invitation.setRespondedAt(LocalDateTime.now());
        syncLegacy(invitation, true);
        ChatRoom room = chatService.getOrCreateRoom(job, invitation.getFreelancer());
        return toResponse(invitation, room.getId());
    }

    @Transactional
    public JobInvitationResponse decline(UUID freelancerId, UUID invitationId) {
        Job job = lockInvitationJob(invitationId);
        JobInvitation invitation = requireInvitation(invitationId);
        if (!invitation.getFreelancer().getId().equals(freelancerId)) throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        requirePendingOpen(invitation, job);
        invitation.setStatus(JobInvitation.Status.DECLINED);
        invitation.setRespondedAt(LocalDateTime.now());
        syncLegacy(invitation, false);
        notifyAfterCommit(() -> notificationService.sendJobInvitationNotification(
                job.getClient().getId(), freelancerId, invitation.getFreelancer().getFullName(), job.getId(), job.getTitle(), invitationId, null,
                "JOB_INVITE_DECLINED", "Freelancer đã từ chối lời mời."));
        return toResponse(invitation);
    }

    @Transactional
    public JobInvitationResponse withdraw(UUID clientId, UUID invitationId) {
        Job job = lockInvitationJob(invitationId);
        if (!job.getClient().getId().equals(clientId)) throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        JobInvitation invitation = requireInvitation(invitationId);
        requirePendingOpen(invitation, job);
        invitation.setStatus(JobInvitation.Status.WITHDRAWN);
        invitation.setRespondedAt(LocalDateTime.now());
        syncLegacy(invitation, false);
        notifyAfterCommit(() -> notificationService.sendJobInvitationNotification(
                invitation.getFreelancer().getId(), clientId, job.getClient().getFullName(), job.getId(), job.getTitle(), invitationId, null,
                "JOB_INVITE_WITHDRAWN", "Khách hàng đã thu hồi lời mời."));
        return toResponse(invitation);
    }

    @Transactional
    public JobInvitationResponse select(UUID clientId, UUID jobId, UUID invitationId) {
        Job job = lockOpenOwnedJob(jobId, clientId);
        JobInvitation invitation = requireInvitation(invitationId);
        if (!invitation.getJob().getId().equals(jobId)
                || invitation.getStatus() != JobInvitation.Status.ACCEPTED) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Chỉ chọn được freelancer đã đồng ý lời mời cho dự án này.");
        }
        if (contractRepository.findByJobId(jobId).isPresent()) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Dự án đã có hợp đồng.");
        }
        ChatRoom selectedRoom = chatRoomRepository.findByJobIdAndFreelancerId(jobId, invitation.getFreelancer().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.REQUEST_FAILED, "Chưa có phòng chat cho ứng viên này."));
        job.setSelectedFreelancer(invitation.getFreelancer());
        job.setStatus(JobStatus.NEGOTIATING);
        invitation.setStatus(JobInvitation.Status.SELECTED);
        for (JobInvitation other : invitationRepository.findByJobIdAndStatusIn(jobId,
                List.of(JobInvitation.Status.PENDING, JobInvitation.Status.ACCEPTED))) {
            if (!other.getId().equals(invitationId)) {
                other.setStatus(JobInvitation.Status.NOT_SELECTED);
                syncLegacyClosed(other);
                UUID otherFreelancerId = other.getFreelancer().getId();
                notifyAfterCommit(() -> notificationService.sendJobInvitationNotification(
                        otherFreelancerId, clientId, job.getClient().getFullName(), jobId, job.getTitle(), other.getId(), null,
                        "JOB_INVITE_NOT_SELECTED", "Dự án đã chọn freelancer khác."));
            }
        }
        notifyAfterCommit(() -> notificationService.sendJobInvitationNotification(
                invitation.getFreelancer().getId(), clientId, job.getClient().getFullName(), jobId, job.getTitle(), invitationId, selectedRoom.getId(),
                "JOB_INVITE_SELECTED", "Khách hàng đã chọn bạn để trao đổi hợp đồng."));
        cacheInvalidationService.jobChanged(jobId, clientId, invitation.getFreelancer().getId());
        return toResponse(invitation);
    }

    @Transactional(readOnly = true)
    public JobInvitationResponse get(UUID userId, UUID invitationId) {
        JobInvitation invitation = requireInvitation(invitationId);
        if (!invitation.getFreelancer().getId().equals(userId)
                && !invitation.getJob().getClient().getId().equals(userId)) throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        return toResponse(invitation);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobInvitationResponse> getJobPage(UUID clientId, UUID jobId,
            JobInvitation.Status status, int page, int size) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!job.getClient().getId().equals(clientId)) throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        return mapPage(invitationRepository.findJobPage(jobId, status,
                PageRequests.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<JobInvitationResponse> getMine(UUID freelancerId,
            JobInvitation.Status status, int page, int size) {
        return mapPage(invitationRepository.findFreelancerPage(freelancerId, status,
                PageRequests.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
        );
    }

    @Transactional(readOnly = true)
    public List<ClientInvitationStatusResponse> getOpenStatusesForFreelancers(UUID clientId, List<UUID> freelancerIds) {
        if (freelancerIds == null || freelancerIds.isEmpty()) return List.of();
        if (freelancerIds.size() > 20) throw new ApiException(ErrorCode.REQUEST_FAILED, "Tối đa 20 freelancer mỗi lần.");
        return invitationRepository.findOpenStatusesForClientAndFreelancers(clientId, freelancerIds);
    }

    @Transactional(readOnly = true)
    public UUID findId(UUID jobId, UUID freelancerId) {
        return invitationRepository.findByJobIdAndFreelancerId(jobId, freelancerId)
                .map(JobInvitation::getId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public void closeForJob(UUID jobId) {
        for (JobInvitation invitation : invitationRepository.findByJobIdAndStatusIn(jobId,
                List.of(JobInvitation.Status.PENDING, JobInvitation.Status.ACCEPTED))) {
            invitation.setStatus(JobInvitation.Status.NOT_SELECTED);
            syncLegacyClosed(invitation);
        }
    }

    @Transactional
    public void expirePending() {
        LocalDateTime now = LocalDateTime.now();
        for (UUID invitationId : invitationRepository.findIdsByStatusAndExpiresAtBefore(
                JobInvitation.Status.PENDING, now)) {
            UUID jobId = invitationRepository.findJobIdById(invitationId).orElse(null);
            if (jobId == null || jobRepository.findById(jobId).isEmpty()) continue;
            lockJob(jobId);
            JobInvitation invitation = requireInvitation(invitationId);
            if (invitation.getStatus() != JobInvitation.Status.PENDING
                    || invitation.getExpiresAt() == null || invitation.getExpiresAt().isAfter(now)) continue;
            invitation.setStatus(JobInvitation.Status.EXPIRED);
            syncLegacy(invitation, false);
        }
    }

    private void syncLegacy(JobInvitation invitation, boolean accepted) {
        UUID jobId = invitation.getJob().getId();
        UUID freelancerId = invitation.getFreelancer().getId();
        recommendationRepository.findByJobIdAndFreelancerId(jobId, freelancerId).ifPresent(rec -> {
            if (rec.getStatus() == RecommendationStatus.CLIENT_REQUESTED) {
                rec.setStatus(accepted ? RecommendationStatus.ACCEPTED : RecommendationStatus.REJECTED);
                cacheInvalidationService.recommendationChanged(jobId);
            }
        });
        proposalRepository.findByJobIdAndFreelancerId(jobId, freelancerId).ifPresent(proposal -> {
            if (proposal.getStatus() == ProposalStatus.INVITED) {
                proposal.setStatus(accepted ? ProposalStatus.ACCEPTED : ProposalStatus.REJECTED);
                cacheInvalidationService.proposalChanged(jobId, freelancerId);
            }
        });
    }

    private void syncLegacySent(JobInvitation invitation) {
        UUID jobId = invitation.getJob().getId();
        UUID freelancerId = invitation.getFreelancer().getId();
        recommendationRepository.findByJobIdAndFreelancerId(jobId, freelancerId).ifPresent(rec -> {
            if (rec.getStatus() == RecommendationStatus.AUTO_MATCHED
                    || rec.getStatus() == RecommendationStatus.DEV_REQUESTED) {
                rec.setStatus(RecommendationStatus.CLIENT_REQUESTED);
                cacheInvalidationService.recommendationChanged(jobId);
            }
        });
        proposalRepository.findByJobIdAndFreelancerId(jobId, freelancerId).ifPresent(proposal -> {
            if (proposal.getStatus() == ProposalStatus.PENDING) {
                proposal.setStatus(ProposalStatus.INVITED);
                cacheInvalidationService.proposalChanged(jobId, freelancerId);
            }
        });
    }

    private void syncLegacyClosed(JobInvitation invitation) {
        UUID jobId = invitation.getJob().getId();
        UUID freelancerId = invitation.getFreelancer().getId();
        recommendationRepository.findByJobIdAndFreelancerId(jobId, freelancerId).ifPresent(rec -> {
            if (rec.getStatus() == RecommendationStatus.CLIENT_REQUESTED
                    || rec.getStatus() == RecommendationStatus.ACCEPTED) {
                rec.setStatus(RecommendationStatus.REJECTED);
                cacheInvalidationService.recommendationChanged(jobId);
            }
        });
        proposalRepository.findByJobIdAndFreelancerId(jobId, freelancerId).ifPresent(proposal -> {
            if (proposal.getStatus() == ProposalStatus.PENDING
                    || proposal.getStatus() == ProposalStatus.INVITED
                    || proposal.getStatus() == ProposalStatus.ACCEPTED) {
                proposal.setStatus(ProposalStatus.REJECTED);
                cacheInvalidationService.proposalChanged(jobId, freelancerId);
            }
        });
    }

    private Job lockOpenOwnedJob(UUID jobId, UUID clientId) {
        Job job = lockJob(jobId);
        if (!job.getClient().getId().equals(clientId)) throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        if (job.getStatus() != JobStatus.OPEN || job.getSelectedFreelancer() != null
                || (job.getDeadline() != null && job.getDeadline().isBefore(LocalDateTime.now()))) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Dự án đã đóng tuyển.");
        }
        return job;
    }

    private Job lockInvitationJob(UUID invitationId) {
        UUID jobId = invitationRepository.findJobIdById(invitationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return lockJob(jobId);
    }

    private Job lockJob(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        entityManager.refresh(job, LockModeType.PESSIMISTIC_WRITE);
        return job;
    }

    private JobInvitation requireInvitation(UUID invitationId) {
        return invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void requirePendingOpen(JobInvitation invitation, Job job) {
        if (invitation.getStatus() != JobInvitation.Status.PENDING || job.getStatus() != JobStatus.OPEN
                || job.getSelectedFreelancer() != null
                || (job.getDeadline() != null && !job.getDeadline().isAfter(LocalDateTime.now()))) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Lời mời không còn hiệu lực.");
        }
        if (invitation.getExpiresAt() != null && !invitation.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Lời mời đã hết hạn.");
        }
    }

    private JobInvitationResponse toResponse(JobInvitation invitation) {
        UUID roomId = chatRoomRepository.findByJobIdAndFreelancerId(
                invitation.getJob().getId(), invitation.getFreelancer().getId()).map(ChatRoom::getId).orElse(null);
        return toResponse(invitation, roomId);
    }

    private PageResponse<JobInvitationResponse> mapPage(Page<JobInvitation> page) {
        List<JobInvitation> items = page.getContent();
        if (items.isEmpty()) return PageResponse.from(page.map(i -> toResponse(i, null)));
        List<UUID> jobIds = items.stream().map(i -> i.getJob().getId()).distinct().toList();
        List<UUID> freelancerIds = items.stream().map(i -> i.getFreelancer().getId()).distinct().toList();
        Map<InvitationPair, UUID> roomIds = chatRoomRepository.findInvitationRooms(jobIds, freelancerIds)
                .stream().collect(Collectors.toMap(
                        room -> new InvitationPair(room.getJob().getId(), room.getFreelancer().getId()),
                        ChatRoom::getId));
        return PageResponse.from(page.map(i -> toResponse(i,
                roomIds.get(new InvitationPair(i.getJob().getId(), i.getFreelancer().getId())))));
    }

    private record InvitationPair(UUID jobId, UUID freelancerId) {}

    private JobInvitationResponse toResponse(JobInvitation invitation, UUID roomId) {
        Job job = invitation.getJob();
        User freelancer = invitation.getFreelancer();
        return new JobInvitationResponse(invitation.getId(), job.getId(), job.getTitle(), job.getStatus(),
                freelancer.getId(), freelancer.getFullName(), invitation.getSource(), invitation.getStatus(),
                invitation.getMessage(), roomId, invitation.getExpiresAt(), invitation.getCreatedAt(),
                invitation.getRespondedAt());
    }

    private void notifyAfterCommit(Runnable notification) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { notification.run(); } catch (RuntimeException ignored) {
                    // Lời mời đã commit; lỗi gửi realtime không được biến API thành thất bại.
                }
            }
        });
    }
}
