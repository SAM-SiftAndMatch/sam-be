package com.sam.be.modules.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.response.PageResponse;
import com.sam.be.infrastructure.cache.keys.RedisKeys;
import com.sam.be.infrastructure.cache.service.RedisCacheService;
import com.sam.be.modules.job.dto.response.JobResponse;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.job.service.impl.JobServiceImpl;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobReadCacheTest {

    @Test
    void cachedNormalJobDoesNotQueryDatabase() {
        UUID jobId = UUID.randomUUID();
        JobRepository repository = mock(JobRepository.class);
        RedisCacheService cache = mock(RedisCacheService.class);
        JobResponse cached = JobResponse.builder().id(jobId).isUrgentHiring(false).build();
        when(cache.getOrSet(
                        eq(RedisKeys.jobDetail(jobId)),
                        eq(Duration.ofMinutes(30)),
                        eq(JobResponse.class),
                        any()))
                .thenReturn(cached);

        assertSame(cached, service(repository, cache).getJobById(jobId));
        verifyNoInteractions(repository);
    }

    @Test
    void cachedUrgentJobStillRejectsAnonymousCaller() {
        UUID jobId = UUID.randomUUID();
        JobRepository repository = mock(JobRepository.class);
        RedisCacheService cache = mock(RedisCacheService.class);
        JobResponse cached =
                JobResponse.builder()
                        .id(jobId)
                        .clientId(UUID.randomUUID())
                        .isUrgentHiring(true)
                        .status(JobStatus.OPEN)
                        .createdAt(LocalDateTime.now())
                        .build();
        when(cache.getOrSet(
                        eq(RedisKeys.jobDetail(jobId)),
                        eq(Duration.ofMinutes(30)),
                        eq(JobResponse.class),
                        any()))
                .thenReturn(cached);

        assertThrows(ApiException.class, () -> service(repository, cache).getJobById(jobId));
        verifyNoInteractions(repository);
    }

    @Test
    void fixedBudgetSortUsesSeparateInvalidatablePageKey() {
        JobRepository repository = mock(JobRepository.class);
        RedisCacheService cache = mock(RedisCacheService.class);
        PageResponse<JobResponse> cached = new PageResponse<>(List.of(), 0, 10, 0, 0, false);
        String key = RedisKeys.openJobsPage(0, 10, "budget_asc");
        when(cache.getPageOrSet(eq(key), eq(Duration.ofSeconds(30)), eq(JobResponse.class), any()))
                .thenReturn(cached);

        assertSame(
                cached,
                service(repository, cache)
                        .getOpenJobsPage(
                                0, 10, null, null, null, null, false, null, null, "budget_asc"));
        assertEquals("job:open:page:0:10:sort:budget_asc", key);
        verifyNoInteractions(repository);
    }

    private JobServiceImpl service(JobRepository repository, RedisCacheService cache) {
        return new JobServiceImpl(
                repository,
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
                cache,
                null);
    }
}
