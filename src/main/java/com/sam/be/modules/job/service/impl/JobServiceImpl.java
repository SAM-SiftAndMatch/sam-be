package com.sam.be.modules.job.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.constant.enums.RecommendationStatus;
import com.sam.be.common.constant.enums.SubscriptionStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.infrastructure.cache.keys.RedisKeys;
import com.sam.be.infrastructure.cache.service.RedisCacheService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

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
    private final RedisCacheService redisCacheService;

    @Override
    @Transactional
    public JobResponse createJob(UUID clientId, JobCreateRequest request) {
        User client =
                userRepository
                        .findById(clientId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        String srsContent = "";
        try {
            srsContent =
                    restClient.get().uri(request.getSrsDocumentUrl()).retrieve().body(String.class);
        } catch (Exception e) {
            log.warn("Failed to fetch SRS content from Cloudinary URL", e);
        }

        List<Skill> allSkills = skillRepository.findAll();
        String availableSkillsJson = "[]";
        try {
            availableSkillsJson =
                    objectMapper.writeValueAsString(
                            allSkills.stream()
                                    .map(s -> Map.of("id", s.getId(), "name", s.getName()))
                                    .toList());
        } catch (Exception ignored) {
        }

        List<AiExtractedSkill> extractedSkills =
                aiService.extractSkillsForJob(srsContent, availableSkillsJson);

        Job job =
                Job.builder()
                        .client(client)
                        .title(request.getTitle())
                        .description(request.getDescription())
                        .budgetMin(request.getBudgetMin())
                        .budgetMax(request.getBudgetMax())
                        .estimatedDurationMonths(request.getEstimatedDurationMonths())
                        .srsDocumentUrl(request.getSrsDocumentUrl())
                        .srsContent(request.getSrsContent())
                        .deadline(
                                request.getDeadline() != null
                                        ? request.getDeadline()
                                        : java.time.LocalDateTime.now().plusDays(30))
                        .isFeatured(
                                request.getIsFeatured() != null ? request.getIsFeatured() : false)
                        .isUrgentHiring(
                                request.getIsUrgentHiring() != null
                                        ? request.getIsUrgentHiring()
                                        : false)
                        .requiresAiQa(
                                request.getRequiresAiQa() != null
                                        ? request.getRequiresAiQa()
                                        : false)
                        .status(JobStatus.OPEN)
                        .build();

        Job savedJob = jobRepository.save(job);

        // Lọc trùng skillId từ AI (nếu AI nhả trùng) và build JobSkill
        Set<JobSkill> jobSkills =
                extractedSkills.stream()
                        .collect(
                                Collectors.toMap(
                                        AiExtractedSkill::getSkillId,
                                        ext -> ext,
                                        (ext1, ext2) -> ext1))
                        .values()
                        .stream()
                        .map(
                                ext -> {
                                    Skill skill =
                                            skillRepository.findById(ext.getSkillId()).orElse(null);
                                    if (skill == null) return null;
                                    return JobSkill.builder()
                                            .id(
                                                    new JobSkill.JobSkillId(
                                                            savedJob.getId(), skill.getId()))
                                            .job(savedJob)
                                            .skill(skill)
                                            .requiredYearsOfExperience(ext.getYearsOfExperience())
                                            .build();
                                })
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());

        // Chỉ cần gán vào entity đã managed, JPA CascadeType.ALL sẽ tự động chèn vào DB an toàn 1
        // lần duy nhất
        savedJob.setJobSkills(jobSkills);
        jobRepository.save(savedJob);

        if (savedJob.getIsUrgentHiring()) {
            processUrgentHiring(savedJob, srsContent, extractedSkills);
        }

        JobResponse response = mapToResponse(savedJob);
        redisCacheService.delete(RedisKeys.clientJobs(clientId));
        return response;
    }

    private void processUrgentHiring(
            Job job, String srsContent, List<AiExtractedSkill> requiredSkills) {
        try {
            List<UserSubscription> proDevSubs =
                    userSubscriptionRepository.findByServicePackage_NameAndStatusAndEndDateAfter(
                            "Gói PRO DEV", SubscriptionStatus.ACTIVE, LocalDateTime.now());
            List<UUID> proDevIds = proDevSubs.stream().map(sub -> sub.getUser().getId()).toList();
            log.info("Urgent hiring for job {}: found {} PRO devs", job.getId(), proDevIds.size());

            // Ưu tiên PRO trước
            List<FreelancerProfile> profiles =
                    proDevIds.isEmpty()
                            ? new java.util.ArrayList<>()
                            : new java.util.ArrayList<>(
                                    freelancerProfileRepository.findAllByUserIdIn(proDevIds));
            java.util.Set<UUID> proIdSet = new java.util.HashSet<>(proDevIds);

            // FALLBACK: nếu không đủ 5 PRO thì lấp đầy bằng freelancer thường có profile
            // (đúng yêu cầu: không có PRO nào thì lấy luôn freelancer thường cho đủ 5)
            if (profiles.size() < 5) {
                java.util.Set<UUID> pickedIds =
                        profiles.stream().map(p -> p.getUser().getId()).collect(Collectors.toSet());
                List<FreelancerProfile> allProfiles = freelancerProfileRepository.findAll();
                for (FreelancerProfile p : allProfiles) {
                    if (profiles.size() >= 5) break;
                    UUID uid = p.getUser().getId();
                    if (pickedIds.contains(uid)) continue;
                    // Không lấy chính client đăng job (phòng trường hợp client cũng có freelancer
                    // profile)
                    if (job.getClient() != null && job.getClient().getId().equals(uid)) continue;
                    profiles.add(p);
                    pickedIds.add(uid);
                }
                log.info(
                        "Urgent hiring for job {}: after fallback total candidates={} (pro={}, normal={})",
                        job.getId(),
                        profiles.size(),
                        profiles.stream()
                                .filter(p -> proIdSet.contains(p.getUser().getId()))
                                .count(),
                        profiles.stream()
                                .filter(p -> !proIdSet.contains(p.getUser().getId()))
                                .count());
            }

            if (profiles.isEmpty()) {
                log.warn(
                        "Urgent hiring for job {}: no freelancer profiles at all, skip AI matching",
                        job.getId());
                return;
            }

            List<FreelancerProfile> allDevSkillsProfiles = profiles;
            List<UUID> candidateIds = profiles.stream().map(p -> p.getUser().getId()).toList();
            List<FreelancerSkill> allDevSkills =
                    freelancerSkillRepository.findByFreelancerIdIn(candidateIds);
            Map<UUID, List<FreelancerSkill>> devSkillMap =
                    allDevSkills.stream()
                            .collect(Collectors.groupingBy(fs -> fs.getFreelancer().getId()));

            List<FreelancerProfile> top5Candidates =
                    allDevSkillsProfiles.stream()
                            .sorted(
                                    (p1, p2) -> {
                                        int score1 =
                                                calculateLocalScore(
                                                        requiredSkills,
                                                        devSkillMap.get(p1.getUser().getId()));
                                        int score2 =
                                                calculateLocalScore(
                                                        requiredSkills,
                                                        devSkillMap.get(p2.getUser().getId()));
                                        return Integer.compare(score2, score1);
                                    })
                            .limit(5)
                            .toList();

            if (top5Candidates.isEmpty()) {
                log.warn(
                        "Urgent hiring for job {}: top candidates empty after scoring",
                        job.getId());
                return;
            }

            // KỸ THUẬT ALIAS MAPPING: Tráo đổi UUID thành A, B, C, D, E để AI khỏi bị ngáo
            java.util.Map<String, UUID> idMapping = new java.util.HashMap<>();
            java.util.Map<UUID, Integer> localScoreMap = new java.util.HashMap<>();
            for (FreelancerProfile p : top5Candidates) {
                localScoreMap.put(
                        p.getUser().getId(),
                        calculateLocalScore(requiredSkills, devSkillMap.get(p.getUser().getId())));
            }

            // FIX LỖI: Dùng mảng 1 phần tử để lách luật "effectively final" của Java Lambda
            char[] alias = {'A'};

            List<Map<String, Object>> candidateData =
                    top5Candidates.stream()
                            .map(
                                    p -> {
                                        String shortId =
                                                String.valueOf(
                                                        alias[0]++); // Lấy giá trị ở index 0 rồi tự
                                        // tăng lên B, C...
                                        idMapping.put(
                                                shortId,
                                                p.getUser()
                                                        .getId()); // Lưu vào bộ nhớ để tí map lại

                                        List<String> skillNames =
                                                devSkillMap
                                                        .getOrDefault(
                                                                p.getUser().getId(),
                                                                java.util.List.of())
                                                        .stream()
                                                        .map(fs -> fs.getSkill().getName())
                                                        .toList();
                                        return Map.<String, Object>of(
                                                "candidateId",
                                                shortId, // Gửi ID ngắn cho AI
                                                "headline",
                                                p.getHeadline() != null ? p.getHeadline() : "",
                                                "bio",
                                                p.getBio() != null ? p.getBio() : "",
                                                "skills",
                                                skillNames);
                                    })
                            .toList();

            String candidatesJson = "[]";
            try {
                candidatesJson = objectMapper.writeValueAsString(candidateData);
            } catch (Exception ignored) {
            }

            // Gọi AI chấm điểm, nhưng KHÔNG để AI fail làm mất ứng viên
            List<AiCandidateScore> aiScores = List.of();
            try {
                aiScores = aiService.evaluateCandidates(srsContent, candidatesJson);
                if (aiScores == null) aiScores = List.of();
            } catch (Exception e) {
                log.warn(
                        "Urgent hiring for job {}: AI evaluate failed, fallback to local score",
                        job.getId(),
                        e);
                aiScores = List.of();
            }
            log.info(
                    "Urgent hiring for job {}: AI returned {}/{} scores",
                    job.getId(),
                    aiScores.size(),
                    top5Candidates.size());

            java.util.Set<UUID> aiMatchedIds = new java.util.HashSet<>();
            List<AiJobRecommendation> recommendations =
                    aiScores.stream()
                            .map(
                                    score -> {
                                        // MAP NGƯỢC LẠI: Lấy UUID thật từ chữ cái A, B, C
                                        UUID realId = idMapping.get(score.getCandidateId());
                                        if (realId == null) return null;

                                        User dev = userRepository.findById(realId).orElse(null);
                                        if (dev == null) return null;
                                        aiMatchedIds.add(realId);
                                        return AiJobRecommendation.builder()
                                                .job(job)
                                                .freelancer(dev)
                                                .matchScore(score.getMatchScore())
                                                .aiComment(score.getAiComment())
                                                .isViewed(false)
                                                .status(RecommendationStatus.AUTO_MATCHED)
                                                .build();
                                    })
                            .filter(java.util.Objects::nonNull)
                            .collect(Collectors.toCollection(java.util.ArrayList::new));

            // FALLBACK: những ứng viên AI bỏ sót / AI fail hoàn toàn -> dùng local score để vẫn đủ
            // 5
            for (FreelancerProfile p : top5Candidates) {
                UUID uid = p.getUser().getId();
                if (aiMatchedIds.contains(uid)) continue;
                User dev = userRepository.findById(uid).orElse(null);
                if (dev == null) continue;
                int localScore = localScoreMap.getOrDefault(uid, 0);
                // Quy đổi local score -> thang 0-95 để hiển thị % khớp
                // localScore mỗi skill khớp = 10đ (+5 nếu đủ năm KN)
                java.math.BigDecimal matchScore =
                        java.math.BigDecimal.valueOf(Math.min(95, 55 + localScore * 3));
                boolean isPro = proIdSet.contains(uid);
                String comment =
                        (isPro ? "[PRO] " : "[Thường] ")
                                + "Ứng viên phù hợp với yêu cầu kỹ năng của dự án"
                                + (localScore > 0
                                        ? " (khớp " + localScore + " điểm kỹ năng)."
                                        : " (hồ sơ tiềm năng, cần xem thêm).");
                recommendations.add(
                        AiJobRecommendation.builder()
                                .job(job)
                                .freelancer(dev)
                                .matchScore(matchScore)
                                .aiComment(comment)
                                .isViewed(false)
                                .status(RecommendationStatus.AUTO_MATCHED)
                                .build());
            }

            if (recommendations.isEmpty()) {
                log.warn("Urgent hiring for job {}: no recommendations to save", job.getId());
                return;
            }

            List<AiJobRecommendation> savedRecs =
                    aiJobRecommendationRepository.saveAll(recommendations);
            log.info(
                    "Urgent hiring for job {}: saved {} recommendations",
                    job.getId(),
                    savedRecs.size());
            savedRecs.forEach(
                    rec -> {
                        try {
                            notificationService.sendInviteNotification(
                                    rec.getFreelancer().getId(),
                                    job.getId(),
                                    job.getTitle(),
                                    rec.getMatchScore(),
                                    rec.getId());
                        } catch (Exception e) {
                            log.warn(
                                    "Failed to send invite notification to {}",
                                    rec.getFreelancer().getId(),
                                    e);
                        }
                    });
        } catch (Exception e) {
            // Không bao giờ để lỗi mai mối làm fail cả việc đăng job
            log.error("Urgent hiring failed for job {}", job.getId(), e);
        }
    }

    private int calculateLocalScore(
            List<AiExtractedSkill> requiredSkills, List<FreelancerSkill> devSkills) {
        if (devSkills == null || devSkills.isEmpty()) return 0;
        int score = 0;
        for (AiExtractedSkill req : requiredSkills) {
            for (FreelancerSkill dev : devSkills) {
                if (dev.getSkill().getId().equals(req.getSkillId())) {
                    score += 10;
                    if (req.getYearsOfExperience() != null
                            && dev.getYearsOfExperience() != null
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
        Job job =
                jobRepository
                        .findById(jobId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!job.getClient().getId().equals(clientId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        if (job.getStatus() != JobStatus.OPEN) {
            throw new ApiException(ErrorCode.JOB_CANNOT_BE_CANCELLED);
        }
        job.setStatus(JobStatus.CANCELLED);
        jobRepository.save(job);

        JobResponse response = mapToResponse(job);
        redisCacheService.delete(RedisKeys.jobDetail(jobId));
        redisCacheService.delete(RedisKeys.clientJobs(clientId));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public JobResponse getJobById(UUID jobId) {
        // Gating độc quyền 5 phút PHẢI chạy trước cache (cache dùng chung mọi user,
        // không được để người ngoài top 5 đọc ké bản cache của người được mời)
        Job gate =
                jobRepository
                        .findById(jobId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        assertVisible(gate);
        return redisCacheService.getOrSet(
                RedisKeys.jobDetail(jobId),
                Duration.ofMinutes(30),
                JobResponse.class,
                () -> {
                    Job job =
                            jobRepository
                                    .findByIdWithDetails(jobId)
                                    .orElseThrow(
                                            () -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
                    return mapToResponse(job);
                });
    }

    // Job gấp trong 5 phút đầu: ẩn cả xem trực tiếp — chỉ chủ job, admin và 5 người được
    // AI chọn mới xem được. Hết 5 phút hoặc job thường thì public.
    private void assertVisible(Job job) {
        if (!Boolean.TRUE.equals(job.getIsUrgentHiring())
                || job.getStatus() != JobStatus.OPEN
                || job.getCreatedAt() == null
                || !job.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(5))) {
            return;
        }
        if (com.sam.be.common.security.util.SecurityUtils.isAdmin()) {
            return;
        }
        UUID caller =
                com.sam.be.common.security.util.SecurityUtils.getCurrentUserIdOptional()
                        .orElse(null);
        if (caller == null) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        if (job.getClient() != null && caller.equals(job.getClient().getId())) {
            return;
        }
        boolean invited =
                aiJobRecommendationRepository
                        .findByJobIdAndFreelancerId(job.getId(), caller)
                        .isPresent();
        if (!invited) {
            throw new ApiException(
                    ErrorCode.FORBIDDEN_ACTION,
                    "Dự án tuyển gấp đang trong 5 phút độc quyền, bạn chưa được mời xem.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobResponse> getJobsByClientId(UUID clientId) {
        return redisCacheService.getListOrSet(
                RedisKeys.clientJobs(clientId),
                Duration.ofMinutes(30),
                JobResponse.class,
                () -> {
                    List<Job> jobs = jobRepository.findAllByClientId(clientId);
                    return jobs.stream().map(this::mapToResponse).collect(Collectors.toList());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobResponse> getAllOpenJobs() {
        List<Job> jobs =
                jobRepository.findAllOpenJobsFiltered(
                        JobStatus.OPEN, java.time.LocalDateTime.now().minusMinutes(5));
        return jobs.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private JobResponse mapToResponse(Job job) {
        List<SkillResponse> skillResponses =
                job.getJobSkills() == null
                        ? List.of()
                        : job.getJobSkills().stream()
                                .map(
                                        jobSkill ->
                                                SkillResponse.builder()
                                                        .id(jobSkill.getSkill().getId())
                                                        .name(jobSkill.getSkill().getName())
                                                        .yearsOfExperience(
                                                                jobSkill
                                                                        .getRequiredYearsOfExperience())
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
                .estimatedDurationMonths(job.getEstimatedDurationMonths())
                .deadline(job.getDeadline())
                .srsDocumentUrl(job.getSrsDocumentUrl())
                .srsContent(job.getSrsContent())
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
        Job job =
                jobRepository
                        .findById(jobId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!job.getClient().getId().equals(clientId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        return redisCacheService.getListOrSet(
                RedisKeys.jobRecommendations(jobId),
                Duration.ofMinutes(30),
                AiRecommendationResponse.class,
                () -> {
                    List<AiJobRecommendation> recommendations =
                            aiJobRecommendationRepository.findAllByJobIdOrderByMatchScoreDesc(
                                    jobId);

                    return recommendations.stream()
                            .map(
                                    rec -> {
                                        User dev = rec.getFreelancer();
                                        FreelancerProfile profile =
                                                freelancerProfileRepository
                                                        .findByUserId(dev.getId())
                                                        .orElse(null);
                                        List<FreelancerSkill> fSkills =
                                                freelancerSkillRepository.findByFreelancerIdIn(
                                                        List.of(dev.getId()));

                                        List<SkillExperienceDto> skillDtos =
                                                fSkills.stream()
                                                        .map(
                                                                fs ->
                                                                        SkillExperienceDto.builder()
                                                                                .skillName(
                                                                                        fs.getSkill()
                                                                                                .getName())
                                                                                .yearsOfExperience(
                                                                                        fs
                                                                                                .getYearsOfExperience())
                                                                                .build())
                                                        .toList();

                                        return AiRecommendationResponse.builder()
                                                .id(rec.getId())
                                                .freelancerId(dev.getId())
                                                .fullName(dev.getFullName())
                                                .headline(
                                                        profile != null
                                                                ? profile.getHeadline()
                                                                : "")
                                                .matchScore(rec.getMatchScore())
                                                .aiComment(rec.getAiComment())
                                                .status(rec.getStatus())
                                                .skills(skillDtos)
                                                .build();
                                    })
                            .toList();
                });
    }

    @Override
    @Transactional
    public void inviteCandidate(UUID clientId, UUID jobId, UUID recommendationId) {
        Job job =
                jobRepository
                        .findById(jobId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!job.getClient().getId().equals(clientId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        AiJobRecommendation recommendation =
                aiJobRecommendationRepository
                        .findById(recommendationId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!recommendation.getJob().getId().equals(jobId)) {
            throw new ApiException(ErrorCode.REQUEST_FAILED);
        }

        if (recommendation.getStatus() != RecommendationStatus.AUTO_MATCHED) {
            throw new ApiException(ErrorCode.REQUEST_FAILED);
        }

        recommendation.setStatus(RecommendationStatus.CLIENT_REQUESTED);
        aiJobRecommendationRepository.save(recommendation);

        notificationService.sendInviteNotification(
                recommendation.getFreelancer().getId(),
                job.getId(),
                job.getTitle(),
                recommendation.getMatchScore(),
                recommendation.getId());
        redisCacheService.delete(RedisKeys.jobRecommendations(jobId));
    }

    @Override
    @Transactional
    public AcceptInvitationResponse acceptJobInvitation(
            UUID freelancerId, UUID jobId, UUID recommendationId) {
        AiJobRecommendation recommendation =
                validateAndGetInvitation(
                        freelancerId,
                        jobId,
                        recommendationId,
                        RecommendationStatus.CLIENT_REQUESTED);

        // 1. Cập nhật trạng thái ứng viên (KHÔNG đổi JobStatus, vẫn giữ OPEN)
        recommendation.setStatus(RecommendationStatus.ACCEPTED);
        aiJobRecommendationRepository.save(recommendation);

        // 2. Tạo hoặc lấy phòng Chat
        com.sam.be.modules.chat.entity.ChatRoom room =
                chatService.getOrCreateRoom(
                        recommendation.getJob(), recommendation.getFreelancer());

        // 3. Báo cho Client biết Dev đã đồng ý -> vào chat ngay
        try {
            notificationService.sendChatOpenedNotification(
                    recommendation.getJob().getClient().getId(),
                    recommendation.getJob().getId(),
                    recommendation.getJob().getTitle(),
                    recommendation.getId(),
                    room.getId());
        } catch (Exception e) {
            log.warn("Failed to send CHAT_OPENED to client", e);
        }
        redisCacheService.delete(RedisKeys.jobRecommendations(jobId));

        // 4. Trả về roomId cho Frontend
        return AcceptInvitationResponse.builder().roomId(room.getId()).build();
    }

    @Override
    @Transactional
    public void rejectJobInvitation(UUID freelancerId, UUID jobId, UUID recommendationId) {
        AiJobRecommendation recommendation =
                validateAndGetInvitation(
                        freelancerId,
                        jobId,
                        recommendationId,
                        RecommendationStatus.CLIENT_REQUESTED);

        // Đổi trạng thái sang REJECTED
        recommendation.setStatus(RecommendationStatus.REJECTED);
        aiJobRecommendationRepository.save(recommendation);
        redisCacheService.delete(RedisKeys.jobRecommendations(jobId));
    }

    // Hàm dùng chung để validate bảo mật tránh Dev này nhận bừa job của Dev khác
    private AiJobRecommendation validateAndGetInvitation(
            UUID freelancerId,
            UUID jobId,
            UUID recommendationId,
            RecommendationStatus expectedStatus) {
        AiJobRecommendation recommendation =
                aiJobRecommendationRepository
                        .findById(recommendationId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!recommendation.getJob().getId().equals(jobId)) {
            throw new ApiException(ErrorCode.REQUEST_FAILED);
        }

        // Bắt buộc người gọi API phải chính là Freelancer được AI đề xuất
        if (!recommendation.getFreelancer().getId().equals(freelancerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        // Chỉ cho phép thao tác khi đúng trạng thái mong đợi
        if (recommendation.getStatus() != expectedStatus) {
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED, "Thao tác không hợp lệ với trạng thái hiện tại");
        }

        return recommendation;
    }

    @Override
    @Transactional
    public void devRequestChat(UUID freelancerId, UUID jobId, UUID recommendationId) {
        AiJobRecommendation recommendation =
                validateAndGetInvitation(
                        freelancerId, jobId, recommendationId, RecommendationStatus.AUTO_MATCHED);

        recommendation.setStatus(RecommendationStatus.DEV_REQUESTED);
        aiJobRecommendationRepository.save(recommendation);

        // Báo cho Client biết Dev muốn chat (bắt tay 2 chiều - chiều Dev -> Client)
        try {
            String devName =
                    recommendation.getFreelancer() != null
                            ? recommendation.getFreelancer().getFullName()
                            : "Freelancer";
            notificationService.sendDevClaimNotification(
                    recommendation.getJob().getClient().getId(),
                    recommendation.getJob().getId(),
                    recommendation.getJob().getTitle(),
                    recommendation.getId(),
                    devName);
        } catch (Exception e) {
            log.warn("Failed to send DEV_CLAIM to client", e);
        }
        redisCacheService.delete(RedisKeys.jobRecommendations(jobId));
    }

    @Override
    @Transactional
    public AcceptInvitationResponse clientAcceptDevRequest(
            UUID clientId, UUID jobId, UUID recommendationId) {
        Job job =
                jobRepository
                        .findById(jobId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!job.getClient().getId().equals(clientId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        AiJobRecommendation recommendation =
                aiJobRecommendationRepository
                        .findById(recommendationId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!recommendation.getJob().getId().equals(jobId)
                || recommendation.getStatus() != RecommendationStatus.DEV_REQUESTED) {
            throw new ApiException(ErrorCode.REQUEST_FAILED);
        }

        recommendation.setStatus(RecommendationStatus.ACCEPTED);
        aiJobRecommendationRepository.save(recommendation);

        com.sam.be.modules.chat.entity.ChatRoom room =
                chatService.getOrCreateRoom(
                        recommendation.getJob(), recommendation.getFreelancer());

        // Báo cho Freelancer biết Client đã đồng ý -> vào chat ngay
        try {
            notificationService.sendChatOpenedNotification(
                    recommendation.getFreelancer().getId(),
                    recommendation.getJob().getId(),
                    recommendation.getJob().getTitle(),
                    recommendation.getId(),
                    room.getId());
        } catch (Exception e) {
            log.warn("Failed to send CHAT_OPENED to freelancer", e);
        }
        redisCacheService.delete(RedisKeys.jobRecommendations(jobId));

        return AcceptInvitationResponse.builder().roomId(room.getId()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public AiRecommendationResponse getMyJobRecommendation(UUID freelancerId, UUID jobId) {
        AiJobRecommendation rec =
                aiJobRecommendationRepository
                        .findByJobIdAndFreelancerId(jobId, freelancerId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        User dev = rec.getFreelancer();
        FreelancerProfile profile =
                freelancerProfileRepository.findByUserId(dev.getId()).orElse(null);
        List<FreelancerSkill> fSkills =
                freelancerSkillRepository.findByFreelancerIdIn(List.of(dev.getId()));

        List<SkillExperienceDto> skillDtos =
                fSkills.stream()
                        .map(
                                fs ->
                                        SkillExperienceDto.builder()
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
    }
}
