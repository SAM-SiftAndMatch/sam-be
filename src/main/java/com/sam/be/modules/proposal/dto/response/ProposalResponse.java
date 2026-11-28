package com.sam.be.modules.proposal.dto.response;

import com.sam.be.common.constant.enums.ProposalStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class ProposalResponse {
    private UUID id;
    private UUID jobId;
    private String jobTitle;
    private UUID freelancerId;
    private String freelancerName;
    private String headline;
    private String freelancerBio;
    private List<String> freelancerSkills;
    private BigDecimal hourlyRate;
    private String portfolioUrl;
    private String githubUrl;
    private String coverLetter;
    private BigDecimal proposedBudget;
    private Integer estimatedDurationDays;
    private String attachmentUrl;
    private String attachmentName;
    private ProposalStatus status;
    private LocalDateTime createdAt;
}
