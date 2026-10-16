package com.sam.be.modules.chat.dto;

import com.sam.be.common.constant.enums.ContractStatus;
import com.sam.be.common.constant.enums.JobStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record WorkspaceSummaryDto(
        UUID roomId,
        UUID jobId,
        String jobTitle,
        JobStatus jobStatus,
        UUID selectedFreelancerId,
        UUID clientId,
        String clientName,
        UUID freelancerId,
        String freelancerName,
        UUID contractId,
        ContractStatus contractStatus,
        BigDecimal agreedAmount,
        String reviewStatus) {}
