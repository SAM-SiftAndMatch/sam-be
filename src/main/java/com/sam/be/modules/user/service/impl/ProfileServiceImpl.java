package com.sam.be.modules.user.service.impl;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.skill.entity.Skill;
import com.sam.be.modules.skill.repository.SkillRepository;
import com.sam.be.modules.user.dto.request.ClientProfileRequest;
import com.sam.be.modules.user.dto.request.FreelancerProfileRequest;
import com.sam.be.modules.user.dto.request.FreelancerSkillRequest;
import com.sam.be.modules.user.dto.response.ClientProfileResponse;
import com.sam.be.modules.user.dto.response.FreelancerProfileResponse;
import com.sam.be.modules.user.dto.response.FreelancerSkillResponse;
import com.sam.be.modules.user.entity.ClientProfile;
import com.sam.be.modules.user.entity.FreelancerProfile;
import com.sam.be.modules.user.entity.FreelancerSkill;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.ClientProfileRepository;
import com.sam.be.modules.user.repository.FreelancerProfileRepository;
import com.sam.be.modules.user.repository.FreelancerSkillRepository;
import com.sam.be.modules.user.repository.UserRepository;
import com.sam.be.modules.user.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final FreelancerProfileRepository freelancerProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FreelancerSkillRepository freelancerSkillRepository;
    private final SkillRepository skillRepository;

    @Override
    @Transactional(readOnly = true)
    public FreelancerProfileResponse getFreelancerProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        FreelancerProfile profile = freelancerProfileRepository.findByUserId(userId)
                .orElse(new FreelancerProfile());

        List<FreelancerSkill> skills = freelancerSkillRepository.findAllByFreelancerId(userId);

        return mapToFreelancerProfileResponse(user, profile, skills);
    }

    @Override
    @Transactional
    public FreelancerProfileResponse updateFreelancerProfile(UUID userId, FreelancerProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        FreelancerProfile profile = freelancerProfileRepository.findByUserId(userId)
                .orElse(FreelancerProfile.builder().user(user).build());

        profile.setHeadline(request.getHeadline());
        profile.setBio(request.getBio());
        profile.setHourlyRate(request.getHourlyRate());
        profile.setGithubUrl(request.getGithubUrl());
        profile.setPortfolioUrl(request.getPortfolioUrl());

        freelancerProfileRepository.save(profile);

        List<FreelancerSkill> existingSkills = freelancerSkillRepository.findAllByFreelancerId(userId);
        freelancerSkillRepository.deleteAll(existingSkills);

        List<FreelancerSkill> newSkills = new ArrayList<>();
        if (request.getSkills() != null && !request.getSkills().isEmpty()) {
            for (FreelancerSkillRequest skillRequest : request.getSkills()) {
                Skill skill = skillRepository.findById(skillRequest.getSkillId())
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

                FreelancerSkill freelancerSkill = FreelancerSkill.builder()
                        .id(new FreelancerSkill.FreelancerSkillId(userId, skill.getId()))
                        .freelancer(user)
                        .skill(skill)
                        .yearsOfExperience(skillRequest.getYearsOfExperience())
                        .build();

                newSkills.add(freelancerSkill);
            }
            freelancerSkillRepository.saveAll(newSkills);
        }

        return mapToFreelancerProfileResponse(user, profile, newSkills);
    }

    @Override
    @Transactional(readOnly = true)
    public ClientProfileResponse getClientProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        ClientProfile profile = clientProfileRepository.findByUserId(userId)
                .orElse(new ClientProfile());

        return mapToClientProfileResponse(user, profile);
    }

    @Override
    @Transactional
    public ClientProfileResponse updateClientProfile(UUID userId, ClientProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        ClientProfile profile = clientProfileRepository.findByUserId(userId)
                .orElse(ClientProfile.builder().user(user).build());

        profile.setCompanyName(request.getCompanyName());
        profile.setIndustry(request.getIndustry());
        profile.setWebsiteUrl(request.getWebsiteUrl());
        profile.setDescription(request.getDescription());

        clientProfileRepository.save(profile);

        return mapToClientProfileResponse(user, profile);
    }

    private FreelancerProfileResponse mapToFreelancerProfileResponse(User user, FreelancerProfile profile, List<FreelancerSkill> skills) {
        List<FreelancerSkillResponse> skillResponses = skills.stream()
                .map(fs -> FreelancerSkillResponse.builder()
                        .skillId(fs.getSkill().getId())
                        .skillName(fs.getSkill().getName())
                        .yearsOfExperience(fs.getYearsOfExperience())
                        .build())
                .collect(Collectors.toList());

        return FreelancerProfileResponse.builder()
                .id(profile.getId())
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .headline(profile.getHeadline())
                .bio(profile.getBio())
                .hourlyRate(profile.getHourlyRate())
                .githubUrl(profile.getGithubUrl())
                .portfolioUrl(profile.getPortfolioUrl())
                .skills(skillResponses)
                .build();
    }

    private ClientProfileResponse mapToClientProfileResponse(User user, ClientProfile profile) {
        return ClientProfileResponse.builder()
                .id(profile.getId())
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .companyName(profile.getCompanyName())
                .industry(profile.getIndustry())
                .websiteUrl(profile.getWebsiteUrl())
                .description(profile.getDescription())
                .build();
    }
}