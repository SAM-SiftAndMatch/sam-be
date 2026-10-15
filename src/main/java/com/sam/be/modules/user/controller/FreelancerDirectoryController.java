package com.sam.be.modules.user.controller;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.response.PageRequests;
import com.sam.be.common.response.PageResponse;
import com.sam.be.modules.user.dto.response.FreelancerSkillResponse;
import com.sam.be.modules.user.dto.response.PublicFreelancerResponse;
import com.sam.be.modules.user.entity.FreelancerProfile;
import com.sam.be.modules.user.entity.FreelancerSkill;
import com.sam.be.modules.user.repository.FreelancerProfileRepository;
import com.sam.be.modules.user.repository.FreelancerSkillRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/freelancers")
@PreAuthorize("hasRole('CLIENT')")
@RequiredArgsConstructor
public class FreelancerDirectoryController {
    private final FreelancerProfileRepository profileRepository;
    private final FreelancerSkillRepository skillRepository;

    @GetMapping("/page")
    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<PublicFreelancerResponse>> page(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer skillId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String query = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
        var profiles = profileRepository.findDirectoryPage(query, skillId,
                PageRequests.of(page, size, Sort.by(Sort.Order.desc("user.createdAt"), Sort.Order.desc("id"))));
        List<UUID> ids = profiles.getContent().stream().map(p -> p.getUser().getId()).toList();
        Map<UUID, List<FreelancerSkillResponse>> skills = ids.isEmpty() ? Map.of()
                : skillRepository.findByFreelancerIdIn(ids).stream().collect(Collectors.groupingBy(
                        fs -> fs.getFreelancer().getId(), Collectors.mapping(this::toSkill, Collectors.toList())));
        return ApiResponse.<PageResponse<PublicFreelancerResponse>>builder()
                .result(PageResponse.from(profiles.map(p -> toPublic(p, skills.getOrDefault(p.getUser().getId(), List.of())))))
                .build();
    }

    @GetMapping("/{freelancerId}")
    @Transactional(readOnly = true)
    public ApiResponse<PublicFreelancerResponse> get(@PathVariable UUID freelancerId) {
        FreelancerProfile profile = profileRepository.findByUserId(freelancerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!Boolean.TRUE.equals(profile.getUser().getIsActive())
                || profile.getUser().getRole() != com.sam.be.common.constant.enums.UserRole.FREELANCER) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return ApiResponse.<PublicFreelancerResponse>builder().result(toPublic(profile,
                skillRepository.findAllByFreelancerId(freelancerId).stream().map(this::toSkill).toList())).build();
    }

    private PublicFreelancerResponse toPublic(FreelancerProfile p, List<FreelancerSkillResponse> skills) {
        return new PublicFreelancerResponse(p.getUser().getId(), p.getUser().getFullName(),
                p.getHeadline(), p.getBio(), p.getHourlyRate(), p.getGithubUrl(), p.getPortfolioUrl(), skills);
    }

    private FreelancerSkillResponse toSkill(FreelancerSkill fs) {
        return new FreelancerSkillResponse(fs.getSkill().getId(), fs.getSkill().getName(), fs.getYearsOfExperience());
    }
}
