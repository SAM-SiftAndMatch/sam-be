package com.sam.be.modules.skill.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillResponse {
    private Integer id;
    private String name;

    /** Số năm kinh nghiệm yêu cầu (chỉ có khi skill nằm trong Job/JobSkill) */
    private Integer yearsOfExperience;
}
