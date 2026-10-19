package com.sam.be.modules.user.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicFreelancerResponse(UUID userId, String fullName, String headline,
        String bio, BigDecimal hourlyRate, String githubUrl, String portfolioUrl,
        List<FreelancerSkillResponse> skills) {}
