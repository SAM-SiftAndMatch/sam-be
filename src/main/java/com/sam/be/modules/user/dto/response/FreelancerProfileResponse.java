package com.sam.be.modules.user.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerProfileResponse {

    private UUID id;

    private UUID userId;

    private String fullName;

    private String email;
    private String contractName;
    private String contactEmail;
    private String phoneNumber;
    private java.time.Instant policyAcceptedAt;
    private String policyVersion;

    private String headline;

    private String bio;

    private BigDecimal hourlyRate;

    private String githubUrl;

    private String portfolioUrl;

    private List<FreelancerSkillResponse> skills;
}
