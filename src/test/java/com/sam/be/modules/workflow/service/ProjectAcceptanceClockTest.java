package com.sam.be.modules.workflow.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sam.be.common.constant.enums.*;
import com.sam.be.infrastructure.cache.service.CacheInvalidationService;
import com.sam.be.modules.chat.repository.ChatRoomRepository;
import com.sam.be.modules.contract.entity.Contract;
import com.sam.be.modules.contract.repository.ContractRepository;
import com.sam.be.modules.finance.FinancePolicy;
import com.sam.be.modules.finance.FinanceStore;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.wallet.dto.response.ProjectSettlementResponse;
import com.sam.be.modules.wallet.service.ProjectSettlementService;
import com.sam.be.modules.workflow.entity.ProjectDelivery;
import com.sam.be.modules.workflow.entity.ProjectTimelineTask;
import com.sam.be.modules.workflow.repository.ProjectDeliveryRepository;
import com.sam.be.modules.workflow.repository.ProjectTimelineTaskRepository;
import com.sam.be.modules.workflow.service.impl.ProjectWorkflowServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProjectAcceptanceClockTest {
    private static final Instant ACCEPTED_AT = Instant.parse("2026-11-18T15:00:00Z");

    @Test
    void acceptanceUsesFinanceClockInEveryServerTimezone() {
        TimeZone previous = TimeZone.getDefault();
        try {
            for (String zone : List.of("UTC", "Asia/Ho_Chi_Minh", "America/New_York")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                Fixture f = new Fixture();
                var result =
                        f.service.acceptDelivery(
                                f.client.getId(), f.job.getId(), f.delivery.getId());
                Instant recorded = result.getDelivery().getReviewedAt().toInstant(ZoneOffset.UTC);
                assertEquals(ACCEPTED_AT, recorded, zone);
                assertTrue(FinancePolicy.canOpen(recorded, ACCEPTED_AT), zone);
                Instant end = ACCEPTED_AT.plus(Duration.ofHours(168));
                assertFalse(FinancePolicy.canOpen(recorded, end), zone);
                assertTrue(
                        FinancePolicy.canRelease(recorded.plus(FinancePolicy.WARRANTY), end, false),
                        zone);
                verify(f.settlements).settle(f.contract.getId(), f.delivery);
                assertEquals(ProjectTaskStatus.ACCEPTED, f.task.getStatus());
            }
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void retryReturnsExistingSettlementWithoutMovingWarrantyWindow() {
        Fixture f = new Fixture();
        f.job.setStatus(JobStatus.COMPLETED);
        f.delivery.setStatus(DeliveryStatus.ACCEPTED);
        f.delivery.setReviewedAt(LocalDateTime.ofInstant(ACCEPTED_AT, ZoneOffset.UTC));
        var recorded =
                ProjectSettlementResponse.builder().completedAt(f.delivery.getReviewedAt()).build();
        when(f.settlements.getByContract(f.client.getId(), f.contract.getId()))
                .thenReturn(recorded);

        var result = f.service.acceptDelivery(f.client.getId(), f.job.getId(), f.delivery.getId());

        assertSame(recorded, result.getSettlement());
        assertEquals(ACCEPTED_AT, result.getDelivery().getReviewedAt().toInstant(ZoneOffset.UTC));
        verify(f.settlements, never()).settle(any(), any());
        verify(f.deliveries, never()).save(any());
        verify(f.tasks, never()).saveAll(any());
    }

    private static class Fixture {
        final JobRepository jobs = mock(JobRepository.class);
        final ContractRepository contracts = mock(ContractRepository.class);
        final ProjectTimelineTaskRepository tasks = mock(ProjectTimelineTaskRepository.class);
        final ProjectDeliveryRepository deliveries = mock(ProjectDeliveryRepository.class);
        final FinanceStore finance = mock(FinanceStore.class);
        final ProjectSettlementService settlements = mock(ProjectSettlementService.class);
        final User client = User.builder().id(UUID.randomUUID()).fullName("Client").build();
        final User freelancer = User.builder().id(UUID.randomUUID()).fullName("Freelancer").build();
        final Job job =
                Job.builder()
                        .id(UUID.randomUUID())
                        .title("Project")
                        .status(JobStatus.IN_PROGRESS)
                        .build();
        final Contract contract =
                Contract.builder()
                        .id(UUID.randomUUID())
                        .job(job)
                        .client(client)
                        .freelancer(freelancer)
                        .build();
        final ProjectDelivery delivery =
                ProjectDelivery.builder()
                        .id(UUID.randomUUID())
                        .job(job)
                        .submittedBy(freelancer)
                        .versionNumber(1)
                        .status(DeliveryStatus.SUBMITTED)
                        .build();
        final ProjectTimelineTask task =
                ProjectTimelineTask.builder()
                        .id(UUID.randomUUID())
                        .job(job)
                        .status(ProjectTaskStatus.SUBMITTED)
                        .progress(100)
                        .build();
        final ProjectWorkflowServiceImpl service =
                new ProjectWorkflowServiceImpl(
                        jobs,
                        contracts,
                        null,
                        tasks,
                        null,
                        deliveries,
                        mock(ManualQaPlanService.class),
                        finance,
                        settlements,
                        mock(NotificationService.class),
                        mock(ChatRoomRepository.class),
                        null,
                        null,
                        null,
                        mock(CacheInvalidationService.class));

        Fixture() {
            when(contracts.findByJobId(job.getId())).thenReturn(Optional.of(contract));
            when(jobs.findByIdForWorkflowUpdate(job.getId())).thenReturn(Optional.of(job));
            when(deliveries.findByIdAndJobIdForUpdate(delivery.getId(), job.getId()))
                    .thenReturn(Optional.of(delivery));
            when(deliveries.save(any())).thenAnswer(call -> call.getArgument(0));
            when(tasks.findAllByJobIdOrderByWeekNumberAscSortOrderAsc(job.getId()))
                    .thenReturn(List.of(task));
            when(finance.now()).thenReturn(ACCEPTED_AT);
        }
    }
}
