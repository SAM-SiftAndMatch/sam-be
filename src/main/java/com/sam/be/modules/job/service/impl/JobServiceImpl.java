package com.sam.be.modules.job.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.constant.enums.RecommendationStatus;
import com.sam.be.common.constant.enums.SubscriptionStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.ai.dto.response.AiCandidateScore;
import com.sam.be.modules.ai.dto.response.AiExtractedSkill;
import com.sam.be.modules.ai.service.AiService;
import com.sam.be.modules.chat.service.ChatService;
import com.sam.be.modules.job.dto.request.JobCreateRequest;
import com.sam.be.modules.job.dto.response.AcceptInvitationResponse;
import com.sam.be.modules.job.dto.response.AiRecommendationResponse;
import com.sam.be.modules.job.dto.response.JobResponse;
import com.sam.be.modules.job.dto.response.SkillExperienceDto;
import com.sam.be.modules.job.entity.AiJobRecommendation;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.entity.JobSkill;
import com.sam.be.modules.job.repository.AiJobRecommendationRepository;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.job.repository.JobSkillRepository;
import com.sam.be.modules.job.service.JobService;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.skill.dto.response.SkillResponse;
import com.sam.be.modules.skill.entity.Skill;
import com.sam.be.modules.skill.repository.SkillRepository;
import com.sam.be.modules.subscription.entity.UserSubscription;
import com.sam.be.modules.subscription.repository.UserSubscriptionRepository;
import com.sam.be.modules.user.entity.FreelancerProfile;
import com.sam.be.modules.user.entity.FreelancerSkill;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.FreelancerProfileRepository;
import com.sam.be.modules.user.repository.FreelancerSkillRepository;
import com.sam.be.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
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
    private final JobSkillRepository jobSkillRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final FreelancerProfileRepository freelancerProfileRepository;
    private final FreelancerSkillRepository freelancerSkillRepository;
    private final AiJobRecommendationRepository aiJobRecommendationRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final NotificationService notificationService;
    private final ChatService chatService;
    @Override
    @Transactional
    public JobResponse createJob(UUID clientId, JobCreateRequest request) {
        User client = userRepository.findById(clientId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        String srsContent = "";
        try {
            srsContent = restClient.get().uri(request.getSrsDocumentUrl()).retrieve().body(String.class);
        } catch (Exception e) {
            log.warn("Failed to fetch SRS content from Cloudinary URL", e);
        }

        List<Skill> allSkills = skillRepository.findAll();
        String availableSkillsJson = "[]";
        try {
            availableSkillsJson = objectMapper.writeValueAsString(allSkills.stream().map(s -> Map.of("id", s.getId(), "name", s.getName())).toList());
        } catch (Exception ignored) {}

        List<AiExtractedSkill> extractedSkills = aiService.extractSkillsForJob(srsContent, availableSkillsJson);

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
                .status(JobStatus.OPEN)
                .build();

        Job savedJob = jobRepository.save(job);

        // Lọc trùng skillId từ AI (nếu AI nhả trùng) và build JobSkill
        Set<JobSkill> jobSkills = extractedSkills.stream()
                .collect(Collectors.toMap(AiExtractedSkill::getSkillId, ext -> ext, (ext1, ext2) -> ext1))
                .values().stream()
                .map(ext -> {
                    Skill skill = skillRepository.findById(ext.getSkillId()).orElse(null);
                    if (skill == null) return null;
                    return JobSkill.builder()
                            .id(new JobSkill.JobSkillId(savedJob.getId(), skill.getId()))
                            .job(savedJob)
                            .skill(skill)
                            .requiredYearsOfExperience(ext.getYearsOfExperience())
                            .build();
                }).filter(java.util.Objects::nonNull).collect(Collectors.toSet());

        // Chỉ cần gán vào entity đã managed, JPA CascadeType.ALL sẽ tự động chèn vào DB an toàn 1 lần duy nhất
        savedJob.setJobSkills(jobSkills);
        jobRepository.save(savedJob);

        if (savedJob.getIsUrgentHiring()) {
            processUrgentHiring(savedJob, srsContent, extractedSkills);
        }

        return mapToResponse(savedJob);
    }

    private void processUrgentHiring(Job job, String srsContent, List<AiExtractedSkill> requiredSkills) {
        List<UserSubscription> proDevSubs = userSubscriptionRepository.findByServicePackage_NameAndStatusAndEndDateAfter(
                "Gói PRO DEV", SubscriptionStatus.ACTIVE, LocalDateTime.now());
        List<UUID> proDevIds = proDevSubs.stream().map(sub -> sub.getUser().getId()).toList();

        if (proDevIds.isEmpty()) return;

        List<FreelancerProfile> profiles = freelancerProfileRepository.findAllByUserIdIn(proDevIds);
        List<FreelancerSkill> allDevSkills = freelancerSkillRepository.findByFreelancerIdIn(proDevIds);
        Map<UUID, List<FreelancerSkill>> devSkillMap = allDevSkills.stream().collect(Collectors.groupingBy(fs -> fs.getFreelancer().getId()));

        List<FreelancerProfile> top5Candidates = profiles.stream()
                .sorted((p1, p2) -> {
                    int score1 = calculateLocalScore(requiredSkills, devSkillMap.get(p1.getUser().getId()));
                    int score2 = calculateLocalScore(requiredSkills, devSkillMap.get(p2.getUser().getId()));
                    return Integer.compare(score2, score1);
                })
                .limit(5)
                .toList();

        // KỸ THUẬT ALIAS MAPPING: Tráo đổi UUID thành A, B, C, D, E để AI khỏi bị ngáo
        java.util.Map<String, UUID> idMapping = new java.util.HashMap<>();

        // FIX LỖI: Dùng mảng 1 phần tử để lách luật "effectively final" của Java Lambda
        char[] alias = {'A'};

        List<Map<String, Object>> candidateData = top5Candidates.stream().map(p -> {
            String shortId = String.valueOf(alias[0]++); // Lấy giá trị ở index 0 rồi tự tăng lên B, C...
            idMapping.put(shortId, p.getUser().getId()); // Lưu vào bộ nhớ để tí map lại

            List<String> skillNames = devSkillMap.getOrDefault(p.getUser().getId(), java.util.List.of()).stream().map(fs -> fs.getSkill().getName()).toList();
            return Map.<String, Object>of(
                    "candidateId", shortId, // Gửi ID ngắn cho AI
                    "headline", p.getHeadline() != null ? p.getHeadline() : "",
                    "bio", p.getBio() != null ? p.getBio() : "",
                    "skills", skillNames
            );
        }).toList();

        String candidatesJson = "[]";
        try { candidatesJson = objectMapper.writeValueAsString(candidateData); } catch (Exception ignored) {}

        List<AiCandidateScore> aiScores = aiService.evaluateCandidates(srsContent, candidatesJson);

        List<AiJobRecommendation> recommendations = aiScores.stream().map(score -> {
            // MAP NGƯỢC LẠI: Lấy UUID thật từ chữ cái A, B, C
            UUID realId = idMapping.get(score.getCandidateId());
            if (realId == null) return null;

            User dev = userRepository.findById(realId).orElse(null);
            if (dev == null) return null;
            return AiJobRecommendation.builder()
                    .job(job)
                    .freelancer(dev)
                    .matchScore(score.getMatchScore())
                    .aiComment(score.getAiComment())
                    .isViewed(false)
                    .build();
        }).filter(java.util.Objects::nonNull).toList();

        aiJobRecommendationRepository.saveAll(recommendations);
    }

    private int calculateLocalScore(List<AiExtractedSkill> requiredSkills, List<FreelancerSkill> devSkills) {
        if (devSkills == null || devSkills.isEmpty()) return 0;
        int score = 0;
        for (AiExtractedSkill req : requiredSkills) {
            for (FreelancerSkill dev : devSkills) {
                if (dev.getSkill().getId().equals(req.getSkillId())) {
                    score += 10;
                    if (req.getYearsOfExperience() != null && dev.getYearsOfExperience() != null
                            && dev.getYearsOfExperience() >= req.getYearsOfExperience()) {
                        score += 5;
                    }
                }
            }
        }
        return score;
    }

    @Override
    @Transactional
    public JobResponse cancelJob(UUID clientId, UUID jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
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
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return mapToResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobResponse> getJobsByClientId(UUID clientId) {
        List<Job> jobs = jobRepository.findAllByClientId(clientId);
        return jobs.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private JobResponse mapToResponse(Job job) {
        List<SkillResponse> skillResponses = job.getJobSkills() == null ? List.of() : job.getJobSkills().stream()
                .map(jobSkill -> SkillResponse.builder()
                        .id(jobSkill.getSkill().getId())
                        .name(jobSkill.getSkill().getName())
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
    @Override
    @Transactional(readOnly = true)
    public List<AiRecommendationResponse> getJobRecommendations(UUID clientId, UUID jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!job.getClient().getId().equals(clientId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        List<AiJobRecommendation> recommendations = aiJobRecommendationRepository.findAllByJobIdOrderByMatchScoreDesc(jobId);

        return recommendations.stream().map(rec -> {
            User dev = rec.getFreelancer();
            FreelancerProfile profile = freelancerProfileRepository.findByUserId(dev.getId()).orElse(null);
            List<FreelancerSkill> fSkills = freelancerSkillRepository.findByFreelancerIdIn(List.of(dev.getId()));

            List<SkillExperienceDto> skillDtos = fSkills.stream()
                    .map(fs -> SkillExperienceDto.builder()
                            .skillName(fs.getSkill().getName())
                            .yearsOfExperience(fs.getYearsOfExperience())
                            .build())
                    .toList();

            return AiRecommendationResponse.builder()
                    .id(rec.getId())
                    .freelancerId(dev.getId())
                    .fullName(dev.getFullName())
                    .headline(profile != null ? profile.getHeadline() : "")
                    .matchScore(rec.getMatchScore())
                    .aiComment(rec.getAiComment())
                    .status(rec.getStatus())
                    .skills(skillDtos)
                    .build();
        }).toList();
    }

    @Override
    @Transactional
    public void inviteCandidate(UUID clientId, UUID jobId, UUID recommendationId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!job.getClient().getId().equals(clientId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        AiJobRecommendation recommendation = aiJobRecommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!recommendation.getJob().getId().equals(jobId)) {
            throw new ApiException(ErrorCode.REQUEST_FAILED);
        }

        if (recommendation.getStatus() != RecommendationStatus.PENDING) {
            throw new ApiException(ErrorCode.REQUEST_FAILED);
        }

        recommendation.setStatus(RecommendationStatus.INVITED);
        aiJobRecommendationRepository.save(recommendation);

        notificationService.sendInviteNotification(
                recommendation.getFreelancer().getId(),
                job.getId(),
                job.getTitle(),
                recommendation.getMatchScore()
        );
    }

    @Override
    @Transactional
    public AcceptInvitationResponse acceptJobInvitation(UUID freelancerId, UUID jobId, UUID recommendationId) {
        AiJobRecommendation recommendation = validateAndGetInvitation(freelancerId, jobId, recommendationId);

        // 1. Cập nhật trạng thái ứng viên (KHÔNG đổi JobStatus, vẫn giữ OPEN)
        recommendation.setStatus(RecommendationStatus.ACCEPTED);
        aiJobRecommendationRepository.save(recommendation);

        // 2. Tạo hoặc lấy phòng Chat
        com.sam.be.modules.chat.entity.ChatRoom room = chatService.getOrCreateRoom(recommendation.getJob(), recommendation.getFreelancer());

        // 3. Trả về roomId cho Frontend
        return AcceptInvitationResponse.builder()
                .roomId(room.getId())
                .build();
    }

    @Override
    @Transactional
    public void rejectJobInvitation(UUID freelancerId, UUID jobId, UUID recommendationId) {
        AiJobRecommendation recommendation = validateAndGetInvitation(freelancerId, jobId, recommendationId);

        // Đổi trạng thái sang REJECTED
        recommendation.setStatus(RecommendationStatus.REJECTED);
        aiJobRecommendationRepository.save(recommendation);
    }

    // Hàm dùng chung để validate bảo mật tránh Dev này nhận bừa job của Dev khác
    private AiJobRecommendation validateAndGetInvitation(UUID freelancerId, UUID jobId, UUID recommendationId) {
        AiJobRecommendation recommendation = aiJobRecommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!recommendation.getJob().getId().equals(jobId)) {
            throw new ApiException(ErrorCode.REQUEST_FAILED);
        }

        // Bắt buộc người gọi API phải chính là Freelancer được AI đề xuất
        if (!recommendation.getFreelancer().getId().equals(freelancerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        // Chỉ cho phép thao tác khi Client đã gửi lời mời (INVITED)
        if (recommendation.getStatus() != RecommendationStatus.INVITED) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Lời mời không hợp lệ hoặc đã được xử lý");
        }

        return recommendation;
    }
}