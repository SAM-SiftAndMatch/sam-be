package com.sam.be.modules.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.infrastructure.cache.keys.RedisKeys;
import com.sam.be.infrastructure.cache.service.RedisCacheService;
import com.sam.be.modules.job.dto.response.ClientDashboardOverviewResponse;
import com.sam.be.modules.job.dto.response.ClientDashboardProjectResponse;
import com.sam.be.modules.job.repository.JobRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class ClientDashboardServiceTest {
    @Test
    void overviewCountsAllPublishedStatusesAndReturnsOnlyRecentProjects() {
        UUID clientId = UUID.randomUUID();
        JobRepository jobs = mock(JobRepository.class);
        RedisCacheService cache = mock(RedisCacheService.class);
        when(cache.getOrSet(eq(RedisKeys.clientDashboardOverview(clientId)),
                eq(Duration.ofMinutes(1)), eq(ClientDashboardOverviewResponse.class), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(3)).get());
        when(jobs.countClientJobsByStatus(clientId)).thenReturn(List.of(
                new Object[] {JobStatus.OPEN, 2L},
                new Object[] {JobStatus.IN_PROGRESS, 3L},
                new Object[] {JobStatus.COMPLETED, 4L},
                new Object[] {JobStatus.CANCELLED, 1L}));
        var recent = new ClientDashboardProjectResponse(UUID.randomUUID(), "Dự án A",
                JobStatus.IN_PROGRESS, BigDecimal.valueOf(100000), BigDecimal.valueOf(150000),
                LocalDateTime.now(), "Freelancer A");
        when(jobs.findRecentClientDashboardProjects(eq(clientId), any(Pageable.class)))
                .thenReturn(List.of(recent));

        var result = new ClientDashboardService(jobs, cache).getOverview(clientId);

        assertEquals(10, result.totalPosted());
        assertEquals(3, result.inProgress());
        assertEquals(4, result.completed());
        assertEquals(List.of(recent), result.recentProjects());
    }
}
