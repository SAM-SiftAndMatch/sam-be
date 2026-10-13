package com.sam.be.modules.job.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class SkillExperienceDto {
    private String skillName;
    private Integer yearsOfExperience;
}
