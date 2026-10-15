package com.sam.be.modules.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.infrastructure.cache.keys.RedisKeys;
import com.sam.be.infrastructure.cache.service.RedisCacheService;
import com.sam.be.modules.job.dto.response.JobResponse;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.job.service.impl.JobServiceImpl;
import com.sam.be.modules.user.entity.User;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class OpenJobsBatchTest {

    @Test
    void loadsDetailsOnceAndPreservesIdOrder() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        User client = User.builder().id(UUID.randomUUID()).fullName("Client").build();
        Job first = Job.builder().id(firstId).client(client).title("First").build();
        Job second = Job.builder().id(secondId).client(client).title("Second").build();
        JobRepository repository = mock(JobRepository.class);
        RedisCacheService cache = mock(RedisCacheService.class);
        when(repository.findAllOpenJobIdsFiltered(eq(JobStatus.OPEN), any(LocalDateTime.class)))
                .thenReturn(List.of(firstId, secondId));
        when(repository.findPageDetails(List.of(firstId, secondId)))
                .thenReturn(List.of(second, first));
        when(cache.getListOrSet(
                        eq(RedisKeys.openJobs()),
                        eq(Duration.ofSeconds(30)),
                        eq(JobResponse.class),
                        any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get());
        JobServiceImpl service =
                new JobServiceImpl(
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

        List<JobResponse> response = service.getAllOpenJobs();

        assertEquals(
                List.of(firstId, secondId), response.stream().map(JobResponse::getId).toList());
        verify(repository).findPageDetails(List.of(firstId, secondId));
    }
}
