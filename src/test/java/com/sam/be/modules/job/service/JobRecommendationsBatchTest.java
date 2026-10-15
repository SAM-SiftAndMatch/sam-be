package com.sam.be.modules.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sam.be.infrastructure.cache.keys.RedisKeys;
import com.sam.be.infrastructure.cache.service.RedisCacheService;
import com.sam.be.modules.job.dto.response.AiRecommendationResponse;
import com.sam.be.modules.job.entity.AiJobRecommendation;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.repository.AiJobRecommendationRepository;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.job.service.impl.JobServiceImpl;
import com.sam.be.modules.skill.entity.Skill;
import com.sam.be.modules.user.entity.FreelancerProfile;
import com.sam.be.modules.user.entity.FreelancerSkill;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.FreelancerProfileRepository;
import com.sam.be.modules.user.repository.FreelancerSkillRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class JobRecommendationsBatchTest {

    @Test
    void loadsProfilesAndSkillsOnceForAllRecommendations() {
        UUID clientId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        User first = User.builder().id(UUID.randomUUID()).fullName("First").build();
        User second = User.builder().id(UUID.randomUUID()).fullName("Second").build();
        Job job = Job.builder().id(jobId).client(User.builder().id(clientId).build()).build();
        AiJobRecommendation firstRec =
                AiJobRecommendation.builder().id(UUID.randomUUID()).freelancer(first).build();
        AiJobRecommendation secondRec =
                AiJobRecommendation.builder().id(UUID.randomUUID()).freelancer(second).build();
        FreelancerProfile firstProfile =
                FreelancerProfile.builder().user(first).headline("Backend").build();
        FreelancerSkill secondSkill =
                FreelancerSkill.builder()
                        .freelancer(second)
                        .skill(Skill.builder().name("Java").build())
                        .yearsOfExperience(3)
                        .build();

        JobRepository jobs = mock(JobRepository.class);
        AiJobRecommendationRepository recommendations = mock(AiJobRecommendationRepository.class);
        FreelancerProfileRepository profiles = mock(FreelancerProfileRepository.class);
        FreelancerSkillRepository skills = mock(FreelancerSkillRepository.class);
        RedisCacheService cache = mock(RedisCacheService.class);
        when(jobs.findById(jobId)).thenReturn(Optional.of(job));
        when(recommendations.findAllByJobIdOrderByMatchScoreDesc(jobId))
                .thenReturn(List.of(firstRec, secondRec));
        List<UUID> ids = List.of(first.getId(), second.getId());
        when(profiles.findAllByUserIdIn(ids)).thenReturn(List.of(firstProfile));
        when(skills.findByFreelancerIdIn(ids)).thenReturn(List.of(secondSkill));
        when(cache.getListOrSet(
                        eq(RedisKeys.jobRecommendations(jobId)),
                        eq(Duration.ofMinutes(30)),
                        eq(AiRecommendationResponse.class),
                        any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get());

        JobServiceImpl service =
                new JobServiceImpl(
                        jobs,
                        null,
                        null,
                        null,
                        null,
                        profiles,
                        skills,
                        recommendations,
                        null,
                        null,
                        null,
                        null,
                        null,
                        cache,
                        null);
        List<AiRecommendationResponse> result = service.getJobRecommendations(clientId, jobId);

        assertEquals(2, result.size());
        assertEquals("Backend", result.get(0).getHeadline());
        assertEquals("Java", result.get(1).getSkills().getFirst().getSkillName());
        verify(profiles).findAllByUserIdIn(ids);
        verify(skills).findByFreelancerIdIn(ids);
    }
}
