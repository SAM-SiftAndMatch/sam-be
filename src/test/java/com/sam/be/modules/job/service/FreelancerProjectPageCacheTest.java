package com.sam.be.modules.job.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.response.PageResponse;
import com.sam.be.infrastructure.cache.keys.RedisKeys;
import com.sam.be.infrastructure.cache.service.RedisCacheService;
import com.sam.be.modules.job.dto.response.JobResponse;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.job.service.impl.JobServiceImpl;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class FreelancerProjectPageCacheTest {

    @Test
    void fixedStatusTabUsesItsOwnCacheKey() {
        UUID freelancerId = UUID.randomUUID();
        JobRepository repository = mock(JobRepository.class);
        RedisCacheService cache = mock(RedisCacheService.class);
        PageResponse<JobResponse> cached = new PageResponse<>(List.of(), 0, 10, 0, 0, false);
        when(cache.getPageOrSet(
                        eq(RedisKeys.freelancerProjectsPage(freelancerId, "in_progress", 0, 10)),
                        eq(Duration.ofSeconds(30)),
                        eq(JobResponse.class),
                        any()))
                .thenReturn(cached);
        JobServiceImpl service = service(repository, cache);

        assertSame(
                cached,
                service.getFreelancerProjectsPage(
                        freelancerId, freelancerId, 0, 10, null, "in_progress", null));
        verifyNoInteractions(repository);
    }

    @Test
    void textSearchBypassesRedis() {
        UUID freelancerId = UUID.randomUUID();
        JobRepository repository = mock(JobRepository.class);
        RedisCacheService cache = mock(RedisCacheService.class);
        when(repository.findFreelancerProjectsPage(
                        eq(freelancerId),
                        isNull(),
                        eq(true),
                        eq(List.of(JobStatus.IN_PROGRESS)),
                        eq("%website%"),
                        any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));
        JobServiceImpl service = service(repository, cache);

        service.getFreelancerProjectsPage(
                freelancerId, freelancerId, 0, 10, null, "in_progress", "website");

        verifyNoInteractions(cache);
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
