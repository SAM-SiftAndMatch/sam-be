package com.sam.be.modules.job.service;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.infrastructure.cache.keys.RedisKeys;
import com.sam.be.infrastructure.cache.service.RedisCacheService;
import com.sam.be.modules.job.dto.response.ClientDashboardOverviewResponse;
import com.sam.be.modules.job.repository.JobRepository;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientDashboardService {
    private final JobRepository jobRepository;
    private final RedisCacheService redisCacheService;

    @Transactional(readOnly = true)
    public ClientDashboardOverviewResponse getOverview(UUID clientId) {
        return redisCacheService.getOrSet(
                RedisKeys.clientDashboardOverview(clientId),
                Duration.ofMinutes(1),
                ClientDashboardOverviewResponse.class,
                () -> loadOverview(clientId));
    }

    private ClientDashboardOverviewResponse loadOverview(UUID clientId) {
        long total = 0;
        long inProgress = 0;
        long completed = 0;
        for (Object[] row : jobRepository.countClientJobsByStatus(clientId)) {
            JobStatus status = (JobStatus) row[0];
            long count = (Long) row[1];
            total += count;
            if (status == JobStatus.IN_PROGRESS) inProgress = count;
            if (status == JobStatus.COMPLETED) completed = count;
        }
        return new ClientDashboardOverviewResponse(
                total,
                inProgress,
                completed,
                jobRepository.findRecentClientDashboardProjects(clientId, PageRequest.of(0, 4)));
    }
}
