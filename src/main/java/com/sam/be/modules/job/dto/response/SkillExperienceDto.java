package com.sam.be.modules.job.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SkillExperienceDto {
    private String skillName;
    private Integer yearsOfExperience;
}