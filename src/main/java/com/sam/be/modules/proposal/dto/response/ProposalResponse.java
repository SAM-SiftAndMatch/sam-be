package com.sam.be.modules.proposal.dto.response;

import com.sam.be.common.constant.enums.ProposalStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProposalResponse {
    private UUID id;
    private UUID jobId;
    private String jobTitle;
    private UUID freelancerId;
    private String freelancerName;
    private String headline;
    private String coverLetter;
    private BigDecimal proposedBudget;
    private Integer estimatedDurationDays;
    private String attachmentUrl;
    private String attachmentName;
    private ProposalStatus status;
    private LocalDateTime createdAt;
}
