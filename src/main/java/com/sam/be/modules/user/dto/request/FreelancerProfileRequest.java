package com.sam.be.modules.user.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerProfileRequest {

    @NotBlank(message = "Headline is required")
    private String headline;

    private String bio;

    private BigDecimal hourlyRate;

    private String githubUrl;

    private String portfolioUrl;

    @Valid
    private List<FreelancerSkillRequest> skills;
}