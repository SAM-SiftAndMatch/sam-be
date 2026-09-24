package com.sam.be.modules.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerSkillResponse {

    private Integer skillId;

    private String skillName;

    private Integer yearsOfExperience;
}