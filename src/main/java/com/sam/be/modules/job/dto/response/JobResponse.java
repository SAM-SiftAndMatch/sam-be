package com.sam.be.modules.job.dto.response;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.modules.skill.dto.response.SkillResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResponse {
    private UUID id;
    private UUID clientId;
    private String clientName;
    private String title;
    private String description;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private JobStatus status;
    private LocalDateTime deadline;
    private String srsDocumentUrl;
    private String riskLevel;
    private Boolean isFeatured;
    private Boolean isUrgentHiring;
    private Boolean requiresAiQa;
    private List<SkillResponse> skills;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}