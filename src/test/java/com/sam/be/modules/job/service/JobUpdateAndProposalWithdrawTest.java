package com.sam.be.modules.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.constant.enums.ProposalStatus;
import com.sam.be.common.constant.enums.UserRole;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.infrastructure.cache.service.CacheInvalidationService;
import com.sam.be.modules.chat.repository.ChatRoomRepository;
import com.sam.be.modules.chat.service.ChatService;
import com.sam.be.modules.contract.repository.ContractRepository;
import com.sam.be.modules.job.dto.request.JobUpdateRequest;
import com.sam.be.modules.job.dto.response.JobResponse;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.entity.JobInvitation;
import com.sam.be.modules.job.repository.AiJobRecommendationRepository;
import com.sam.be.modules.job.repository.JobInvitationRepository;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.job.service.impl.JobServiceImpl;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.proposal.dto.request.ProposalCreateRequest;
import com.sam.be.modules.proposal.entity.Proposal;
import com.sam.be.modules.proposal.repository.ProposalRepository;
import com.sam.be.modules.proposal.service.impl.ProposalServiceImpl;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.FreelancerProfileRepository;
import com.sam.be.modules.user.repository.FreelancerSkillRepository;
import com.sam.be.modules.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class JobUpdateAndProposalWithdrawTest {

    @BeforeEach
    void initSync() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSync() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void updateJobUpdatesOpenOwnedJobAndInvalidatesCache() {
        UUID clientId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        User client = User.builder().id(clientId).fullName("Client User").build();
        Job job =
                Job.builder()
                        .id(jobId)
                        .client(client)
                        .title("Old Title")
                        .description("Old Description")
                        .budgetMin(new BigDecimal("3000000"))
                        .budgetMax(new BigDecimal("5000000"))
                        .status(JobStatus.OPEN)
                        .build();

        JobRepository jobRepository = mock(JobRepository.class);
        CacheInvalidationService cacheInvalidationService = mock(CacheInvalidationService.class);
        when(jobRepository.findByIdForWorkflowUpdate(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        JobServiceImpl service =
                new JobServiceImpl(
                        jobRepository,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        cacheInvalidationService,
                        null);

        LocalDateTime nextDeadline = LocalDateTime.now().plusDays(14);
        JobUpdateRequest request =
                JobUpdateRequest.builder()
                        .title("  Updated Title  ")
                        .description("  Updated Description  ")
                        .budgetMin(new BigDecimal("4000000"))
                        .budgetMax(new BigDecimal("7000000"))
                        .estimatedDurationMonths(new BigDecimal("2.0"))
                        .deadline(nextDeadline)
                        .build();

        JobResponse updated = service.updateJob(clientId, jobId, request);

        assertEquals("Updated Title", updated.getTitle());
        assertEquals("Updated Description", updated.getDescription());
        assertEquals(new BigDecimal("4000000"), updated.getBudgetMin());
        assertEquals(new BigDecimal("7000000"), updated.getBudgetMax());
        assertEquals(nextDeadline, updated.getDeadline());
        verify(cacheInvalidationService).jobChanged(jobId, clientId, null);
    }

    @Test
    void updateJobRejectsNonOpenJobOrPastDeadline() {
        UUID clientId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        User client = User.builder().id(clientId).build();
        Job closedJob =
                Job.builder().id(jobId).client(client).status(JobStatus.IN_PROGRESS).build();
        Job openJob = Job.builder().id(jobId).client(client).status(JobStatus.OPEN).build();

        JobRepository jobRepository = mock(JobRepository.class);
        CacheInvalidationService cacheInvalidationService = mock(CacheInvalidationService.class);
        JobServiceImpl service =
                new JobServiceImpl(
                        jobRepository,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        cacheInvalidationService,
                        null);

        JobUpdateRequest valid =
                JobUpdateRequest.builder()
                        .title("New")
                        .description("Desc")
                        .budgetMin(new BigDecimal("3000000"))
                        .budgetMax(new BigDecimal("4000000"))
                        .deadline(LocalDateTime.now().plusDays(5))
                        .build();

        when(jobRepository.findByIdForWorkflowUpdate(jobId)).thenReturn(Optional.of(closedJob));
        ApiException closedEx =
                assertThrows(ApiException.class, () -> service.updateJob(clientId, jobId, valid));
        assertEquals(ErrorCode.REQUEST_FAILED, closedEx.getErrorCode());

        when(jobRepository.findByIdForWorkflowUpdate(jobId)).thenReturn(Optional.of(openJob));
        JobUpdateRequest expired =
                JobUpdateRequest.builder()
                        .title("New")
                        .description("Desc")
                        .budgetMin(new BigDecimal("3000000"))
                        .budgetMax(new BigDecimal("4000000"))
                        .deadline(LocalDateTime.now().minusDays(1))
                        .build();
        ApiException pastEx =
                assertThrows(ApiException.class, () -> service.updateJob(clientId, jobId, expired));
        assertEquals(ErrorCode.VALIDATION_ERROR, pastEx.getErrorCode());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void withdrawProposalDeletesPendingOnOpenJobAndSubmitRejectsExpiredDeadline() {
        UUID freelancerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();

        User freelancer = User.builder().id(freelancerId).fullName("Dev User").build();
        Job openJob =
                Job.builder()
                        .id(jobId)
                        .status(JobStatus.OPEN)
                        .deadline(LocalDateTime.now().minusHours(1))
                        .build();
        Proposal pendingProposal =
                Proposal.builder()
                        .id(proposalId)
                        .job(openJob)
                        .freelancer(freelancer)
                        .status(ProposalStatus.PENDING)
                        .proposedBudget(new BigDecimal("3500000"))
                        .build();

        ProposalRepository proposalRepository = mock(ProposalRepository.class);
        JobRepository jobRepository = mock(JobRepository.class);
        CacheInvalidationService cacheInvalidationService = mock(CacheInvalidationService.class);

        ProposalServiceImpl proposalService =
                new ProposalServiceImpl(
                        proposalRepository,
                        jobRepository,
                        mock(UserRepository.class),
                        mock(FreelancerProfileRepository.class),
                        mock(FreelancerSkillRepository.class),
                        mock(AiJobRecommendationRepository.class),
                        mock(JobInvitationService.class),
                        mock(NotificationService.class),
                        null,
                        cacheInvalidationService);

        when(proposalRepository.findById(proposalId)).thenReturn(Optional.of(pendingProposal));
        when(jobRepository.findByIdForWorkflowUpdate(jobId)).thenReturn(Optional.of(openJob));
        proposalService.withdrawProposal(freelancerId, proposalId);
        verify(proposalRepository).delete(pendingProposal);
        verify(cacheInvalidationService).proposalChanged(jobId, freelancerId);

        ProposalCreateRequest submitReq =
                new ProposalCreateRequest(
                        jobId, "Cover", new BigDecimal("3500000"), 14, null, null);
        ApiException expiredEx =
                assertThrows(
                        ApiException.class,
                        () -> proposalService.submitProposal(freelancerId, submitReq));
        assertEquals(ErrorCode.REQUEST_FAILED, expiredEx.getErrorCode());
    }

    @Test
    void inviteReactivatesWithdrawnOrDeclinedInvitation() {
        UUID clientId = UUID.randomUUID();
        UUID freelancerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        User client = User.builder().id(clientId).fullName("Client").build();
        User freelancer =
                User.builder()
                        .id(freelancerId)
                        .fullName("Dev")
                        .role(UserRole.FREELANCER)
                        .isActive(true)
                        .build();
        Job job =
                Job.builder()
                        .id(jobId)
                        .client(client)
                        .title("Job Title")
                        .status(JobStatus.OPEN)
                        .build();
        JobInvitation withdrawn =
                JobInvitation.builder()
                        .id(UUID.randomUUID())
                        .job(job)
                        .freelancer(freelancer)
                        .source(JobInvitation.Source.DIRECT)
                        .status(JobInvitation.Status.WITHDRAWN)
                        .respondedAt(LocalDateTime.now().minusDays(1))
                        .build();

        JobRepository jobRepository = mock(JobRepository.class);
        JobInvitationRepository invitationRepository = mock(JobInvitationRepository.class);
        AiJobRecommendationRepository recRepository = mock(AiJobRecommendationRepository.class);
        ProposalRepository proposalRepository = mock(ProposalRepository.class);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(invitationRepository.findByJobIdAndFreelancerId(jobId, freelancerId))
                .thenReturn(Optional.of(withdrawn));
        when(invitationRepository.save(any(JobInvitation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(recRepository.findByJobIdAndFreelancerId(jobId, freelancerId))
                .thenReturn(Optional.empty());
        when(proposalRepository.findByJobIdAndFreelancerId(jobId, freelancerId))
                .thenReturn(Optional.empty());

        JobInvitationService service =
                new JobInvitationService(
                        jobRepository,
                        invitationRepository,
                        mock(UserRepository.class),
                        mock(ChatRoomRepository.class),
                        mock(ChatService.class),
                        mock(NotificationService.class),
                        mock(CacheInvalidationService.class),
                        recRepository,
                        proposalRepository,
                        mock(ContractRepository.class),
                        mock(EntityManager.class));

        var response =
                service.invite(
                        clientId,
                        jobId,
                        freelancerId,
                        "  Mời lại bạn nhé ",
                        JobInvitation.Source.DIRECT,
                        null);

        assertEquals(JobInvitation.Status.PENDING, response.status());
        assertEquals("Mời lại bạn nhé", response.message());
        assertNull(withdrawn.getRespondedAt());
    }
}
