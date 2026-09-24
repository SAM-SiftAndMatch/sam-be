package com.sam.be.modules.job.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.ai.service.AiService;
import com.sam.be.modules.job.dto.request.JobCreateRequest;
import com.sam.be.modules.job.dto.response.JobResponse;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.job.service.JobService;
import com.sam.be.modules.skill.dto.response.SkillResponse;
import com.sam.be.modules.skill.entity.Skill;
import com.sam.be.modules.skill.repository.SkillRepository;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Override
    @Transactional
    public JobResponse createJob(UUID clientId, JobCreateRequest request) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        String srsContent = "";
        try {
            srsContent = restClient.get()
                    .uri(request.getSrsDocumentUrl())
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("Failed to fetch SRS content from Cloudinary URL", e);
        }

        List<Skill> allSkills = skillRepository.findAll();
        List<Map<String, Object>> skillListForAi = allSkills.stream()
                .map(s -> Map.<String, Object>of("id", s.getId(), "name", s.getName()))
                .toList();

        String availableSkillsJson = "[]";
        try {
            availableSkillsJson = objectMapper.writeValueAsString(skillListForAi);
        } catch (Exception ignored) {}

        List<Integer> matchedSkillIds = aiService.extractSkillIdsForJob(srsContent, availableSkillsJson);

        List<Skill> foundSkills = skillRepository.findAllById(matchedSkillIds);
        Set<Skill> skills = new HashSet<>(foundSkills);

        Job job = Job.builder()
                .client(client)
                .title(request.getTitle())
                .description(request.getDescription())
                .budgetMin(request.getBudgetMin())
                .budgetMax(request.getBudgetMax())
                .deadline(request.getDeadline())
                .srsDocumentUrl(request.getSrsDocumentUrl())
                .isFeatured(request.getIsFeatured() != null ? request.getIsFeatured() : false)
                .isUrgentHiring(request.getIsUrgentHiring() != null ? request.getIsUrgentHiring() : false)
                .requiresAiQa(request.getRequiresAiQa() != null ? request.getRequiresAiQa() : false)
                .skills(skills)
                .status(JobStatus.OPEN)
                .build();

        Job savedJob = jobRepository.save(job);
        return mapToResponse(savedJob);
    }

    @Override
    @Transactional
    public JobResponse cancelJob(UUID clientId, UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!job.getClient().getId().equals(clientId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ApiException(ErrorCode.JOB_CANNOT_BE_CANCELLED);
        }

        job.setStatus(JobStatus.CANCELLED);
        jobRepository.save(job);
        return mapToResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public JobResponse getJobById(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return mapToResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobResponse> getJobsByClientId(UUID clientId) {
        List<Job> jobs = jobRepository.findAllByClientId(clientId);
        return jobs.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private JobResponse mapToResponse(Job job) {
        List<SkillResponse> skillResponses = job.getSkills().stream()
                .map(skill -> SkillResponse.builder()
                        .id(skill.getId())
                        .name(skill.getName())
                        .build())
                .collect(Collectors.toList());

        return JobResponse.builder()
                .id(job.getId())
                .clientId(job.getClient().getId())
                .clientName(job.getClient().getFullName())
                .title(job.getTitle())
                .description(job.getDescription())
                .budgetMin(job.getBudgetMin())
                .budgetMax(job.getBudgetMax())
                .status(job.getStatus())
                .deadline(job.getDeadline())
                .srsDocumentUrl(job.getSrsDocumentUrl())
                .riskLevel(job.getRiskLevel())
                .isFeatured(job.getIsFeatured())
                .isUrgentHiring(job.getIsUrgentHiring())
                .requiresAiQa(job.getRequiresAiQa())
                .skills(skillResponses)
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}