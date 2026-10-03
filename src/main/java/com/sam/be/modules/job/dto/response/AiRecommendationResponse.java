package com.sam.be.modules.job.dto.response;

import com.sam.be.common.constant.enums.RecommendationStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiRecommendationResponse {
    private UUID id;
    private UUID freelancerId;
    private String fullName;
    private String headline;
    private BigDecimal matchScore;
    private String aiComment;
    private RecommendationStatus status;
    private List<SkillExperienceDto> skills;
}
